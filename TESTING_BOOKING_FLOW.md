# TESTING_BOOKING_FLOW.md — Test tay luồng Đặt sân ↔ Thanh toán qua Frontend

Runbook **bấm tay trên FE** để kiểm tra trọn vẹn luồng booking↔payment: 1 happy path (đơn CONFIRMED) +
5 nhánh ngoại lệ. Mỗi bước ghi rõ **bấm gì → mong đợi gì → kiểm chứng ở đâu** (trạng thái FE + màu lưới ô +
Kafka UI/DB khi cần).

> Spec luồng đầy đủ: `UC_Visual_Day_Booking.md`. Cổng/port: `architecture.md`. Đây là tài liệu **QA tay**,
> không phải test tự động.

**Quy ước màu lưới ô** (nguồn sự thật cho hold/release):
`AVAILABLE` = trắng (bấm được) · `RESERVED` = đỏ (đã giữ) · `BLOCKED` = xám · `EVENT` = tím.

---

## A. Chuẩn bị môi trường (1 lần)

### A1. Hạ tầng
```bash
cd /Users/phucnguyen/ClaudeCodeProjects/badmintonHub
docker compose up -d        # PG×9, Redis, Kafka, Zookeeper, Kafka UI :8080, Zipkin, Mongo, ES
```
- Mở **Kafka UI** `http://localhost:8080` → thấy broker UP. (Dùng để soi event ở Mục E.)

### A2. `.env` — RÚT NGẮN cửa sổ để test nhanh
File `.env` ở **gốc repo** (gitignored — copy từ `.env.example` nếu chưa có). Đặt:
```dotenv
JWT_SECRET=<chạy: openssl rand -hex 64>
BOOKING_HOLD_MINUTES=1        # rút 10 → 1 để nhánh timeout nổ sau ~2'
PAYMENT_EXPIRE_MINUTES=1      # PHẢI bằng BOOKING_HOLD_MINUTES
# Để TRỐNG (dev fallback tự động):
SENDGRID_API_KEY=            # trống → link verify in ra console user-service
CLOUDINARY_CLOUD_NAME=       # trống → proof lưu URL local-fallback:// (không cần tài khoản Cloudinary)
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
```
> ⚠️ `BOOKING_HOLD_MINUTES` **phải bằng** `PAYMENT_EXPIRE_MINUTES` (bất biến của hệ). Đổi window thì
> **restart lại booking-service + payment-service**. Nhớ **trả về 10** sau khi test xong (Mục E).
> DB/Redis/Kafka vars đã có `:default` khớp docker-compose nên không cần điền.

### A3. Chạy service (mỗi cái 1 terminal, **từ gốc repo**, đúng thứ tự)
```bash
mvn -pl eureka-server  spring-boot:run     # 8761  ← chạy trước
mvn -pl api-gateway    spring-boot:run     # 3000
mvn -pl user-service   spring-boot:run     # 3001
mvn -pl court-service  spring-boot:run     # 3002  ← DataSeeder tạo CLB "An Bình" + 5 sân + slot từ HÔM NAY
mvn -pl booking-service spring-boot:run    # 3003
mvn -pl payment-service spring-boot:run    # 3006
```
- Chờ cả 6 hiện **UP** trên Eureka dashboard `http://localhost:8761`.
- court-service in log seed xong → lưới `/courts` sẽ có dữ liệu thật.

### A4. Frontend
```bash
cd frontend && npm install && npm run dev   # http://localhost:5173 (VITE_API_URL đã trỏ :3000)
```

---

## B. Chuẩn bị tài khoản

### B1. Người đặt (USER, đã verify email) — **bắt buộc** để `POST /api/bookings`
1. FE `/register` → đăng ký (vd `player@test.local`).
2. Mở **terminal user-service**, tìm dòng:
   `[DEV] Email verify link for player@test.local: http://localhost:5173/verify-email?token=<UUID>`
3. Mở link đó (hoặc vào `/verify-email?token=<UUID>`) → "Email đã được xác thực".
4. **Đăng nhập lại** ở `/login` → JWT mới mang claim `email_verified=true`.
   *(Quan trọng: phải login SAU khi verify; token cũ vẫn `email_verified=false` → đặt sân bị 403.)*

