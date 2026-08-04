# Day 3 — Runbook thao tác: bắt đầu từ đâu, gõ gì, dừng ở đâu

> **File này tự chứa.** Mọi thứ cần để đi từ *"chưa có gì"* đến *"EKS chạy rồi và đã destroy sạch"* đều nằm ở đây: click path trong Console, lệnh copy-paste được, bảng verify, và cách kiểm bill về 0. Không phải mở tài liệu nào khác.
>
> Tuỳ chọn duy nhất: [`DAY3-EXPLAINED.md`](DAY3-EXPLAINED.md) *(cùng thư mục)* trả lời **"vì sao"** — Terraform là gì, IRSA hoạt động thế nào, vì sao né NAT Gateway. Không đọc vẫn làm được Day 3; đọc thì hiểu mình đang làm gì.

> ⚠️ **Region = `ap-southeast-1` (Singapore)** xuyên suốt. Đây là bẫy verify phổ biến nhất: Console mở **đúng trang** nhưng góc phải trên đang ở region khác → thấy danh sách rỗng và tưởng code hỏng. Kiểm region **trước** khi kết luận bất cứ thứ gì thiếu.

---

## §A — Sự thật về tiền (đọc trước khi gõ `apply` lần đầu)

🔴 **Dự án này KHÔNG chạy trong Free Tier.** Đừng nghĩ "tài khoản mới 12 tháng miễn phí" sẽ che được. "Rẻ" đến từ **kỷ luật `destroy` sau mỗi buổi** — không có mẹo kỹ thuật nào thay thế.

| Hạng mục | Giá xấp xỉ | Free-Tier? | Ai tạo | Sống sót `destroy`? |
|---|---|---|---|---|
| **EKS control plane** | ~**$0.10/giờ** (~$73/tháng) — tính **kể cả khi 0 pod** | ❌ | Terraform *(ephemeral)* | ❌ |
| EC2 node `t3.xlarge` **spot** ×2 | ~**$0.13/giờ** tổng | ❌ | Terraform *(ephemeral)* | ❌ |
| ALB | ~$0.0225/giờ + LCU | ❌ | AWS LB Controller *(từ Ingress)* | ❌ *nếu xoá Ingress trước* |
| ~~NAT Gateway~~ | ~~$0.045/giờ~~ | ❌ | **né hoàn toàn — không tạo** | — |
| EBS gp3 | ~$0.08/GB-tháng · **vẫn tính tiền dù pod đã chết** | ✅ 30 GB free | EBS CSI *(từ PVC)* | ❌ *nếu xoá PVC trước* |
| ECR × 9 | ~$0.10/GB-tháng · 9 image Java ≈ 3 GB | ✅ 500 MB free | Terraform *(**bootstrap**)* | ✅ ~**$0.30/tháng** |
| S3 + DynamoDB *(state)* | ~vài cent | ✅ phần lớn free | Terraform *(**bootstrap**)* | ✅ |
| SSM Parameter Store | **$0** với standard param | ✅ | **Bạn, bằng tay** *(Day 6)* | ✅ |
| Route53 zone *(Day 8)* | $0.50/zone-tháng · chạy **24/7 kể cả khi cụm đã destroy** | ❌ | Terraform *(**bootstrap**)* | ✅ |
| ACM certificate *(Day 8)* | **$0** khi dùng với ALB | ✅ | Terraform *(**bootstrap**)* | ✅ |

**Con số phải nhớ:**

