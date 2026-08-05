module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0" # 🔴 PIN — module đổi major là hỏng cả stack

  name = "${var.project}-vpc"
  cidr = var.vpc_cidr

  azs             = var.azs
  public_subnets  = var.public_subnets
  private_subnets = var.private_subnets

  # 🔴 KHÔNG tạo NAT Gateway — ~$45/tháng, và tiền chảy kể cả khi không ai dùng.
  #    docs/DAY3-RUNBOOK.md §8.1 kiểm: `aws ec2 describe-nat-gateways` PHẢI rỗng.
  enable_nat_gateway = false

  # ⚠️ Đây là thứ THAY THẾ NAT: node nằm ở public subnet và tự có IP công khai để pull
  #    image từ ECR + gọi EKS/STS API. Thiếu dòng này (và cũng không có VPC endpoint
  #    ecr.api/ecr.dkr/s3/sts/logs) thì Day 4 mọi pod kẹt ImagePullBackOff.
  #    Kiểm sau khi apply: `kubectl get nodes -o wide` cột EXTERNAL-IP không được là <none>.
  map_public_ip_on_launch = true

  # EKS cần cả hai để node đăng ký được bằng DNS nội bộ.
  enable_dns_hostnames = true
  enable_dns_support   = true

  # 🔴 Tag subnet là thứ dễ quên nhất mà hậu quả xuất hiện muộn nhất: thiếu tag thì Day 3
  #    vẫn xanh, nhưng Day 4 Ingress treo vô hạn và `kubectl describe ingress` chỉ nói
  #    "couldn't auto-discover subnets". AWS Load Balancer Controller TỰ DÒ subnet qua tag.
  public_subnet_tags = {
    "kubernetes.io/role/elb"                    = "1" # ALB internet-facing đứng ở đây
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }

  # Hôm nay chưa dùng internal ALB, nhưng tag sẵn để Day sau bật không phải sửa VPC.
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }

  tags = local.tags
}