### B2. Nhân viên (STAFF) — để duyệt/từ chối/hoàn tiền
1. Đăng ký tài khoản thứ 2 qua FE `/register` (vd `staff@test.local`). *(Script chỉ **thêm role**, không tạo
   tài khoản → phải đăng ký trước.)*
2. **Sửa email** trong `scripts/promote-staff.sql` (mặc định `staff@test.local`) rồi chạy **1 trong 3 cách** —
   file là **SQL thuần** nên chạy được ở cả 3:

   | Cách | Lệnh |
   |---|---|
   | **DataGrip** *(khuyên dùng nếu bạn đang mở DataGrip)* | Mở console nối **`user_db@localhost`** → mở file `scripts/promote-staff.sql` (hoặc dán nội dung) → **chọn hết → Run** ▶ |
   | **psql CLI** (từ gốc repo) | `psql "postgresql://postgres:postgres@localhost:5441/user_db" -f scripts/promote-staff.sql` |
   | **docker exec** (máy chưa cài psql) | `docker exec -i postgres-user psql -U postgres -d user_db < scripts/promote-staff.sql` |

   > ⚠️ Phải chạy trên DB **`user_db`** (chứa bảng `users`/`roles`), KHÔNG phải `court_db`. Query thứ 3 phải
   > in ra `roles = {STAFF,USER}`.
   > psql backslash (`\if`/`\set`) **không** chạy trong DataGrip — vì vậy file dùng SQL thuần, sửa email trực tiếp.
3. **Đăng xuất → đăng nhập lại** trên FE → vào được `/admin`.

> Mẹo: dùng **2 trình duyệt** (hoặc 1 thường + 1 ẩn danh) — 1 đăng nhập USER, 1 đăng nhập STAFF — để
> không phải login qua login lại giữa các bước.

---

## C. Happy path → đơn CONFIRMED (bấm hết trên FE)

| # | Bấm (USER) | Mong đợi | Kiểm chứng |
|---|---|---|---|
| 1 | `/courts` → chọn 1 môn (Pickleball/Badminton) | Vào lưới `/courts/<clubId>/booking?sport=…` | Lưới = hàng Sân × cột ô 30' (05:00–22:00) |
| 2 | Chọn vài ô **trắng** | Bottom bar cộng **Tổng giờ / Tổng tiền** | Giá ô = `price_per_hour ÷ 2` |
| 3 | **TIẾP THEO** → `/booking/confirm` → điền tên/SĐT → **XÁC NHẬN ĐẶT** | 201, chuyển sang màn thanh toán | `POST /api/bookings` trả `bookingId`, `holdExpiresAt = now+1'` |
| 4 | (tự động) | Ô vừa đặt thành **đỏ/RESERVED** | Mở lại lưới (tab khác) thấy ô đỏ · Kafka UI: `booking.slot.changed` (HELD) |
| 5 | Màn **Bank QR** mở | Hiện số TK + QR + mã `#orderCode` + đếm ngược | `POST /api/payments/initiate` → `amount` = `totalPrice` (server tự suy) |
| 6 | Upload 1 ảnh bất kỳ làm proof → **Đã chuyển khoản — Nộp chứng từ** | Trạng thái → **PROOF_SUBMITTED**, đồng hồ **dừng**, hiện "chờ STAFF duyệt" | `POST /{id}/proof` · Kafka UI: `payment.proof.submitted` · booking `hold_expires_at=null` |
| 7 | **(STAFF)** `/admin` → tab **Duyệt thanh toán** → **Xem** | `ProofDetailModal` hiện ảnh biên lai + tóm tắt đơn | `GET /api/payments/pending-review` + `GET /{id}/proofs` |
| 8 | **(STAFF)** bấm **Xác nhận** | payment **CONFIRMED** | Kafka UI: `payment.player.confirmed` |
| 9 | (USER) màn thanh toán tự poll | ✅ thành công; ô vẫn **đỏ** (giữ chỗ thật) | `/my-bookings` → đơn **CONFIRMED** |

✅ **Happy path xong** = đơn CONFIRMED + payment CONFIRMED + ô RESERVED vĩnh viễn.

---

## D. 5 nhánh ngoại lệ

> Mỗi nhánh nên đặt **đơn mới** (vài ô khác) để trạng thái sạch.

