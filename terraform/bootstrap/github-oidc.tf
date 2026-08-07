# ══════════════════════════════════════════════════════════════════════════════
# GitHub Actions → AWS bằng OIDC (Day 5)
#
# Vấn đề cần giải: CI phải đẩy image lên ECR và chạy Terraform, tức là phải có
# quyền AWS. Cách cũ là tạo access key rồi dán vào GitHub Secrets — key đó SỐNG
# MÃI cho tới khi ai đó nhớ ra mà xoá, và repo này thì PUBLIC.
#
# OIDC đảo ngược hướng tin cậy: GitHub ký một token ngắn hạn mô tả "run này đến
# từ repo X, nhánh Y", AWS kiểm chữ ký rồi cấp credential sống **1 giờ**. Không
# có bí mật dài hạn nào tồn tại ở đâu cả.
#
# Vì sao nằm ở stack `bootstrap` chứ không phải `terraform/`: stack kia bị
# `destroy` mỗi buổi. Role CI mà nằm đó thì mỗi lần dựng lại sẽ có ARN mới ⇒
# phải sửa GitHub Secrets thủ công sau mỗi lần rebuild.
#
# 💰 Chi phí: $0. OIDC provider và IAM role không tính tiền.
# ══════════════════════════════════════════════════════════════════════════════

# ── 1. OIDC provider ──────────────────────────────────────────────────────────
# Khai báo với AWS: "tin các token do GitHub Actions ký". Mỗi tài khoản AWS chỉ
# được có ĐÚNG MỘT provider cho mỗi URL — apply lần hai khi đã tạo tay sẽ báo
# EntityAlreadyExists, lúc đó phải `terraform import` chứ không tạo thêm.
resource "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"

  # Đối tượng nhận token. `sts.amazonaws.com` là giá trị mà
  # aws-actions/configure-aws-credentials luôn xin — đừng đổi.
  client_id_list = ["sts.amazonaws.com"]

  # Từ tháng 6/2023 AWS KHÔNG còn thẩm định thumbprint cho provider của GitHub
  # (AWS tự tin cậy root CA), nhưng API vẫn bắt truyền. Để cả hai giá trị đã
  # công bố cho chắc — nếu GitHub xoay CA thì cũng không gãy.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]

  tags = merge(local.tags, { Name = "${var.project}-github-oidc" })
}

locals {
  # Chuỗi `sub` mà GitHub nhét vào token. Đây là thứ DUY NHẤT chặn repo khác
  # (hoặc nhánh khác) mượn role này — sai một ký tự là hoặc không ai vào được,
  # hoặc tệ hơn: mở rộng hơn ý định.
  gha_sub_main = "repo:${var.github_owner}/${var.github_repo}:ref:refs/heads/main"
  gha_sub_pr   = "repo:${var.github_owner}/${var.github_repo}:pull_request"
}

# ⚠️ MỌI `description` của aws_iam_role DƯỚI ĐÂY PHẢI LÀ ASCII THUẦN — đừng dịch
#    sang tiếng Việt, kể cả khi comment xung quanh là tiếng Việt.
#
#    API `iam:CreateRole` chỉ nhận description trong khoảng U+0020..U+007E cộng
#    U+00A1..U+00FF (ASCII in được + Latin-1). Chữ có dấu tiếng Việt nằm NGOÀI
#    khoảng đó — đ = U+0111, ẩ = U+1EA9, ề = U+1EC1 — và cả dấu gạch dài “—”
#    (U+2014) cũng vậy. Vi phạm thì apply CHẾT GIỮA CHỪNG bằng ValidationError,
#    sau khi đã tạo xong OIDC provider và policy.
#
#    Bất đối xứng rất dễ nhầm: `iam:CreatePolicy` LẠI nhận tiếng Việt bình thường
#    — xem description của aws_iam_policy.gha_ecr ngay dưới, và của
#    aws_iam_policy.external_secrets trong ../irsa.tf (đã chạy thật từ Day 3).
#    Chỉ ROLE mới bị siết.

# ── 2. Trust policy dùng chung ────────────────────────────────────────────────
# Hai bản: một cho run trên `main` (được ghi), một cho run `pull_request` (chỉ
# đọc). Điều kiện `aud` bắt buộc phải có — thiếu nó thì bất kỳ token OIDC nào
# của GitHub, kể cả từ repo người lạ, cũng khớp.

data "aws_iam_policy_document" "gha_assume_main" {
  statement {
    sid     = "GitHubActionsPushToMain"
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # StringEquals (KHÔNG phải StringLike): khớp tuyệt đối một nhánh duy nhất.
    # `workflow_dispatch` bấm trên main cũng sinh đúng chuỗi này.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.gha_sub_main]
    }
  }
}

data "aws_iam_policy_document" "gha_assume_pull_request" {
  statement {
    sid     = "GitHubActionsPullRequest"
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # PR có `sub` dạng `repo:owner/repo:pull_request` — KHÔNG kèm tên nhánh.
    # PR đến từ fork không được GitHub cấp `id-token: write`, nên dù chuỗi này
    # trông rộng, fork vẫn không lấy nổi token để vào đây.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [local.gha_sub_pr]
    }
  }
}

