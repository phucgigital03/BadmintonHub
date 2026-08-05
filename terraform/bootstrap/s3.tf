# S3 bucket giữ Terraform state của stack ephemeral (../).
# Phải SỐNG SÓT qua mọi `terraform destroy` — mất bucket này là mất quyền quản lý
# hạ tầng bằng Terraform (phải `terraform import` lại từng resource).

resource "aws_s3_bucket" "tfstate" {
  bucket = var.state_bucket_name

  # 🔴 Chặn `terraform destroy` chạy nhầm ở stack này — nó sẽ FAIL TO thay vì xoá state.
  #    Muốn xoá thật thì phải cố ý gỡ dòng này trước.
  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = "${var.project}-tfstate"
  }
}

# Versioning = Enabled là BẮT BUỘC (docs/DAY3-RUNBOOK.md §8.2 kiểm ở Console):
# state ghi đè sai thì còn version cũ để khôi phục.
resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# tfstate chứa giá trị THÔ của mọi resource (kể cả field đánh dấu sensitive) → khoá cả 4 cờ.
resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Mỗi lần apply/destroy sinh một version mới. Không dọn thì bucket phình dần.
resource "aws_s3_bucket_lifecycle_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  # Versioning phải bật trước khi khai rule cho noncurrent version.
  depends_on = [aws_s3_bucket_versioning.tfstate]

  rule {
    id     = "expire-noncurrent-state-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