### Nhánh #1 — Không trả tiền / bỏ mặc (auto-huỷ do timeout)
1. Đặt đơn (bước C1–C3). **KHÔNG** mở thanh toán, hoặc mở rồi để yên.
2. Chờ **≤ ~2'** (1' hold + tối đa 60s `HoldExpiryScheduler`).
3. **Mong đợi**: booking **CANCELLED** (`cancelReason=PAYMENT_TIMEOUT`) · payment EXPIRED hoặc không có ·
   ô trở lại **trắng/AVAILABLE**.
4. **Kiểm chứng**: `/my-bookings` đơn "Đã huỷ" · lưới ô xanh lại · Kafka UI `booking.slot.changed` (RELEASED).

### Nhánh #2 — STAFF từ chối proof
1. Làm tới bước C6 (đã PROOF_SUBMITTED).
2. **(STAFF)** `/admin` → **Duyệt thanh toán** → **Xem** → **Từ chối** (nhập lý do ở prompt).
3. **Mong đợi**: payment **EXPIRED** + `rejectReason` · `payment.player.expired` → booking **CANCELLED** · ô nhả AVAILABLE.
4. **Kiểm chứng**: Kafka UI `payment.player.expired` · `/my-bookings` đơn đã huỷ · ô xanh lại.

### Nhánh #3 — Huỷ đơn ĐÃ TRẢ (CONFIRMED → hoàn tiền theo bậc)
> Để có hoàn tiền > 0, **đặt ô cách hiện tại ≥ 24h** (bậc 100%). Bậc: >24h=100% · 2–24h=50% · <2h=0%
> (mốc theo `earliestStartTime`).
1. Chạy trọn happy path (đơn CONFIRMED) với ô ≥24h tới.
2. (USER) `/my-bookings` → **Huỷ** đơn đó (confirm).
3. **Mong đợi**: booking CANCELLED + `refundAmount` theo bậc · phát `booking.refund.required` (kèm amount) →
   payment gắn `refundRequired=true`.
4. **(STAFF)** `/admin` → tab **Hoàn tiền** → thấy payment trong hàng chờ → **Hoàn tiền** → điền `RefundModal`
   (bank/STK/tên/amount/note) → xác nhận → payment **REFUNDED**.
5. **Kiểm chứng**: Kafka UI `booking.refund.required` rồi `payment.refund.processed` · payment REFUNDED.

### Nhánh #4 — Lỡ chuyển khoản khi đơn ĐÃ huỷ (paid-late salvage)
1. Tạo đơn rồi để **timeout** như nhánh #1 (đơn đã CANCELLED, ô đã nhả) — nhưng **giữ nguyên màn thanh toán đang mở**.
2. Trên màn đó, **upload proof** (giả lập user chuyển khoản trễ).
3. **Mong đợi**: proof **vẫn được lưu** (không vứt) · payment gắn `refundRequired=true`
   (reason `PAYMENT_EXPIRED_PAID_LATE`) · **KHÔNG** tự confirm · không hồi sinh booking.
4. **Kiểm chứng**: `/admin` tab **Hoàn tiền** thấy payment này → STAFF đối soát rồi hoàn/loại.

