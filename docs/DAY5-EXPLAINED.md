# Day 5 — CI GitHub Actions + Terraform pipeline

> Repo: **`badmintonHub`** (app). Tài liệu này **tự chứa**: đọc từ trên xuống là làm được.
> Khái niệm hạ tầng nền (Terraform, state, IRSA, EKS) ở [`DAY3-EXPLAINED.md`](DAY3-EXPLAINED.md).
> Runbook deploy tay lên EKS ở `badmintonHub-gitops/docs/DAY4-EXPLAINED.md`.

---

## 0. Day 5 đổi cái gì

**Trước Day 5** — mỗi lần muốn đưa code lên cụm, bạn gõ tay:

```bash
./scripts/build-push-ecr.sh                    # build 9 image, đẩy ECR, tag = git SHA
cd ../badmintonHub-gitops
vim values/booking-service-staging.yaml        # sửa image.tag thành SHA vừa rồi
git commit && git push
```

Ba vấn đề:
1. **Không có gì chặn code chưa test** lên `main`.
2. Tag ảnh lấy từ `git rev-parse` **trên máy bạn** — chỉ đúng khi working tree sạch và đúng nhánh.
3. Bước sửa values dễ gõ sai tên file. **Sai tên file thì không có lỗi ở đâu cả** — commit vẫn vào repo,
   CI vẫn xanh, chỉ là ArgoCD không đọc nên không deploy gì.

**Sau Day 5** — merge một PR là xong. Vài phút sau ECR có image mới, repo gitops có commit bump tự động.

---

## 1. Đang dựng cái gì

```
repo badmintonHub (app)                       repo badmintonHub-gitops
────────────────────────                      ────────────────────────
feature/*  ──push──►  (không chạy gì)                    ─
     │
   mở PR ──────────►  CI: validate                       ─
     │                test · trivy · sonar
     │                KHÔNG chạm AWS
     │
   merge main ─────►  CI: release
                      build amd64 → trivy
                      push ECR :SHA
                      ─────ghi image.tag────►  values/<svc>-staging.yaml
                                                          │
                                                    ArgoCD (Day 6)
                                                          ▼
                                                   ns staging tự sync

   terraform/** ─PR─►  terraform.yml: plan → comment vào PR
                └─bấm tay──►  apply / destroy
```

**Điều quan trọng nhất phải nắm**: CI **không bao giờ** chạm vào cụm EKS. Nó không có `kubectl`, không có
quyền vào cluster. Quyền AWS duy nhất nó cần là **đẩy image lên ECR**. Việc đưa thay đổi vào cụm là của
ArgoCD — chạy *bên trong* cụm, tự *kéo* về. Đó là điểm khác biệt giữa GitOps và CI-push truyền thống: nếu
CI bị chiếm quyền, kẻ tấn công cũng không có đường vào cụm.

---

## 2. OIDC — vì sao không dùng access key

**Cách cũ**: tạo AWS access key → dán vào GitHub Secrets. Key đó **sống mãi** cho tới khi có người nhớ ra
mà xoá. Repo này public, và một key lộ ra là mất cả tài khoản AWS.

**OIDC đảo ngược hướng tin cậy.** Không có bí mật nào được lưu ở đâu cả:

```
1. Job CI chạy  →  GitHub tự ký một token: "run này đến từ repo phucgigital03/BadmintonHub,
                   nhánh refs/heads/main". Token sống vài phút.
2. Job đưa token đó cho AWS STS.
3. AWS kiểm chữ ký của GitHub (đã khai báo tin cậy ở bước tạo OIDC provider),
   rồi so chuỗi mô tả với trust policy của role.
4. Khớp  →  cấp credential tạm sống 1 giờ.  Không khớp  →  từ chối.
```

Chuỗi mô tả đó gọi là **`sub`**, và nó là thứ **duy nhất** chặn repo khác mượn role của bạn:

| Sự kiện | `sub` GitHub sinh ra |
|---|---|
| push / workflow_dispatch trên `main` | `repo:phucgigital03/BadmintonHub:ref:refs/heads/main` |
| pull request | `repo:phucgigital03/BadmintonHub:pull_request` |

Vì hai chuỗi **khác nhau**, ta tạo **3 role riêng** thay vì một role vạn năng:

