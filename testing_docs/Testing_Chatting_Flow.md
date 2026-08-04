# TESTING_CHATTING_FLOW.md — Test tay luồng Chat hỗ trợ (Khách ↔ STAFF) qua Frontend

Runbook **bấm tay trên FE** để kiểm tra trọn vẹn feature chat real-time hỗ trợ khách hàng: 1 happy path
(khách ↔ STAFF nhắn tin tức thì) + các kịch bản (ảnh, typing, tick 3 trạng thái, claim/transfer/release/close,
reconnect + history-sync) + các cổng bảo mật **§G**. Mỗi bước ghi rõ **bấm gì → mong đợi gì → kiểm chứng ở đâu**
(trạng thái FE + STOMP frame trên DevTools + RabbitMQ/Mongo khi cần).

> Spec luồng đầy đủ: `../usecase/UC_Chatting.md` (đặc biệt **§G** production + **§G.10** go-live). Cổng/port:
> `architecture.md` + `eureka-config.md`. Đây là tài liệu **QA tay**, không phải test tự động
> (test tự động = 21 unit + 22 IT, chạy `mvn -pl chat-service verify`).

**Kiến trúc rút gọn** (nguồn sự thật để đối chiếu hành vi):
- **REST persist + STOMP push (hybrid)**: gửi tin đi qua **REST** (1 đường authz/validate/persist Mongo);
  WebSocket **chỉ** fan-out + typing + delivered ACK. Broker chết → REST vẫn 201, người nhận lấy qua history-sync.
- **Transport = STOMP over WebSocket** qua gateway `/ws` (PUBLIC ở gateway; auth ở **CONNECT frame**, không phải handshake).
- **Broker = RabbitMQ STOMP relay** (`CHAT_BROKER_RELAY=true`) → fan-out cross-instance, không sticky.
- **3-state tick**: `sent ✓` → `delivered ✓✓` → `read ✓✓ (xanh)` — suy từ timestamp, không có cột status.
- **Mô hình**: per-staff private inbox. `OPEN` (chưa gán, ở hàng đợi) → `claim` → `ASSIGNED` → `CLOSED`;
  có `transfer` (chuyển STAFF) / `release` (nhả về hàng đợi).

---

## A. Chuẩn bị môi trường (1 lần)

### A1. Hạ tầng
```bash
cd /Users/phucnguyen/ClaudeCodeProjects/badmintonHub
docker compose up -d        # cần: mongodb-chat :27018, rabbitmq (STOMP :61613 + UI :15672), redis
```
Kiểm tra 2 container **bắt buộc** cho chat đang UP:
```bash
docker compose ps mongodb-chat rabbitmq redis
```
- **RabbitMQ Management UI** `http://localhost:15672` (user/pass mặc định `badminton` / `badminton`) → đăng nhập được = broker sống.
  (Dùng để soi STOMP relay ở Mục F.)
- Chat **KHÔNG cần** Kafka / court / booking / payment (chat độc lập — chỉ Mongo + Redis + RabbitMQ).

### A2. `.env` — biến cho chat
File `.env` ở **gốc repo** (gitignored — copy từ `.env.example` nếu chưa có). Đảm bảo có:
```dotenv
JWT_SECRET=<chạy: openssl rand -hex 64>     # BẮT BUỘC — chat-service fail-fast nếu thiếu
MONGODB_CHAT_URI=mongodb://localhost:27018/chat_db   # RIÊNG — KHÔNG dùng chung MONGODB_URI (:27017)
RABBITMQ_HOST=localhost
RABBITMQ_STOMP_PORT=61613
RABBITMQ_USER=badminton                     # PHẢI khớp docker-compose (guest bị chặn cross-container)
RABBITMQ_PASS=badminton
CHAT_BROKER_RELAY=true                       # true = RabbitMQ relay (mặc định). false = simple-broker in-memory 1 instance
FRONTEND_URL=http://localhost:5173           # = origin cho WS handshake CORS (§G.5)
# Để TRỐNG (dev fallback tự động):
CLOUDINARY_CLOUD_NAME=                        # trống → ảnh lưu URL local-fallback://chat/… (FE hiện "[Ảnh]", không load được — bình thường ở dev)
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
```
> ⚠️ Không cần chỉnh window/timeout gì cả — chat **không có đồng hồ hết hạn** như booking. Thứ duy nhất
> "hết hạn" là **JWT access token 15'** (dùng cho kịch bản §G.2 token-exp ở Mục E, tuỳ chọn nâng cao).

