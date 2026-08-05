# Stack BOOTSTRAP — apply MỘT LẦN, KHÔNG BAO GIỜ destroy.
#
# 🥚 Bẫy con-gà-quả-trứng: stack này TẠO RA chính cái S3 bucket dùng để lưu state,
#    nên lần `apply` ĐẦU TIÊN buộc phải chạy với local state — khối `backend` bên dưới
#    vì thế đang để comment. Bỏ comment sớm thì `terraform init` chết ngay vì bucket
#    chưa tồn tại.
#
# ⚠️ `terraform init -migrate-state` CHỈ có tác dụng khi cấu hình backend THAY ĐỔI.
#    Chạy nó lúc file này chưa có khối `backend` thì Terraform in "successfully
#    initialized" nhưng state VẪN NẰM LOCAL — dễ tưởng đã đẩy lên S3 mà không.
#
# Trình tự đúng (docs/DAY3-RUNBOOK.md Phase 5):
#   1. terraform init && terraform plan && terraform apply     ← state local, bucket ra đời
#   2. bỏ comment khối `backend` bên dưới
#   3. terraform init -migrate-state                           ← "copy existing state?" → yes
#   4. aws s3 ls s3://badminton-tfstate-apse1/bootstrap/       ← thấy terraform.tfstate mới là XONG
#
# ⚠️ Khối `backend` KHÔNG nội suy biến được (`var.*` vô dụng ở đó) — Terraform đọc nó
#    trước cả khi biết biến là gì. Nên 2 chuỗi là literal và phải KHỚP tay:
#      bucket         = var.state_bucket_name (variables.tf)
#      dynamodb_table = var.lock_table_name   (variables.tf)
#    Đổi tên bucket = sửa ĐÚNG 3 CHỖ: variables.tf · file này · ../backend.tf
#
# `key` khác ../backend.tf ("eks/terraform.tfstate"): hai stack dùng CHUNG bucket nhưng
# PHẢI tách file state, nếu không stack này ghi đè stack kia.

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # ✅ Đã bỏ comment 2026-08-04 sau khi `apply` đầu tiên tạo xong bucket.
  #    Từ giờ `terraform init` ở thư mục này đọc/ghi state trên S3, không còn local.
  backend "s3" {
    bucket         = "badminton-tfstate-apse1"
    key            = "bootstrap/terraform.tfstate"
    region         = "ap-southeast-1"
    dynamodb_table = "badminton-tflock"
    encrypt        = true
  }
}
