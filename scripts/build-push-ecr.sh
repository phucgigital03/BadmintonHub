#!/usr/bin/env bash
#
# Day 4 · Bước 4 — build 9 image rồi đẩy lên ECR, tag = git SHA.
#
#   ./scripts/build-push-ecr.sh                    # cả 9 image
#   ./scripts/build-push-ecr.sh chat-service       # chỉ 1 (rebuild sau khi sửa)
#   ./scripts/build-push-ecr.sh chat-service frontend
#
# 🔴 CHẠY KHI CỤM EKS ĐANG TẮT. Bước này mất 20–40' và tốn $0 (build trên máy bạn,
#    đẩy dữ liệu VÀO AWS miễn phí). Không có lý do gì để `terraform apply` rồi ngồi
#    chờ build trong lúc đồng hồ ~$0.27/giờ đang chạy.
#    Thứ tự đúng:  build+push  →  terraform apply  →  deploy  →  nghiệm thu  →  destroy.
#
# 🔴 TAG = GIT SHA, nên phải COMMIT TRƯỚC KHI BUILD. Build trước commit sau ⇒ SHA đổi ⇒
#    image :SHA-cũ mang code cũ, còn values ở repo gitops trỏ :SHA-mới chưa hề tồn tại
#    ⇒ pod đứng ImagePullBackOff, triệu chứng trông chẳng liên quan gì tới thứ tự lệnh.
#    Script tự chặn nếu working tree bẩn (bỏ qua bằng ALLOW_DIRTY=1, chỉ để thử nghiệm).
#
# 🔴 KIẾN TRÚC: máy dev Apple Silicon ra arm64, node EKS là amd64. Build sai kiến trúc thì
#    image push lên bình thường, nhưng trên cụm pod crash ngay với "exec format error".
#    Vì vậy luôn --platform linux/amd64 (qua giả lập QEMU ⇒ chậm hơn native ~3–5 lần).
#
# HAI LOẠI IMAGE, HAI BUILD CONTEXT KHÁC NHAU — đây là chỗ sai phổ biến nhất:
#   • 8 service Java  → DÙNG CHUNG ./Dockerfile, chọn module bằng --build-arg SERVICE.
#                       Context PHẢI là repo root: Dockerfile có `COPY . .` và pom gốc là
#                       aggregator liệt kê 15 module — copy lẻ là Maven fail ngay.
#   • frontend        → ./frontend/Dockerfile, context PHẢI là ./frontend
#                       (`COPY package.json package-lock.json ./` chạy từ root sẽ không thấy).
#   KHÔNG tồn tại chat-service/Dockerfile, user-service/Dockerfile, ... — chỉ có ĐÚNG 2 file.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

REGION="${AWS_REGION:-ap-southeast-1}"
PLATFORM="${PLATFORM:-linux/amd64}"
BUILDER_NAME="${BUILDER_NAME:-badminton}"

# service:port — port CHỈ để đặt EXPOSE trong image (tài liệu hoá), cổng chạy thật đến từ
# SERVER_PORT/application.yml. Nguồn: .claude/rules/eureka-config.md.
# chat-service đứng đầu CÓ CHỦ ĐÍCH: nó là service hay bị sửa nhất trước lúc deploy, nên
# lỗi biên dịch (nếu có) lộ ở phút thứ 5 thay vì phút thứ 35.
JAVA_SERVICES="
chat-service:3011
eureka-server:8761
api-gateway:3000
user-service:3001
court-service:3002
booking-service:3003
payment-service:3006
escrow-service:3007
"
FE_SERVICE="frontend"

# macOS mặc định là bash 3.2 → KHÔNG có mảng kết hợp (declare -A). Dùng chuỗi "svc:port".

C_INFO=$'\033[1;36m'; C_OK=$'\033[1;32m'; C_WARN=$'\033[1;33m'; C_ERR=$'\033[1;31m'; C_OFF=$'\033[0m'
log()  { printf '\n%s▶ %s%s\n' "$C_INFO" "$*" "$C_OFF"; }
ok()   { printf '%s  ✔ %s%s\n' "$C_OK" "$*" "$C_OFF"; }
warn() { printf '%s  ! %s%s\n' "$C_WARN" "$*" "$C_OFF"; }
die()  { printf '\n%s✖ %s%s\n\n' "$C_ERR" "$*" "$C_OFF" >&2; exit 1; }

fmt_dur() { printf '%02d:%02d' $(( $1 / 60 )) $(( $1 % 60 )); }

# ────────────────────────────────────────────── 0. Điều kiện tiên quyết
log "Kiểm tra điều kiện"

command -v docker >/dev/null || die "Không thấy docker. Mở Docker Desktop rồi chạy lại."