### A3. Chạy service (mỗi cái 1 terminal, **từ gốc repo**, đúng thứ tự) — chỉ **4 service**
```bash
mvn -pl eureka-server   spring-boot:run     # 8761  ← chạy trước
mvn -pl api-gateway     spring-boot:run     # 3000  (route /api/chat/** + /ws/** → chat-service)
mvn -pl user-service    spring-boot:run     # 3001  (đăng ký/đăng nhập → JWT)
mvn -pl chat-service    spring-boot:run     # 3011  ← Mongo chat_db + STOMP + RabbitMQ relay
```
- Chờ cả 4 hiện **UP** trên Eureka dashboard `http://localhost:8761`.
- Log chat-service khi boot phải thấy: `ChatIndexInitializer` tạo index Mongo + kết nối RabbitMQ relay
  **KHÔNG lỗi** (nếu `Failed to connect to broker` → RabbitMQ chưa up hoặc sai user/pass ⇒ quay lại A1/A2).

### A4. Frontend
```bash
cd frontend && npm install && npm run dev   # http://localhost:5173
```
- `VITE_API_URL=http://localhost:3000` (đã có sẵn). WS chat tự suy = `ws://localhost:3000/ws` (không cần set
  `VITE_CHAT_WS_URL` trừ khi muốn override).

---

## B. Chuẩn bị tài khoản

Chat cần **2 vai**: **Khách** (USER hoặc COACH) và **STAFF**. Muốn test **transfer** thì cần **STAFF thứ 2**.

### B1. Khách (USER) — **không** cần verify email
1. FE `/register` → đăng ký (vd `customer@test.local`) → **đăng nhập**.
2. ✅ Chat mở được ngay — endpoint `POST /api/chat/conversations` chỉ đòi `hasAnyRole('USER','COACH')`,
   **KHÔNG** đòi `email_verified` (khác booking). Đăng ký + login là đủ.
   *(COACH cũng là "khách" của chat — thấy popup 💬. STAFF/ADMIN **không** thấy popup, mà dùng console ở /admin.)*

### B2. STAFF — để nhận/trả lời/transfer/release/close
1. Đăng ký tài khoản thứ 2 qua FE `/register` (vd `staff@test.local`). *(Script chỉ **thêm role**, không tạo
   tài khoản → phải đăng ký trước.)*
2. **Sửa email** trong `scripts/promote-staff.sql` (mặc định `staff@test.local`) rồi chạy **1 trong 3 cách**
   (file là SQL thuần):

   | Cách | Lệnh |
   |---|---|
   | **DataGrip** | Mở console nối **`user_db@localhost`** → mở/paste `scripts/promote-staff.sql` → chọn hết → **Run** ▶ |
   | **psql CLI** | `psql "postgresql://postgres:postgres@localhost:5441/user_db" -f scripts/promote-staff.sql` |
   | **docker exec** | `docker exec -i postgres-user psql -U postgres -d user_db < scripts/promote-staff.sql` |

   > ⚠️ DB **`user_db`** (cổng **5441**), chứa bảng `users`/`roles`. Query cuối in ra `roles = {STAFF,USER}`.
3. **Đăng xuất → đăng nhập lại** trên FE → vào `/admin` → có tab **"Hỗ trợ"**.

### B3. (Tuỳ chọn) STAFF thứ 2 — chỉ cần khi test **transfer**
1. Đăng ký `staff2@test.local` → promote STAFF như B2 (sửa email trong script).
2. **Lấy `userId` của staff2** (transfer nhập ID nhân viên qua `window.prompt`):
   ```sql
   -- console user_db@localhost
   SELECT id, email FROM users WHERE email = 'staff2@test.local';
   ```
   Copy cột `id` (UUID) — sẽ dán vào prompt khi bấm **Chuyển** ở Mục D.

> Mẹo: dùng **2–3 trình duyệt/cửa sổ ẩn danh** — 1 đăng nhập Khách, 1 STAFF, (1 STAFF2) — để không phải
> login qua login lại. Chat real-time chỉ "thấy đã" khi 2 vai mở song song 2 cửa sổ.

---

## C. Happy path → khách ↔ STAFF nhắn tin tức thì (bấm hết trên FE)