| Role | Tin `sub` nào | Quyền | Dùng ở đâu |
|---|---|---|---|
| `badminton-gha-ecr` | `...:ref:refs/heads/main` | chỉ push 9 ECR repo | job release của `ci.yml` |
| `badminton-gha-tf-plan` | `...:pull_request` | `ReadOnlyAccess` | job `plan` của `terraform.yml` |
| `badminton-gha-tf-apply` | `...:ref:refs/heads/main` | `AdministratorAccess` | apply/destroy bấm tay |

⇒ Một PR **không thể** đẩy image, kể cả khi người mở PR sửa file workflow trong chính PR đó — vì token của
PR không mang đúng `sub`. **Tuyến phòng thủ nằm ở trust policy trên AWS, không phải ở dòng `if` trong YAML.**

💰 IAM role và OIDC provider **không tính tiền**.

---

## 3. Việc bạn phải làm — 6 bước

> Làm **tuần tự**. Bước 6 bắt buộc phải sau lần CI chạy đầu tiên (GitHub chỉ cho chọn status check mà nó
> đã từng thấy). 🔴 **Đừng push lên `main` trước khi xong bước 5** — push vào `main` kích hoạt nhánh
> *release*, nó sẽ chết ở bước assume role vì chưa có secret.

### Bước 1 — Tạo OIDC provider + 3 role trên AWS

```bash
cd terraform/bootstrap
terraform init
```

**Làm gì**: nối lại backend S3 (`s3://badminton-tfstate-apse1/bootstrap/terraform.tfstate`).

**Đúng thấy gì**: `Successfully configured the backend "s3"` → `Terraform has been successfully initialized!`

**Sai thấy gì**: nếu hỏi `Do you want to copy existing state to the new backend?` → trả lời **`no`**
(state thật đã ở S3; file `terraform.tfstate` local là 0 byte, tàn dư của lần migrate 2026-08-04).

```bash
terraform plan
```

**Làm gì**: đọc `github-oidc.tf`, so với thực tế trên AWS, in ra những gì sắp đổi.

**Đúng thấy gì**: `Plan: 17 to add, 0 to change, 9 to destroy`

| | Gồm những gì |
|---|---|
| **8 trong số 17 add** | 1 `aws_iam_openid_connect_provider` + 1 `aws_iam_policy` + **3** `aws_iam_role` + **3** `aws_iam_role_policy_attachment` |
| **9 destroy + 9 add còn lại** | **9** × `aws_ecr_lifecycle_policy` bị **replace** (`-/+`) vì đổi `ecr_keep_last_images` 10 → 20 |

> 🔑 **Vì sao "replace" chứ không phải "change"** — chỗ này rất dễ hoảng. Trong AWS provider, trường
> `policy` của `aws_ecr_lifecycle_policy` là **ForceNew**: sửa một ký tự trong JSON là Terraform xoá
> policy cũ rồi tạo policy mới, chứ không cập nhật tại chỗ. Plan sẽ in nguyên văn
> `} # forces replacement`.
>
> **Đây KHÔNG phải xoá repo hay xoá image.** Thứ bị xoá là *quy tắc dọn ảnh cũ*, không phải cái kho.
> Kiểm chứng trong plan: 9 dòng `aws_ecr_repository.this[...]` chỉ xuất hiện ở phần
> `Refreshing state...`, **không** có dòng nào `must be replaced` / `will be destroyed`.

**Cách tự kiểm trước khi gõ `yes`** — chạy đúng 2 lệnh này, cả hai phải ra như mô tả:

```bash
terraform plan -no-color | grep -E '^  # aws_' | sort | uniq -c
#   → chỉ được thấy: 9 dòng aws_ecr_lifecycle_policy "must be replaced"
#     + 8 dòng aws_iam_* "will be created". KHÔNG có gì khác.

terraform plan -no-color | grep -E 'aws_ecr_repository.*(destroyed|replaced)'
#   → phải RỖNG. Có dòng nào là DỪNG NGAY.
```