# `docker info` THOÁT 0 kể cả khi Docker Desktop mới bật và engine còn đang khởi động —
# lúc đó nó trả về một Info rỗng. Phải kiểm một trường CÓ THẬT thì mới biết engine sẵn sàng.
# (Bỏ qua bước này thì build chạy được vài giây rồi chết với lỗi trông chẳng liên quan.)
DOCKER_READY=""
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
  DOCKER_READY="$(docker info --format '{{.ServerVersion}}' 2>/dev/null || true)"
  [ -n "$DOCKER_READY" ] && break
  printf '  … chờ Docker engine khởi động xong\n'
  sleep 5
done
[ -n "$DOCKER_READY" ] || die "Docker engine chưa sẵn sàng sau 60 giây.
     Mở Docker Desktop, đợi biểu tượng cá voi hết nhấp nháy, rồi chạy lại.
     Kiểm bằng tay:  docker info --format '{{.ServerVersion}}'   (phải in ra số version)"
command -v aws >/dev/null || die "Không thấy aws CLI."
command -v git >/dev/null || die "Không thấy git."

if [ -n "$(git status --porcelain)" ]; then
  if [ "${ALLOW_DIRTY:-0}" = "1" ]; then
    warn "Working tree BẨN nhưng ALLOW_DIRTY=1 — tag SHA sẽ KHÔNG mô tả đúng code đang build."
  else
    git status --short
    die "Working tree bẩn. Commit trước đã — tag image = git SHA, build bây giờ thì image sẽ mang nhãn sai.
     (Thật sự muốn build thử: ALLOW_DIRTY=1 $0)"
  fi
fi

SHA="$(git rev-parse --short HEAD)"

ACCOUNT="$(aws sts get-caller-identity --query Account --output text 2>/dev/null)" \
  || die "Không lấy được AWS account. Chạy 'aws configure' hoặc kiểm tra access key."
ECR="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"

ok "account=${ACCOUNT}  region=${REGION}  tag=${SHA}  platform=${PLATFORM}"

# ────────────────────────────────────────────── 1. Builder buildx
# Driver "docker" (mặc định của Docker Desktop) KHÔNG xuất được thẳng lên registry:
#   ERROR: push is not supported by the "docker" driver
# Driver "docker-container" thì được, và cũng là driver hỗ trợ cross-platform ổn định.
log "Chuẩn bị buildx builder"

CUR_DRIVER="$(docker buildx inspect 2>/dev/null | sed -n 's/^[[:space:]]*Driver:[[:space:]]*//p' | head -1 || true)"
if [ "$CUR_DRIVER" != "docker-container" ]; then
  if docker buildx inspect "$BUILDER_NAME" >/dev/null 2>&1; then
    docker buildx use "$BUILDER_NAME"
  else
    warn "Builder hiện tại driver='${CUR_DRIVER:-?}' không push thẳng registry được → tạo '${BUILDER_NAME}'."
    docker buildx create --name "$BUILDER_NAME" --driver docker-container --use >/dev/null
  fi
fi
docker buildx inspect --bootstrap >/dev/null

BUILDER_PLATFORMS="$(docker buildx inspect | sed -n 's/^[[:space:]]*Platforms:[[:space:]]*//p' | head -1 || true)"
BUILDER_ACTIVE="$(docker buildx inspect | sed -n 's/^[[:space:]]*Name:[[:space:]]*//p' | head -1 || true)"
case "$BUILDER_PLATFORMS" in
  *"$PLATFORM"*) ok "builder=${BUILDER_ACTIVE:-?}  hỗ trợ ${PLATFORM}" ;;
  *) die "Builder không hỗ trợ ${PLATFORM} (chỉ có: ${BUILDER_PLATFORMS:-?}).
     Cài lớp giả lập rồi chạy lại:
       docker run --privileged --rm tonistiigi/binfmt --install amd64" ;;
esac

# ────────────────────────────────────────────── 2. Đăng nhập ECR
log "Đăng nhập ECR"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR" >/dev/null 2>&1 \
  || die "Đăng nhập ECR thất bại. Kiểm tra region và quyền của IAM user."
ok "đã đăng nhập ${ECR}  (phiên có hiệu lực 12 giờ)"

# ────────────────────────────────────────────── 3. Chọn danh sách cần build
port_of() { printf '%s' "$JAVA_SERVICES" | sed -n "s|^${1}:||p" | head -1; }

TARGETS=""
if [ "$#" -gt 0 ]; then
  for want in "$@"; do
    if [ "$want" = "$FE_SERVICE" ] || [ -n "$(port_of "$want")" ]; then
      TARGETS="${TARGETS}${want}"$'\n'
    else
      die "Không biết service '${want}'. Hợp lệ: $(printf '%s' "$JAVA_SERVICES" | sed 's/:.*//' | tr '\n' ' ')${FE_SERVICE}"
    fi
  done
