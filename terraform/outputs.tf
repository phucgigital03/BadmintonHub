# scripts/bootstrap.sh đọc các output này bằng `terraform output -raw <tên>` —
# nhờ vậy script KHÔNG hardcode ARN / vpc-id nào, và tự khớp sau mỗi lần rebuild cụm.

output "region" {
  description = "AWS region của cụm."
  value       = var.region
}

output "cluster_name" {
  description = "Tên EKS cluster (dùng cho `aws eks update-kubeconfig`)."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "Endpoint API server."
  value       = module.eks.cluster_endpoint
}

output "vpc_id" {
  description = "VPC id — AWS Load Balancer Controller cần tham số này."
  value       = module.vpc.vpc_id
}

output "public_subnet_ids" {
  description = "Public subnet (node + ALB internet-facing)."
  value       = module.vpc.public_subnets
}

output "private_subnet_ids" {
  description = "Private subnet (ENI control plane; chưa dùng cho workload)."
  value       = module.vpc.private_subnets
}

output "oidc_provider_arn" {
  description = "OIDC provider của cụm — nền tin cậy của cả 4 IRSA role."
  value       = module.eks.oidc_provider_arn
}

output "irsa_alb_controller_role_arn" {
  description = "Role cho ServiceAccount kube-system/aws-load-balancer-controller."
  value       = module.irsa_alb_controller.iam_role_arn
}

output "irsa_ebs_csi_role_arn" {
  description = "Role cho ServiceAccount kube-system/ebs-csi-controller-sa (add-on dùng)."
  value       = module.irsa_ebs_csi.iam_role_arn
}

output "irsa_external_secrets_role_arn" {
  description = "Role cho ServiceAccount external-secrets/external-secrets."
  value       = module.irsa_external_secrets.iam_role_arn
}

output "irsa_external_dns_role_arn" {
  description = "Role cho ServiceAccount kube-system/external-dns (chart cài ở Day 8)."
  value       = module.irsa_external_dns.iam_role_arn
}

output "update_kubeconfig_command" {
  description = "Lệnh nối kubectl vào cụm."
  value       = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.region}"
}