> Cửa sổ **A = Khách** (đã login USER). Cửa sổ **B = STAFF** (đã login, đang ở `/admin` → tab **Hỗ trợ**).

| # | Bấm | Ở đâu | Mong đợi | Kiểm chứng |
|---|---|---|---|---|
| 1 | Bấm bong bóng **💬** góc phải-dưới | A (Khách) | Popup "Hỗ trợ khách hàng" mở; thread được tạo lần đầu | `POST /api/chat/conversations` → **201** (lần đầu) / 200 (mở lại). DevTools A: WS `/ws` **CONNECT** thành công |
| 2 | Gõ 1 tin (vd "Cho hỏi sân trống…") → **Gửi** | A | Tin hiện ngay bên phải, tick **✓ (sent)** | `POST /api/chat/conversations/{id}/messages` → 201. Mongo `messages` có bản ghi |
| 3 | (tự động) | B (STAFF) | Thread nhảy vào **"Hàng đợi chưa gán"** (live, không cần F5) | STAFF subscribe `/topic/staff.queue` → list refetch. DevTools B: có frame MESSAGE tới `/topic/staff.queue` |
| 4 | Bấm **Nhận** trên thread ở hàng đợi | B | Thread rời hàng đợi → vào **"Của tôi"**, mở ChatThread bên phải | `POST /{id}/claim` → status `ASSIGNED`, `assignedStaffId` = STAFF. 409 nếu ai đó nhận trước (atomic) |
| 5 | STAFF gõ trả lời → **Gửi** | B | Khách (A) thấy tin **tức thì** (không F5) | `POST /{id}/messages` (STAFF) → push `/user/queue/messages` tới Khách. Popup A hiện tin bên trái |
| 6 | (Khách đang mở popup) | A | Tin STAFF vào ⇒ **tự đánh dấu đã đọc** | FE gọi `PATCH /{id}/read` → STAFF thấy tick tin mình chuyển **✓✓ xanh (read)** |
| 7 | Khách gõ tiếp (chưa gửi) | A | STAFF thấy **"đang nhập…"** | `/app/conv/{id}/typing` → push `/user/queue/typing`. Ngừng gõ → mất sau ~vài giây |
| 8 | Khách **gửi** tin thứ 2 khi STAFF **không** mở đúng thread | A | Ở "Của tôi", dòng thread hiện **badge số chưa đọc** đỏ | `staffUnread` tăng; mở thread → `PATCH /read` → badge về 0 |

✅ **Happy path xong** = 2 chiều nhắn tin real-time + tick 3 trạng thái chạy đúng + typing + unread badge.

---

## D. Các kịch bản chức năng

> Mỗi kịch bản có thể dùng lại cùng 1 thread (chat không "dùng 1 lần" như đơn booking).

### D1. Gửi ảnh (UC-CHAT-04)
1. (A hoặc B) trong ChatThread → bấm nút đính kèm ảnh → chọn 1 ảnh **png/jpeg/webp ≤ 5MB** → gửi.
2. **Mong đợi**: tin ảnh hiện inline (dev không có Cloudinary → URL `local-fallback://` **không load** →
   `onError` hiển thị **"[Ảnh]"** — đây là hành vi đúng ở dev, không phải bug).
3. **Kiểm chứng**: `POST /{id}/images` (multipart) → 201. Mongo `messages` có `type=IMAGE` + `imageUrl`.
   (Có key Cloudinary thật thì ảnh load được.)

### D2. Tick 3 trạng thái (delivered vs read)
- **sent ✓**: vừa gửi, người kia **chưa** online/chưa nhận.
- **delivered ✓✓ (xám)**: client người nhận đang mở socket → nhận được → tự ACK `/app/conv/{id}/delivered`.
- **read ✓✓ (xanh)**: người nhận **mở đúng thread** → `PATCH /read` → đẩy `/user/queue/read` về người gửi.
- **Cách bày**: gửi khi người kia **đóng** app → chỉ `✓`. Người kia mở app (chưa mở thread) → `✓✓ xám`.
  Người kia mở đúng thread → `✓✓ xanh`.

### D3. Cuộn lên tải thêm (keyset history)
1. Gửi qua lại **> 30 tin** trong 1 thread.
2. Đóng/mở lại thread → chỉ tải 30 tin mới nhất; **cuộn lên đầu** → tự tải thêm 30 tin cũ hơn.
3. **Kiểm chứng**: `GET /{id}/messages?before=<ObjectId tin cũ nhất>&limit=30` (keyset, **không** dùng `skip`/offset).