| Kịch bản | Tiền |
|---|---|
| Cụm sống | **~$0.25/giờ** |
| 1 buổi trọn gói (apply 15' + demo 10' + destroy 10') | ≈ **$0.15** |
| Chạy 3 giờ | ≈ $0.75 |
| **Quên tắt 1 tháng** | ≈ **$180** |
| Thường trực giữa các buổi (đã destroy) | **$0.30/tháng** (ECR) → $0.80/tháng sau Day 8 (+ Route53 zone) |

> 🔴 **`destroy` phải CHẠY XONG**, không phải "chạy rồi `Ctrl-C`". Ctrl-C giữa chừng để lại state nửa vời — **tệ hơn không chạy**, vì tài nguyên đã tạo vẫn tính tiền mà Terraform không còn quản đúng.

---

## Bản đồ toàn phiên

| Phase | Ở đâu | ~Thời gian | Tiền | Bỏ qua được? |
|---|---|---|---|---|
| **0** 🔴 Xin tăng quota vCPU | Console | 5' **+ chờ tới 48h** | $0 | ❌ **Không** — đường găng thật |
| **1** Budget · MFA · IAM user | Console | 20' | $0 | ❌ |
| **2** Cài `aws` + `terraform` | Terminal | 10' | $0 | ❌ |
| **3** Vá `.gitignore` | Repo | 2' | $0 | ❌ — repo sắp PUBLIC |
| **4** Paste prompt → Claude viết Terraform | Claude Code | 30–45' | $0 | ❌ |
| **5** `apply` bootstrap | Terminal | 3' | ~$0 | 1 lần duy nhất |
| **6** ⏱ **`apply` ephemeral** | Terminal | **20'** | **đồng hồ chạy** | mỗi buổi |
| **7** `kubectl` + add-on | Terminal | 8' | — | mỗi buổi |
| **8** Nghiệm thu | Terminal + Console | 10' | — | mỗi buổi |
| **9** 🔴 **`destroy`** | Terminal | 15' | ⏱ dừng | ❌ **BẮT BUỘC** |
| **10** Commit | Repo | 3' | $0 | 1 lần |

**Cụm sống ≈ 50 phút × ~$0.25/giờ ≈ dưới $0.25 cho cả phiên.**

> **Phase 0–5 và 10 làm MỘT LẦN trong đời dự án.** Từ buổi sau, dựng lại cụm chỉ còn **Phase 6 → 7 → 8 → 9**.

> 💡 **Phase 0 chờ lâu thì làm Phase 2–4 song song.** Viết code Terraform **không cần AWS credential** (`terraform validate` chạy offline) — nên nếu quota phải chờ 48h, cứ viết code trước, chỉ `apply` là phải chờ.

---

## Phase 0 — 🔴 Xin tăng quota vCPU (làm ĐẦU TIÊN, trước cả khi cài gì)

**Vì sao đây là việc đầu tiên, không phải `terraform apply`:**

Node group của dự án = 2× `t3.xlarge` = **8 vCPU**. Tài khoản AWS chưa từng chạy EC2 thường có quota *All Standard Spot Instance Requests* mặc định **5 vCPU** — có tài khoản mới còn là **0**.

Quá quota thì hỏng **không phải ngay lập tức**: Terraform dựng VPC → dựng EKS control plane (**10–15 phút**) → rồi mới tới node group và **lúc đó** mới fail với `MaxSpotInstanceCountExceeded`. Tức là bạn **trả tiền 15 phút control plane để nhận một lỗi lẽ ra biết trước**.

Xin tăng quota **không cần cụm, không cần Terraform, không cần cài gì** — nhưng có thể mất **vài phút đến 48 giờ**. Nên nó phải nằm ở đầu hàng đợi.

**Click path:** Console → ô tìm kiếm gõ **Service Quotas** → *AWS services* → chọn **Amazon Elastic Compute Cloud (Amazon EC2)**.

Trang này có **~69 trang** quota sắp xếp alphabet — **đừng cuộn tay**, dùng ô **`Search by quota name`**. Với mỗi quota trong bảng dưới:

1. **Gõ chuỗi search** ở cột *Gõ vào ô search* → còn đúng 1–2 dòng.
2. **Nhìn cột `Applied account-level quota value` TRƯỚC.** Nếu đã **≥ 16** → quota này xong, bỏ qua. Nếu là **5** (hoặc **0**) → làm tiếp.
3. **Bấm vào tên quota (chữ xanh)** → trang chi tiết → nút **`Request increase at account level`** góc phải trên.
4. Ô **`Increase quota value`** → nhập **16** → **Request**.

> ⚠️ **Nút `Request increase at account level` ở trang danh sách mặc định BỊ XÁM** khi chưa chọn dòng nào — không phải tài khoản bạn thiếu quyền. Hoặc tick **radio tròn bên trái dòng** cho nút sáng lên, hoặc bấm thẳng vào tên quota như bước 3.
>
> ⚠️ **Đừng search chữ `vCPU`** → trả về **rỗng**. Đơn vị của 2 quota này là vCPU nhưng **tên quota không chứa từ đó** — rất dễ kết luận nhầm "tài khoản mình không có quota này".

Xin tăng **cả hai**:

| Quota | Gõ vào ô search | Quota code | Xin lên | Dùng khi |
|---|---|---|---|---|
| All Standard (A, C, D, H, I, M, R, T, Z) **Spot** Instance Requests | `All Standard` | `L-34B43A08` | **16** | node group spot (mặc định của dự án) |
| Running **On-Demand** Standard (A, C, D, H, I, M, R, T, Z) instances | `Running On-Demand Standard` | `L-1216C47A` | **16** | fallback khi spot hết hàng |

**Theo dõi kết quả:** Service Quotas → **Quota request history** (menu trái). Trạng thái đi từ `Pending` → `Case opened` → **`Case Closed`**.

> 🔴 **`Case Closed` KHÔNG có nghĩa là được duyệt.** Duyệt xong và bị từ chối đều hiển thị y hệt dòng đó — cột `Status` chỉ nói *"case hỗ trợ đã đóng"*, không nói kết quả. **Nguồn sự thật duy nhất là cột `Applied account-level quota value`** ở trang *Service Quotas → EC2* (nhớ đúng region `ap-southeast-1`):
>
> | Applied value | Nghĩa |
> |---|---|
> | **16** | ✅ Duyệt đủ — xong Phase 0 |
> | **8–15** | ⚠️ Duyệt **một phần** — vẫn đi tiếp được (8 = đúng 2× `t3.xlarge`), nhưng headroom = 0 ⇒ **bắt buộc ghim `max_size = 2`** ở Phase 4, xem cảnh báo ở đó |
> | **5** hoặc **0** | ❌ Bị từ chối — quota **không đổi** |
>
> ⏱ **Applied value TRỄ hơn thư duyệt.** Support đóng case xong, backend mới đẩy giá trị sang Service Quotas — thường vài phút, đôi khi lâu hơn. Nên nếu correspondence trong case ghi `New Limit = 16` mà Console còn hiện `8` thì **chưa phải bị cắt** — refresh lại sau ít phút. Đọc **cả hai** nguồn trước khi kết luận.
>
> ⚠️ **Mỗi quota là một case RIÊNG.** Thư duyệt của case này **không** nói gì về case kia. Phân biệt bằng tên trong thư: có chữ **`Spot Instance Requests`** = `L-34B43A08` · chỉ ghi **`instances`** = `L-1216C47A` (on-demand). Phải mở **cả hai** case — Spot mới là cái node group dùng hằng ngày.
>
> Bị từ chối thì mở link case ở cột *AWS Support Center Case* → đọc correspondence (AWS luôn ghi lý do; với tài khoản mới thường là *"chưa có lịch sử thanh toán, mô tả use case"*) → trả lời ngay trong case: *"Deploying an Amazon EKS managed node group of 2× t3.xlarge (8 vCPU) in ap-southeast-1 for a personal DevOps learning project. Requesting 16 vCPU for rolling node-group update headroom."*

> Xin **16** chứ không phải 8: chừa chỗ cho lúc rolling-update node group (AWS dựng node mới **trước** khi hạ node cũ → chạm đỉnh **12 vCPU** trong vài phút).
>
> ⚠️ Quota tính **theo region** — phải đang ở `ap-southeast-1` khi bấm request.

**Fallback nếu quota kẹt ở 5 vCPU và bạn cần demo gấp:** đổi sang `t3.large` ×2 (4 vCPU, vừa quota) → chỉ **16 GB RAM** → chỉ chạy được **1 environment** (`staging`), verify `prod` ở kind local. Chấp nhận được, nhưng biết trước tốt hơn phát hiện lúc pod `OOMKilled` giữa buổi demo.

---

## Phase 1 — Console AWS: Budget · MFA · IAM user (20')

Làm luôn trong lần đăng nhập ở Phase 0.

### 1.1 Budget alert $5 — làm TRƯỚC mọi thứ khác

Console → **Billing and Cost Management** → **Budgets** → *Create budget*.

> ⚠️ **Màn hình mở ra đã chọn sẵn thứ KHÔNG dùng được**: `Use a template (simplified)` + template `Zero spend budget`. Cái template đó báo động khi tiêu quá **$0.01** → sẽ kêu inh ỏi suốt **mọi** buổi demo, và bạn sẽ học cách phớt lờ alert — đúng thứ ta muốn tránh. Mọi trường cần điền bên dưới (*Period* · *amount* · *alert*) **không xuất hiện** cho tới khi đổi lựa chọn.

**Màn 1 — Choose budget type**

1. Khối *Budget setup* → bấm **`Customize (advanced)`** (ô bên phải). Khối *Templates* sẽ **biến mất** — đúng, không phải bấm hỏng.
2. Khối *Budget type* → **`Cost budget - Recommended`** → **Next**

**Màn 2 — Set budget amount**

| Trường | Điền |
|---|---|
| Budget name | `badminton-eks-monthly` |
| Period | **Monthly** |
| Budget renewal type | **Recurring budget** |
| Start month | để nguyên (tháng hiện tại) |
| Budgeting method | **Fixed** |
| Enter your budgeted amount ($) | **`5`** |
| Budget scope | để nguyên **All AWS services** |

**Màn 3 — Configure alerts** *(phần quan trọng nhất — bấm `Add an alert threshold` **hai lần**)*

- **Alert 1**: threshold **`80`** · đơn vị **`% of budgeted amount`** · trigger **`Actual`** · email bạn
- **Alert 2**: threshold **`100`** · đơn vị **`% of budgeted amount`** · trigger **`Forecasted`** · email bạn

> Đơn vị phải là **`% of budgeted amount`**, không phải *absolute value*.
>
> Hai trigger khác nhau có chủ đích: **`Actual`** = *"đã tiêu thật $4"* (chắc chắn đúng, nhưng báo sau khi tiền đã mất) · **`Forecasted`** = *"với đà này cuối tháng chạm $5"* (báo **sớm** — đây mới là cái cứu bạn khi quên `destroy`).

Màn *Attach actions - optional* → **bỏ qua**, bấm **Next** → **Review** → **`Create budget`**.

> 🔴 **Alert `Forecasted` sẽ IM LẶNG trong vài tuần đầu** — AWS chưa đủ lịch sử dùng để dự báo cho tài khoản mới. **Không phải bạn cấu hình sai.** Trong giai đoạn đó, thứ thật sự bảo vệ bạn là **hẹn giờ điện thoại** ở gạch đầu dòng cuối mục này, không phải AWS Budget.
>
> 💡 **2 budget đầu miễn phí** (từ cái thứ 3 mới $0.02/ngày) — tạo cái này không tốn gì.

☐ **Bật cho IAM user xem được billing** (không bật thì chỉ root thấy hoá đơn) — 🔴 **phải đăng nhập bằng ROOT mới làm được**:
Console *(đăng nhập root)* → tên account (góc phải trên) → **Account** → mục *IAM user and role access to Billing information* → **Edit** → tick **Activate** → Update.

> **Con gà–quả trứng:** làm bước này khi đang đăng nhập bằng IAM user sẽ ra `You don't have the billing:GetIAMAccessPreference permission` — vì chính cái công tắc này là thứ cấp quyền đó. IAM user **không thể tự bật cho mình**.
>
> ⚠️ **Không bắt buộc, không chặn gì**: Budget alert vẫn gửi email bình thường, `terraform apply` không đụng tới billing console. Bỏ qua được — chỉ là sau này muốn xem hoá đơn thì phải đăng nhập root.
>
> 💡 Nhưng nếu gặp lỗi trên, **nhân tiện kiểm luôn** IAM → Users → user của bạn → tab *Permissions* có `AdministratorAccess` chưa (§1.3 nhánh A). Thiếu nó thì `terraform apply` sẽ chết bằng `AccessDenied` **giữa chừng**, lúc control plane đã tính tiền.

☐ **Đặt hẹn giờ điện thoại "DESTROY"** ngay sau khung giờ demo. Budget alert báo **sau khi** đã tốn tiền; hẹn giờ mới là cái chặn.

### 1.2 Bật MFA cho user root

Console (đang đăng nhập bằng root) → **IAM** → **Security recommendations** → *Add MFA* → chọn **Authenticator app** (Google Authenticator / Authy) → quét QR → nhập 2 mã liên tiếp.

**Từ giờ không dùng root nữa** — chỉ dùng IAM user ở bước sau.

☐ **Verify** (làm sau khi xong Phase 2, vì cần `aws` CLI): đang đăng nhập bằng IAM user thì **không nhìn thấy** trạng thái MFA của root ở đâu cả — phải hỏi API:

```bash
aws iam get-account-summary --query 'SummaryMap.AccountMFAEnabled'
```

Phải in **`1`**. Ra **`0`** nghĩa là MFA đã gắn nhầm vào **IAM user** chứ không phải root — root vẫn đang trần.

### 1.3 IAM user + access key

> Runbook dùng tên **`itadmin`**. Nếu bạn dùng user tên khác, nhớ thay ở **5 chỗ** còn lại: §1.4 · Phase 2 (`get-caller-identity`) · Phase 4 (prompt access entry) · Phase 7 (lệnh vá nóng ×2).

**Nhánh A — bạn ĐÃ có sẵn IAM user** *(trường hợp thường gặp; user `itadmin` của dự án này đi đường này)*

1. **Kiểm quyền trước** — IAM → **Users** → `itadmin` → tab **Permissions**: phải thấy **`AdministratorAccess`** (hoặc bộ policy phủ đủ **EKS · EC2 · VPC · ECR · IAM · S3 · DynamoDB** + **SSM · Route53 · ACM** cho Day 6/8).
   > 🔴 **Đừng bỏ qua bước kiểm này.** Thiếu quyền **không lộ ra** lúc `aws configure` — nó lộ bằng `AccessDenied` **giữa chừng `terraform apply`**, tức là lúc EKS control plane đã dựng xong và **đang tính tiền**. Rồi bạn phải `destroy`, gắn policy, làm lại từ đầu.
2. Tab **Security credentials** → *Create access key* → use case **Command Line Interface (CLI)** → tick xác nhận → **Download .csv**

**Nhánh B — tài khoản trắng, chưa có IAM user nào**

Console → **IAM** → **Users** → *Create user*:

1. **User name**: `itadmin`
2. *(Không cần tick "Provide user access to the AWS Management Console" — user này chỉ dùng cho CLI)*
3. **Permissions options** → **Attach policies directly**
4. Quyền cần cho toàn dự án: **EKS · EC2 · VPC · ECR · IAM · S3 · DynamoDB** — cộng **SSM · Route53 · ACM** cho Day 6/8.
   > 👉 **Khuyến nghị thực dụng cho dự án solo: gắn thẳng `AdministratorAccess`.** Ghép policy tay cho đủ 10 dịch vụ tốn cả buổi, và mỗi lần thiếu quyền lại nhận `AccessDenied` **giữa lúc `terraform apply`** — không đáng đổi với dự án 1 người, ephemeral. Bảo mật thật của bạn nằm ở **MFA cho root + không commit access key**, không nằm ở việc bào mỏng policy của user này.
5. Tạo xong → mở user → tab **Security credentials** → *Create access key* → use case **Command Line Interface (CLI)** → tick xác nhận → **Download .csv**

### 1.4 Ba việc dọn dẹp ngay sau đó

1. **Ghi lại ARN của user** — IAM → Users → `itadmin` → copy **ARN** (dạng `arn:aws:iam::<12-số>:user/itadmin`).
   > **ARN dùng để làm gì:** EKS có **hai tầng phân quyền chồng lên nhau** — IAM cho phép bạn *tạo* cụm (`eks:CreateCluster`), nhưng *nói chuyện* với cụm (`kubectl`) lại là **Kubernetes RBAC**, một hệ thống độc lập. Cụm cần bảng ánh xạ *"IAM principal nào ↔ user Kubernetes nào"* — gọi là **access entry** — và nó nhận diện bạn bằng **ARN**. Thiếu ⇒ `Unauthorized` dù cụm khoẻ (xem Phase 7).
   >
   > File `.tf` **không cần** ARN này: Terraform tự lấy bằng `data.aws_caller_identity.current.arn`. Bạn chỉ cần nó cho **lệnh vá nóng thủ công** ở Phase 7 và để **đối chiếu** xem đúng principal đã được cấp quyền chưa.
2. **File `.csv`**: lưu **ngoài** repo (ví dụ `~/Documents/`), tuyệt đối không để trong `~/ClaudeCodeProjects/`. Nhầm một lần là nó vào git.
3. Đây là access key **duy nhất** của cả dự án. Day 5 GitHub Actions dùng **OIDC**, không dùng key — nên bạn sẽ không bao giờ phải tạo key thứ hai.

---

## Phase 2 — Cài công cụ (10')

Máy bạn đã có `kubectl` v1.33.2 · `helm` v3.18.4 · `docker` 29.6.2. Thiếu `aws` và `terraform`.

> 🔴 **`brew install terraform` KHÔNG chạy được nữa** — báo `Error: No available formula with the name "terraform"`. Homebrew core đã gỡ formula sau khi HashiCorp đổi license sang BUSL. Phải qua tap chính chủ.

```bash
brew tap hashicorp/tap
brew install hashicorp/tap/terraform
brew install awscli

terraform version     # >= 1.6
aws --version         # aws-cli/2.x
```

Cấu hình + xác thực:

```bash
aws configure
#   AWS Access Key ID     : <từ .csv Phase 1.3>
#   AWS Secret Access Key : <từ .csv Phase 1.3>
#   Default region name   : ap-southeast-1
#   Default output format : json

aws sts get-caller-identity     # Arn phải là .../itadmin — KHÔNG phải :root
aws configure get region        # phải in: ap-southeast-1
```

**Cổng chặn — kiểm quota Phase 0 đã duyệt chưa:**

```bash
aws service-quotas list-service-quotas --service-code ec2 \
  --query "Quotas[?contains(QuotaName,'Standard') && contains(QuotaName,'Spot')].[QuotaName,Value]" \
  --output table
```

Thấy `Value` **< 8** → **dừng ở đây**, đừng sang Phase 6. Xem trạng thái request đang chờ:

```bash
aws service-quotas list-requested-service-quota-change-history --service-code ec2 \
  --query 'RequestedQuotas[].[QuotaName,DesiredValue,Status]' --output table
```

---

## Phase 3 — Vá `.gitignore` trước khi viết một dòng Terraform (2')

**Vì sao phải làm TRƯỚC Phase 4:** `terraform.tfstate` lưu **giá trị thô** của mọi thứ Terraform quản lý — kể cả field mà provider đánh dấu `sensitive`. Kế hoạch đã chốt **cả hai repo sẽ PUBLIC** ở Day 5. `.gitignore` gốc **chưa có một dòng terraform nào** (chỉ chặn `.env`). Viết code trước rồi mới nhớ ra là đã muộn — file đã nằm trong `git add .` đầu tiên.

```gitignore
# ── Terraform ─────────────────────────────────────────────────────────────────
.terraform/
*.tfstate
*.tfstate.*
*.tfvars
!*.tfvars.example
override.tf
override.tf.json
# crash.log đã được `*.log` ở mục Logs chặn sẵn
```

✅ **Đã áp dụng sẵn vào [`../.gitignore`](../.gitignore)** — bạn không phải gõ lại, chỉ cần kiểm:

```bash
git check-ignore -q terraform/terraform.tfstate && echo "tfstate bị chặn ✅"
git check-ignore -q terraform/.terraform.lock.hcl || echo "lock file commit được ✅"
```

> ⚠️ **`.terraform.lock.hcl` PHẢI được commit** — đừng ignore nó. Nó ghim đúng version provider; bỏ đi thì máy khác `init` ra version khác và mất tính tái lập. Nó không chứa secret.

---

## Phase 4 — Paste prompt §Day 3 → Claude viết Terraform (30–45')

1. Mở Claude Code tại **`/Users/phucnguyen/ClaudeCodeProjects/badmintonHub`** (app repo — `terraform/` sống ở đây, **không** phải repo gitops).
2. Bật **plan mode** (`Shift+Tab`).
3. Paste nguyên khối prompt ở [`../planning/Planning_CICD.md`](../planning/Planning_CICD.md) §Day 3 (khối ```` ```text ```` ngay dưới dòng *"📋 Prompt paste-ready — Day 3"*).
4. **Dán thêm khối này vào cuối prompt** — prompt gốc thiếu, và hậu quả rơi vào Phase 7:

```text
Bổ sung:
- Module eks: enable_cluster_creator_admin_permissions = true, và thêm access entry cho
  principal đang chạy terraform, với policy AmazonEKSClusterAdminPolicy. Lấy ARN bằng
  data "aws_caller_identity" "current" {} rồi dùng data.aws_caller_identity.current.arn —
  KHÔNG hardcode account ID hay tên user vào file .tf (repo sẽ PUBLIC ở Day 5).
- Node group: ghim CỨNG min_size = desired_size = max_size = 2. KHÔNG để max_size mặc
  định của module (là 3) — cụm này không có autoscaler, node thứ 3 chỉ là tiền vứt đi.
- Gói bước nối kubectl + cài 3 add-on thành scripts/bootstrap.sh (idempotent, chạy lại được
  mỗi buổi rebuild), đúng thứ tự: EBS CSI + StorageClass gp3 → ALB controller → External
  Secrets + ClusterSecretStore. KHÔNG cert-manager, KHÔNG ExternalDNS (Day 8).
```

> **Vì sao dòng thứ nhất quan trọng:** module EKS ≥ v20 **không** tự cho principal tạo cụm quyền admin bên trong Kubernetes. Thiếu nó thì Phase 7 `kubectl get nodes` trả `error: You must be logged in to the server (Unauthorized)` — **cụm hoàn toàn khoẻ, chỉ là bạn không có quyền nói chuyện với nó** — và người mới sẽ tưởng cụm hỏng rồi đi destroy dựng lại (mất 30' + tiền).
>
> **Vì sao dòng thứ hai quan trọng — hiểu cho đúng.** Node group có **3 con số**: `min_size` (sàn) · `desired_size` (số máy **đang thật sự chạy** — đây là cái bạn trả tiền) · `max_size` (**trần** — AWS *được phép* tự dựng tới đây mà không hỏi). `max_size` **không tạo ra máy nào**; máy chỉ mọc khi có ai đó nâng `desired_size`, mà người làm việc đó là **Cluster Autoscaler** — thứ dự án này **không cài**.
>
> ⇒ Thành thật: với cấu hình hiện tại, `max_size = 3` và `max_size = 2` cho **cùng kết quả 2 node**, không tốn thêm đồng nào. Ghim bằng 2 là **rào chắn**, không phải tiết kiệm tức thì: nó biến lời hứa *"cụm chỉ ~$0.25/giờ, không phát sinh"* thành **ràng buộc kỹ thuật** thay vì niềm tin — về sau có cài thêm autoscaler hay lỡ sửa `desired_size` thì cụm vẫn không thể tự phình. Giá phải trả gần như bằng 0.
>
> **Có ĐÚNG HAI đường sinh ra node thứ 3 — đừng lẫn chúng với nhau:**
>
> | Đường | Ai kích hoạt | Khi nào | Dự án này |
> |---|---|---|---|
> | **A. Scaling** | Cluster Autoscaler nâng `desired_size` | tự động, khi pod `Pending` | ❌ không cài |
> | **B. Surge lúc nâng version** | chính EKS, tạm thời | **chỉ** khi bạn chủ động nâng version node group | ❌ ephemeral thì destroy/recreate, không nâng tại chỗ |
>
> Đường **A** là lý do câu trên nói *"`max_size=3` cũng chỉ ra 2 node"*. Đường **B** là lý do có con số **12 vCPU** dưới đây. Chúng **không mâu thuẫn** — B không bao giờ tự xảy ra.
>
> 🔴 **Nếu quota chỉ được duyệt 8** (xem Phase 0): ghim chuyển từ *rào chắn tuỳ chọn* sang **bắt buộc**, vì đường **B** sẽ chạm **12 vCPU** > 8 → `MaxSpotInstanceCountExceeded`, nổ **sau khi** control plane đã dựng xong, tức đang tính tiền.
>
> ✅ **Nếu quota đã là 16**: cả A lẫn B đều không thể làm hỏng `apply` (12 < 16). Lúc đó ghim `max_size = 2` chỉ còn là thói quen tốt — giữ thì giá bằng 0, nhưng không phải thứ cần lo.
>
> ⚠️ **Đánh đổi đã biết của `max_size = 2`:** EKS managed node group khi **nâng version** sẽ dựng node mới *trước* rồi mới hạ node cũ → cần headroom, nên nâng tại chỗ sẽ **fail**. Với mô hình ephemeral thì vô hại: mỗi buổi `destroy` rồi `apply` lại từ đầu, không bao giờ nâng tại chỗ. Muốn nâng tại chỗ thì nới `max_size = 3` — cần quota ≥ **12**.

**Xong Phase 4 phải có:**

```
terraform/bootstrap/     # S3 state · DynamoDB lock · 9 ECR repo
terraform/               # backend.tf · VPC + subnet tag · EKS · node group spot · OIDC + 4 IRSA
scripts/bootstrap.sh     # nối kubectl + 3 add-on, đúng thứ tự
```

Claude chạy `terraform fmt` + `terraform validate` (**không** `apply` — hai lệnh này không cần AWS credential). Bạn tự chạy `apply` ở Phase 5–6.

---

## Phase 5 — `apply` bootstrap (3' · MỘT LẦN trong đời dự án)

```bash
cd terraform/bootstrap
terraform init
terraform plan          # ĐỌC KỸ: phải đúng 1 S3 + 1 DynamoDB + 9 ECR. Nhiều hơn = sai stack.
terraform apply
terraform output        # ghi lại: tên bucket state + ECR base URL (Day 4 cần)
```

**🥚 Bẫy con-gà-quả-trứng** *(giải thích ở [`DAY3-EXPLAINED.md`](DAY3-EXPLAINED.md) §3.5)*: stack này **tạo ra** chính cái S3 bucket dùng để lưu state, nên nó không thể dùng backend S3 → nó dùng **local state**. Chọn một trong hai:

```bash
# Cách A (khuyến nghị) — đẩy state lên chính bucket vừa tạo, hết lo mất file
terraform init -migrate-state

# Cách B — giữ local, tự backup RA NGOÀI repo
cp terraform.tfstate ~/Documents/badminton-bootstrap.tfstate.bak
```

Mất file này **không mất tài nguyên**, nhưng mất quyền quản lý chúng bằng Terraform (phải `terraform import` lại từng cái).

---

## Phase 6 — ⏱ `apply` ephemeral (20') — ĐỒNG HỒ TIỀN BẮT ĐẦU

**Hẹn giờ điện thoại "DESTROY" TRƯỚC khi gõ lệnh.** Không phải sau — sau thì quên.

```bash
cd ../                              # về terraform/
terraform init                      # lần này backend = S3 (bucket Phase 5)
terraform plan | tee /tmp/day3-plan.txt
terraform apply                     # ~15-20'; phần lâu nhất là EKS control plane
```

Kỳ vọng ~60–70 resource. Ba lỗi hay gặp **ở đúng bước này**:

| Lỗi | Nghĩa | Sửa |
|---|---|---|
| `MaxSpotInstanceCountExceeded` / node group `CREATE_FAILED` | Quota vCPU chưa đủ | Phase 0 — hoặc fallback `t3.large` ×2 |
| `Unsupported instance type in availability zone` | AZ đó không có `t3.xlarge` | Đổi AZ trong biến Terraform |
| `UnauthorizedOperation` / `AccessDenied` | IAM user thiếu quyền | Phase 1.3 — gắn `AdministratorAccess` |

> Lỡ `Ctrl-C` giữa `apply`? **Chạy lại `terraform apply`**, đừng bỏ đó. Terraform ghi state theo từng resource nên nó biết chỗ đang dở. Bỏ đó = tài nguyên đã tạo vẫn tính tiền mà không ai quản.

---

## Phase 7 — Nối `kubectl` + cài add-on (8')

```bash
aws eks update-kubeconfig --name badminton --region ap-southeast-1
kubectl get nodes                   # 2 dòng · t3.xlarge · Ready
```

> 🔴 **Nếu ra `error: You must be logged in to the server (Unauthorized)`**: cụm **không** hỏng. Đây là thiếu access entry (xem Phase 4). Vá **không cần dựng lại cụm**:
> ```bash
> aws eks create-access-entry --cluster-name badminton \
>   --principal-arn arn:aws:iam::<12-số>:user/itadmin --type STANDARD
> aws eks associate-access-policy --cluster-name badminton \
>   --principal-arn arn:aws:iam::<12-số>:user/itadmin \
>   --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \
>   --access-scope type=cluster
> ```
> Rồi thêm vào code Terraform để lần rebuild sau tự có.

```bash
./scripts/bootstrap.sh
```

**Thứ tự trong script có ràng buộc thật** *(chi tiết ở [`DAY3-EXPLAINED.md`](DAY3-EXPLAINED.md) §7.4)*: EBS CSI + `gp3` → ALB controller → External Secrets + `ClusterSecretStore`. Từ Day 6 trở đi, ESO phải **Ready trước** khi ArgoCD sync app, nếu không pod khởi động lúc `Secret` chưa tồn tại → `CreateContainerConfigError`.

🔴 **Không cài cert-manager** ở bất kỳ Day nào. ALB terminate TLS ở tầng AWS và **chỉ nhận cert từ ACM/IAM — nó không đọc được Kubernetes Secret**, mà Secret lại đúng là nơi cert-manager cất cert. Ghép vào thì cert xin về thành công rồi **ALB lờ đi** → không có HTTPS mà chẳng báo lỗi ở đâu. HTTPS của dự án đi bằng **ACM** (Day 8).

---

## Phase 8 — Nghiệm thu (10')

### 8.1 CLI — nguồn sự thật

```bash
kubectl get nodes -o wide                                              # 2× t3.xlarge · Ready
aws ecr describe-repositories --query 'repositories[].repositoryName'   # đúng 9
kubectl get storageclass                                               # có gp3
kubectl get clustersecretstore                                         # STATUS = Valid
aws ec2 describe-subnets --filters Name=tag:kubernetes.io/role/elb,Values=1 \
  --query 'Subnets[].SubnetId'                                         # KHÔNG rỗng
aws ec2 describe-nat-gateways --query 'NatGateways[].NatGatewayId'      # PHẢI rỗng
```

### 8.2 Console — cross-check `bootstrap` stack *(apply 1 lần, KHÔNG BAO GIỜ destroy)*

| Code tạo | Console → | Phải thấy |
|---|---|---|
| S3 bucket (tf state) | **S3 → Buckets** | Bucket tồn tại · **Properties → Bucket Versioning = Enabled** · bên trong có `terraform.tfstate` |
| DynamoDB (state lock) | **DynamoDB → Tables** | Bảng tồn tại · Partition key = **`LockID`** kiểu **String** |
| **9 ECR repository** | **ECR → Repositories** | Đúng **9**: `eureka-server` · `api-gateway` · `user-service` · `court-service` · `booking-service` · `payment-service` · `escrow-service` · `chat-service` · `frontend` |

### 8.3 Console — cross-check `ephemeral` stack *(destroy sau mỗi buổi)*

| Code tạo | Console → | Phải thấy |
|---|---|---|
| VPC | **VPC → Your VPCs** | 1 VPC |
| **Subnet + tag** | **VPC → Subnets** → chọn subnet → tab **Tags** | 🔴 Public subnet có `kubernetes.io/role/elb = 1` **và** `kubernetes.io/cluster/badminton = shared`; private subnet có `kubernetes.io/role/internal-elb = 1` |
| *(không tạo NAT)* | **VPC → NAT Gateways** | **0** — thấy 1 cái là đang chảy **$45/tháng** |
| EKS cluster | **EKS → Clusters** | `badminton` · Status **Active** · tab **Overview** có **OpenID Connect provider URL** |
| Node group | **EKS → badminton → Compute** | 1 node group · **2** node · Desired size 2 |
| Node EC2 | **EC2 → Instances** | 2 instance **Running** · Type **`t3.xlarge`** · cột **Lifecycle = `spot`** |
| OIDC provider | **IAM → Identity providers** | Provider `oidc.eks.ap-southeast-1.amazonaws.com/id/…` |
| **4 IRSA role** | **IAM → Roles** | 4 role · mỗi role tab **Trust relationships** tham chiếu OIDC trên + `sub` = `system:serviceaccount:<ns>:<sa>` |
| StorageClass `gp3` | *(không hiện ở Console)* | dùng `kubectl get storageclass` |

> 🔴 **Tag subnet là thứ dễ quên nhất và hậu quả xuất hiện muộn nhất**: thiếu tag thì **Day 3 vẫn xanh**, nhưng **Day 4 Ingress treo vô hạn** và `kubectl describe ingress` chỉ nói `couldn't auto-discover subnets`. Kiểm ngay hôm nay.

### 8.4 Bốn IRSA role — thiếu cái nào hỏng cái đó

| Role cho | ServiceAccount / namespace | Quyền chính | Thiếu thì |
|---|---|---|---|
| AWS Load Balancer Controller | `kube-system` | `elasticloadbalancing:*` · `ec2:Describe*` | Ingress **không có ADDRESS** → không có ALB → không vào được hệ thống |
| EBS CSI driver | `kube-system` | `ec2:CreateVolume` · `AttachVolume`… | PVC kẹt `Pending` → 5 datastore không boot |
| **External Secrets** | `external-secrets` / `external-secrets` | `ssm:GetParameter*` + `ssm:DescribeParameters` trên `arn:aws:ssm:<region>:<acct>:parameter/badminton/*` **+** `kms:Decrypt` trên `alias/aws/ssm`. **Không** cấp `ssm:*` toàn account | `SecretSyncedError` / `AccessDenied` → pod `CreateContainerConfigError` |
| **ExternalDNS** *(dùng ở Day 8)* | `kube-system` | `route53:ChangeResourceRecordSets` · `ListHostedZones` · `ListResourceRecordSets` | Record DNS không tự tạo → phải sửa DNS tay mỗi buổi |

> 💡 **Tạo cả 4 role ngay hôm nay** dù ExternalDNS đến Day 8 mới cài chart — **IAM role không tính tiền khi không dùng**.

### 8.5 🔴 Thấy những thứ này trong Console = SAI thiết kế

| Thấy gì | Vì sao sai |
|---|---|
| **NAT Gateway** | Cố tình né — $45/tháng. Node ở public subnet với `map_public_ip_on_launch=true`, hoặc VPC endpoints (`ecr.api` · `ecr.dkr` · `s3` · `sts` · `logs`). Thiếu **cả hai** thì Day 4 pod kẹt `ImagePullBackOff` |
| **2 ALB** *(Day 4+)* | `group.name: badminton` phải gộp staging + prod vào **1** |
| **RDS / ElastiCache / MSK / DocumentDB / AmazonMQ** | Datastore chạy **in-cluster Bitnami**. Managed service phá mô hình chi phí ephemeral |
| **Tag `latest` trong ECR** *(Day 4+)* | Image tag **phải** = git SHA, bất biến |
| **Cert của Let's Encrypt / cert-manager trong cụm** | ALB chỉ nhận cert ACM/IAM — **im lặng không có HTTPS** |

### 8.6 Tuỳ chọn rất đáng làm — chứng minh IRSA chạy THẬT ngay hôm nay

```bash
aws ssm put-parameter --name /badminton/staging/SMOKE_TEST --type SecureString --value "hello-irsa"
# tạo 1 ExternalSecret trỏ param đó → kubectl get secret → thấy giá trị = IRSA + ESO thông suốt
aws ssm delete-parameter --name /badminton/staging/SMOKE_TEST
```

`clustersecretstore = Valid` mới chỉ chứng minh ESO **xác thực** được với AWS. Kéo được **một param thật** mới chứng minh **policy đủ quyền**. Sai policy mà không test thì tới Day 6 mới lộ — lúc đó lẫn vào 20 thứ khác đang hỏng.

---

## Phase 9 — 🔴 `destroy` cùng ngày (15')

### 9.1 Lệnh cho HÔM NAY (Day 3)

Hôm nay **chưa** có ArgoCD, chưa có PVC, chưa có Ingress — nên bản destroy rút gọn:

```bash
helm uninstall aws-lb-controller -n kube-system   # nếu script đã cài; hôm nay chưa tạo ALB nào
cd terraform && terraform destroy                 # ~10-15'
```

### 9.2 ⚠️ Từ Day 4 trở đi phải dùng bản ĐẦY ĐỦ này

ALB và EBS volume do **controller** tạo, **không nằm trong Terraform state** → `terraform destroy` không biết chúng tồn tại. Bỏ qua thứ tự dưới đây thì: ALB ở lại · EBS volume mồ côi (~40 GB ≈ **$3.2/tháng chảy âm thầm**) · và destroy có thể **fail** vì VPC còn ENI của ALB đang giữ.

```bash
# 1. Xoá ROOT app — KHÔNG phải child. ApplicationSet controller sinh lại child ngay lập tức.
argocd app delete badmintonhub-root --cascade

# 2. Xoá PVC KHI CỤM CÒN SỐNG — reclaim policy Delete chỉ chạy lúc PVC bị xoá.
#    Destroy thẳng cụm thì không ai gọi nó → EBS mồ côi VẪN TÍNH TIỀN.
kubectl delete pvc --all -n data-staging
kubectl delete pvc --all -n data-prod

# 3. Xoá Ingress để LB Controller tự gỡ ALB (PHẢI trước khi gỡ controller)
kubectl delete ingress --all -A

# 4. Gỡ add-on tạo AWS resource bên ngoài
helm uninstall aws-lb-controller -n kube-system

# 5. Giờ mới huỷ hạ tầng
cd terraform && terraform destroy
```

### 9.3 Verify bill về ~0 — ☐ Phải về 0

| Console → | Phải thấy |
|---|---|
| **EKS → Clusters** | 0 |
| **EC2 → Instances** | 0 Running |
| **EC2 → Load Balancers** | 0 |
| **EC2 → Volumes** → lọc **State = `available`** | 🔴 **0** — đây là chỗ rò tiền |
| **VPC → NAT Gateways** | 0 |
| **EC2 → Elastic IPs** | 0 cái không associated (EIP mồ côi vẫn tính tiền) |

```bash
aws eks list-clusters                                                          # rỗng
aws ec2 describe-instances --filters Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].InstanceId'                              # rỗng
aws elbv2 describe-load-balancers --query 'LoadBalancers[].LoadBalancerName'   # rỗng
aws ec2 describe-volumes --filters Name=status,Values=available \
  --query 'Volumes[].VolumeId'                                                 # rỗng — chỗ rò tiền
```

### 9.4 ☐ Phải CÒN (đây là lý do buổi sau rebuild chỉ mất ~15')

| Console → | Còn gì | Mất thì phải làm lại gì |
|---|---|---|
| **S3** | Bucket state | Terraform mất state → **không destroy được** stack cũ |
| **DynamoDB** | Bảng lock | — |
| **ECR** | 9 repo + image | **Build lại toàn bộ 9 image** |
| **Systems Manager → Parameter Store** | Các param secret *(nạp từ Day 6)* | **Nạp lại toàn bộ secret bằng tay** |
| **Route 53** | Hosted zone *(từ Day 8)* | Đổi NS lại + chờ 1–48h |
| **Certificate Manager** | ACM cert *(từ Day 8)* | Xin + validate lại cert |

**KHÔNG destroy `terraform/bootstrap/`** — đó chính là stack giữ 3 dòng đầu bảng này.

### 9.5 Hôm sau

**Billing and Cost Management → Cost Explorer** → granularity **Daily** → ngày demo có một cột nhỏ, ngày kế **gần 0**. Đây là xác nhận cuối cùng, đáng tin hơn mọi cảm giác.

> 💡 Giảm chi phí dài hạn: **ECR → repo → Lifecycle policy** → giữ 5 image gần nhất. Mỗi lần push thêm một SHA là thêm ~3 GB; con số "$0.30/tháng" ở §A chỉ đúng cho **một** bộ tag.

---

## Phase 10 — Commit (3')

```bash
git status                    # KHÔNG được thấy *.tfstate, .terraform/, *.csv
git add terraform/ scripts/ .gitignore docs/DAY3-RUNBOOK.md
git commit -m "feat(terraform): Day 3 — bootstrap stack (S3/DynamoDB/9 ECR) + EKS ephemeral stack"
```

Quy ước dự án: commit thẳng `main`, **không** thêm trailer `Co-Authored-By`.

---

## Tra nhanh khi hỏng

| Triệu chứng | Nguyên nhân thật | Về phase |
|---|---|---|
| `brew install terraform` → *No available formula* | Homebrew core đã gỡ formula (BUSL) | 2 — dùng `hashicorp/tap` |
| `terraform plan` → `NoCredentialProviders` | Chưa `aws configure`, hoặc sai profile | 2 |
| node group `CREATE_FAILED` sau khi đợi 15' | Quota vCPU | **0** |
| `kubectl get nodes` → `Unauthorized` | Thiếu EKS access entry — **cụm không hỏng** | 7 |
| Pod `ImagePullBackOff` *(Day 4)* | Node không ra được internet: thiếu `map_public_ip_on_launch` **và** thiếu VPC endpoint | 4 · 8.5 |
| Ingress không có ADDRESS *(Day 4)* | Thiếu tag `kubernetes.io/role/elb` trên public subnet | 4 · 8.3 |
| `SecretSyncedError` / pod `CreateContainerConfigError` | IRSA của External Secrets thiếu quyền SSM | 8.4 · 8.6 |
| Console thấy danh sách rỗng | **Sai region** ở góc phải trên | — |
| `terraform destroy` treo ở VPC | ENI của ALB còn giữ — phải xoá Ingress **trước** | 9.2 |
| Bill vẫn chảy sau khi destroy | EBS volume `available` mồ côi (quên xoá PVC) | 9.2 bước 2 · 9.3 |

---

## Buổi sau muốn dựng lại

Chỉ còn **Phase 6 → 7 → 8 → 9**. Phase 0–5 và 10 là một lần duy nhất.

```bash
cd terraform && terraform apply && aws eks update-kubeconfig --name badminton --region ap-southeast-1
./scripts/bootstrap.sh
# … demo …
cd terraform && terraform destroy      # từ Day 4 dùng bản đầy đủ §9.2
```

---

## Đọc tiếp

- [`DAY3-EXPLAINED.md`](DAY3-EXPLAINED.md) — **vì sao** mỗi thứ tồn tại (Terraform · VPC · IRSA · add-on · bẫy · chi phí)
- [`../planning/Planning_CICD.md`](../planning/Planning_CICD.md) §Day 3 — prompt paste-ready · §7 runbook teardown · §8 chi phí
- **Day 4** = deploy staging + Ingress ALB → làm ở repo `badmintonHub-gitops`

> ⏭ **Nhắc cho Day 8**: bạn đã chọn **bỏ qua domain** ở Day 3. Đổi nameserver mất **1–48 giờ** và ACM cert đứng `PENDING_VALIDATION` cho tới khi DNS validate resolve được — cả chuỗi đó **không cần cụm**. Mua domain sát ngày demo là canh bạc. Muốn an toàn thì mua bất cứ lúc nào từ giờ tới Day 7 (Route53 → *Registered domains* → *Register domain*, ~$13–15/năm `.com`, NS tự cấu hình), rồi để đó.

<sub>Bảng verify Console ở §8.2–8.5 và §9.3–9.4 được gộp vào đây để file tự chứa; repo `badmintonHub-gitops` có bản tương ứng phủ **cả Day 4/5/6/8** (`docs/MANUAL-SETUP.md`). Sửa một bên thì nhớ bên kia.</sub>
