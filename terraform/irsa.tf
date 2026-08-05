# IRSA — mỗi ServiceAccount trong cụm mượn một IAM role riêng qua OIDC, thay vì gắn quyền
# vào node role (nơi MỌI pod trên node đều dùng chung được).
#
# Tạo đủ CẢ 4 role ngay hôm nay dù ExternalDNS đến Day 8 mới cài chart:
# IAM role không tính tiền khi không dùng, mà thiếu role thì hỏng đúng thứ nó phục vụ.

# ── 1. AWS Load Balancer Controller ───────────────────────────────────────────
# Thiếu → Ingress không có ADDRESS → không có ALB → không vào được hệ thống (Day 4).
module "irsa_alb_controller" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name                              = "${var.cluster_name}-alb-controller"
  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }

  tags = local.tags
}

# ── 2. EBS CSI driver ─────────────────────────────────────────────────────────
# Thiếu → PVC kẹt Pending → 5 datastore không boot (Day 4).
# Role này được eks.tf gắn thẳng vào add-on aws-ebs-csi-driver.
module "irsa_ebs_csi" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name             = "${var.cluster_name}-ebs-csi"
  attach_ebs_csi_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }

  tags = local.tags
}

# ── 3. External Secrets Operator ──────────────────────────────────────────────
# Thiếu/sai quyền → SecretSyncedError → pod CreateContainerConfigError (Day 6).
# Policy TỰ VIẾT vì bản dựng sẵn của module rộng hơn mức cần.
data "aws_iam_policy_document" "external_secrets" {
  # (1) Chỉ đọc được param của dự án. KHÔNG cấp ssm:* toàn account.
  statement {
    sid = "ReadBadmintonParameters"

    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]

    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${var.project}/*",
    ]
  }

  # (2) ssm:DescribeParameters KHÔNG hỗ trợ resource-level permission — AWS bắt buộc "*".
  #     Viết arn:...parameter/badminton/* ở đây thì policy trông chặt nhưng VÔ DỤNG:
  #     lời gọi luôn bị từ chối và ESO không liệt kê được param.
  statement {
    sid       = "ListParameters"
    actions   = ["ssm:DescribeParameters"]
    resources = ["*"]
  }

  # (3) Giải mã SecureString.
  #     ⚠️ Lệch có chủ đích so với docs/DAY3-RUNBOOK.md §8.4 (ghi "kms:Decrypt trên
  #     alias/aws/ssm"): key AWS-managed alias/aws/ssm CHỈ ĐƯỢC TẠO khi tài khoản tạo
  #     SecureString đầu tiên. Tài khoản này chưa có param nào, nên tra key bằng
  #     `data "aws_kms_alias"` sẽ làm `terraform plan` FAIL với "no matching alias found"
  #     — hỏng trước cả khi kịp apply.
  #     Điều kiện kms:ViaService cho quyền tương đương-hoặc-chặt-hơn (chỉ giải mã được khi
  #     lời gọi đi qua SSM, không dùng key cho việc khác) mà không phụ thuộc key đã tồn tại.
  statement {
    sid       = "DecryptSecureStringViaSsm"
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_policy" "external_secrets" {
  name        = "${var.cluster_name}-external-secrets"
  description = "Quyền tối thiểu để ESO đọc /${var.project}/* trong SSM Parameter Store"
  policy      = data.aws_iam_policy_document.external_secrets.json

  tags = local.tags
}

module "irsa_external_secrets" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name = "${var.cluster_name}-external-secrets"

  role_policy_arns = {
    ssm_read = aws_iam_policy.external_secrets.arn
  }

  oidc_providers = {
    main = {
      provider_arn = module.eks.oidc_provider_arn
      # Trust condition sẽ là sub = system:serviceaccount:external-secrets:external-secrets
      namespace_service_accounts = ["external-secrets:external-secrets"]
    }
  }

  tags = local.tags
}

# ── 4. ExternalDNS (chart cài ở Day 8, role tạo từ hôm nay) ───────────────────
# Thiếu → record DNS không tự tạo → phải sửa DNS bằng tay mỗi buổi rebuild.
module "irsa_external_dns" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name                  = "${var.cluster_name}-external-dns"
  attach_external_dns_policy = true

  # Hosted zone chưa tồn tại (Day 8 mới mua domain) → để wildcard.
  # Sau Day 8 nên siết lại thành ARN của đúng zone đó.
  external_dns_hosted_zone_arns = ["arn:aws:route53:::hostedzone/*"]

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:external-dns"]
    }
  }

  tags = local.tags
}
