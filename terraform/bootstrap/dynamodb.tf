# Bảng khoá state: chặn 2 lần `terraform apply` chạy song song ghi đè state của nhau.
#
# Partition key BẮT BUỘC tên `LockID` kiểu String — đây là quy ước cứng của backend S3,
# đặt tên khác thì Terraform không tìm thấy khoá (docs/DAY3-RUNBOOK.md §8.2 kiểm ở Console).

resource "aws_dynamodb_table" "tflock" {
  name = var.lock_table_name

  # PAY_PER_REQUEST: vài lượt ghi mỗi buổi → gần như $0. PROVISIONED sẽ tính tiền 24/7.
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  point_in_time_recovery {
    enabled = false
  }

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = "${var.project}-tflock"
  }
}
