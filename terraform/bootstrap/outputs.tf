output "state_bucket_name" {
  description = "Tên S3 bucket lưu state — phải khớp literal `bucket` trong ../backend.tf."
  value       = aws_s3_bucket.tfstate.id
}

output "lock_table_name" {
  description = "Tên bảng DynamoDB giữ state lock — phải khớp `dynamodb_table` trong ../backend.tf."
  value       = aws_dynamodb_table.tflock.name
}

# Day 4/5 cần chuỗi này để tag + push image, và để bump image.repository ở repo gitops.
output "ecr_registry_url" {
  description = "Registry URL gốc: <account-id>.dkr.ecr.<region>.amazonaws.com"
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
}

output "ecr_repository_urls" {
  description = "URL đầy đủ từng repo, key = tên service."
  value       = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "ecr_login_command" {
  description = "Lệnh đăng nhập Docker vào ECR (Day 4/5)."
  value       = "aws ecr get-login-password --region ${var.region} | docker login --username AWS --password-stdin ${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
}

# ── Day 5: 3 ARN dưới đây dán vào GitHub → Settings → Secrets and variables →
#    Actions. Dùng Secrets (không phải Variables) vì ARN chứa account ID, mà
#    repo này public. Xem lại bất cứ lúc nào bằng `terraform output` ở thư mục
#    terraform/bootstrap.
output "gha_ecr_role_arn" {
  description = "→ secret AWS_ROLE_ECR_ARN. Job release của ci.yml assume role này để push ECR."
  value       = aws_iam_role.gha_ecr.arn
}

output "gha_tf_plan_role_arn" {
  description = "→ secret AWS_ROLE_TF_PLAN_ARN. Job plan của terraform.yml (chạy trên PR, chỉ đọc)."
  value       = aws_iam_role.gha_tf_plan.arn
}

output "gha_tf_apply_role_arn" {
  description = "→ secret AWS_ROLE_TF_APPLY_ARN. Job apply/destroy của terraform.yml (bấm tay)."
  value       = aws_iam_role.gha_tf_apply.arn
}
