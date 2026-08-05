#!/usr/bin/env bash
#
# Day 3 · Phase 8.6 — chứng minh IRSA + External Secrets chạy THẬT (docs/DAY3-RUNBOOK.md §8.6).
#
#   ./scripts/smoke-irsa.sh
#
# Chạy SAU ./scripts/bootstrap.sh. Tự dọn sạch mọi thứ nó tạo ra (kể cả khi lỗi giữa chừng).
#
# 🔴 VÌ SAO CẦN: `kubectl get clustersecretstore` ra STATUS=Valid mới chỉ chứng minh ESO
#    XÁC THỰC được với AWS (assume-role qua OIDC thành công). Nó KHÔNG chứng minh policy
#    đủ quyền đọc param. Đường duy nhất biết chắc là kéo về MỘT param thật.
#    Sai policy mà không test hôm nay thì tới Day 6 mới lộ, dưới dạng SecretSyncedError →
#    pod CreateContainerConfigError — lúc đó lẫn vào 20 thứ khác đang hỏng.
#
#    Cụ thể nó bắt được 2 lỗi mà `Valid` bỏ qua:
#      · thiếu kms:Decrypt        → đọc được tên param nhưng không mở được SecureString
#      · ssm:DescribeParameters bị siết resource (action này KHÔNG hỗ trợ resource-level,
#        bắt buộc "*") → policy hợp lệ, deploy sạch, nhưng mọi lời gọi bị từ chối IM LẶNG

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TF_DIR="${REPO_ROOT}/terraform"

# Phải khớp SECRET_STORE_NAME trong bootstrap.sh — đây là hợp đồng với mọi ExternalSecret Day 6.
SECRET_STORE_NAME="aws-ssm"

PARAM_NAME="/badminton/staging/SMOKE_TEST"
NAMESPACE="default"
ES_NAME="smoke-irsa"
SECRET_NAME="smoke-irsa-secret"

log() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok() { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m  ! %s\033[0m\n' "$*"; }
die() {
  printf '\n\033[1;31m✘ %s\033[0m\n' "$*" >&2
  exit 1
}

for bin in aws kubectl terraform; do
  command -v "$bin" >/dev/null 2>&1 || die "thiếu '$bin' trên PATH"
done

REGION="$(terraform -chdir="$TF_DIR" output -raw region 2>/dev/null)" ||
  die "không đọc được output 'region'. Đã chạy 'cd terraform && terraform apply' chưa?"

# Dọn dẹp chạy trong MỌI đường ra (thành công, lỗi, Ctrl-C) — không để lại rác trên AWS/cụm.
cleanup() {
  log "Dọn dẹp"
  kubectl delete externalsecret "$ES_NAME" -n "$NAMESPACE" --ignore-not-found >/dev/null 2>&1 || true
  kubectl delete secret "$SECRET_NAME" -n "$NAMESPACE" --ignore-not-found >/dev/null 2>&1 || true
  aws ssm delete-parameter --name "$PARAM_NAME" --region "$REGION" >/dev/null 2>&1 || true
  ok "đã xoá ExternalSecret · Secret · SSM parameter"
}
trap cleanup EXIT

# Giá trị ngẫu nhiên: nếu một Secret cũ còn sót thì so sánh sẽ FAIL thay vì đậu giả.
EXPECTED="hello-irsa-$(date +%s)-$RANDOM"

# ─────────────────────────────────────────────────────────────────────────────
log "1/4 · Tạo SecureString trên SSM Parameter Store"
aws ssm put-parameter --name "$PARAM_NAME" --type SecureString \
  --value "$EXPECTED" --overwrite --region "$REGION" >/dev/null
ok "$PARAM_NAME (SecureString, mã hoá bằng alias/aws/ssm)"

# ─────────────────────────────────────────────────────────────────────────────
log "2/4 · Xác định apiVersion ESO đang phục vụ"
# ESO đổi apiVersion giữa các đời chart (v1beta1 → v1) — hỏi thẳng CRD thay vì đoán.
if kubectl get crd externalsecrets.external-secrets.io \
  -o jsonpath='{range .spec.versions[*]}{.name}{"\n"}{end}' 2>/dev/null | grep -qx 'v1'; then
  ES_API="external-secrets.io/v1"
else
  ES_API="external-secrets.io/v1beta1"
fi
ok "dùng $ES_API"

# ─────────────────────────────────────────────────────────────────────────────
log "3/4 · Apply ExternalSecret trỏ vào param đó"
kubectl apply -f - <<EOF
apiVersion: ${ES_API}
kind: ExternalSecret
metadata:
  name: ${ES_NAME}
  namespace: ${NAMESPACE}
spec:
  refreshInterval: 1m
  secretStoreRef:
    name: ${SECRET_STORE_NAME}
    kind: ClusterSecretStore
  target:
    name: ${SECRET_NAME}
    creationPolicy: Owner
  data:
    - secretKey: value
      remoteRef:
        key: ${PARAM_NAME}
EOF

if ! kubectl wait --for=condition=Ready --timeout=90s \
  "externalsecret/${ES_NAME}" -n "$NAMESPACE" >/dev/null 2>&1; then
  warn "ExternalSecret không Ready sau 90s — đây CHÍNH LÀ lỗi cần bắt hôm nay:"
  kubectl describe "externalsecret/${ES_NAME}" -n "$NAMESPACE" | tail -25
  echo
  die "IRSA/policy chưa thông. Xem reason ở trên:
  · AccessDeniedException ... ssm:GetParameter   → policy thiếu quyền đọc /badminton/*
  · AccessDeniedException ... kms:Decrypt        → thiếu kms:Decrypt (điều kiện kms:ViaService)
  · AccessDeniedException ... DescribeParameters → action này PHẢI để Resource \"*\"
  · WebIdentityErr / no identity-based policy    → sai trust policy IRSA (sub của ServiceAccount)"
fi
ok "ExternalSecret Ready"

# ─────────────────────────────────────────────────────────────────────────────
log "4/4 · Đối chiếu giá trị thật trong Kubernetes Secret"
ACTUAL="$(kubectl get secret "$SECRET_NAME" -n "$NAMESPACE" \
  -o jsonpath='{.data.value}' | base64 --decode)"

if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  die "giá trị KHÔNG khớp — mong '$EXPECTED', nhận '$ACTUAL'"
fi

printf '\n\033[1;32m✔ PASS — ESO kéo được SecureString thật từ SSM về Kubernetes Secret.\033[0m\n'
printf '  Chứng minh đủ 3 mắt xích: IRSA assume-role · ssm:GetParameter · kms:Decrypt.\n'
printf '  ⇒ Day 6 (ExternalSecret cho 9 service) sẽ không kẹt vì policy.\n'
