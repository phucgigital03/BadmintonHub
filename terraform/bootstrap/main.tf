provider "aws" {
  region = var.region

  default_tags {
    tags = local.tags
  }
}

# Hỏi AWS "tôi đang là ai" lúc apply — KHÔNG hardcode account ID vào file .tf
# (repo này sẽ PUBLIC ở Day 5).
data "aws_caller_identity" "current" {}

locals {
  tags = {
    Project   = var.project
    ManagedBy = "terraform"
    Stack     = "bootstrap"
  }
}
