provider "aws" {
  region = var.region

  default_tags {
    tags = local.tags
  }
}

# "Tôi đang là ai" — dùng để dựng ARN của SSM parameter (irsa.tf) mà KHÔNG hardcode
# account ID vào file .tf. Repo này sẽ PUBLIC ở Day 5.
data "aws_caller_identity" "current" {}

locals {
  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Stack     = "ephemeral" # 🔴 destroy sau mỗi buổi demo
    Cluster   = var.cluster_name
  }
}
