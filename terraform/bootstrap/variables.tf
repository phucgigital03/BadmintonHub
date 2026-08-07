variable "region" {
  description = "AWS region. Toàn dự án dùng ap-southeast-1 (Singapore) — quota vCPU đã duyệt ở đây."
  type        = string
  default     = "ap-southeast-1"
}

variable "project" {
  description = "Tiền tố tên + tag Project cho mọi resource."
  type        = string
  default     = "badminton"
}

# ⚠️ Tên S3 là UNIQUE TOÀN CẦU (mọi tài khoản AWS trên thế giới).
#    Chuỗi này phải khớp CHÍNH XÁC với literal `bucket` trong ../backend.tf.
#    Nếu `apply` báo BucketAlreadyExists → đổi ở ĐÚNG 2 CHỖ: biến này + ../backend.tf.
variable "state_bucket_name" {
  description = "Tên S3 bucket lưu Terraform state (unique toàn cầu). Phải khớp ../backend.tf."
  type        = string
  default     = "badminton-tfstate-apse1"
}

variable "lock_table_name" {
  description = "Tên bảng DynamoDB giữ state lock. Phải khớp ../backend.tf."
  type        = string
  default     = "badminton-tflock"
}

# Đúng 9 deployable của Day 1 — khớp 1-1 với values/<svc>-<env>.yaml ở repo badmintonHub-gitops.
# Đổi tên ở đây = phải đổi cả tên file values bên đó, nếu không CI Day 5 bump nhầm chỗ.
variable "ecr_repositories" {
  description = "Danh sách ECR repo, mỗi service deployable một repo."
  type        = list(string)
  default = [
    "eureka-server",
    "api-gateway",
    "user-service",
    "court-service",
    "booking-service",
    "payment-service",
    "escrow-service",
    "chat-service",
    "frontend",
  ]
}

# 🔴 Từ Day 5, MỖI LẦN merge vào main là CI đẩy thêm một image ⇒ trần này bị
# chạm thường xuyên hơn hẳn thời còn build tay. Lifecycle dùng tagStatus="any"
# nên nó xoá cả image mà values/<svc>-prod.yaml đang trỏ tới — prod sẽ
# ImagePullBackOff ở lần pod restart kế tiếp, và log không nói gì về ECR.
# 20 cho ~2x biên an toàn. Chi phí ECR ≈ $0.10/GB-tháng: giữ 10 ≈ $1.1/tháng,
# giữ 20 ≈ $2.0/tháng, giữ 30 ≈ $2.9/tháng.
variable "ecr_keep_last_images" {
  description = "Số image gần nhất giữ lại mỗi repo (lifecycle policy). Hạ xuống 10 nếu muốn tiết kiệm dung lượng."
  type        = number
  default     = 20
}

# ── GitHub Actions OIDC (Day 5) ───────────────────────────────────────────────
# Hai chuỗi này đi thẳng vào điều kiện `sub` của trust policy trong
# github-oidc.tf. Sai chữ hoa/thường ⇒ CI báo
# "Not authorized to perform sts:AssumeRoleWithWebIdentity" mà không nói vì sao.
# Kiểm bằng `git remote -v`: phải khớp CHÍNH XÁC đoạn <owner>/<repo> trong URL.
variable "github_owner" {
  description = "Chủ sở hữu repo trên GitHub (user hoặc org)."
  type        = string
  default     = "phucgigital03"
}

variable "github_repo" {
  description = "Tên repo APP chứa .github/workflows (không phải repo gitops). Phân biệt hoa/thường."
  type        = string
  default     = "BadmintonHub"
}