> Biến thể (#4b — proof sau khi đơn bị huỷ tay): đặt đơn → mở thanh toán → sang `/my-bookings` **Huỷ** ngay →
> quay lại upload proof. Kết cục tương tự, reason `BOOKING_CANCELLED`.

### Nhánh #5 — STAFF confirm trúng đơn đã huỷ (orphaned)
> PROOF_SUBMITTED **không** tự hết hạn → đây không phải đua gắt, cứ bấm tuần tự.
1. Làm tới bước C6 (PROOF_SUBMITTED), **chưa** cho STAFF duyệt.
2. (USER) `/my-bookings` → **Huỷ** đơn (lúc này đơn vẫn PENDING nên huỷ được) → booking CANCELLED, ô nhả.
3. **(STAFF)** giờ mới vào **Duyệt thanh toán** → **Xác nhận** payment đó.
4. **Mong đợi**: `payment.player.confirmed` rơi vào booking đã CANCELLED → booking phát
   `booking.payment.orphaned` → payment **CONFIRMED + refundRequired=true** → vào hàng **Hoàn tiền**.
5. **Kiểm chứng**: Kafka UI `booking.payment.orphaned` · `/admin` tab Hoàn tiền có payment này.

---

## E. Bộ công cụ kiểm chứng & dọn dẹp

### Kiểm chứng
- **Màu lưới ô** = nguồn sự thật hold/release (xanh AVAILABLE ↔ đỏ RESERVED).
- **Kafka UI** `:8080` — soi topic: `booking.slot.changed`, `payment.proof.submitted`,
  `payment.player.confirmed`, `payment.player.expired`, `booking.refund.required`,
  `booking.payment.orphaned`. **Bất kỳ message nào ở `*.DLT` = 1 bug cần chụp lại** (mất tiền/không hoàn im lặng).
- **DB peek** (tuỳ chọn) — SQL thuần, chạy trong **DataGrip** (console `booking_db` / `payment_db`) hoặc psql:
  ```sql
  -- console booking_db@localhost
  SELECT id, status, cancel_reason, refund_amount, hold_expires_at
  FROM bookings ORDER BY created_at DESC LIMIT 5;
  -- console payment_db@localhost
  SELECT order_code, status, amount, refund_required, refund_required_amount
  FROM payments ORDER BY created_at DESC LIMIT 5;
  ```

### Bảng tra nhanh kết cục
| Nhánh | booking | payment | ô lưới |
|---|---|---|---|
| C happy | CONFIRMED | CONFIRMED | RESERVED (giữ) |
| #1 timeout | CANCELLED (PAYMENT_TIMEOUT) | EXPIRED/none | AVAILABLE |
| #2 reject | CANCELLED | EXPIRED (+rejectReason) | AVAILABLE |
| #3 cancel-paid | CANCELLED (+refundAmount) | CONFIRMED→REFUNDED | AVAILABLE |
| #4 paid-late | (đã CANCELLED) | PROOF/EXPIRED + refundRequired | AVAILABLE |
| #5 orphaned | CANCELLED | CONFIRMED + refundRequired | AVAILABLE |

### Dọn dẹp
- **Trả `.env` window về 10**: `BOOKING_HOLD_MINUTES=10`, `PAYMENT_EXPIRE_MINUTES=10` (rồi restart booking+payment nếu còn chạy).
- Tắt 6 service: `pkill -f spring-boot:run` (hoặc Ctrl-C từng terminal). **Giữ** docker infra (`docker compose`) theo quy ước dự án.

---

## Phụ lục — Fallback bằng `curl` (khi không tiện bấm FE)

Tất cả qua gateway `:3000`. Lấy token: `POST /api/auth/login` → field `accessToken` → đặt `-H "Authorization: Bearer <token>"`.

```bash
# Huỷ đơn (nhánh #3/#5) — owner hoặc STAFF/ADMIN
curl -X POST http://localhost:3000/api/bookings/<bookingId>/cancel \
  -H "Authorization: Bearer <USER_TOKEN>" -H "Content-Type: application/json" \
  -d '{"reason":"test cancel"}'

# STAFF từ chối (nhánh #2)
curl -X POST http://localhost:3000/api/payments/<paymentId>/reject \
  -H "Authorization: Bearer <STAFF_TOKEN>" -H "Content-Type: application/json" \
  -d '{"reason":"proof giả"}'

# STAFF confirm (happy / nhánh #5)
curl -X POST http://localhost:3000/api/payments/<paymentId>/confirm \
  -H "Authorization: Bearer <STAFF_TOKEN>"

# STAFF hoàn tiền (nhánh #3) — amount ≤ payments.amount
curl -X POST http://localhost:3000/api/payments/<paymentId>/refund \
  -H "Authorization: Bearer <STAFF_TOKEN>" -H "Content-Type: application/json" \
  -d '{"amount":120000,"toBankName":"Vietcombank","toAccountNumber":"123","toAccountName":"NGUYEN VAN A","refundNote":"test"}'

# Hàng chờ STAFF
curl -H "Authorization: Bearer <STAFF_TOKEN>" http://localhost:3000/api/payments/pending-review
curl -H "Authorization: Bearer <STAFF_TOKEN>" http://localhost:3000/api/payments/refund-required
```
