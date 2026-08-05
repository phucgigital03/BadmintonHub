# 9 ECR repository — mỗi deployable một repo. Phải SỐNG SÓT qua `terraform destroy`:
# mất repo = phải build + push lại toàn bộ 9 image trước buổi demo kế tiếp.
#
# Image tag = git SHA, KHÔNG BAO GIỜ `latest` (docs/DAY3-RUNBOOK.md §8.5).
# URL đầy đủ: <account-id>.dkr.ecr.ap-southeast-1.amazonaws.com/<svc>:<git-SHA>

resource "aws_ecr_repository" "this" {
  for_each = toset(var.ecr_repositories)

  name = each.value

  # MUTABLE chứ không IMMUTABLE: tag đã là git SHA nên trùng tag gần như không xảy ra,
  # nhưng CI (Day 5) chạy LẠI trên cùng một commit là chuyện thường — IMMUTABLE sẽ làm
  # lần push thứ hai fail và job đỏ vì một lý do không liên quan đến code.
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true # basic scanning — miễn phí
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = each.value
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  # Rule có tagStatus = "any" BẮT BUỘC mang rulePriority cao nhất (quy tắc của ECR).
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Xoá image untagged sau 1 ngày (rác từ build dở)"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Chỉ giữ ${var.ecr_keep_last_images} image gần nhất"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.ecr_keep_last_images
        }
        action = { type = "expire" }
      },
    ]
  })
}