🔴 **Sai thấy gì**: `aws_s3_bucket.tfstate`, `aws_dynamodb_table.tflock`, hoặc **`aws_ecr_repository`**
xuất hiện kèm `destroyed`/`replaced` → **DỪNG, đừng apply**. Đó là sổ ghi chép và 9 kho ảnh của cả dự án.
*(S3 + DynamoDB có `prevent_destroy` nên Terraform tự chặn; **ECR repo thì KHÔNG có** — nên dòng grep thứ
hai ở trên là lớp bảo vệ duy nhất.)*

```bash
terraform apply          # gõ yes
# `terraform output <tên>` chỉ nhận ĐÚNG MỘT tên mỗi lần. Lấy cả 4 giá trị cần
# cho GitHub Secrets bằng một lệnh:
terraform output -json | python3 -c "
import json,sys; d=json.load(sys.stdin)
m={'gha_ecr_role_arn':'AWS_ROLE_ECR_ARN','gha_tf_plan_role_arn':'AWS_ROLE_TF_PLAN_ARN',
   'gha_tf_apply_role_arn':'AWS_ROLE_TF_APPLY_ARN','ecr_registry_url':'ECR_REGISTRY'}
[print(f'{n:24s} = {d[k][\"value\"]}') for k,n in m.items()]"
```

**Đúng thấy gì**: `Apply complete! Resources: 17 added, 0 changed, 9 destroyed.` rồi in 3 chuỗi
`arn:aws:iam::<account-id>:role/badminton-gha-*` — đây là 3 secret của bước 5.
Quên thì chạy lại `terraform output` bất cứ lúc nào.

**Sai thấy gì**:

- `EntityAlreadyExists ... OpenIDConnectProvider` ⇒ tài khoản đã có provider GitHub tạo tay từ trước.
  Mỗi tài khoản chỉ được **một** provider cho mỗi URL, nên lúc đó phải `terraform import` chứ không tạo thêm.
- `ValidationError: Value at 'description' failed to satisfy constraint` ⇒ description của `aws_iam_role`
  có ký tự ngoài ASCII/Latin-1 (xem §7 ⑤). **Apply đã chết giữa chừng nhưng không hỏng gì**: OIDC provider,
  IAM policy và 9 lifecycle policy đã tạo xong và nằm trong state. Sửa description rồi `terraform apply`
  lại — Terraform chỉ làm nốt phần còn thiếu (`Plan: 6 to add`).

> 🔁 **Nguyên tắc chung khi apply chết giữa chừng**: đừng dọn tay, đừng destroy. Sửa nguyên nhân rồi
> `apply` lại. State ghi lại chính xác cái gì đã xong, nên lần chạy sau chỉ bù phần thiếu.

💰 **$0.**

---

### Bước 2 — Deploy key cho repo gitops

CI phải **ghi** commit vào một repo **khác**. `GITHUB_TOKEN` mặc định chỉ có quyền trên repo đang chạy, nên
cần một chìa riêng.

```bash
ssh-keygen -t ed25519 -C "gha-bump" -f ~/.ssh/gitops_deploy -N ""
cat ~/.ssh/gitops_deploy.pub     # phần CÔNG KHAI
cat ~/.ssh/gitops_deploy         # phần BÍ MẬT
```

| Phần | Dán vào đâu |
|---|---|
| `.pub` (công khai) | repo **BadmintonHub-GitOps** → Settings → Deploy keys → Add deploy key → ✅ **Allow write access** |
| không đuôi (bí mật) | repo **BadmintonHub** → Settings → Secrets and variables → Actions → `GITOPS_DEPLOY_KEY` |

⚠️ Dán **toàn bộ** phần bí mật, kể cả 2 dòng `-----BEGIN...-----` / `-----END...-----`.

**Sai thấy gì**: quên tick *Allow write access* → job `bump` chạy tới bước push rồi chết với
`ERROR: The key you are authenticating with has been marked as read only`.

---

### Bước 3 — SonarCloud

