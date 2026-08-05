#!/usr/bin/env bash
#
# Day 3 · Phase 7 — nối kubectl vào cụm rồi cài add-on.
# Chạy SAU `terraform apply` ở terraform/ (docs/DAY3-RUNBOOK.md Phase 6).
#
#   ./scripts/bootstrap.sh
#
# IDEMPOTENT: chạy lại bao nhiêu lần cũng được. Mỗi buổi rebuild cụm chỉ cần
#   cd terraform && terraform apply && ../scripts/bootstrap.sh
#
# 🔴 THỨ TỰ Ở ĐÂY LÀ RÀNG BUỘC THẬT, không phải cho gọn:
#      StorageClass gp3 → ALB controller → External Secrets + ClusterSecretStore
#    Từ Day 6, ESO phải Ready TRƯỚC khi ArgoCD sync app; nếu không pod khởi động lúc
#    Secret chưa tồn tại → CreateContainerConfigError.
#    (EBS CSI driver KHÔNG nằm ở đây — nó là EKS managed add-on khai trong terraform/eks.tf
#     nên nằm trong tf state và `terraform destroy` gỡ sạch.)
#
# 🔴 KHÔNG cài cert-manager ở bất kỳ Day nào: ALB terminate TLS ở tầng AWS và chỉ nhận
#    cert từ ACM/IAM — nó KHÔNG đọc được Kubernetes Secret, đúng chỗ cert-manager cất cert.
#    Ghép vào thì cert xin về thành công mà ALB lờ đi: không có HTTPS và chẳng báo lỗi ở đâu.
#    HTTPS của dự án đi bằng ACM (Day 8). ExternalDNS cũng để Day 8 (role đã tạo sẵn).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${REPO_ROOT}/terraform"

# Ghim version chart để mỗi lần rebuild ra ĐÚNG MỘT cụm giống nhau — không để trống, vì "bản mới
# nhất" nghĩa là cụm hôm nay và cụm tuần sau có thể khác nhau mà không ai đổi một dòng code nào.
# Số dưới đây chép từ lần cài THẬT đầu tiên (2026-08-05, script tự in ở cuối).
# Nâng version = đổi hành vi cụm ⇒ chỉ nâng có chủ đích, rồi chạy lại §8.6 để chắc IRSA vẫn thông.
# Muốn thử bản mới 1 lần mà không sửa file:  ALB_CHART_VERSION="" ./scripts/bootstrap.sh
ALB_CHART_VERSION="${ALB_CHART_VERSION:-3.5.0}"
ESO_CHART_VERSION="${ESO_CHART_VERSION:-2.8.0}"

# Tên release PHẢI giữ nguyên: runbook §9.1/§9.2 gõ `helm uninstall aws-lb-controller`.
ALB_RELEASE="aws-lb-controller"
ESO_RELEASE="external-secrets"
ESO_NAMESPACE="external-secrets"

# Tên này là HỢP ĐỒNG với Day 6 — mọi ExternalSecret sẽ tham chiếu nó.
SECRET_STORE_NAME="aws-ssm"

log() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok() { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
die() {
  printf '\n\033[1;31m✘ %s\033[0m\n' "$*" >&2
  exit 1
}

# ─────────────────────────────────────────────────────────────────────────────
log "Kiểm công cụ"
for bin in aws kubectl helm terraform; do
  command -v "$bin" >/dev/null 2>&1 || die "thiếu '$bin' trên PATH"
done
ok "aws · kubectl · helm · terraform"

# ─────────────────────────────────────────────────────────────────────────────
log "Đọc output của Terraform (không hardcode ARN/vpc-id nào)"
tf_out() {
  terraform -chdir="$TF_DIR" output -raw "$1" 2>/dev/null ||
    die "không đọc được output '$1'. Đã chạy 'cd terraform && terraform apply' chưa?"
}

REGION="$(tf_out region)"
CLUSTER_NAME="$(tf_out cluster_name)"
VPC_ID="$(tf_out vpc_id)"
ALB_ROLE_ARN="$(tf_out irsa_alb_controller_role_arn)"
ESO_ROLE_ARN="$(tf_out irsa_external_secrets_role_arn)"
ok "cluster=${CLUSTER_NAME} region=${REGION} vpc=${VPC_ID}"

# ─────────────────────────────────────────────────────────────────────────────
log "Nối kubectl vào cụm"
aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$REGION" >/dev/null
ok "kubeconfig đã trỏ vào ${CLUSTER_NAME}"

if ! kubectl get nodes >/dev/null 2>&1; then
  cat >&2 <<EOF

✘ Không nói chuyện được với cụm.

  Nếu lỗi là "You must be logged in to the server (Unauthorized)" thì CỤM KHÔNG HỎNG —
  chỉ là principal hiện tại chưa có access entry. ĐỪNG destroy rồi dựng lại (mất 30' + tiền).
  Vá nóng, không cần dựng lại cụm:

    ARN=\$(aws sts get-caller-identity --query Arn --output text)
    aws eks create-access-entry --cluster-name ${CLUSTER_NAME} --region ${REGION} \\
      --principal-arn "\$ARN" --type STANDARD
    aws eks associate-access-policy --cluster-name ${CLUSTER_NAME} --region ${REGION} \\
      --principal-arn "\$ARN" \\
      --policy-arn arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy \\
      --access-scope type=cluster

  Rồi thêm ARN đó vào biến 'additional_cluster_admin_arns' để lần rebuild sau tự có.
EOF
  exit 1
fi

kubectl get nodes -o wide

# Node phải có EXTERNAL-IP: mô hình này né NAT Gateway nên node ra Internet bằng public IP
# của chính nó. Không có IP = không pull được image từ ECR → Day 4 kẹt ImagePullBackOff.
if kubectl get nodes -o jsonpath='{.items[*].status.addresses[?(@.type=="ExternalIP")].address}' |
  grep -q '[0-9]'; then
  ok "node có ExternalIP — đường ra ECR thông"
else
  warn "node KHÔNG có ExternalIP! Kiểm map_public_ip_on_launch trong terraform/vpc.tf,"
  warn "nếu không Day 4 mọi pod sẽ kẹt ImagePullBackOff."
fi

# ─────────────────────────────────────────────────────────────────────────────
log "1/3 · StorageClass gp3 (mặc định)"
# volumeBindingMode: WaitForFirstConsumer là bắt buộc — EBS volume gắn CHẶT vào một AZ.
# Với Immediate, volume có thể được tạo ở AZ mà scheduler không xếp pod vào → PVC kẹt Pending.
kubectl apply -f - <<EOF
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: gp3
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
  encrypted: "true"
volumeBindingMode: WaitForFirstConsumer
allowVolumeExpansion: true
reclaimPolicy: Delete
EOF

# EKS ship sẵn gp2 và đánh dấu nó là default → phải gỡ, nếu không có HAI default class
# và Kubernetes chọn bừa một cái.
if kubectl get storageclass gp2 >/dev/null 2>&1; then
  kubectl patch storageclass gp2 \
    -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}' \
    >/dev/null
  ok "gp2 không còn là default"
fi
kubectl get storageclass

# ─────────────────────────────────────────────────────────────────────────────
log "2/3 · AWS Load Balancer Controller"
helm repo add eks https://aws.github.io/eks-charts --force-update >/dev/null
helm repo update eks >/dev/null

# Mảng RỖNG + `set -u` làm bash 3.2 (bản mặc định của macOS) báo unbound variable khi expand,
# nên khởi tạo sẵn với --wait/--timeout để mảng không bao giờ rỗng.
# (Cố ý KHÔNG dùng --atomic: nó rollback khi fail, xoá luôn pod cần xem để chẩn đoán —
#  mà lúc này cụm đang tính tiền.)
alb_args=(--wait --timeout 5m)
if [[ -n "$ALB_CHART_VERSION" ]]; then
  alb_args+=(--version "$ALB_CHART_VERSION")
fi

helm upgrade --install "$ALB_RELEASE" eks/aws-load-balancer-controller \
  --namespace kube-system \
  --set "clusterName=${CLUSTER_NAME}" \
  --set "region=${REGION}" \
  --set "vpcId=${VPC_ID}" \
  --set serviceAccount.create=true \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=${ALB_ROLE_ARN}" \
  "${alb_args[@]}"
ok "release '${ALB_RELEASE}' sẵn sàng (Day 4 sẽ tạo ALB từ Ingress)"

# ─────────────────────────────────────────────────────────────────────────────
log "3/3 · External Secrets Operator + ClusterSecretStore"
helm repo add external-secrets https://charts.external-secrets.io --force-update >/dev/null
helm repo update external-secrets >/dev/null

eso_args=(--wait --timeout 5m)
if [[ -n "$ESO_CHART_VERSION" ]]; then
  eso_args+=(--version "$ESO_CHART_VERSION")
fi

# `installCRDs` là tên cũ, `crds.enabled` là tên mới — đặt cả hai để chạy được ở cả hai đời
# chart (Helm bỏ qua value không tồn tại, nên thừa một cái là vô hại).
helm upgrade --install "$ESO_RELEASE" external-secrets/external-secrets \
  --namespace "$ESO_NAMESPACE" --create-namespace \
  --set installCRDs=true \
  --set crds.enabled=true \
  --set serviceAccount.create=true \
  --set "serviceAccount.name=${ESO_RELEASE}" \
  --set "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=${ESO_ROLE_ARN}" \
  "${eso_args[@]}"

# CRD phải Established và webhook phải Available trước khi apply ClusterSecretStore, nếu không
# apply fail với "no endpoints available for service admission webhook".
kubectl wait --for=condition=Established --timeout=120s \
  crd/clustersecretstores.external-secrets.io >/dev/null
kubectl wait --for=condition=Available --timeout=180s \
  -n "$ESO_NAMESPACE" deployment --all >/dev/null || warn "một số deployment ESO chưa Available"

# ESO đổi apiVersion giữa các đời (v1beta1 → v1). Hỏi thẳng CRD xem bản nào đang được phục vụ
# thay vì đoán theo version chart.
if kubectl get crd clustersecretstores.external-secrets.io \
  -o jsonpath='{range .spec.versions[*]}{.name}{"\n"}{end}' | grep -qx 'v1'; then
  CSS_API="external-secrets.io/v1"
else
  CSS_API="external-secrets.io/v1beta1"
fi
ok "dùng apiVersion ${CSS_API}"

# Cấp cụm (Cluster-, không phải SecretStore thường) để MỌI namespace — staging, prod,
# data-staging, data-prod — dùng chung một cửa vào SSM.
# Webhook có thể còn đang lên trong vài giây đầu → thử lại vài lần thay vì chết ngay.
for attempt in 1 2 3 4 5; do
  if kubectl apply -f - <<EOF
apiVersion: ${CSS_API}
kind: ClusterSecretStore
metadata:
  name: ${SECRET_STORE_NAME}
spec:
  provider:
    aws:
      service: ParameterStore
      region: ${REGION}
      auth:
        jwt:
          serviceAccountRef:
            name: ${ESO_RELEASE}
            namespace: ${ESO_NAMESPACE}
EOF
  then
    break
  fi
  [[ $attempt -eq 5 ]] && die "không apply được ClusterSecretStore sau 5 lần thử"
  warn "webhook ESO chưa sẵn sàng, thử lại (${attempt}/5)..."
  sleep 10
done

kubectl get clustersecretstore

# ─────────────────────────────────────────────────────────────────────────────
log "Xong"
cat <<EOF
  Nghiệm thu nhanh (docs/DAY3-RUNBOOK.md §8.1):
    kubectl get nodes -o wide          # 2 node · Ready
    kubectl get storageclass           # gp3 (default)
    kubectl get clustersecretstore     # STATUS = Valid

  ⚠️ 'Valid' mới chứng minh ESO XÁC THỰC được với AWS, CHƯA chứng minh policy ĐỦ QUYỀN.
     Kéo thử một param thật (§8.6) — sai policy mà không test thì tới Day 6 mới lộ:
       aws ssm put-parameter --name /badminton/staging/SMOKE_TEST \\
         --type SecureString --value hello-irsa --region ${REGION}

  🔴 ĐỪNG QUÊN destroy cùng ngày: cd terraform && terraform destroy
EOF

if [[ -z "$ALB_CHART_VERSION" || -z "$ESO_CHART_VERSION" ]]; then
  printf '\n  Chart vừa cài (cột CHART) — ghim vào đầu script để mỗi lần rebuild giống hệt nhau:\n\n'
  helm list -A -f "^(${ALB_RELEASE}|${ESO_RELEASE})\$"
fi