### D4. Transfer — chuyển thread cho STAFF khác (§G.7)
> Cần **STAFF2** + `userId` của nó (Mục B3).
1. (B, STAFF1) chọn 1 thread trong "Của tôi" → bấm **Chuyển** → dán **userId STAFF2** vào prompt → OK.
2. **Mong đợi**: thread rời "Của tôi" của STAFF1; xuất hiện ở "Của tôi" của **STAFF2** (cửa sổ STAFF2 live).
3. **Kiểm chứng**: `POST /{id}/transfer` body `{toStaffId}` → `assignedStaffId` = STAFF2. Push `/user/queue/inbox` tới STAFF2.

### D5. Release — nhả về hàng đợi (§G.7)
1. (B) chọn thread "Của tôi" → **Nhả**.
2. **Mong đợi**: thread về **"Hàng đợi chưa gán"** (mọi STAFF thấy), `assignedStaffId=null`, status `OPEN`.
3. **Kiểm chứng**: `POST /{id}/release` → broadcast `/topic/staff.queue`.

### D6. Close — đóng thread
1. (B) chọn thread → **Đóng**.
2. **Mong đợi**: status `CLOSED`, rời "Của tôi". Khách mở popup lại + gửi tin mới → **mở lại thread mới** (ensure-open).
3. **Kiểm chứng**: `POST /{id}/close` → status `CLOSED`. Khách `POST /conversations` sau đó → tạo/mở thread OPEN mới.

### D7. Reconnect + history-sync (mất mạng giữa chừng)
1. Đang chat, tắt Wi-Fi / kill RabbitMQ (`docker compose stop rabbitmq`) vài giây → bật lại (`start`).
2. Trong lúc broker chết, **vẫn gửi được tin** (REST persist) — chỉ **không** push real-time.
3. **Mong đợi**: socket tự reconnect (`reconnectDelay 3s`), `onConnect` **re-subscribe** + **history-sync** →
   các tin gửi lúc offline hiện đủ (kéo từ Mongo qua REST), không mất tin.
4. **Kiểm chứng**: DevTools → WS đóng rồi mở lại; danh sách tin đầy đủ sau khi nối lại.

### D8. Người nhận offline → nhận khi quay lại
1. STAFF **đăng xuất/đóng tab**. Khách gửi vài tin.
2. STAFF đăng nhập lại → mở tab Hỗ trợ → thread có badge chưa đọc + đủ tin (lưu Mongo, kéo qua REST history).
3. **Mong đợi**: **không** mất tin (chat không phụ thuộc notification/offline-push — chỉ mất cú ping, không mất tin).

---

## E. Kiểm tra bảo mật / phân quyền (§G) — nên chạy trước khi "go-live"

> Đây là phần khác biệt lớn so với booking. Chat mở WS public ở gateway nên **auth/authz dồn vào STOMP**.

### E1. USER **không** được nghe hàng đợi STAFF (§G.1 authz SUBSCRIBE)
- Xác nhận trên FE: đăng nhập **Khách** → **không** có tab/console STAFF, popup khách **không** hiển thị hàng đợi.
- Sâu hơn (DevTools/console): nếu 1 USER cố `SUBSCRIBE /topic/staff.queue` → server ném ERROR frame
  ("Only STAFF/ADMIN may subscribe to the staff queue"). *(Đã có IT tự động `ChatWebSocketIT` chứng minh.)*

### E2. Không thể spoof tin của người khác (§G.1 authz SEND)
- Mọi `SEND /app/conv/{id}/**` (typing/delivered) bị `StompAuthChannelInterceptor` check **participant** —
  không phải người trong hội thoại → ERROR frame. (Gửi tin thật đi qua REST, cũng check ownership ở service.)

### E3. CONNECT không/kém token → bị từ chối (§G.1/§G.2)
- Token thiếu → CONNECT refuse ("CONNECT without a bearer token"). Token sai/hết hạn → "Invalid or expired token".
- **§G.2 token-exp mid-session** (nâng cao): access token 15' hết hạn giữa phiên → server đóng session
  (WS close **1008**) → FE `stompClient.ts` bắt 1008 → **silent-refresh** → reconnect (không cần login lại).
  *(Muốn ép nhanh: chỉnh TTL access token ngắn ở user-service rồi ngồi chờ — tuỳ chọn, không bắt buộc.)*

