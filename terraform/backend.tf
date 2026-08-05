# State nằm NGOÀI cụm (S3 + DynamoDB do bootstrap/ tạo) → `terraform destroy` mỗi buổi
# không làm mất khả năng dựng lại.
#
# ⚠️ Khối `backend` KHÔNG nội suy biến được (`var.*` không dùng được ở đây) — Terraform
#    đọc nó trước cả khi biết biến là gì. Nên hai chuỗi dưới đây phải là literal, và phải
#    KHỚP CHÍNH XÁC với bootstrap/variables.tf:
#      bucket         ↔ var.state_bucket_name
#      dynamodb_table ↔ var.lock_table_name
#    Đổi tên bucket = sửa ĐÚNG 2 CHỖ (file này + bootstrap/variables.tf).
#
# Lần init đầu tiên phải chạy SAU khi `terraform apply` ở bootstrap/ xong, nếu không
# Terraform báo bucket không tồn tại. Muốn kiểm cú pháp mà chưa có bucket:
#   terraform init -backend=false && terraform validate

terraform {
  backend "s3" {
    bucket         = "badminton-tfstate-apse1"
    key            = "eks/terraform.tfstate"
    region         = "ap-southeast-1"
    dynamodb_table = "badminton-tflock"
    encrypt        = true
  }
}
