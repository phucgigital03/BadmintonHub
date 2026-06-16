# 📋 Use Case: Đặt Lịch Ngày Trực Quan (Visual Day Booking)

> **Luồng booking ĐẦY ĐỦ — đã chạy thật**: chọn ô → giữ chỗ (Saga Kafka/Outbox) → Bank QR → STAFF confirm → đơn `CONFIRMED`.
> Mô hình dữ liệu: `clubs` ──< `courts` (Sân) ──< `time_slots` (ô **30'**); giá từ `court_pricing_rules`;
> đơn = **header `bookings` + N `booking_items`** (mỗi item = 1 ô 30', giữ chỗ bằng `UNIQUE(slot_id)`).

| Field | Detail |
|---|---|
| **Use Case ID** | UC-BOOKING-01 |
| **Module** | Booking + Payment |
| **Actor chính** | User đã đăng nhập (email đã xác thực) · STAFF (duyệt thanh toán) |
| **Trigger** | Chọn "Đặt lịch ngày trực quan" trên trang CLB |

---

## 1. Tiền đề (Preconditions)
- User **đăng nhập**, JWT mang `email_verified=true` (gác rule #10 ngay từ token — không cần gọi user-service).
- Đang ở trang **1 CLB** (`clubId`); court-service đã sinh `time_slots` 30' cho ngày chọn.
- Có ≥ 1 ô `AVAILABLE`; có ≥ 1 `bank_account` active để hiển thị QR.

---

## 2. Kịch bản người dùng

### 2.1 Luồng chính (happy path → đơn `CONFIRMED`)

1. **Xem lưới** — User mở trang CLB. FE gọi `GET /api/clubs/{clubId}/slots?date=&sport=` → lưới **hàng = Sân, cột = ô 30'** (05:00–22:00), mỗi ô kèm `status` + `price` (= `price_per_hour ÷ 2`). User chọn vài ô trống; bottom bar cộng **Tổng giờ / Tổng tiền** trực tiếp.
2. **Đặt** — Bấm **XÁC NHẬN ĐẶT** → `POST /api/bookings {clubId, date, customerName, customerPhone, note, items:[{courtId,slotId}]}`. booking-service: **rate-limit** theo user (10 lượt/phút, fail-open) → **NGOÀI transaction/lock**: Feign verify ô còn `AVAILABLE` + chốt giá/tên sân/giờ (**snapshot**) + chặn ô đã qua giờ → khoá Redis tất cả ô (TTL 5s, all-or-nothing, **sau** Feign — không giữ lock qua mạng) → **transaction ngắn**: INSERT `bookings(PENDING)` + N `booking_items` (`UNIQUE slot_id`) + đặt `hold_expires_at = now+10'` + ghi `outbox(booking.slot.changed·HELD)` (1 message/ô, key=slotId). Trả `201 {bookingId, totalPrice, holdExpiresAt}`. *(Feign + validate chạy ngoài tx để court-service chậm không giữ kết nối DB của booking → tránh cạn pool.)*
3. **Lưới đỏ** — Outbox (3s) phát `booking.slot.changed (HELD)` → court-service flip ô `AVAILABLE→RESERVED` (idempotent). Người khác thấy ô đỏ ở lần fetch kế tiếp.
4. **Mở Bank QR** — FE gọi `POST /api/payments/initiate {bookingId, BOOKING, amount}`. payment-service handshake `POST /api/bookings/{id}/begin-payment` (Feign, forward token): booking gác cổng — đúng chủ + còn `PENDING` → gia hạn `hold=+10'` + trả `totalPrice` làm **amount chuẩn** → tạo `payment(PENDING)` + `order_code (#184)` + `expires_at=now+10'`. Trả **bank info + QR + countdown**.
5. **Chuyển khoản + nộp proof** — User chuyển khoản (nội dung `#184`) rồi `POST /api/payments/{id}/proof` (ảnh). payment-service **LUÔN lưu proof** (Cloudinary + `payment_proofs`) → `PROOF_SUBMITTED`; booking nghe `payment.proof.submitted` → `hold_expires_at=null` (dừng đồng hồ tự huỷ). FE hiện "chờ STAFF duyệt".
6. **STAFF duyệt** — STAFF mở `GET /api/payments/pending-review` (hàng chờ FIFO) → `POST /api/payments/{id}/confirm` → `payment CONFIRMED` + phát `payment.player.confirmed` → booking `PENDING→CONFIRMED` (ô giữ `RESERVED`). ✅ **Đặt sân thành công.**

### 2.2 Các nhánh kết thúc khác

| # | Nhánh | Diễn biến | Kết cục |
|---|---|---|---|
| **1** | **Không initiate / initiate rồi bỏ mặc** | Hết 10' không trả tiền → `HoldExpiryScheduler` (+ `PaymentExpiryScheduler` nếu đã initiate) tự nổ | booking `CANCELLED` · payment `EXPIRED`/không tạo · ô nhả `AVAILABLE` |
| **2** | **STAFF reject proof** | Proof giả/không có tiền → `POST /{id}/reject` → `payment.player.expired` | booking `CANCELLED` · payment `EXPIRED` · ô nhả `AVAILABLE` |
| **3** | **Huỷ đơn đã trả** | Đơn `CONFIRMED` rồi huỷ → booking phát `booking.refund.required` (kèm amount tier) → payment vào hàng `/refund-required` → STAFF chuyển khoản tay → `POST /{id}/refund` | booking `CANCELLED` (refund theo tier) · payment `CONFIRMED`+`refundRequired` → `REFUNDED` |
| **4** | **Proof khi đơn đã huỷ** (lỡ chuyển khoản) | Đơn đã `CANCELLED` nhưng user vẫn nộp proof → giữ proof + `refundRequired=true` | payment `PROOF_SUBMITTED` + cờ → STAFF đối soát → `REFUNDED`/`reject` |
| **5** | **Confirm trúng đơn đã huỷ** (huỷ sau proof) | STAFF lỡ confirm đơn đã `CANCELLED` → booking phát `booking.payment.orphaned` | payment `CONFIRMED` + `refundRequired=true` → STAFF hoàn tiền tay |

**Diễn biến chi tiết — vì sao xảy ra**

> Hai gốc rễ sinh ra mọi nhánh dưới đây: **(a)** booking & payment là **2 service / 2 DB riêng**, đồng bộ qua Kafka (eventual consistency) → trạng thái 2 bên có thể **lệch nhau vài giây tới ~60s** (scheduler 2 JVM lệch pha · Kafka lag). **(b)** Thanh toán **thủ công, không có cổng tự xác thực** (rule #2) → hệ thống **không tự biết tiền đã vào hay chưa**; chỉ STAFF đối soát sao kê bank mới biết. Mọi nhánh "trúng đơn đã chết" đều sinh từ khe lệch (a), và mọi quyết định hoàn/từ chối đều dựa vào sự thật ở (b).

- **NHÁNH 1 — Timeout.** *Vì sao:* đặt sân (`POST /api/bookings`) và trả tiền (`initiate`) là 2 bước rời — user có thể dừng ở bất kỳ đâu, mà hệ thống không được giữ ô vô hạn. *Cơ chế:* 2 đồng hồ 10' độc lập (`HoldExpiryScheduler` ở booking theo `hold_expires_at` · `PaymentExpiryScheduler` ở payment theo `expires_at`), đều `@Scheduled(60s)` và **chỉ đụng record còn `PENDING`**. Chưa initiate → chỉ đồng hồ booking chạy; initiate rồi bỏ mặc → cả 2 cùng nổ, **cái nào tick trước thắng, cái sau no-op** nhờ idempotency. *Kết cục hội tụ:* booking `CANCELLED` · payment `EXPIRED`/không tạo · ô về `AVAILABLE`.

- **NHÁNH 2 — STAFF reject.** *Vì sao:* bằng chứng duy nhất là **ảnh chuyển khoản** → có thể giả/sai số tiền/sai nội dung/trùng giao dịch cũ; STAFF phải có quyền từ chối. *Cơ chế:* `reject` → payment `PROOF_SUBMITTED→EXPIRED` + `payment.player.expired` → booking `PENDING→CANCELLED` + nhả ô. Dùng **chung topic** với timeout vì từ phía booking "tiền không vào" do hết giờ hay do bị từ chối là **cùng một hệ quả**. *Lưu ý:* `reject` **xoá cờ `refundRequired`** — STAFF đã xác định KHÔNG có tiền nên **không có gì để hoàn**.

- **NHÁNH 3 — Huỷ đơn ĐÃ TRẢ.** *Vì sao:* đơn đã `CONFIRMED` (tiền đã vào, ô giữ chắc) nhưng đời thực user vẫn đổi ý → **tiền đang nằm ở tài khoản CLB**, phải trả lại. *Cơ chế:* `cancel` (đọc booking **row-locked** để không đua với `payment.player.confirmed`) thấy booking `CONFIRMED` → tính `refund = %tier × total_price` (tier theo `earliest_start_time`: >24h=100% · 2–24h=50% · <2h=0%) → `CANCELLED` + nhả ô + (chỉ khi `refund>0`) phát **`booking.refund.required`** (Outbox, kèm amount) → payment gắn `refundRequired=true` + lưu `refund_required_amount` → vào hàng `GET /api/payments/refund-required`; STAFF `refund` (chặn trần `amount ≤ đã trả`) → ghi `manual_refunds` + payment `REFUNDED`. *Điểm phân biệt:* khác huỷ-đơn-PENDING (refund=0) — **chỉ đơn từng `CONFIRMED` mới phát sinh nghĩa vụ hoàn**; hoàn **thủ công** (STAFF tự chuyển khoản, hệ thống chỉ ghi nhận). *Vì sao thiết kế thế:* trước đây `cancel` tính ra số tiền hoàn nhưng **không báo cho payment-service** → payment vẫn `CONFIRMED`, không vào hàng nào → **STAFF không có tín hiệu, tiền user mất trong im lặng**; event `booking.refund.required` nối lại đường này (dùng đúng machinery cờ `refundRequired` của orphaned).

- **NHÁNH 4 — Proof khi đơn ĐÃ HUỶ (lớp 1, money-safe).** *Vì sao:* race 2 đồng hồ lệch pha → `HoldExpiryScheduler` huỷ booking **trước** khi user kịp nộp proof, trong khi payment còn `PENDING`; user vốn **chuyển khoản TRƯỚC rồi mới upload** → proof tới khi đơn đã chết. *Cơ chế:* `submitProof` **LUÔN lưu proof trước** (Cloudinary + `payment_proofs` + `PROOF_SUBMITTED`) rồi mới đối soát booking; thấy `CANCELLED` → **giữ proof + `refundRequired=true`**, KHÔNG vứt, KHÔNG confirm. *Vì sao thiết kế thế:* bản fail-closed cũ "409, vứt ảnh" làm **mất tiền không dấu vết** khi user đã chuyển khoản → nguyên tắc **proof = bằng chứng tiền CÓ THỂ đã chuyển → không bao giờ vứt**. STAFF đối soát bank: có tiền → `refund` (full), proof giả → `reject` (xoá cờ).

- **NHÁNH 5 — Confirm trúng đơn ĐÃ HUỶ (lớp 2, orphaned).** *Vì sao:* ngược NHÁNH 4 — user nộp proof **hợp lệ** (lúc đó đơn còn `PENDING`) **rồi mới tự huỷ đơn** trước khi STAFF confirm; khi STAFF bấm confirm, booking đã `CANCELLED`. *Cơ chế (zombie-event, rule #6):* confirm vẫn cho payment `→CONFIRMED` (tiền đã thật) + `payment.player.confirmed` → booking thấy đã `CANCELLED` → **KHÔNG hồi sinh** (ô đã nhả cho người khác, hồi sinh là sai), phát `booking.payment.orphaned` → payment gắn `refundRequired=true`. *Vì sao thiết kế thế:* trước đây nhánh này **no-op im lặng** → user trả tiền mà không có sân, không tín hiệu hoàn; pattern bù trừ biến "event muộn về đơn đã chết" thành **tín hiệu hoàn tường minh**. Hoàn **FULL** (đơn huỷ lúc còn `PENDING` là huỷ-miễn-phí; tiền vào sau là khoản dư cho đơn đã chết).

> **NHÁNH 3 vs 5** khác nhau đúng ở **thứ tự `confirm`/`cancel`**: **3** = confirm TRƯỚC rồi huỷ → hoàn **theo tier** (qua `booking.refund.required`) · **5** = huỷ TRƯỚC rồi confirm → hoàn **full** (qua `booking.payment.orphaned`). NHÁNH **3 + 4 + 5** đều đổ về hàng `GET /api/payments/refund-required` (cờ `refundRequired`) → **không đường nào làm mất tiền user trong im lặng**. Mọi chuyển trạng thái booking/payment đều **row-locked** (`SELECT … FOR UPDATE`) nên `cancel` và `confirm` đua nhau luôn hội tụ về một trong hai đường này, không bao giờ ra "tiền vào mà không cờ hoàn".

### 2.3 Quy tắc & nhánh lỗi cốt lõi

| Quy tắc | Chi tiết |
|---|---|
| Email-verified | Chỉ user đăng nhập + `email_verified=true` mới đặt (authority `EMAIL_VERIFIED` từ JWT) |
| Không đặt quá khứ | `date ≥ hôm nay` **và** từng ô phải còn ở tương lai (chặn ô cùng-ngày đã qua giờ → `409 SLOT_IN_PAST`); đơn vị tối thiểu = 1 ô 30' = 1 `booking_item` |
| Chống spam đặt | Rate-limit `rate_limit:booking:{userId}` = 10 lượt tạo/phút (Redis, **fail-open**) + tối đa **20 ô**/đơn (`@Size`) → chống 1 user squat lưới ô |
| Atomic all-or-nothing | 1 ô khoá hỏng / vi phạm `UNIQUE(slot_id)` → **409**, huỷ nguyên đơn; FE báo "ô vừa bị đặt, chọn lại" |
| Snapshot giá | `booking_items.price` chốt lúc đặt — không đọc live về sau |
| Refund tier | Theo `earliest_start_time`: >24h=100% · 2–24h=50% · <2h=0% (× tiền đã trả) |
| Hold = payment window | Cả 2 đồng hồ = **10 phút** (`BOOKING_HOLD_MINUTES` = `PAYMENT_EXPIRE_MINUTES`) |
| Trần giữ chỗ tuyệt đối | `begin-payment` được gia hạn hold (người trả chậm) nhưng KHÔNG quá `createdAt + 30'` (`BOOKING_MAX_HOLD_MINUTES`); hết trần → `409 BOOKING_HOLD_EXHAUSTED` → không tạo payment, ô được nhả → chống spam `begin-payment` giữ ô vĩnh viễn |
| Double-initiate idempotent | Gọi `initiate` 2 lần / 1 booking → trả lại payment đang active, KHÔNG tạo trùng (partial unique index chốt chặn DB) |
| Fail-closed | court/booking-service lỗi khi verify/handshake → từ chối (không đoán giá / không tạo payment cho đơn không xác thực được) |
| 3 lớp giữ chỗ | `lock:slot:{id}` Redis 5s (lúc tạo) · `booking_items.slot_id` UNIQUE (suốt đời đơn) · `time_slots.status=RESERVED` (làm lưới đỏ) |

---

## 3. Sequence — luồng đầy đủ (booking + payment)

> Sơ đồ **một mảnh** phủ đủ **5 nhánh kết cục** — tag `NHÁNH 1..5` ngay trên mỗi `alt/else/opt` để khớp bảng **2.2**. Đọc cặp `booking=… · payment=…` trong mỗi `Note` để biết trạng thái 2 thực thể ở từng mốc: `bookings.status` (booking-service) · `payments.status` (payment-service).
> Phân biệt **NHÁNH 3 vs 5** = thứ tự `confirm`/`cancel`: **3** = confirm TRƯỚC rồi mới huỷ (đơn từng thành công → hoàn **theo tier**) · **5** = huỷ TRƯỚC khi STAFF confirm, confirm trúng đơn đã chết (orphaned bù trừ → hoàn **full**).
> Mọi event payment phát qua **Outbox** (3s); consume **idempotent** (`processed_events`) + manual-ack + DLT. Xem **bảng 4** + **state machine 5** bên dưới.

```mermaid
sequenceDiagram
    actor U as User
    actor S as STAFF
    participant FE as Frontend
    participant GW as Gateway
    participant CS as court-service
    participant BS as booking-service
    participant PS as payment-service
    participant K as Kafka

    %% ===== Tạo đơn + giữ ô (§2.1 bước 1–3) =====
    U->>FE: chọn ô → "XÁC NHẬN ĐẶT"
    FE->>GW: POST /api/bookings {items:[{courtId,slotId}...]}
    GW->>BS: route (verify JWT + EMAIL_VERIFIED)
    BS->>CS: Feign GET lưới (verify AVAILABLE + chốt giá snapshot)
    BS->>BS: INSERT bookings + items + outbox(slot.changed·HELD mỗi ô) · booking PENDING (hold=+10')
    BS-->>FE: 201 {bookingId, totalPrice, holdExpiresAt}
    BS->>K: (Outbox 3s) booking.slot.changed (HELD·key=slotId)
    K->>CS: consume → ô AVAILABLE→RESERVED (lưới đỏ)
    Note over BS,PS: booking=PENDING · payment=(chưa tạo)

    alt User KHÔNG initiate — bỏ ngay sau khi giữ ô (NHÁNH 1)
        Note over BS: HoldExpiryScheduler (60s): booking PENDING quá hold_expires_at (10')
        BS->>BS: booking PENDING→CANCELLED (PAYMENT_TIMEOUT) + xoá items + outbox(slot.changed·RELEASED)
        BS->>K: booking.slot.changed (RELEASED·key=slotId)
        K->>CS: ô RESERVED→AVAILABLE (lưới xanh)
        Note over BS,PS: ❌ booking=CANCELLED · payment=(chưa tạo)
    else User initiate — mở màn Bank QR
        FE->>GW: POST /api/payments/initiate {bookingId, BOOKING, amount}
        GW->>PS: route (verify JWT + EMAIL_VERIFIED)
        PS->>BS: (Feign + forward token) POST /api/bookings/{id}/begin-payment
        BS->>BS: gác cổng — đơn PENDING? đúng chủ? · reset hold_expires_at=+10'
        BS-->>PS: BookingResponse (totalPrice = amount CHUẨN)
        Note over PS,BS: đơn đã huỷ/hết hạn/không thuộc bạn → 409 → PS KHÔNG tạo payment (fail-closed)
        PS->>PS: INSERT payment + order_code + expires_at=+10' · amount=booking.totalPrice · PENDING
        PS-->>FE: 201 {orderCode #184, bank+QR, amount, expiresAt}
        Note over BS,PS: booking=PENDING (hold gia hạn) · payment=PENDING (chờ chuyển khoản)

        alt User chuyển khoản + upload proof trong 10'
            U->>FE: chuyển khoản (nội dung #184) → upload ảnh
            FE->>GW: POST /api/payments/{id}/proof (multipart)
            GW->>PS: route (owner / STAFF)
            PS->>PS: LƯU proof (Cloudinary + payment_proofs) · payment PROOF_SUBMITTED — LUÔN nhận (money-safe)
            PS->>BS: (Feign + token) begin-payment — đơn còn PENDING?
            PS-->>FE: 200 PROOF_SUBMITTED (FE hiện "chờ STAFF duyệt")

            alt booking còn PENDING khi nộp proof (đường thường)
                BS-->>PS: OK · reset hold_expires_at=+10'
                PS->>K: (Outbox) payment.proof.submitted
                K->>BS: consume → booking.hold_expires_at=null (vẫn PENDING · DỪNG auto-huỷ)
                Note over BS,PS: booking=PENDING (hold=null) · payment=PROOF_SUBMITTED → chờ STAFF

                opt User đổi ý → huỷ đơn KHI ĐANG CHỜ DUYỆT (booking PENDING) — tạo tiền đề NHÁNH 5
                    U->>BS: POST /api/bookings/{id}/cancel
                    BS->>BS: booking PENDING→CANCELLED (refund=0 · chưa confirm) + outbox(slot.changed·RELEASED)
                    BS->>K: booking.slot.changed (RELEASED·key=slotId)
                    K->>CS: ô RESERVED→AVAILABLE
                    Note over BS,PS: booking=CANCELLED · payment=PROOF_SUBMITTED (tiền chờ STAFF đối soát)
                end

                S->>GW: GET /api/payments/pending-review (hàng chờ duyệt, FIFO)
                GW->>PS: route (STAFF/ADMIN)
                PS-->>S: danh sách payment PROOF_SUBMITTED (kèm cờ refundRequired)
                Note over S,PS: STAFF tìm proof qua pending-review · notification push/email = CHƯA build

                alt STAFF confirm — đối soát bank thấy CÓ tiền
                    S->>GW: POST /api/payments/{id}/confirm
                    GW->>PS: route (STAFF/ADMIN)
                    PS->>PS: set confirmed_by/at · payment PROOF_SUBMITTED→CONFIRMED (tiền đã vào)
                    PS->>K: (Outbox) payment.player.confirmed
                    alt booking vẫn PENDING → THÀNH CÔNG
                        K->>BS: consume (idempotent) · booking PENDING→CONFIRMED (ô GIỮ RESERVED)
                        Note over BS,PS: ✅ booking=CONFIRMED · payment=CONFIRMED
                        opt Sau này huỷ đơn ĐÃ TRẢ → vào hàng refund-required (NHÁNH 3)
                            U->>BS: POST /api/bookings/{id}/cancel (hoặc STAFF · row-locked)
                            BS->>BS: booking CONFIRMED→CANCELLED · refund=%tier×total + outbox(slot.changed·RELEASED)
                            BS->>K: booking.slot.changed (RELEASED·key=slotId)
                            K->>CS: ô RESERVED→AVAILABLE
                            BS->>K: (Outbox) booking.refund.required {refundAmount=%tier×total} (chỉ khi refund>0)
                            K->>PS: consume (idempotent) · payment CONFIRMED + refundRequired=true + refund_required_amount
                            Note over BS,PS: ⚠️ booking=CANCELLED · payment=CONFIRMED + refundRequired (amount gợi ý)
                            S->>PS: GET /api/payments/refund-required → POST /{id}/refund {amount≤đã trả, bank đích}
                            PS->>PS: INSERT manual_refunds · payment CONFIRMED→REFUNDED (đóng cờ)
                            PS->>K: (Outbox) payment.refund.processed
                            Note over BS,PS: booking=CANCELLED · payment=REFUNDED (hoàn theo tier %)
                        end
                    else booking ĐÃ HUỶ trước confirm — orphaned bù trừ (NHÁNH 5)
                        K->>BS: consume · booking đã CANCELLED → KHÔNG hồi sinh
                        BS->>K: (Outbox) booking.payment.orphaned (zombie-event)
                        K->>PS: consume (idempotent) · payment vẫn CONFIRMED + refundRequired=true
                        Note over BS,PS: ⚠️ booking=CANCELLED · payment=CONFIRMED + refundRequired
                        S->>PS: POST /api/payments/{id}/refund {amount đầy đủ, bank đích}
                        PS->>PS: INSERT manual_refunds · payment CONFIRMED→REFUNDED (đóng cờ)
                        Note over BS,PS: payment=REFUNDED (STAFF hoàn FULL — đơn huỷ lúc chưa confirm)
                    end
                else STAFF reject — bank KHÔNG có tiền / proof giả (NHÁNH 2)
                    S->>GW: POST /api/payments/{id}/reject {reason}
                    GW->>PS: route (STAFF/ADMIN)
                    PS->>PS: set reject_reason · payment PROOF_SUBMITTED→EXPIRED (xoá cờ refundRequired)
                    PS->>K: (Outbox) payment.player.expired
                    K->>BS: consume · booking PENDING→CANCELLED + outbox(slot.changed·RELEASED) · đã CANCELLED → no-op
                    BS->>K: booking.slot.changed (RELEASED·key=slotId)
                    K->>CS: ô RESERVED→AVAILABLE (lưới xanh)
                    Note over BS,PS: ❌ booking=CANCELLED · payment=EXPIRED
                end
            else booking ĐÃ HUỶ trước khi nộp proof — user lỡ chuyển khoản (NHÁNH 4, lớp 1)
                BS-->>PS: 409 BOOKING_NOT_PAYABLE
                PS->>PS: refundRequired=true (BOOKING_CANCELLED) — GIỮ proof, KHÔNG confirm
                Note over BS,PS: ⚠️ booking=CANCELLED · payment=PROOF_SUBMITTED + refundRequired
                S->>PS: GET /api/payments/refund-required → đối soát bank
                Note over S,PS: có tiền → POST /{id}/refund → REFUNDED (hoàn full) · proof giả → reject → EXPIRED
            end
        else User initiate XONG rồi BỎ MẶC — không upload proof, hai đồng hồ 10' tự nổ (NHÁNH 1)
            Note over PS: PaymentExpiryScheduler (60s): payment PENDING quá expires_at → EXPIRED (chỉ PENDING · bỏ qua PROOF_SUBMITTED)
            PS->>K: (Outbox) payment.player.expired
            K->>BS: consume → booking còn PENDING → CANCELLED + outbox(slot.changed·RELEASED) · đã CANCELLED → no-op (idempotent)
            Note over BS: (độc lập) HoldExpiryScheduler cũng huỷ booking nếu chưa bị huỷ — cái nào nổ trước thắng
            BS->>K: booking.slot.changed (RELEASED·key=slotId)
            K->>CS: ô RESERVED→AVAILABLE (lưới xanh)
            Note over BS,PS: ❌ booking=CANCELLED · payment=EXPIRED · ô AVAILABLE
        end
    end
```

---

## 4. Bảng trạng thái theo từng mốc

| Mốc | Hành động | `bookings.status` | `payments.status` | ô `time_slots` |
|---|---|---|---|---|
| Tạo đơn | `POST /api/bookings` | **PENDING** (hold=+10') | *(chưa tạo)* | AVAILABLE → **RESERVED** (sau held) |
| Initiate | `POST /api/payments/initiate` (→ begin-payment) | PENDING (**hold gia hạn +10'**) | **PENDING** (expires=+10' · amount=booking.totalPrice) | RESERVED |
| Upload proof | `POST /{id}/proof` | PENDING (hold=null · dừng đồng hồ) | **PROOF_SUBMITTED** | RESERVED |
| STAFF confirm | `POST /{id}/confirm` | **CONFIRMED** | **CONFIRMED** | RESERVED (giữ) |
| STAFF reject | `POST /{id}/reject` | **CANCELLED** | **EXPIRED** | RESERVED → **AVAILABLE** (nhả) |
| Chưa initiate, hết 10' | `HoldExpiryScheduler` | **CANCELLED** (PAYMENT_TIMEOUT) | *(chưa tạo)* | RESERVED → **AVAILABLE** (nhả) |
| **Initiate rồi bỏ mặc** (không proof), hết 10' | `PaymentExpiryScheduler` + `HoldExpiryScheduler` (độc lập) | **CANCELLED** | **EXPIRED** | RESERVED → **AVAILABLE** (nhả) |
| **Proof khi đơn đã huỷ** (user lỡ chuyển khoản) | `POST /{id}/proof` | **CANCELLED** (đã huỷ trước) | **PROOF_SUBMITTED** + `refundRequired` | giữ AVAILABLE (ô đã nhả) |
| Huỷ đơn đã trả / proof-đơn-huỷ + hoàn tiền | `cancel` + `POST /{id}/refund` | **CANCELLED** | **REFUNDED** | (đã AVAILABLE) |

---

## 5. State machine — 2 thực thể đồng bộ qua Kafka

```
payments.status (payment_db):
   PENDING ──► PROOF_SUBMITTED ──► CONFIRMED ──► REFUNDED
      │              ├──────────────────────────► REFUNDED   (proof cho đơn đã huỷ + refundRequired → STAFF hoàn)
      └──────────────┴──────────► EXPIRED         (hết 10' timeout / STAFF reject)

bookings.status (booking_db):
   PENDING ──► CONFIRMED ──► COMPLETED
      └────────────┴──────► CANCELLED             (timeout / reject / huỷ tay)
```

- **Cặp khớp nhau:** payment `CONFIRMED` ⇄ booking `CONFIRMED` · payment `EXPIRED` ⇄ booking `CANCELLED` · payment `REFUNDED` ⇒ booking đã `CANCELLED` trước đó (huỷ đơn đã trả **NHÁNH 3** — hoàn theo tier · hoặc orphaned **NHÁNH 5** — hoàn full · hoặc proof-đơn-huỷ **NHÁNH 4**).
- 2 status sống ở **2 DB riêng**, không bao giờ ghi chung 1 transaction — đồng bộ qua event `payment.player.confirmed/expired` (eventual consistency, idempotent).

---

## 6. Ghi chú kỹ thuật (khớp code)

- **Handshake `initiate` (fail-closed)**: payment-service gọi booking-service `POST /api/bookings/{id}/begin-payment` (Feign `lb://`, **forward token user**) TRƯỚC khi tạo payment. booking là **người gác cổng**: đơn phải `PENDING` + đúng chủ → reset `hold_expires_at=now+10'` (gia hạn nhưng **không quá `createdAt + 30'`**) + trả `totalPrice` làm **amount chuẩn** (bỏ amount client gửi). Đơn đã `CANCELLED`/hết hạn/không thuộc bạn → **409 `BOOKING_NOT_PAYABLE`**, payment KHÔNG được tạo → hết cảnh "trả tiền cho đơn đã chết". Giữ quá lâu chưa trả (vượt trần) → **409 `BOOKING_HOLD_EXHAUSTED`** (payment map thành `BOOKING_NOT_PAYABLE`). booking-service lỗi/không tới → **409 `BOOKING_SERVICE_UNAVAILABLE`** (fail-closed, mirror cách booking fail-closed khi court lỗi).
- **Trần giữ chỗ tuyệt đối (chống squat `begin-payment`)**: `begin-payment` re-anchor hold mỗi lần gọi → nếu không chặn, chủ đơn có thể spam endpoint (public, owner-check) để giữ ô **vĩnh viễn mà không trả tiền**. `beginPayment` kẹp hold ≤ `createdAt + BOOKING_MAX_HOLD_MINUTES` (mặc định 30' = 3× cửa sổ); hết trần → `409 BOOKING_HOLD_EXHAUSTED` → không tạo payment, ô nhả ở lần `HoldExpiryScheduler` kế. Luồng hợp lệ (tạo → initiate → trả → proof) luôn nằm trong trần. Rate-limit tạo đơn (NEW-D) chỉ gác `POST /api/bookings`, KHÔNG gác `begin-payment` — nên trần này là chốt cho đường re-anchor.
- **`create()` chạy Feign NGOÀI transaction**: court-service Feign (lấy grid + chốt giá) và toàn bộ validate (kể cả chặn ô quá giờ) chạy **trước** mọi tx/lock; chỉ INSERT header+items+outbox nằm trong **transaction ngắn** (`TransactionTemplate`). Redis lock khoá ô **sau** Feign (không giữ lock qua mạng). Lý do: court-service chậm không được giữ kết nối DB của booking (cạn pool sẽ kéo sập cả `cancel`/`refund`).
- **`initiate` idempotent (chống double-initiate)**: gọi `initiate` 2 lần cho cùng `bookingId` → KHÔNG tạo 2 payment. Trước khi tạo, payment-service tra payment **đang active** (`PENDING`/`PROOF_SUBMITTED`) của booking đó → có thì **trả lại chính nó** (giữ nguyên countdown, KHÔNG gọi lại begin-payment để đồng hồ payment vẫn khớp hold booking; check chủ sở hữu — người khác đụng vào → 403). Chốt chặn DB: **partial unique index** `uk_payments_active_booking ON payments(booking_id) WHERE booking_id IS NOT NULL AND status IN ('PENDING','PROOF_SUBMITTED')` (đơn được phép tích nhiều payment `EXPIRED` cũ nên unique phải là **partial**, KHÔNG full-column) → race đồng thời thật mà query bỏ lọt thì kẻ thua nhận **409 `PAYMENT_ALREADY_INITIATED`** thay vì tạo trùng.
- **Hai đồng hồ 10'**: booking `HoldExpiryScheduler` (theo `hold_expires_at`) và payment `PaymentExpiryScheduler` (theo `expires_at`) đều dài 10', chạy mỗi 60s, **độc lập ở 2 service**. **Initiate rồi BỎ MẶC (không upload proof)**: `PaymentExpiryScheduler` đưa payment `PENDING→EXPIRED` + phát `payment.player.expired`; `HoldExpiryScheduler` đưa booking `PENDING→CANCELLED` + nhả ô. Cái nào nổ trước thắng, cái sau **no-op** (record không còn `PENDING`) + idempotency — kết cục **booking=CANCELLED · payment=EXPIRED · ô AVAILABLE** (booking bị huỷ do `HoldExpiryScheduler` trực tiếp HOẶC do consume `payment.player.expired`, tuỳ cái nào trước). ⚠️ `PaymentExpiryScheduler` **chỉ** đụng `PENDING` — KHÔNG đụng `PROOF_SUBMITTED` (đã nộp tiền, chờ STAFF). **Khi user upload proof** → booking nghe `payment.proof.submitted` set `hold_expires_at=null` (dừng đồng hồ booking) ⇒ **sau proof cả 2 phía chờ STAFF** quyết định (`confirm`→CONFIRMED / `reject`→huỷ + nhả ô). Đặt `PAYMENT_EXPIRE_MINUTES`=`BOOKING_HOLD_MINUTES`.
- **Nộp proof cho đơn ĐÃ HUỶ → KHÔNG bao giờ vứt proof (money-safe, lớp 1)**: user thường **chuyển khoản TRƯỚC rồi mới upload** → proof = bằng chứng tiền *có thể* đã chuyển ⇒ tuyệt đối không reject/đánh rơi. `submitProof` **LUÔN lưu proof** (Cloudinary + `payment_proofs` + `PROOF_SUBMITTED`) rồi mới gọi `begin-payment` đối soát booking: (a) còn `PENDING` → re-anchor `hold=+10'` + phát `payment.proof.submitted` (booking dừng auto-huỷ); (b) đã `CANCELLED` (user lỡ chuyển khoản trước khi đơn chết, vd `HoldExpiryScheduler` nổ trước) → **giữ proof + set `refundRequired=true`** (reason `BOOKING_CANCELLED`), **KHÔNG confirm**; (c) booking lỗi/không tới → giữ proof + vẫn phát `payment.proof.submitted` (booking xử lý nếu sống; lớp 2 chặn lúc confirm nếu chết). STAFF xem qua `pending-review`/`refund-required` (cờ hiện trong response) → **đối soát bank**: có tiền → `POST /{id}/refund` (cho phép refund từ `PROOF_SUBMITTED` khi đã gắn cờ → `REFUNDED`); proof giả/không có tiền → `reject` (→ `EXPIRED`, xoá cờ). **Không còn cảnh user trả tiền mà hệ thống vứt bằng chứng.**
- **Confirm trúng đơn ĐÃ HUỶ → bù trừ hoàn tiền** (lớp 2): nếu vẫn lọt (user nộp proof hợp lệ **rồi tự huỷ đơn** trước khi STAFF confirm) → `handleConfirmed` thấy booking `CANCELLED` → KHÔNG hồi sinh, phát `booking.payment.orphaned` (Outbox, zombie-event pattern) → payment-service (consumer đầu tiên + `processed_events`) gắn cờ **`refundRequired=true`** trên payment đã `CONFIRMED` (tiền đã vào). STAFF thấy qua `GET /api/payments/refund-required` rồi hoàn tiền tay (`/{id}/refund` → đóng cờ). **Tiền không bị nuốt im lặng nữa.**
- **Huỷ đơn ĐÃ TRẢ → nối lại đường hoàn (NHÁNH 3)**: `cancel` một đơn `CONFIRMED` (refund tier > 0) phát **`booking.refund.required`** (Outbox, kèm `refundAmount`) → payment-service consume → gắn `refundRequired=true` + lưu `refund_required_amount` (số gợi ý hiện trong hàng `/refund-required`). Trước đây `cancel` tính refund nhưng **không báo payment** → tiền user mất trong im lặng; event này dùng đúng machinery cờ của orphaned. refund=0 (huỷ <2h) thì CLB giữ tiền hợp lệ → KHÔNG phát.
- **Chống mất tiền do đua (row-lock + cap)**: mọi chuyển trạng thái booking (`cancel`/`beginPayment`/handler `payment.player.*`/hold-expiry) và payment (`confirm`/`reject`/`refund`) đọc record bằng **`SELECT … FOR UPDATE`** → `cancel` ‖ `payment.player.confirmed` không lose-update nhau (luôn hội tụ về NHÁNH 3 hoặc 5, không ra "tiền vào mà không cờ hoàn"); 2 STAFF bấm `refund`/`confirm` đồng thời → kẻ thua block rồi 409. `refund` chặn trần **`amount ≤ payments.amount`** (409 `REFUND_EXCEEDS_PAID`) — không bao giờ hoàn quá số đã trả.
- **`submitProof` cũng row-lock (chống hồi sinh đơn đã reject)**: upload Cloudinary **TRƯỚC** (ngoài lock, không giữ row-lock qua network) → `SELECT … FOR UPDATE` re-check status. Nếu giữa lúc đó STAFF vừa `reject`/`confirm` (đơn `EXPIRED`/`CONFIRMED`) → **409, KHÔNG ghi đè** (không đẩy đơn đã từ chối trở lại hàng chờ duyệt).
- **Chống tạo payment thứ 2 (idempotent initiate)**: đã có payment `CONFIRMED` cho booking → `initiate` lần 2 **trả lại chính nó**, không mở payment mới (đóng khe lag "booking còn PENDING nhưng payment đã CONFIRMED" → tránh user chuyển khoản 2 lần). `booking.refund.required` ưu tiên gắn cờ payment `CONFIRMED` (đúng cái đang giữ tiền).
- **Topic theo `payment_type`**: BOOKING/MATCH_PLAYER → `payment.player.*`; MATCH_HOST → `payment.host.*`. booking **chỉ** nghe `payment.player.confirmed/expired`; nếu payload `bookingId=null` (vd event MATCH_PLAYER) → booking **ack bỏ qua**.
- **`order_code`** = Postgres `bigserial` (`@Generated` đọc lại sau INSERT), hiển thị `"#"+value` (vd `#184`) — user ghi vào nội dung chuyển khoản.
- **Cloudinary degrade**: thiếu key → `image_url = local-fallback://proof/{uuid}` (luồng vẫn chạy để test); điền key thật để upload thật.
- **API payment**: `POST /api/payments/initiate` (USER/COACH + email-verified) · `/{id}/proof` (owner/STAFF, multipart) · `/{id}/confirm` · `/{id}/reject` · `/{id}/refund` · `GET /api/payments/pending-review` (STAFF/ADMIN — hàng chờ duyệt, FIFO) · `GET /api/payments/refund-required` (STAFF/ADMIN) · `GET /{id}` · `GET /` (của mình) · `GET /api/bank-accounts/active`. Refund (`/{id}/refund`) = STAFF chuyển khoản tay → `manual_refunds` → `payment.refund.processed`.
- **STAFF tìm proof để duyệt** (lấp gap): chưa có notification-service → STAFF chủ động gọi **`GET /api/payments/pending-review`** (liệt kê payment `PROOF_SUBMITTED`, cũ nhất trước) rồi `confirm`/`reject`. Khi notification-service lên, `payment.proof.submitted` (đã phát) sẽ push/email cho STAFF — endpoint vẫn là nguồn tra cứu chính.
- **Kafka & Routing**: topic `booking.slot.changed` (booking → court · 1 message/ô, key=`slotId` để HELD/RELEASED của 1 ô đúng thứ tự, chống ô kẹt RESERVED · idempotency theo `eventId` trong payload) + `payment.player.confirmed/expired` / `payment.proof.submitted` / `payment.refund.processed` (payment → …) + `booking.payment.orphaned` / `booking.refund.required` (booking → payment). Routing: `/api/clubs/**`,`/api/courts/**` → `lb://court-service`; `/api/bookings/**` → `lb://booking-service`; `/api/payments/**`,`/api/bank-accounts/**` → `lb://payment-service`.
