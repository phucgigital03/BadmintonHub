# Stack EPHEMERAL — destroy sau MỖI buổi demo (docs/DAY3-RUNBOOK.md Phase 9).
# Mọi thứ ở đây dựng lại được 100% từ code; thứ phải sống sót nằm ở bootstrap/.

terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