# ── 3. Role đẩy image lên ECR ─────────────────────────────────────────────────
# Chỉ dùng ở job release của ci.yml (merge vào main). Đây là quyền AWS DUY NHẤT
# mà pipeline ứng dụng cần — CI không có kubectl, không đụng tới cụm EKS.
data "aws_iam_policy_document" "gha_ecr" {
  # (1) Lấy token đăng nhập Docker. Action này KHÔNG hỗ trợ resource-level —
  #     AWS bắt buộc "*". Viết ARN cụ thể vào đây thì policy vẫn hợp lệ, apply
  #     vẫn sạch, nhưng `docker login` trả "no basic auth credentials" và không
  #     có gì chỉ ra nguyên nhân. (Cùng họ với ssm:DescribeParameters ở Day 3.)
  statement {
    sid       = "EcrGetAuthToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # (2) Đẩy/đọc layer — siết đúng 9 repo của dự án, không phải toàn tài khoản.
  #     Danh sách lấy thẳng từ var.ecr_repositories nên không bao giờ lệch với
  #     resource ECR thật.
  statement {
    sid    = "EcrPushToProjectRepositories"
    effect = "Allow"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      # 3 action đọc dưới đây cần cho layer cache và cho bước nghiệm thu
      # `describe-images` sau khi push.
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:DescribeImages",
    ]

    resources = [
      for repo in var.ecr_repositories :
      "arn:aws:ecr:${var.region}:${data.aws_caller_identity.current.account_id}:repository/${repo}"
    ]
  }
}

resource "aws_iam_policy" "gha_ecr" {
  name        = "${var.project}-gha-ecr"
  description = "Quyền tối thiểu để GitHub Actions đẩy image lên 9 ECR repo của dự án"
  policy      = data.aws_iam_policy_document.gha_ecr.json

  tags = local.tags
}

resource "aws_iam_role" "gha_ecr" {
  name               = "${var.project}-gha-ecr"
  description        = "GitHub Actions pushes images to the 9 project ECR repos. Trusts refs/heads/main only." # ASCII-only, xem cảnh báo phía trên
  assume_role_policy = data.aws_iam_policy_document.gha_assume_main.json

  tags = merge(local.tags, { Name = "${var.project}-gha-ecr" })
}

resource "aws_iam_role_policy_attachment" "gha_ecr" {
  role       = aws_iam_role.gha_ecr.name
  policy_arn = aws_iam_policy.gha_ecr.arn
}

# ── 4. Role chạy `terraform plan` trên Pull Request ───────────────────────────
# Tách khỏi role apply để run trên PR KHÔNG BAO GIỜ cầm quyền ghi. `plan` vẫn
# gọi rất nhiều API đọc (refresh state với AWS thật) nên ReadOnlyAccess là mức
# hẹp nhất còn dùng được; viết policy tay ở đây sẽ là danh sách vài trăm action.
#
# ⚠️ Workflow phải chạy `terraform plan -lock=false`: khoá state là thao tác GHI
#    vào DynamoDB, role này không có quyền đó.
resource "aws_iam_role" "gha_tf_plan" {
  name               = "${var.project}-gha-tf-plan"
  description        = "GitHub Actions runs terraform plan on pull requests. Read-only." # ASCII-only, xem cảnh báo phía trên
  assume_role_policy = data.aws_iam_policy_document.gha_assume_pull_request.json

  tags = merge(local.tags, { Name = "${var.project}-gha-tf-plan" })
}

resource "aws_iam_role_policy_attachment" "gha_tf_plan" {
  role       = aws_iam_role.gha_tf_plan.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# ── 5. Role chạy `terraform apply` / `destroy` ────────────────────────────────
# Chỉ dùng ở job workflow_dispatch (bấm tay) của terraform.yml. Cần
# AdministratorAccess thật vì stack ephemeral dựng VPC + EKS + IAM + node group;
# thu hẹp cho đủ 76 resource sẽ thành một danh sách không ai bảo trì nổi, và
# thiếu một quyền thì hỏng SAU 15 phút dựng control plane (tức là mất tiền để
# nhận một lỗi phân quyền).
#
# Bù lại, bề mặt bị siết ở chỗ khác: chỉ nhánh `main` mượn được role, và chỉ
# workflow_dispatch mới gọi tới nó — mà dispatch đòi quyền write trên repo.
resource "aws_iam_role" "gha_tf_apply" {
  name               = "${var.project}-gha-tf-apply"
  description        = "GitHub Actions runs terraform apply/destroy via workflow_dispatch on main." # ASCII-only, xem cảnh báo phía trên
  assume_role_policy = data.aws_iam_policy_document.gha_assume_main.json

  # Mặc định 1 giờ. Dựng cụm đo được ~22 phút, xoá ~13 phút — vẫn dư, nhưng để
  # 2 giờ để một lần `destroy` kẹt ở Internet Gateway không làm hết hạn phiên
  # giữa chừng (credential hết hạn giữa apply = state ghi dở, rất khó dọn).
  max_session_duration = 7200

  tags = merge(local.tags, { Name = "${var.project}-gha-tf-apply" })
}

resource "aws_iam_role_policy_attachment" "gha_tf_apply" {
  role       = aws_iam_role.gha_tf_apply.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}
