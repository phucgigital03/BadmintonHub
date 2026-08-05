variable "region" {
  description = "AWS region. Quota vCPU (Spot L-34B43A08 + On-Demand L-1216C47A = 16) đã duyệt ở ap-southeast-1."
  type        = string
  default     = "ap-southeast-1"
}

variable "project" {
  description = "Tiền tố tên + tag Project."
  type        = string
  default     = "badminton"
}

variable "cluster_name" {
  description = "Tên EKS cluster. Đi vào tag subnet kubernetes.io/cluster/<name> — đổi là phải đổi cả tài liệu verify."
  type        = string
  default     = "badminton"
}

variable "cluster_version" {
  description = "Phiên bản Kubernetes của control plane. Nên khớp với kubectl trên máy dev (hiện v1.33.2)."
  type        = string
  default     = "1.33"
}

# ── Mạng ──────────────────────────────────────────────────────────────────────

variable "vpc_cidr" {
  description = "CIDR của VPC."
  type        = string
  default     = "10.0.0.0/16"
}

# 3 AZ chứ không phải 2 (mức tối thiểu EKS yêu cầu): managed node group được CHỌN AZ khi
# spot t3.xlarge cạn hàng ở một zone. Đây đúng là lỗi "Unsupported instance type in
# availability zone" ở bảng Phase 6 của runbook. Subnet không tính tiền nên AZ thứ 3 miễn phí.
variable "azs" {
  description = "Danh sách Availability Zone. Spot hết hàng ở một AZ thì sửa list này rồi apply lại."
  type        = list(string)
  default     = ["ap-southeast-1a", "ap-southeast-1b", "ap-southeast-1c"]
}

# /19 = 8190 IP mỗi subnet. Rộng có chủ đích: VPC CNI cấp một IP THẬT của VPC cho MỖI POD
# (không phải mỗi node), mà t3.xlarge trần ~58 pod/node. Subnet /24 sẽ chật khi Day 4 chạy
# song song staging + prod + observability.
variable "public_subnets" {
  description = "CIDR public subnet — NODE nằm ở đây (mô hình né NAT) cùng với ALB."
  type        = list(string)
  default     = ["10.0.96.0/19", "10.0.128.0/19", "10.0.160.0/19"]
}

variable "private_subnets" {
  description = "CIDR private subnet — hôm nay chỉ chứa ENI của control plane, không có route ra Internet. Tạo + tag sẵn để Day sau đổi sang mô hình chuẩn chỉ cần sửa tham số."
  type        = list(string)
  default     = ["10.0.0.0/19", "10.0.32.0/19", "10.0.64.0/19"]
}

# ── Node group ────────────────────────────────────────────────────────────────

variable "node_instance_types" {
  description = "Loại instance cho node group. KHÔNG dùng t3.large: footprint thật 20-24GB (9 app pod × 2 env + 5 datastore × 2 ns + ArgoCD + observability) sẽ OOM giữa demo trên 16GB."
  type        = list(string)
  default     = ["t3.xlarge"]
}

variable "node_capacity_type" {
  description = "SPOT (~$0.13/giờ cho 2 node) hay ON_DEMAND. Quota On-Demand cũng đã ở 16 nên đổi được nếu spot hết hàng."
  type        = string
  default     = "SPOT"

  validation {
    condition     = contains(["SPOT", "ON_DEMAND"], var.node_capacity_type)
    error_message = "node_capacity_type phải là SPOT hoặc ON_DEMAND."
  }
}

# 🔒 Ghim CỨNG min = desired = max. Cụm này KHÔNG cài Cluster Autoscaler nên không ai
#    nâng desired_size — max_size = 3 và = 2 cho cùng 2 node, cùng số tiền. Ghim bằng 2 là
#    RÀO CHẮN: biến lời hứa "cụm chỉ ~$0.25/giờ" thành ràng buộc kỹ thuật thay vì niềm tin.
#    Đánh đổi đã biết: nâng version node group TẠI CHỖ sẽ fail vì cần headroom dựng node mới
#    trước khi hạ node cũ. Mô hình ephemeral destroy/recreate mỗi buổi nên vô hại.
variable "node_size" {
  description = "Số node cố định. Gán cho cả min_size, desired_size và max_size."
  type        = number
  default     = 2
}

variable "node_disk_size" {
  description = "Dung lượng ổ root mỗi node (GB). 20GB mặc định sẽ chật khi node kéo 9 image Java (~440MB/image) + 5 datastore Bitnami × 2 env ở Day 4."
  type        = number
  default     = 50
}

# ── Quyền ─────────────────────────────────────────────────────────────────────

# Principal chạy `terraform apply` ĐÃ được cấp quyền admin trong Kubernetes qua
# `enable_cluster_creator_admin_permissions = true` (xem eks.tf) — KHÔNG khai lại ở đây,
# vì hai access entry cho cùng một principal làm AWS trả ResourceInUseException.
# Biến này chỉ dành cho principal KHÁC: role CI của Day 5, root, hoặc đồng nghiệp.
variable "additional_cluster_admin_arns" {
  description = "ARN của các principal KHÁC (ngoài người chạy apply) cần quyền admin trong cụm."
  type        = list(string)
  default     = []
}

# ── Gỡ lỗi ────────────────────────────────────────────────────────────────────

# Mặc định TẮT log control plane: mỗi log type là một luồng vào CloudWatch có tính tiền,
# cộng thêm log group phải destroy. Bật ["audit", "authenticator"] khi cần điều tra
# "ai gọi API nào" / "vì sao Unauthorized".
variable "cluster_enabled_log_types" {
  description = "Log control plane đẩy sang CloudWatch. [] = tắt hẳn."
  type        = list(string)
  default     = []
}