### E4. Validation nội dung + ảnh (§G.3/§G.4)
| Thử | Mong đợi |
|---|---|
| Gửi tin **> 2000 ký tự** | **400** (`@Size max=2000`) — FE chặn hoặc server trả 400 |
| Gửi tin rỗng/toàn khoảng trắng | **400** (`@NotBlank`) |
| Upload ảnh **> 5MB** | **413** (servlet multipart) — hoặc 400 nếu qua được tới `validateImage` |
| Upload file **không phải png/jpeg/webp** (vd `.gif`/`.pdf`) | **415/400** (MIME allowlist) |

### E5. Rate-limit (§G.4) → 429
- Gửi **> 30 tin text / cửa sổ** cùng 1 user → `429 RATE_LIMITED`. Upload **> 10 ảnh / cửa sổ** → 429.
- Redis chết → **fail-open** (cho qua, log warn) — không chặn nghẽn khách.
- **Kiểm chứng**: key Redis `rate_limit:chat:{userId}` và `rate_limit:chat:img:{userId}`
  (`redis-cli KEYS 'rate_limit:chat*'`).

### E6. Origin WS handshake = FRONTEND_URL (§G.5)
- `setAllowedOrigins(FRONTEND_URL)` — trang khác origin không bắt tay được WS. Đúng khi FE chạy `:5173`.

### E7. §G.10 #4 — Fan-out cross-instance, **không sticky** (runtime, nâng cao — chỉ verify khi cần scale)
> Đây là hạng mục §G.10 duy nhất chưa có test tự động (runtime-only).
1. Chạy **2 instance** chat-service ở **2 port khác nhau**:
   ```bash
   mvn -pl chat-service spring-boot:run                                   # 3011
   mvn -pl chat-service spring-boot:run -Dspring-boot.run.arguments=--server.port=3012   # 3012
   ```
   (Cả 2 cùng `CHAT_BROKER_RELAY=true` → cùng RabbitMQ relay.)
2. Cho Khách nối instance này, STAFF nối instance kia (qua gateway load-balance) → **vẫn nhắn thấy nhau**
   (fan-out qua RabbitMQ, `setUserRegistryBroadcast` — **không** cần sticky session).
3. **Kiểm chứng**: RabbitMQ UI `:15672` → tab **Connections** thấy cả 2 instance; tin vẫn tới đúng người.
   Alert (nếu có) đọc trên **log ERROR** (metrics chat chỉ expose `health,info`, chưa scrape).

---

## F. Bộ công cụ kiểm chứng & dọn dẹp

### Kiểm chứng
- **DevTools → Network → WS (`/ws`)** = soi STOMP frame trực tiếp: `CONNECT` / `SUBSCRIBE` / `MESSAGE` /
  `SEND` / `ERROR`. Đây là "Kafka UI" của chat.
- **RabbitMQ Management UI** `http://localhost:15672` (`badminton`/`badminton`) — tab **Connections/Channels**
  thấy STOMP relay khi chat-service chạy `relay=true`; **Exchanges** có `amq.topic` bindings cho `/topic`/`/queue`.
- **Mongo peek** (`chat_db` trong container `mongodb-chat`, cổng container 27017 / host 27018):
  ```bash
  docker exec -it mongodb-chat mongosh chat_db
  ```
  ```js
  db.conversations.find().sort({lastMessageAt:-1}).limit(5)   // status, assignedStaffId, customerUnread/staffUnread
  db.messages.find().sort({_id:-1}).limit(10)                 // type (TEXT/IMAGE), deliveredAt, readAt, clientMsgId
  db.conversations.getIndexes()                               // xác nhận partial-unique customerId + unique clientMsgId
  ```