else
  TARGETS="$(printf '%s' "$JAVA_SERVICES" | sed 's/:.*//' | sed '/^$/d')"$'\n'"${FE_SERVICE}"
fi
# printf '%s\n' (không phải '%s'): danh sách kết thúc bằng "frontend" không có ký tự xuống
# dòng, mà `wc -l` đếm ký tự xuống dòng ⇒ thiếu '\n' là báo 8/9 và không ai nhận ra.
TOTAL="$(printf '%s\n' "$TARGETS" | sed '/^$/d' | wc -l | tr -d ' ')"

# ────────────────────────────────────────────── 4. Build + push
log "Build ${TOTAL} image → ${ECR}/<service>:${SHA}"
printf '  (lần đầu ~20–40 phút: %s trên máy arm64 chạy qua giả lập QEMU)\n' "$PLATFORM"

RUN_START=$SECONDS
IDX=0
BUILT=""

for svc in $(printf '%s' "$TARGETS" | sed '/^$/d'); do
  IDX=$(( IDX + 1 ))
  IMG="${ECR}/${svc}:${SHA}"
  printf '\n%s──── [%d/%d] %s%s\n' "$C_INFO" "$IDX" "$TOTAL" "$svc" "$C_OFF"
  T0=$SECONDS

  if [ "$svc" = "$FE_SERVICE" ]; then
    # Không build-arg nào: FE lấy origin API/WS từ window.location lúc chạy (same-origin,
    # nginx proxy) ⇒ MỘT image dùng được ở mọi môi trường, kể cả khi ALB DNS đổi.
    docker buildx build \
      --platform "$PLATFORM" \
      -f frontend/Dockerfile \
      -t "$IMG" \
      --push \
      ./frontend \
      || die "Build '${svc}' thất bại. Sửa rồi chạy lại chỉ service này:
       ./scripts/build-push-ecr.sh ${svc}"
  else
    PORT="$(port_of "$svc")"
    docker buildx build \
      --platform "$PLATFORM" \
      -f Dockerfile \
      --build-arg "SERVICE=${svc}" \
      --build-arg "PORT=${PORT}" \
      -t "$IMG" \
      --push \
      . \
      || die "Build '${svc}' thất bại. Sửa rồi chạy lại chỉ service này:
       ./scripts/build-push-ecr.sh ${svc}"
  fi

  ok "${svc} ($(fmt_dur $(( SECONDS - T0 ))))"
  BUILT="${BUILT}${svc}"$'\n'
done

# ────────────────────────────────────────────── 5. Nghiệm thu
# Push thành công KHÔNG chứng minh kiến trúc đúng — image arm64 vẫn push sạch sẽ rồi mới
# chết trên cụm. Phải hỏi lại registry xem nó thật sự nhận được cái gì.
log "Nghiệm thu — hỏi lại ECR về kiến trúc thật của từng image"

FAIL=0
for svc in $(printf '%s' "$BUILT" | sed '/^$/d'); do
  IMG="${ECR}/${svc}:${SHA}"
  ARCH="$(docker buildx imagetools inspect "$IMG" 2>/dev/null \
          | sed -n 's/^[[:space:]]*Platform:[[:space:]]*//p' | head -1 || true)"
  if [ -z "$ARCH" ]; then
    ARCH="$(docker buildx imagetools inspect "$IMG" \
            --format '{{.Image.Platform.OS}}/{{.Image.Platform.Architecture}}' 2>/dev/null || true)"
  fi
  if [ "$ARCH" = "$PLATFORM" ]; then
    printf '%s  ✔ %-18s %s%s\n' "$C_OK" "$svc" "$ARCH" "$C_OFF"
  else
    printf '%s  ✖ %-18s %s  (mong đợi %s)%s\n' "$C_ERR" "$svc" "${ARCH:-không đọc được}" "$PLATFORM" "$C_OFF"
    FAIL=$(( FAIL + 1 ))
  fi
done

[ "$FAIL" -eq 0 ] || die "${FAIL} image sai kiến trúc — ĐỪNG deploy. Build lại đúng --platform ${PLATFORM}."

# ────────────────────────────────────────────── 6. Tóm tắt cho repo gitops
log "XONG — ${TOTAL}/${TOTAL} image · ${PLATFORM} · tag ${SHA} · $(fmt_dur $(( SECONDS - RUN_START )))"
cat <<EOF

  Hai chuỗi mang sang repo badmintonHub-gitops (values/<svc>-staging.yaml):

      image.repository: ${ECR}/<service>
      image.tag:        ${SHA}

  Kiểm tra lại bất cứ lúc nào, không cần cụm:
      aws ecr describe-images --repository-name chat-service --image-ids imageTag=${SHA} \\
        --region ${REGION} --query 'imageDetails[0].imagePushedAt' --output text

EOF
