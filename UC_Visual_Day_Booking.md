# 📋 Use Case: Đặt Lịch Ngày Trực Quan (Visual Day Booking)

> Đồng bộ với **ERD mới**: `clubs` (CLB/venue) ──< `courts` (Sân vật lý) ──< `time_slots` (ô **30 phút**);
> giá tra từ `court_pricing_rules`; đơn đặt = **header `bookings` + N `booking_items`** (mỗi item = 1 ô 30').
> Thanh toán = **Bank QR + proof + STAFF confirm** (KHÔNG VNPay).

---

## 1. Use Case Overview

| Field | Detail |
|---|---|
| **Use Case ID** | UC-BOOKING-01 |
| **Use Case Name** | Đặt Lịch Ngày Trực Quan |
| **English Name** | Visual Day Booking |
| **Module** | Booking |
| **Priority** | High |
| **Actor(s)** | User (primary), court-service (slots/giá), booking-service (header+items), payment-service (Bank QR + STAFF confirm), notification-service |
| **Trigger** | User chọn **"Đặt lịch ngày trực quan"** từ modal **"Chọn hình thức đặt"** trên trang CLB |
| **URL** | `datlich.alobo.vn/userBooking` |

---

## 2. Actors

| Actor | Role |
|---|---|
| **User** | Khách đã đăng nhập, muốn đặt 1 hoặc nhiều ô giờ trên 1 hoặc nhiều **sân** của CLB |
| **court-service** | Cung cấp danh sách **sân + slots 30'** theo ngày, **bảng giá** (`court_pricing_rules`), cập nhật `time_slots.status` |
| **booking-service** | Tạo **booking header + booking_items**, orchestrate Saga, idempotency guard |
| **payment-service** | Hiển thị QR ngân hàng, nhận proof upload, STAFF xác nhận thủ công |
| **notification-service** | Gửi email/push xác nhận đặt lịch |

---

## 3. Preconditions

- ✅ User đã **đăng nhập** (JWT hợp lệ; `is_email_verified=true` để được tạo booking — rule #10)
- ✅ User đã vào trang một **CLB** cụ thể (`clubId` xác định) — hiện hệ thống quản lý **1 CLB**
- ✅ court-service load được **time_slots** (ô 30') cho tất cả **sân** của CLB theo ngày
- ✅ Có ít nhất **1 slot AVAILABLE** trong ngày được chọn

---

## 4. Postconditions

### Success
- 📌 1 `bookings` (HEADER) được tạo `status = CONFIRMED` trong `booking_db`, `total_price = SUM(booking_items.price)`
- 📌 N `booking_items` được tạo — mỗi item = 1 ô 30' (`court_id`, `slot_id` 1:1, snapshot `court_name`/`start_time`/`end_time`/`price`)
- 📌 Mỗi `time_slots` tương ứng chuyển `status = RESERVED` + set `booking_id` (HEADER) trong `court_db`
- 📌 1 `payments` (`payment_type=BOOKING`, `booking_id`=header) `status = CONFIRMED` trong `payment_db`
- 📌 User nhận **email + push notification** xác nhận; booking hiện trong **Dashboard**

### Failure / Rollback
- 🔁 **Tất cả** slot trong đơn trả về `status = AVAILABLE` (huỷ nguyên đơn — atomic)
- 🔁 Mọi Redis lock `lock:slot:{slotId}` được release
- 🔁 `bookings.status = CANCELLED`; `payments.status = EXPIRED` (timeout) hoặc bị STAFF `REJECT`

---

## 5. Main Success Flow

```
Bước  Actor           Hành động
───────────────────────────────────────────────────────────────────────────────
 1.   User            Mở trang CLB (vd "An Bình Pickleball") → nhấn nút "ĐẶT LỊCH"

 2.   System          Hiển thị modal "Chọn hình thức đặt":
                        ┌────────────────────────────────────┐
                        │  Đặt lịch ngày trực quan      →   │  ← màu xanh lá
                        │  Đặt nhiều khung giờ, nhiều sân.   │
                        ├────────────────────────────────────┤
                        │  Đặt lịch sự kiện          🆕  →  │  ← màu hồng/tím
                        └────────────────────────────────────┘

 3.   User            Chọn "Đặt lịch ngày trực quan" → nhấn "→"

 4.   System          Navigate đến timeline grid của CLB.
                      Gọi GET /api/clubs/:clubId/slots?date=<today>&sport=<PICKLEBALL>
                      court-service trả về time_slots (30') cho TẤT CẢ sân của CLB,
                      kèm price mỗi ô (tra court_pricing_rules theo day_type + khung giờ + WALK_IN).
                      Render giao diện:
                        • Date picker (top-right): mặc định = hôm nay
                        • Legend bar: ■ Trống | ■ Đã đặt | ■ Khoá | ! Sự kiện
                                      + link "Xem sân & bảng giá"
                        • Timeline grid: hàng = Sân 1–N (courts.court_number),
                                         cột = 5:00 → 22:00 (mỗi ô = 1 time_slot 30')
                        • Bottom summary bar: "Tổng giờ: 0h00" | "Tổng tiền: 0 đ"
                        • Nút "TIẾP THEO" (yellow) — disabled

 5.   User            (Tùy chọn) Chọn ngày khác từ date picker

 6.   System          Reload slots cho ngày mới (giá có thể đổi nếu WEEKDAY↔WEEKEND),
                      clear mọi selection

 7.   User            Click các ô AVAILABLE (vd Sân 2: 10:00–10:30 và 10:30–11:00 và 11:00–11:30)
                        • Mỗi ô 30' được chọn: highlight xanh lá + viền đậm
                        • Các ô liền kề cùng sân được GỘP hiển thị: "Sân 2: 10:00–11:30"
                        • Bottom summary cập nhật live: "Tổng giờ: 1h30 | Tổng tiền: 120.000 đ"
                          (= Σ price mỗi ô 30' từ court_pricing_rules)
                        • Nút "TIẾP THEO" được kích hoạt

 8.   User            (Tùy chọn) Chọn thêm ô ở sân khác (vd Sân 4: 19:00–20:00)
                        → 1 đơn có thể gồm nhiều sân + nhiều khung giờ

 9.   User            Nhấn "TIẾP THEO"

10.   System          Hiển thị form xác nhận:
                      ┌─────────────────────────────────────────────────────┐
                      │  🏟 Thông tin CLB                                   │
                      │  Tên CLB: An Bình Pickleball  (clubs.name)          │
                      │  Địa chỉ: 12/15 Kha Vạn Cân... (clubs.address)      │
                      ├─────────────────────────────────────────────────────┤
                      │  📋 Thông tin lịch đặt (booking_items gộp theo sân) │
                      │  Ngày: 05/06/2026                                   │
                      │  - Sân 2: 10:00 - 11:30  |  120.000 đ  (3 ô × 40k) │
                      │  - Sân 4: 19:00 - 20:00  |   90.000 đ  (2 ô × 45k) │
                      │  Đối tượng: Pickleball                              │
                      │  Tổng giờ: 2h30   Tổng tiền: 210.000 đ             │
                      ├─────────────────────────────────────────────────────┤
                      │  TÊN CỦA BẠN     [Phúc                          ✕]  │
                      │  SỐ ĐIỆN THOẠI   [🇻🇳 +84 | 399158632          ✕]  │
                      │  GHI CHÚ CHO CHỦ SÂN [______________________]        │
                      └─────────────────────────────────────────────────────┘
                      • Tên/SĐT pre-fill từ profile; customer_type mặc định = WALK_IN

11.   User            (Tùy chọn) Nhấn "Thêm dịch vụ" (bóng, vợt, nước...) — cộng vào tổng

12.   User            Kiểm tra → nhấn "XÁC NHẬN & THANH TOÁN"

13.   System          Gọi POST /api/bookings
                        Body: { clubId, date, customerName, customerPhone, note,
                                items: [ { courtId, slotId }, ... ] }   (mỗi slotId = 1 ô 30')
                        Trong 1 @Transactional:
                        • Acquire Redis lock lock:slot:{slotId} TTL 5s cho TỪNG slotId
                          → nếu BẤT KỲ ô nào fail → rollback + release toàn bộ (huỷ nguyên đơn)
                        • Verify mọi slot vẫn AVAILABLE (qua court-service)
                        • Tra court_pricing_rules → snapshot price cho từng item
                        • Tạo bookings (HEADER): status=PENDING, customer_type=WALK_IN,
                          total_price=Σ item.price, earliest_start_time=min(start_time)
                        • Tạo N booking_items (snapshot court_name/start/end/price)
                        • Gọi POST /api/payments/initiate { bookingId, amount=total_price }
                        → Trả về { bookingId, paymentId, orderCode, bankName,
                                   accountNumber, accountName, qrImageUrl, amount, expiresAt }

14.   System          Hiển thị màn hình thanh toán Bank QR (sportPaymentScreen):
                        ┌──────────────────────────────────────────────────────┐
                        │ 1. Tài khoản ngân hàng:                              │
                        │    Tên TK / Số TK [📋 copy] / Ngân hàng + [QR image] │
                        │ 2. ⚠️ Chuyển khoản [total_price] đ, nội dung #orderCode│
                        │ 3. Đơn được giữ trong: ⏱ 09:59 (countdown 10 phút)  │
                        │ 4. [Upload zone: tải ảnh thanh toán (*)]             │
                        │ 5. [XÁC NHẬN ĐẶT] (enabled sau khi chọn ảnh)         │
                        └──────────────────────────────────────────────────────┘

15.   User            Chuyển khoản + upload ảnh proof
                        → POST /api/payments/{id}/proof  → payment.status = PROOF_SUBMITTED
                        → Kafka payment.proof.submitted → notification-service báo STAFF

16.   STAFF           Vào Admin Panel → đối chiếu sao kê khớp orderCode → click CONFIRM
                        → payment.status = CONFIRMED → Kafka payment.player.confirmed (booking)

17.   System          Xác nhận đơn:
                        • payment-service: payment → CONFIRMED
                        • booking-service: idempotency check → bookings → CONFIRMED
                          → Kafka booking.slot.confirmed (kèm danh sách slotId)
                        • court-service: mỗi time_slot → RESERVED + set booking_id (HEADER)
                        • notification-service: gửi email + push

18.   System          Redirect "Đặt lịch thành công"
                        • bookingId, tên CLB, danh sách (sân + giờ), tổng tiền
```

---

## 6. Alternative Flows

### Alt-A: User đổi ngày (Bước 5)
```
5a.1  User chọn ngày khác trên date picker
5a.2  System clear toàn bộ selection, bottom bar reset "0h00 | 0 đ", TIẾP THEO disabled
5a.3  GET /api/clubs/:clubId/slots?date=<newDate> → re-render grid (giá có thể đổi WEEKDAY↔WEEKEND)
      → Quay lại bước 7
```

### Alt-B: User bỏ chọn ô (Bước 7)
```
7b.1  User click lại ô đang chọn → ô về AVAILABLE
7b.2  Bottom bar trừ đi giờ + tiền của ô đó (live)
7b.3  Nếu không còn ô nào → "0h00 | 0 đ" → TIẾP THEO disabled
```

### Alt-C: User quay lại từ form xác nhận (Bước 10)
```
10c.1 User nhấn "←" → về grid; selection cũ vẫn giữ highlight; bottom bar nguyên giá trị
      → Quay lại bước 7
```

### Alt-D: User sửa tên/SĐT (đặt hộ) (Bước 10)
```
10d.1 Tên/SĐT pre-fill từ profile → user sửa lại (đặt hộ người khác)
10d.2 Validate: tên không rỗng, SĐT đúng định dạng (+84 + 9 số)
      → Tiếp tục bước 12
```

### Alt-E: User thêm dịch vụ phụ (Bước 11)
```
11e.1 Nhấn "Thêm dịch vụ" → court-service trả danh sách dịch vụ của CLB
11e.2 Chọn dịch vụ + số lượng → tổng tiền cộng thêm
      (Lưu ý: phí dịch vụ KHÔNG nằm trong booking_items 30'; cộng ở header total_price)
```

---

## 7. Exception Flows

### Exc-1: Một ô bị chiếm khi đang xử lý (Race Condition) — huỷ nguyên đơn
```
13e.1 Redis SETNX lock:slot:{slotId} thất bại cho ÍT NHẤT 1 ô trong đơn
13e.2 booking-service rollback @Transactional + release mọi lock đã giữ
13e.3 Trả về 409 CONFLICT (kèm slotId/giờ bị chiếm)
13e.4 Hiển thị: "Ô Sân 2 10:30–11:00 vừa được người khác đặt. Vui lòng chọn lại."
13e.5 Reload grid — ô đó đổi màu RESERVED
      → Quay lại bước 7
```

### Exc-2: Click ô không khả dụng (Bước 7)
```
7e.1  Click ô RESERVED (đỏ) hoặc BLOCKED (xám) → không xử lý; tooltip "Đã đặt"/"Đang khoá"
```

### Exc-3: Click ô EVENT (Bước 7)
```
7e.1  Click ô tím (status=EVENT, có event_id) → tooltip thông tin sự kiện
7e.2  Không chọn để đặt được; gợi ý "Chuyển sang Đặt lịch sự kiện để mua vé"
```

### Exc-4: Validate form thất bại (Bước 12)
```
12e.1 Tên rỗng / SĐT sai định dạng → highlight đỏ + message, KHÔNG gọi API
```

### Exc-5: Thanh toán thất bại (EXPIRED hoặc REJECTED) — huỷ nguyên đơn
```
16e.1 Proof không upload kịp 10 phút → EXPIRED; hoặc STAFF REJECT (sao kê không khớp)
16e.2 payment-service: payment → EXPIRED
16e.3 booking-service compensate: bookings → CANCELLED
16e.4 court-service compensate: TẤT CẢ slot trong đơn → AVAILABLE (xoá booking_id)
16e.5 Mọi Redis lock release
16e.6 "Thanh toán không được xác nhận. Các ô đã trả lại." → nút "Thử lại" về form (bước 10)
```

### Exc-6: Lỗi mạng / server timeout
```
*e.1  API fail (network/5xx) → toast "Có lỗi xảy ra. Vui lòng thử lại." + cho retry
```

---

## 8. Business Rules

| ID | Rule |
|---|---|
| BR-01 | Chỉ user đã **đăng nhập** + `is_email_verified=true` mới được đặt lịch (rule #10) |
| BR-02 | Không đặt ô trong **quá khứ** (date < today hoặc giờ đã qua) |
| BR-03 | Đơn vị tối thiểu = **1 ô 30'** (= 1 `time_slot` = 1 `booking_item`) |
| BR-04 | 1 đơn (`bookings`) có thể gồm **nhiều ô / nhiều sân** của **cùng 1 CLB**, **1 thanh toán** |
| BR-05 | Ô **EVENT** (tím) không thể chọn qua flow này |
| BR-06 | Redis lock `lock:slot:{slotId}` TTL **5 giây**; khoá **TẤT CẢ** ô trong 1 transaction — 1 ô fail → rollback cả đơn |
| BR-07 | **Tổng tiền** = Σ `booking_items.price` (tra `court_pricing_rules`: club+sport × WEEKDAY/WEEKEND × khung giờ × `customer_type`) + phí dịch vụ phụ |
| BR-08 | `customer_type` mặc định = **WALK_IN** (đặt qua app = khách vãng lai); FIXED dành cho khách cố định (STAFF tạo) |
| BR-09 | Giá là **snapshot** vào `booking_items.price` lúc đặt — không đọc live về sau (đổi giá CLB không ảnh hưởng đơn cũ) |
| BR-10 | Chính sách hoàn huỷ tính theo `bookings.earliest_start_time`: >24h=100% · 2–24h=50% · <2h=0% × `total_price` |
| BR-11 | Tên/SĐT **pre-fill** từ profile, được phép sửa (đặt hộ) |

---

## 9. Sequence Diagram

```mermaid
sequenceDiagram
    actor U as User
    participant FE as Frontend
    participant GW as API Gateway
    participant CS as court-service
    participant BS as booking-service
    participant PS as payment-service
    participant ST as STAFF (Admin Panel)
    participant NS as notification-service
    participant RD as Redis

    U->>FE: Nhấn "ĐẶT LỊCH" trên trang CLB
    FE->>U: Modal "Chọn hình thức đặt"
    U->>FE: Chọn "Đặt lịch ngày trực quan" →

    FE->>GW: GET /api/clubs/:clubId/slots?date=2026-06-05&sport=PICKLEBALL
    GW->>CS: Route → court-service
    CS-->>FE: Sân[] + time_slots 30' (AVAILABLE|RESERVED|BLOCKED|EVENT) + price/ô
    FE->>U: Render grid (hàng=Sân 1..N, cột=5:00–22:00 bước 30')

    U->>FE: Click các ô 30' (Sân 2: 10:00–11:30)
    FE->>U: Highlight + gộp block; bottom bar "1h30 | 120.000 đ" (Σ giá ô)

    U->>FE: Nhấn "TIẾP THEO"
    FE->>GW: GET /api/clubs/:clubId (thông tin CLB)
    GW->>CS: clubs.name + address
    CS-->>FE: { name, address, sport }
    FE->>U: Form xác nhận (CLB + booking_items gộp + tên/SĐT pre-fill)

    U->>FE: Nhấn "XÁC NHẬN & THANH TOÁN"
    FE->>GW: POST /api/bookings { clubId, date, items:[{courtId,slotId}...], name, phone, note }
    GW->>BS: Route → booking-service

    BS->>RD: SETNX lock:slot:{slotId} TTL=5s (cho TỪNG ô)
    alt Tất cả lock OK
        RD-->>BS: OK
        BS->>CS: Verify mọi slot AVAILABLE + lấy giá (court_pricing_rules)
        CS-->>BS: ✅ AVAILABLE + price[]
        BS->>BS: Tạo bookings(HEADER, PENDING) + N booking_items (snapshot price)
        BS->>PS: POST /api/payments/initiate { bookingId, amount=total_price }
        PS-->>BS: { paymentId, orderCode, qrImageUrl, amount, expiresAt }
        BS-->>FE: { bookingId, paymentId, orderCode, bankName, accountNumber, qrImageUrl, expiresAt }
        FE->>U: Màn hình QR + countdown 10 phút
    else Có ô fail lock (race)
        RD-->>BS: FAIL
        BS->>RD: Release các lock đã giữ
        BS-->>FE: 409 Conflict (slotId bị chiếm)
        FE->>U: "Ô vừa bị đặt — chọn lại"; reload grid
    end

    U->>U: Chuyển khoản + chụp ảnh proof
    U->>GW: POST /api/payments/{id}/proof (multipart)
    GW->>PS: Upload → Cloudinary → PROOF_SUBMITTED
    PS->>NS: Kafka payment.proof.submitted → 🔔 STAFF "New proof #orderCode"

    ST->>GW: POST /api/payments/{id}/confirm
    GW->>PS: payment → CONFIRMED
    PS->>BS: Kafka payment.player.confirmed
    BS->>BS: Idempotency check → bookings → CONFIRMED
    BS->>CS: Kafka booking.slot.confirmed (slotIds[])
    CS->>CS: Mỗi time_slot → RESERVED + set booking_id
    BS->>NS: Kafka booking.confirmed
    NS->>U: 📧 Email + 🔔 Push

    FE->>U: "Đặt lịch thành công 🎉" (bookingId, sân, ngày, tổng tiền)
```

---

## 10. Activity Diagram

```mermaid
flowchart TD
    A([Start: User nhấn ĐẶT LỊCH trên trang CLB]) --> B[Modal Chọn hình thức đặt]
    B --> C{Chọn hình thức}
    C -->|Đặt lịch ngày trực quan| D
    C -->|Đặt lịch sự kiện| Z([→ UC-BOOKING-02])

    D[GET /api/clubs/:clubId/slots → grid Sân×giờ 30'\nDate picker = hôm nay]
    D --> E{Action trên grid}

    E -->|Đổi ngày| F[Reload slots — clear selection — bottom bar 0]
    F --> E
    E -->|Click ô AVAILABLE| G[Highlight + gộp block\nbottom bar += giá ô\nTIẾP THEO enabled]
    E -->|Click ô đang chọn| H[Bỏ chọn — bottom bar trừ]
    H --> E
    E -->|Click RESERVED/BLOCKED| I[Tooltip — không chọn]
    I --> E
    E -->|Click EVENT| J[Tooltip sự kiện — không đặt]
    J --> E

    G --> K{Nhấn TIẾP THEO?}
    K -->|Chưa| E
    K -->|Có| L[Form xác nhận:\nCLB + booking_items gộp + giá\nTên/SĐT pre-fill + ghi chú]

    L --> O[Kiểm tra thông tin]
    O --> P{Validate OK?}
    P -->|Tên/SĐT lỗi| Q[Highlight lỗi]
    Q --> O
    P -->|OK| R[Nhấn XÁC NHẬN & THANH TOÁN]

    R --> S[POST /api/bookings (header + items)]
    S --> T{Khoá TẤT CẢ slot OK?}
    T -->|1 ô fail — Race| U[Rollback + release\n409 Conflict — reload grid]
    U --> E
    T -->|OK| V[bookings PENDING + booking_items\nMàn hình QR ngân hàng + countdown 10']
    V --> W[User chuyển khoản + upload proof]
    W --> X{STAFF confirm?}
    X -->|EXPIRED/REJECTED| Y[payment EXPIRED\nbookings CANCELLED\nTẤT CẢ slot → AVAILABLE]
    Y --> L
    X -->|Confirm| AA[payment CONFIRMED]
    AA --> BB[bookings CONFIRMED\nmỗi slot → RESERVED + booking_id]
    BB --> CC[Email + Push]
    CC --> DD([End: Đặt lịch thành công 🎉])
```

---

## 11. UI Screens — Thực tế từ ảnh

### Screen 1 — Timeline Grid (datlich.alobo.vn/userBooking)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ←   Đặt lịch ngày trực quan — An Bình Pickleball     [05/06/2026  📅]      │
├─────────────────────────────────────────────────────────────────────────────┤
│  ■ Trống  ■ Đã đặt  ■ Khoá  ! Sự kiện          Xem sân & bảng giá          │
├─────────────────────────────────────────────────────────────────────────────┤
│  Time →  5:00 5:30 6:00 ... 10:00 10:30 11:00 11:30 ... 21:30 22:00        │
│  Sân 1   [    trống    ] [                              trống              ]│
│  Sân 2   [    trống    ] [██10:00██|██10:30██|██11:00██] [   trống         ]│  ← 3 ô 30' đã chọn
│  Sân 3   [    trống    ] [                              trống              ]│
│  Sân 4   [    trống    ] [        ...        ██19:00██|██19:30██  ...       ]│
│  Sân 5   [  ██ĐÃ ĐẶT██ ] [                              trống              ]│
├─────────────────────────────────────────────────────────────────────────────┤
│  (dark green) Tổng giờ: 2h30                         Tổng tiền: 210.000 đ  │
│  ╔═══════════════════════════════ TIẾP THEO ═══════════════════════════════╗ │
└─────────────────────────────────────────────────────────────────────────────┘

Mỗi ô = 1 time_slot 30'. Block hiển thị = các ô liền kề cùng sân được gộp.
Màu ô: AVAILABLE→Trắng · SELECTED→Xanh lá+viền · RESERVED→Đỏ "Đã đặt"
        BLOCKED→Xám "Khoá" · EVENT→Tím "Sự kiện" (chỉ xem)
```

### Screen 2 — Form Xác Nhận

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  🏟 Thông tin CLB    Tên CLB: An Bình Pickleball                             │
│                      Địa chỉ: 12/15 Kha Vạn Cân, P.Dĩ An, Tp.HCM            │
├─────────────────────────────────────────────────────────────────────────────┤
│  📋 Thông tin lịch đặt   Ngày: 05/06/2026                                    │
│  - Sân 2: 10:00 - 11:30  |  120.000 đ        ← gộp 3 booking_items          │
│  - Sân 4: 19:00 - 20:00  |   90.000 đ        ← gộp 2 booking_items          │
│  Đối tượng: Pickleball   Tổng giờ: 2h30   Tổng tiền: 210.000 đ              │
├─────────────────────────────────────────────────────────────────────────────┤
│  TÊN CỦA BẠN [Phúc ✕]   SỐ ĐIỆN THOẠI [🇻🇳 +84 | 399158632 ✕]              │
│  GHI CHÚ CHO CHỦ SÂN [______________________________________]                │
│  ╔══════════════════════ XÁC NHẬN & THANH TOÁN ═══════════════════════════╗ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Backend Services Involved

| Service | Trách nhiệm |
|---|---|
| `court-service` | Sở hữu `clubs`/`courts`(Sân)/`court_pricing_rules`/`time_slots`(30')/`club_reviews`; trả grid + giá; cập nhật slot status |
| `booking-service` | Tạo **`bookings` header + `booking_items`**, lock toàn bộ slot, idempotency guard, chính sách huỷ |
| `payment-service` | 1 PENDING payment cho cả đơn (amount=total_price), bank QR, proof, STAFF confirm, queue refund |
| `notification-service` | Email + push sau khi booking confirmed |
| **Redis** | `lock:slot:{slotId}` TTL 5s — khoá mọi ô trong đơn |
| **Kafka** | `payment.player.confirmed` → `booking.slot.confirmed` → `booking.confirmed` · `payment.proof.submitted` → STAFF |

---

## 13. API Endpoints

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/clubs/:clubId/slots?date=&sport=` | Toàn bộ **time_slots 30'** của mọi **sân** trong CLB theo ngày + giá mỗi ô |
| `GET` | `/api/clubs/:clubId` | Thông tin CLB: `clubs.name`, `address`, sport |
| `GET` | `/api/clubs/:clubId/pricing?sport=` | Bảng giá `court_pricing_rules` (WEEKDAY/WEEKEND × khung giờ × FIXED/WALK_IN) |
| `GET` | `/api/clubs/:clubId/services` | Danh sách dịch vụ phụ (bóng, vợt...) |
| `POST` | `/api/bookings` | Tạo **booking header + booking_items** + initiate payment → bank QR info |
| `POST` | `/api/payments/initiate` | Tạo payment (`payment_type=BOOKING`) + trả { orderCode, bank QR, expiresAt } |
| `POST` | `/api/payments/{id}/proof` | User upload ảnh proof chuyển khoản |
| `POST` | `/api/payments/{id}/confirm` | STAFF xác nhận sau khi đối chiếu sao kê |
| `GET` | `/api/payments/pending-proofs` | STAFF xem danh sách PROOF_SUBMITTED chờ duyệt |
| `GET` | `/api/bank-accounts` | TK ngân hàng active để hiển thị QR |

> **Routing**: `/api/clubs/**` và `/api/courts/**` đều `lb://court-service`; `/api/bookings/**` → `booking-service`;
> `/api/payments/**`, `/api/bank-accounts/**` → `payment-service`.