1. [sonarcloud.io](https://sonarcloud.io) → **Log in with GitHub** → import repo `BadmintonHub`.
2. Vào project → **Administration → Analysis Method** → **tắt "Automatic Analysis"**.
   *(Không tắt thì Sonar tự quét song song với CI, hai bên ghi đè kết quả nhau.)*
3. Đối chiếu **Organization Key** và **Project Key** với 2 dòng trong `pom.xml`:
   ```xml
   <sonar.organization>phucgigital03</sonar.organization>
   <sonar.projectKey>phucgigital03_BadmintonHub</sonar.projectKey>
   ```
   Khác thì sửa `pom.xml` cho khớp.
4. Avatar → **My Account → Security** → sinh token → secret `SONAR_TOKEN`.

📌 **Quality gate đang để KHÔNG chặn merge** (`continue-on-error: true`). Lý do: repo chưa có JaCoCo và
`user-service` chưa có test nào, mà gate mặc định của Sonar đòi coverage ≥80% trên code mới ⇒ để chặn thì
**mọi PR đỏ ngay từ ngày đầu**, và kết cục thực tế là bạn tắt gate — mất luôn giá trị của nó. Siết lại sau
bằng cách bỏ `continue-on-error` và thêm `-Dsonar.qualitygate.wait=true`.

---

### Bước 4 — Telegram

1. Chat với **@BotFather** → `/newbot` → đặt tên → nhận **token**.
2. Nhắn một tin bất kỳ cho bot vừa tạo *(bot không nhắn trước cho người lạ được)*.
3. Mở `https://api.telegram.org/bot<TOKEN>/getUpdates` → lấy `message.chat.id`.
4. Hai secret: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`.

Bỏ qua bước này cũng được — job `notify` tự phát hiện thiếu secret và bỏ qua, **không làm CI đỏ**.

---

### Bước 5 — Nạp 8 secret

GitHub → repo **BadmintonHub** → Settings → Secrets and variables → **Actions**:

| Secret | Giá trị |
|---|---|
| `AWS_ROLE_ECR_ARN` | output bước 1 |
| `AWS_ROLE_TF_PLAN_ARN` | output bước 1 |
| `AWS_ROLE_TF_APPLY_ARN` | output bước 1 |
| `ECR_REGISTRY` | `<account-id>.dkr.ecr.ap-southeast-1.amazonaws.com` |
| `GITOPS_DEPLOY_KEY` | bước 2 |
| `SONAR_TOKEN` | bước 3 |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | bước 4 |

> Dùng **Secrets** chứ không phải **Variables** kể cả với ARN: chúng chứa account ID, mà repo này public.

---

### Bước 6 — Branch protection *(làm SAU khi CI xanh lần đầu)*

Settings → Branches → Add rule cho `main`:
- ✅ Require a pull request before merging
- ✅ Require status checks to pass → chọn **`ci-gate`**

🔴 **Chỉ chọn `ci-gate`. Đừng chọn các job `Build <service>`.** Vì matrix chỉ chạy service có thay đổi:
một PR chỉ sửa `frontend/` sẽ khiến 8 job kia **không bao giờ báo cáo** → GitHub kẹt ở
*"Expected — waiting for status"*: không đỏ, không xanh, **không merge được**. Job `ci-gate` chạy với
`if: always()` nên luôn báo cáo, và nó tự tổng hợp kết quả của các job kia.

---

## 4. Nghiệm thu

| # | Việc | Đúng thấy gì | Sai thấy gì |
|---|---|---|---|
| 1 | Mở PR sửa 1 file trong `booking-service/` | Chỉ chạy `Build booking-service` + `SonarCloud` + `ci-gate` | 8 job kia treo "Expected" ⇒ required check đặt sai (bước 6) |
| 2 | Xem log PR đó | Không step nào tên `Lấy credential AWS`; `aws ecr describe-images` không có tag mới | Có image mới ⇒ điều kiện `if: github.event_name == 'push'` bị sửa |
| 3 | Merge PR | Job `bump` chạy xanh | xem bảng §5 |
| 4 | `git -C ../badmintonHub-gitops pull && git log -1 -p` | Commit của `badminton-ci[bot]`, diff **đúng 1 dòng** `tag:` | nhiều dòng ⇒ sed sai; không có commit ⇒ deploy key thiếu quyền ghi |
| 5 | Telegram | Nhận tin kèm link run | không có tin ⇒ sai `chat_id` *(job vẫn xanh — cố ý)* |
| 6 | PR sửa `terraform/vpc.tf` | Bot comment kết quả `plan` vào PR | `AccessDenied` ⇒ `sub` của role plan phải là `:pull_request` |

> ⚠️ **DoD của Day 5 dừng ở #4.** ArgoCD chưa được cài (Day 6), nên commit bump sẽ **nằm im** trong repo
> gitops, chưa deploy gì cả. Đó là **đúng**, không phải hỏng.

---

## 5. Bảng đọc lỗi

| Thông báo | Nguyên nhân thật |
|---|---|
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | `sub` không khớp. Kiểm `github_owner`/`github_repo` trong `terraform/bootstrap/variables.tf` — **phân biệt hoa/thường** |
| `Credentials could not be loaded` | Job thiếu `permissions: id-token: write` |
| `no basic auth credentials` khi push | Policy thiếu `ecr:GetAuthorizationToken` trên `"*"` — action này **không hỗ trợ resource-level** |
| `denied: ... is not authorized to perform: ecr:PutImage` | Tên ECR repo không nằm trong `var.ecr_repositories` |
| `exec format error` (khi pod chạy) | Image không phải amd64. Bước push đã có nghiệm thu kiến trúc — nếu lọt qua được thì kiểm `platforms:` |
| `Could not find a valid Docker environment` (Testcontainers) | Docker daemon quá mới so với docker-java. Trên `ubuntu-latest` chưa gặp; xem `.claude/rules/testing.md` |
| Job `bump`: `values/<svc>-staging.yaml không tồn tại` | Hợp đồng CI↔ArgoCD đã lệch — có người đổi tên file bên repo gitops |
| Job `bump`: `+N/-N dòng (kỳ vọng +1/-1)` | `sed` sửa nhầm nhiều dòng. Kiểm neo `^  tag: ` (đúng 2 space) |
| `error: failed to push some refs` sau 3 lần thử | Có promote PR merge liên tục cùng lúc. Chạy lại job |
| Sonar: `Project not found` | `sonar.projectKey` trong `pom.xml` khác key thật trên SonarCloud |
| `terraform plan`: `AccessDeniedException ... dynamodb:PutItem` | Thiếu `-lock=false`. Role plan chỉ đọc, khoá state là thao tác ghi |
| `terraform apply`: `ValidationError ... 'description' failed to satisfy constraint` | `description` của `aws_iam_role` có chữ có dấu tiếng Việt hoặc dấu `—`. Chỉ ROLE bị siết, POLICY thì không (§7 ⑤). Sửa rồi `apply` lại — state giữ nguyên phần đã xong |

---

## 6. Ba lệch có chủ đích so với `planning/Planning_CICD.md` §Day 5

**⓪ `npm run lint` chạy nhưng KHÔNG chặn merge.** Đo được 2026-08-07: **12 lỗi có sẵn** ở 7 file frontend
(11 × `react-hooks/purity` — gọi `Date.now()` trong thân component — và 1 × `rules-of-hooks`). Bật gate ngay
= job đỏ mọi lần merge vì nợ cũ. Type-check **không mất**: `npm run build` bên trong image vẫn chạy `tsc -b`.
Siết thành gate thật = xoá dòng `continue-on-error` ở step `Lint frontend`.

**① Bỏ Checkstyle.** Spec ghi có, nhưng repo chưa có `checkstyle.xml` nào. Bật ruleset chuẩn lên 15 module
code có sẵn = hàng trăm lỗi ⇒ CI đỏ vĩnh viễn ⇒ bạn sẽ tắt nó. Đây đúng cái bẫy mà chính spec đã cảnh báo
với Trivy. Sonar đã phủ style + code smell + bug.

**② Sonar phân tích 1 lần trên toàn reactor, không nằm trong matrix.** Sonar gắn kết quả vào **một**
project key — chạy per-service sẽ là nhiều phân tích cục bộ ghi đè lên nhau, số liệu vô nghĩa.

**③ `ecr_keep_last_images` 10 → 20.** Lifecycle của ECR giữ N image gần nhất với `tagStatus = "any"`.
Trước Day 5 bạn hiếm khi chạm trần; **từ Day 5 mỗi merge đều đẩy thêm một image**. Repo `frontend` đã có
6 image — vài merge nữa là image mà `values/frontend-prod.yaml` đang trỏ tới bị **xoá khỏi ECR**, và prod
sẽ `ImagePullBackOff` ở lần pod restart kế tiếp mà log ứng dụng không nói gì về ECR.

💰 ECR $0.10/GB-tháng: giữ 10 ≈ **$1.1/tháng** · giữ 20 ≈ **$2.0/tháng** · giữ 30 ≈ **$2.9/tháng**.
Muốn tiết kiệm thì hạ về 10 trong `terraform/bootstrap/variables.tf` và nhớ kiểm image prod còn tồn tại
trước mỗi buổi demo.

---

## 7. Năm quyết định kỹ thuật đáng nhớ

**① Tag = short SHA 7 ký tự, không phải `github.sha`.** `scripts/build-push-ecr.sh` dùng
`git rev-parse --short HEAD`, và values đang ghi `tag: 5a7067c`. Nếu CI dùng SHA 40 ký tự thì **cùng một
commit tồn tại 2 tag khác nhau** trong ECR — rollback mất khả năng truy vết "cụm đang chạy code nào".

**② `sed` chứ không `yq` khi bump.** `yq` viết lại cả file: **xoá sạch comment tiếng Việt** giải thích
quy ước, và thêm dấu nháy (`tag: "abc1234"`) làm lệch định dạng với 26 file còn lại. Commit của bot phải là
diff **đúng 1 dòng**, không hơn.

**③ Bump ở MỘT job sau matrix, không phải trong từng job matrix.** Ba service đổi cùng lúc = ba `git push`
đồng thời vào cùng một repo ⇒ non-fast-forward ⇒ job đỏ ngẫu nhiên. Một job, một commit, gom hết.

**④ Trivy quét TRƯỚC khi push.** Image lỗi không bao giờ được nằm trong ECR. Và `ignore-unfixed: true` là
bắt buộc — base `eclipse-temurin:21-jre` gần như luôn có HIGH CVE **chưa có bản vá**; gate chỉ nên đỏ vì
thứ bạn **hành động được**.

**⑤ `description` của `aws_iam_role` phải là ASCII thuần — nhưng của `aws_iam_policy` thì không.**
`iam:CreateRole` chỉ nhận U+0020..U+007E và U+00A1..U+00FF, nên chữ có dấu tiếng Việt (`đ` U+0111,
`ẩ` U+1EA9, `ề` U+1EC1) và cả gạch dài `—` (U+2014) đều bị từ chối bằng `ValidationError`. Bất đối xứng
dễ nhầm nhất: **`iam:CreatePolicy` lại nhận tiếng Việt bình thường** — `aws_iam_policy.external_secrets`
trong `terraform/irsa.tf` đã chạy thật từ Day 3 với description tiếng Việt, nên rất dễ tưởng role cũng
được. Cảnh báo đã cắm ngay trên 3 role trong `github-oidc.tf`.

---

## 8. Chi phí

| Hạng mục | Tiền |
|---|---|
| GitHub Actions | **$0** — repo public, không giới hạn phút |
| SonarCloud | **$0** — free cho repo public |
| IAM role + OIDC provider | **$0** |
| ECR (giữ 20 image/repo) | ~**$2.0/tháng** |
| **Chạy Day 5** | **$0** — không cần dựng cụm EKS |

Chỉ từ **Day 6** trở đi mới cần cụm sống để ArgoCD sync.

---

## 9. Việc còn để lại

| Việc | Vì sao chưa làm | Thuộc về |
|---|---|---|
| JaCoCo + coverage | Root pom đã khai `<argLine>` cho surefire/failsafe (cần cho Mockito trên JDK 23). Thêm `jacoco:prepare-agent` mà không đổi thành `@{argLine} -Dnet...` sẽ **ghi đè** property của agent ⇒ coverage luôn 0 **mà không báo lỗi** | khi có test đủ |
| Siết quality gate thành chặn merge | Cần coverage trước | sau JaCoCo |
| Nút destroy tự lo đủ 5 bước teardown | ALB/EBS do *controller* tạo **không nằm trong tf state** ⇒ `terraform destroy` không biết chúng tồn tại. Hiện job chỉ in cảnh báo | **Day 7** |
| Cache `.m2` xuyên runner cho bước `docker build` | Cache mount `/root/.m2` là state cục bộ của builder; `type=gha` chỉ khôi phục **layer cache**, không mang nội dung cache mount ⇒ mỗi build image vẫn tải lại dependency (~1–2 phút) | tối ưu, không gấp |