- **Reconcile unread scheduler** (§G.10 #6): chạy **mỗi 5'** — log chat-service:
  `Unread reconcile: corrected counters on N of M active conversations`. Muốn ép lệch để thấy nó "heal":
  sửa tay `customerUnread`/`staffUnread` trong Mongo → chờ ≤5' → counter về đúng.
- **Redis** (rate-limit): `redis-cli KEYS 'rate_limit:chat*'`.

### Bảng tra nhanh trạng thái hội thoại
| Hành động | status | assignedStaffId | Ở đâu (STAFF console) |
|---|---|---|---|
| Khách mở + gửi tin đầu | `OPEN` | null | Hàng đợi chưa gán |
| STAFF **Nhận** (claim) | `ASSIGNED` | STAFF đó | "Của tôi" của STAFF |
| **Transfer** | `ASSIGNED` | STAFF mới | "Của tôi" của STAFF mới |
| **Release** (nhả) | `OPEN` | null | Hàng đợi chưa gán |
| **Close** (đóng) | `CLOSED` | (giữ) | Rời list; khách gửi lại → thread mới |

### Dọn dẹp
- Tắt 4 (hoặc 5) service: `pkill -f spring-boot:run` (hoặc Ctrl-C từng terminal).
- **Giữ** docker infra (`docker compose`) theo quy ước dự án. Xoá sạch dữ liệu chat để test lại từ đầu (tuỳ chọn):
  `docker exec -it mongodb-chat mongosh chat_db --eval "db.conversations.drop(); db.messages.drop()"`
  (index sẽ được `ChatIndexInitializer` tạo lại khi chat-service khởi động).

---

## Phụ lục — Fallback bằng `curl` (khi không tiện bấm FE)

Tất cả REST qua gateway `:3000`. Lấy token: `POST /api/auth/login` → field `accessToken` → đặt `-H "Authorization: Bearer <token>"`.
*(WebSocket/STOMP không test bằng curl được — dùng DevTools hoặc `wscat`/`websocat` nếu cần.)*

```bash
# Khách mở/tạo thread (idempotent) — trả về {id, status, ...}
curl -X POST http://localhost:3000/api/chat/conversations \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" -H "Content-Type: application/json" \
  -d '{"displayName":"Nguyen Van A"}'

# Khách gửi 1 tin text (clientMsgId để idempotent — gửi lại cùng id → 200, không nhân đôi)
curl -X POST http://localhost:3000/api/chat/conversations/<convId>/messages \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>" -H "Content-Type: application/json" \
  -d '{"clientMsgId":"'"$(uuidgen)"'","content":"Cho hỏi sân trống chủ nhật?","type":"TEXT"}'

# STAFF xem hàng đợi chưa gán + inbox của mình
curl -H "Authorization: Bearer <STAFF_TOKEN>" "http://localhost:3000/api/chat/conversations?queue=unassigned"
curl -H "Authorization: Bearer <STAFF_TOKEN>" "http://localhost:3000/api/chat/conversations"

# STAFF nhận (claim) thread
curl -X POST http://localhost:3000/api/chat/conversations/<convId>/claim \
  -H "Authorization: Bearer <STAFF_TOKEN>"

# STAFF trả lời
curl -X POST http://localhost:3000/api/chat/conversations/<convId>/messages \
  -H "Authorization: Bearer <STAFF_TOKEN>" -H "Content-Type: application/json" \
  -d '{"clientMsgId":"'"$(uuidgen)"'","content":"Còn 2 sân bạn nhé","type":"TEXT"}'

# Keyset history (30 tin mới nhất; thêm ?before=<ObjectId> để tải cũ hơn)
curl -H "Authorization: Bearer <STAFF_TOKEN>" \
  "http://localhost:3000/api/chat/conversations/<convId>/messages?limit=30"

# Đánh dấu đã đọc / transfer / release / close
curl -X PATCH http://localhost:3000/api/chat/conversations/<convId>/read     -H "Authorization: Bearer <STAFF_TOKEN>"
curl -X POST  http://localhost:3000/api/chat/conversations/<convId>/transfer -H "Authorization: Bearer <STAFF_TOKEN>" -H "Content-Type: application/json" -d '{"toStaffId":"<STAFF2_UUID>"}'
curl -X POST  http://localhost:3000/api/chat/conversations/<convId>/release  -H "Authorization: Bearer <STAFF_TOKEN>"
curl -X POST  http://localhost:3000/api/chat/conversations/<convId>/close    -H "Authorization: Bearer <STAFF_TOKEN>"

# Cổng bảo mật: USER gọi claim → 403 (chỉ STAFF/ADMIN)
curl -i -X POST http://localhost:3000/api/chat/conversations/<convId>/claim \
  -H "Authorization: Bearer <CUSTOMER_TOKEN>"    # mong đợi 403
```
