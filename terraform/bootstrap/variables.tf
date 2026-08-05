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

variable "ecr_keep_last_images" {
  description = "Số image gần nhất giữ lại mỗi repo (lifecycle policy). Hạ xuống 5 nếu muốn tiết kiệm dung lượng."
  type        = number
  default     = 10
}
