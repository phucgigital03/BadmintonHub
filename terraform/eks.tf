module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0" # 🔴 PIN. v20 bỏ aws-auth ConfigMap, chuyển sang access entries.
  #    Đừng copy ví dụ blog viết cho v19 — cú pháp phân quyền khác hẳn.

  cluster_name    = var.cluster_name
  cluster_version = var.cluster_version

  # Truy cập control plane từ laptop (kubectl) — endpoint vẫn đòi xác thực IAM + access entry.
  cluster_endpoint_public_access  = true
  cluster_endpoint_private_access = true

  # Chỉ dùng access entries, không đụng aws-auth ConfigMap.
  authentication_mode = "API"

  vpc_id = module.vpc.vpc_id

  # Node ở PUBLIC subnet (mô hình né NAT — xem vpc.tf).
  subnet_ids = module.vpc.public_subnets
  # ENI của control plane nằm private: chúng chỉ nói chuyện trong VPC, không cần ra Internet.
  control_plane_subnet_ids = module.vpc.private_subnets

  enable_irsa = true # tạo OIDC provider — nền của cả 4 IRSA role ở irsa.tf

  # Tắt log control plane cho rẻ + ít resource phải destroy.
  # Cần điều tra "ai gọi API nào" / "vì sao Unauthorized" thì đặt
  # cluster_enabled_log_types = ["audit", "authenticator"] ở terraform.tfvars.
  cluster_enabled_log_types   = var.cluster_enabled_log_types
  create_cloudwatch_log_group = length(var.cluster_enabled_log_types) > 0

  # 🔴 Thiếu dòng này thì Phase 7 `kubectl get nodes` trả
  #      error: You must be logged in to the server (Unauthorized)
  #    — cụm HOÀN TOÀN KHOẺ, chỉ là principal chạy apply không có quyền nói chuyện với nó.
  #    Module EKS ≥ v20 KHÔNG tự cấp quyền này.
  enable_cluster_creator_admin_permissions = true

  # ⚠️ CỐ Ý không khai lại principal đang chạy apply ở đây: cờ bên trên đã tạo access entry
  #    cho chính ARN đó, khai thêm lần nữa = hai access entry cùng một principal
  #    → AWS trả ResourceInUseException và apply FAIL.
  #    Map này chỉ dành cho principal KHÁC (role CI Day 5, root, đồng nghiệp).
  #    Key = ARN để danh sách đổi thứ tự không làm Terraform recreate resource.
  access_entries = {
    for arn in var.additional_cluster_admin_arns : arn => {
      principal_arn = arn

      policy_associations = {
        admin = {
          policy_arn   = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
          access_scope = { type = "cluster" }
        }
      }
    }
  }

  cluster_addons = {
    # vpc-cni phải có TRƯỚC khi node join, nếu không node lên NotReady vì chưa có mạng pod.
    vpc-cni = {
      most_recent    = true
      before_compute = true
    }
    kube-proxy = {
      most_recent = true
    }
    # coredns cần node để xếp pod → để sau node group (mặc định).
    coredns = {
      most_recent = true
    }
    # EBS CSI để ở đây (không phải trong scripts/bootstrap.sh) vì như vậy nó NẰM TRONG
    # tf state: `terraform destroy` gỡ sạch, và không phụ thuộc vào việc script có chạy đúng.
    aws-ebs-csi-driver = {
      most_recent              = true
      service_account_role_arn = module.irsa_ebs_csi.iam_role_arn
    }
  }

  eks_managed_node_groups = {
    default = {
      ami_type       = "AL2023_x86_64_STANDARD"
      instance_types = var.node_instance_types
      capacity_type  = var.node_capacity_type

      # 🔒 Ghim cả 3 bằng nhau — xem chú thích ở variables.tf (var.node_size).
      min_size     = var.node_size
      desired_size = var.node_size
      max_size     = var.node_size

      subnet_ids = module.vpc.public_subnets

      # v20 dựng node bằng custom launch template ⇒ tham số `disk_size` bị BỎ QUA.
      # Muốn đổi dung lượng ổ root thì phải khai block_device_mappings như dưới đây.
      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = {
            volume_size           = var.node_disk_size
            volume_type           = "gp3"
            encrypted             = true
            delete_on_termination = true
          }
        }
      }

      labels = {
        role = "app"
      }

      tags = local.tags
    }
  }

  tags = local.tags
}
