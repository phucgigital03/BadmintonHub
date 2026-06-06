# BadmintonHub — 4-Week Implementation Guide

> **Mục tiêu**: Build từ scratch đến complete trong 4 tuần với Claude Code.  
> **Rules**: `.claude/rules/` — đọc trước khi bắt đầu mỗi service.  
> **Nguyên tắc**: Mỗi ngày = 1 deliverable có thể chạy và test được.

---

## Tổng quan timeline

| Tuần | Trọng tâm | Output |
|---|---|---|
| **Week 1** | Foundation — infra, auth, court | eureka + gateway + user-service + court-service chạy end-to-end |
| **Week 2** | Booking core — slot, payment, escrow | Luồng đặt sân hoàn chỉnh với Bank QR + STAFF confirm |
| **Week 3** | Matchmaking + notifications + coaches | Saga join-match, Outbox, realtime slot counter, notifications |
| **Week 4** | Frontend + event-service + integration | UI hoàn chỉnh, toàn bộ luồng chạy từ browser |

---

## Week 1 — Foundation

### Day 1: Maven Multi-Module scaffold + infra

**Mục tiêu**: `docker-compose up` chạy được, tất cả 13 module compile.

Tasks:
- [ ] Tạo parent `pom.xml` (Java 21, Spring Boot 3.2.x, Spring Cloud 2023.x)
- [ ] Tạo 13 module rỗng: `common`, `eureka-server`, `api-gateway`, `user-service`, `court-service`, `booking-service`, `matchmaking-service`, `coach-service`, `payment-service`, `escrow-service`, `notification-service`, `event-service`, `ai-service`
- [ ] Tạo `docker-compose.yml`: PostgreSQL ×9, Redis 7, MongoDB 7, Kafka 3.6 + Zookeeper, Zipkin, Elasticsearch
- [ ] Mỗi service: `application.yml` cơ bản (port, datasource, kafka bootstrap, eureka client)
- [ ] Run: `docker-compose up -d && mvn clean install -DskipTests`

**Claude Code prompt mẫu**:
```
Tạo parent pom.xml cho Maven multi-module project BadmintonHub.
Java 21, Spring Boot 3.2.5, Spring Cloud 2023.0.1.
13 modules: common, eureka-server, api-gateway, user-service, court-service,
booking-service, matchmaking-service, coach-service, payment-service,
escrow-service, notification-service, event-service, ai-service.
Đọc .claude/rules/architecture.md và .claude/rules/eureka-config.md trước.
```

---

### Day 2: common module + eureka-server

**Mục tiêu**: `eureka-server` chạy tại http://localhost:8761.

Tasks:
- [ ] `common`: `BaseAuditEntity` (created_at, updated_at, @EntityListeners), `ApiException`, `ErrorResponse { code, message, timestamp }`, `PageResponse<T>`
- [ ] `eureka-server`: `@EnableEurekaServer`, `application.yml` (register-with-eureka: false, fetch-registry: false)
- [ ] Verify: http://localhost:8761 hiển thị Eureka dashboard, 0 instances registered

---

### Day 3: api-gateway

**Mục tiêu**: Gateway chạy port 3000, JWT filter hoạt động, route đến tất cả service qua `lb://`.

Tasks:
- [ ] Dependencies: `spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client`, `spring-boot-starter-data-redis`
- [ ] `application.yml`: tất cả 10 route với `lb://service-name` (đọc `.claude/rules/eureka-config.md`)
- [ ] `JwtAuthenticationFilter`: validate JWT → kiểm tra Redis blacklist `session:blacklist:{jti}` → forward `X-User-Id`, `X-User-Roles` headers
- [ ] `RateLimitFilter`: Redis counter `rate_limit:{userId}` TTL 60s, max 100 req/min
- [ ] Test: Gateway forward request → 503 (service chưa up = đúng, route đã config)

---

### Day 4: user-service — Auth core

**Mục tiêu**: Register → Email verify → Login → JWT token hoạt động.

Tasks:
- [ ] Entity: `User`, `Role`, `UserRole`, `AuditLog` — đọc `.claude/rules/database.md`
- [ ] `POST /api/auth/register`: hash password (BCrypt), assign ROLE_USER, gửi email verify qua SendGrid, lưu token Redis `email:verify:{token}` TTL 24h
- [ ] `GET /api/auth/verify-email?token=`: consume Redis token (single-use), set `is_email_verified=true`
- [ ] `POST /api/auth/login`: validate credentials → issue access token (15m, HS256) + refresh token (30d bcrypt hash, HttpOnly cookie)
- [ ] `POST /api/auth/refresh`: validate refresh token hash → issue new access token
- [ ] `POST /api/auth/logout`: blacklist `jti` vào Redis `session:blacklist:{jti}`
- [ ] Security config: Gateway header filter nhận `X-User-Id`, `X-User-Roles` (không re-validate JWT)
- [ ] Test: register → verify email → login → nhận JWT → call authenticated endpoint

---

### Day 5: user-service — OAuth2 + Profile + court-service scaffold

**Mục tiêu**: Google login hoạt động; court-service đăng ký Eureka.

Tasks:
- [ ] `POST /api/auth/google`: verify Google idToken → upsert user (match on email) → issue JWT
- [ ] `POST /api/auth/forgot-password` / `POST /api/auth/reset-password`: Redis token `password:reset:{token}` TTL 1h
- [ ] `GET /api/users/{id}`, `PATCH /api/users/{id}`: own resource guard (`@PreAuthorize`)
- [ ] `POST /api/upload/image`: Cloudinary upload → return URL
- [ ] court-service: scaffold entity `Court`, `TimeSlot`, `CourtReview` — Eureka client đăng ký thành công
- [ ] Verify Eureka dashboard: `user-service` + `court-service` appear as UP

---

### Week 1 Checkpoint

```bash
docker-compose up -d
mvn -pl eureka-server spring-boot:run &
mvn -pl api-gateway spring-boot:run &
mvn -pl user-service spring-boot:run &
mvn -pl court-service spring-boot:run &

# Test flows:
curl -X POST http://localhost:3000/api/auth/register -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'
# → 201, email sent
curl -X POST http://localhost:3000/api/auth/login -d '{"email":"test@test.com","password":"123456"}'
# → 200, JWT token
# Eureka: http://localhost:8761 → 4 services registered
```

---

## Week 2 — Booking Core

### Day 6: court-service — Courts + Slots

**Mục tiêu**: CRUD courts, slot auto-generation, geo search.

Tasks:
- [ ] `POST /api/courts` (STAFF/ADMIN): tạo court + Cloudinary ảnh
- [ ] `GET /api/courts?lat=&lng=&radius=&district=&type=&date=`: Redis cache `courts:{district}:{type}:{date}` TTL 60s
- [ ] Slot auto-gen scheduler `@Scheduled(cron = "0 0 0 * * *")`: generate 30 ngày cho tất cả active courts, 06:00–22:00, 60 phút/slot
- [ ] `POST /api/courts/{id}/generate-slots`: manual trigger (STAFF)
- [ ] `PATCH /courts/slots/{id}/block`: STAFF block slot
- [ ] `GET /api/courts/{id}/slots?date=`: list slots by date với status
- [ ] Test: tạo court → slots auto-gen → query by date

---

### Day 7: booking-service — Reservation + distributed lock

**Mục tiêu**: Book slot với Redis lock, không race condition.

Tasks:
- [ ] Entity: `Booking`, `ProcessedEvent` — idempotency guard
- [ ] `POST /api/bookings` (email verified only): acquire Redis lock `lock:slot:{slotId}` TTL 5s → check slot AVAILABLE → insert Booking PENDING → release lock
- [ ] Feign client: `CourtServiceClient` → `GET /api/courts/{courtId}/slots/{slotId}` để validate
- [ ] `@KafkaListener` trên `payment.player.confirmed`: idempotency check → update Booking → CONFIRMED, slot → RESERVED
- [ ] Cancellation policy: `PATCH /api/bookings/{id}/cancel` → tính refund % theo giờ
- [ ] Resilience4j circuit breaker cho Redis lock (fallback: DB lock) — đọc `.claude/rules/resilience.md`
- [ ] Test: 2 concurrent requests cùng slot → chỉ 1 thành công

---

### Day 8: payment-service — Bank QR flow

**Mục tiêu**: Toàn bộ payment flow PENDING → PROOF_SUBMITTED → CONFIRMED.

Tasks:
- [ ] Entity: `BankAccount`, `Payment`, `PaymentProof`, `ManualRefund`
- [ ] `POST /api/payments/initiate`: tạo payment PENDING, `order_code` sequential, lưu `payment:countdown:{paymentId}` Redis TTL 10min, trả về bank QR info
- [ ] `POST /api/payments/{id}/proof` (multipart): Cloudinary upload ảnh → insert `PaymentProofs` → payment PROOF_SUBMITTED → Kafka `payment.proof.submitted`
- [ ] `POST /api/payments/{id}/confirm` (STAFF): payment CONFIRMED → Kafka `payment.player.confirmed` hoặc `payment.host.confirmed`
- [ ] `POST /api/payments/{id}/reject` (STAFF): payment EXPIRED → Kafka `payment.player.expired`
- [ ] Scheduler `@Scheduled(fixedDelay = 60_000)`: PENDING payments past `expires_at` → EXPIRED → Kafka expired event
- [ ] Rate limit: `rate_limit:proof:{userId}` max 3 uploads/5min
- [ ] `GET /api/payments/pending-proofs` (STAFF): queue để review

---

### Day 9: escrow-service

**Mục tiêu**: Escrow hold → reimburse Host → settle Court Owner.

Tasks:
- [ ] Entity: `EscrowAccount`, `EscrowTransaction`, `ProcessedEvent`
- [ ] `@KafkaListener` trên `payment.host.confirmed`: tạo `EscrowAccount` status=HOLDING, amount=court_price — idempotency guard
- [ ] `@KafkaListener` trên `payment.player.confirmed`: log `PLAYER_REIMBURSEMENT` transaction — Host wallet balance update
- [ ] `@KafkaListener` trên `match.completed`: log `COURT_OWNER_SETTLEMENT` → STAFF manual action queue
- [ ] `@KafkaListener` trên `match.cancelled`: log `HOST_REFUND` + `PLAYER_REFUND` → STAFF action queue
- [ ] `POST /api/payments/{id}/refund` (STAFF): record `ManualRefund` in payment-service → Kafka `payment.refund.queued`
- [ ] Test: mock Kafka events → verify escrow_transactions ghi đúng

---

### Day 10: integration test Week 2 + court reviews

**Mục tiêu**: Luồng đặt sân hoàn chỉnh end-to-end.

Tasks:
- [ ] Court review: `POST /api/courts/{id}/reviews` (1 review per booking, chỉ booking COMPLETED)
- [ ] Integration test flow:
  1. STAFF tạo court → slots auto-gen
  2. User (email verified) book slot → payment PENDING, nhận bank QR
  3. User upload proof → PROOF_SUBMITTED
  4. STAFF confirm → payment CONFIRMED
  5. Booking → CONFIRMED, slot → RESERVED
  6. Kafka events propagated đúng
- [ ] Fix issues phát hiện từ integration test

---

### Week 2 Checkpoint

```
Luồng hoàn chỉnh chạy được:
User đăng ký → verify email → tìm court → xem slot → đặt slot
→ nhận bank QR + countdown → upload proof → STAFF confirm
→ booking CONFIRMED, slot blocked, escrow HOLDING
```

---

## Week 3 — Matchmaking + Notifications + Coaches

### Day 11: matchmaking-service — Match creation + Host payment

**Mục tiêu**: Host tạo match, trả tiền qua Bank QR, match → OPEN.

Tasks:
- [ ] Entity: `Match`, `MatchParticipant`, `OutboxEvent`
- [ ] `POST /api/matches`: acquire Redis lock `lock:slot:{slotId}:match_create` TTL 10min → tạo match PENDING_PAYMENT → snapshot `court_price` → tạo payment record
- [ ] **Outbox Pattern**: save `OutboxEvent` trong cùng `@Transactional` với Match — đọc `.claude/rules/kafka-patterns.md`
- [ ] `OutboxPublisherScheduler` `@Scheduled(fixedDelay = 3000)`: poll PENDING outbox → publish Kafka → mark SENT
- [ ] `@KafkaListener` trên `payment.host.confirmed`: **zombie check** (nếu match CANCELLED → compensate), match → OPEN, slot → RESERVED, Host auto-join
- [ ] Match timeout scheduler `@Scheduled(cron = "0 */5 * * * *")`: PENDING_PAYMENT > 10min → CANCELLED, release Redis lock
- [ ] Test: tạo match → payment → STAFF confirm → match OPEN

---

### Day 12: matchmaking-service — Player joins (Saga)

**Mục tiêu**: Saga join match với Redis atomic counter, compensating transactions.

Tasks:
- [ ] `POST /api/matches/{id}/join` (email verified, not STAFF/ADMIN):
  1. Acquire Redis lock `lock:match:{matchId}` TTL 5s
  2. `INCR match:{matchId}:slots` → check <= totalSlots (nếu vượt: DECR + throw MATCH_FULL)
  3. Rate limit `rate_limit:join:{userId}` max 5/min
  4. Save `OutboxEvent(topic="match.slot.joined")` + `MatchParticipant` trong 1 transaction
  5. Release lock
- [ ] `@KafkaListener` trên `booking.slot.confirmed`: **zombie check** → update match status (FULL nếu filled_slots == total_slots)
- [ ] `@KafkaListener` trên `payment.player.expired`: compensate → DECR slot counter, remove participant
- [ ] Socket.io: emit `slot-updated` event khi slot counter thay đổi
- [ ] `GET /api/matches?status=OPEN&date=&skillLevel=`: list open matches với filter

---

### Day 13: notification-service

**Mục tiêu**: Push và email notifications cho tất cả key events.

Tasks:
- [ ] MongoDB: `NotificationTemplate`, `NotificationHistory`
- [ ] Seed templates cho: `SLOT_JOINED`, `PAYMENT_CONFIRMED`, `MATCH_CANCELLED`, `EMAIL_VERIFY`, `PASSWORD_RESET`, `BOOKING_RECEIPT`
- [ ] `@KafkaListener` consumers:
  - `payment.proof.submitted` → push STAFF "có proof mới cần review"
  - `payment.host.confirmed` → push/email Host "match đã OPEN"
  - `payment.player.confirmed` → push/email Player "đã join thành công"
  - `match.cancelled` → push/email tất cả participants
  - `payment.refund.queued` → push/email user "refund đang xử lý"
- [ ] DLQ config: `DefaultErrorHandler` exponential backoff 2s/4s/8s, 3 retries, route to `.DLT`
- [ ] `GET /api/notifications` (own), `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`
- [ ] FCM token update: `PATCH /api/users/{id}/fcm-token`

---

### Day 14: coach-service

**Mục tiêu**: Coach profiles, Elasticsearch search, enrollment với payment.

Tasks:
- [ ] Entity: `Coach`, `CoachSchedule`, `CoachEnrollment`, `CoachReview`
- [ ] Elasticsearch sync: index coach khi create/update, search by `specialty`, `hourly_rate range`, `rating`, `district`
- [ ] `POST /api/coaches` (apply): COACH role → status PENDING_APPROVAL
- [ ] `PATCH /api/coaches/{id}/approve` (ADMIN): status → ACTIVE
- [ ] `POST /api/coaches/{id}/enroll` (USER): tạo payment PENDING → trả bank QR screen
- [ ] `@KafkaListener` trên `payment.player.confirmed` (type=COACH_ENROLLMENT): enrollment → CONFIRMED
- [ ] Coach review: `POST /api/coaches/{id}/reviews` (completed enrollment only), rate limit `rate_limit:review:{userId}` max 2/day
- [ ] `PATCH /api/coaches/{id}/suspend` (STAFF): status → SUSPENDED

---

### Day 15: Week 3 integration + waiting list + admin endpoints

**Mục tiêu**: Toàn bộ match flow end-to-end, admin có thể manage.

Tasks:
- [ ] Match cancel flow: `PATCH /api/matches/{id}/cancel` → Kafka `match.cancelled` → escrow refund queue → notifications
- [ ] Match complete flow: STAFF/ADMIN `PATCH /api/matches/{id}/complete` → `match.completed` → escrow settlement queue
- [ ] Admin Kafka DLQ replay: `POST /api/admin/kafka/replay?topic={topic.DLT}` (ADMIN only)
- [ ] Outbox cleanup: `@Scheduled(cron = "0 0 2 * * *")` delete SENT > 30 ngày
- [ ] ProcessedEvent cleanup: `@Scheduled(cron = "0 0 3 * * *")` delete > 7 ngày
- [ ] Integration test toàn bộ Saga join-match flow

---

### Week 3 Checkpoint

```
Full Saga flow:
Host tạo match → Bank QR → STAFF confirm → match OPEN (Socket.io broadcast)
Player join → Bank QR → STAFF confirm → slot counter INCR (Socket.io)
Match FULL → Host/Players nhận notification
STAFF complete match → escrow settlement queue

Coach flow:
User enroll coach → Bank QR → STAFF confirm → enrollment CONFIRMED
```

---

## Week 4 — Frontend + Event Service + Polish

### Day 16: Frontend scaffold + auth pages

**Mục tiêu**: React app chạy, auth flow hoàn chỉnh trên browser.

Tasks:
- [ ] Vite + React 18 + TypeScript + Tailwind CSS setup
- [ ] `axiosClient.ts`: base URL từ `VITE_API_URL`, request interceptor JWT, response interceptor silent refresh 401 — đọc `.claude/rules/frontend.md`
- [ ] Zustand stores: `authStore` (accessToken, user, setAuth, clearAuth)
- [ ] `LoginPage`, `RegisterPage`: React Hook Form + Zod validation
- [ ] `ProtectedRoute`, `RoleGuard` components
- [ ] React Router v6: public routes + protected routes
- [ ] i18n setup: `react-i18next`, `vi.json`, `en.json` cơ bản
- [ ] Test: register → verify email → login → redirect dashboard

---

### Day 17: Frontend — Courts + Visual Booking Grid

**Mục tiêu**: Tìm sân, xem slots, đặt sân trực tiếp.

Tasks:
- [ ] `CourtsPage`: search form (district, type, date), React Query, `react-leaflet` map với court pins
- [ ] `CourtDetailPage`: court info + `SlotGrid` component
- [ ] `SlotGrid`: visual grid theo thời gian, màu theo status (AVAILABLE/RESERVED/BLOCKED/EVENT) — slot colors từ `.claude/rules/frontend.md`
- [ ] Click AVAILABLE slot → open booking modal → `POST /api/bookings`
- [ ] `PaymentScreen` component: bank info, QR image, countdown timer, proof upload zone — props từ `PaymentScreenProps` interface
- [ ] `toast.success/error` với `react-hot-toast`
- [ ] Test: tìm sân → click slot → đặt → thấy payment screen → upload ảnh

---

### Day 18: Frontend — Matchmaking + Real-time

**Mục tiêu**: Tạo match, join match, thấy slot counter cập nhật real-time.

Tasks:
- [ ] `MatchesPage`: list open matches với filter (date, skill level)
- [ ] `MatchDetailPage`: match info + slot counter + join button
- [ ] `useMatchSocket(matchId)` hook: Socket.io `join-match-room` emit, `slot-updated` listener → update React Query cache — đọc `.claude/rules/frontend.md`
- [ ] Create match modal: form với React Hook Form + Zod, chọn sân/slot, sau submit → `PaymentScreen`
- [ ] Join match → `POST /api/matches/{id}/join` → `PaymentScreen` (price_per_person)
- [ ] `notificationStore` Zustand: badge count, `NotificationBell` component
- [ ] Test: 2 browser tabs join cùng match → slot counter cập nhật đồng thời real-time

---

### Day 19: Frontend — Admin panel + Coach pages

**Mục tiêu**: STAFF có thể confirm payment, manage matches; User tìm và enroll coach.

Tasks:
- [ ] `AdminPage` (STAFF/ADMIN only, `RoleGuard`):
  - Tab "Proof Review": list `GET /api/payments/pending-proofs`, view ảnh proof, Confirm/Reject buttons
  - Tab "Matches": list all matches với status filter, force cancel
  - Tab "Bookings": list all bookings
  - Tab "Refunds": manual refund form (bankName, accountNumber, accountName, amount)
- [ ] `CoachesPage`: search (specialty, hourly_rate, rating), Elasticsearch-powered
- [ ] `CoachDetailPage`: profile + schedule + enroll button → `PaymentScreen`
- [ ] `DashboardPage` (User): my bookings, my matches, my enrollments, my notifications
- [ ] Test: STAFF login → thấy pending proofs → confirm → user nhận notification

---

### Day 20: event-service + ai-service stub + final integration

**Mục tiêu**: event-service cơ bản hoạt động; toàn bộ luồng chạy từ browser đến database.

Tasks:
- [ ] event-service scaffold: Entity `Event`, `EventTicket` — state machine PENDING → CONFIRMED → CANCELLED
- [ ] `POST /api/events` (STAFF/ADMIN): tạo event, block time_slots (type=EVENT)
- [ ] `POST /api/events/{id}/tickets/purchase` (USER): tạo payment PENDING → Bank QR
- [ ] `@KafkaListener` payment confirmed → ticket CONFIRMED
- [ ] `EventsPage` frontend: list events, buy ticket → `PaymentScreen`
- [ ] ai-service: stub `GET /api/ai/chat` trả về static response (placeholder cho RAG sau)
- [ ] **End-to-end smoke test tất cả luồng**:
  - Court booking flow ✓
  - Match host + join flow ✓
  - Coach enrollment flow ✓
  - Event ticket purchase ✓
  - STAFF admin operations ✓
- [ ] Fix critical bugs từ smoke test

---

### Week 4 Checkpoint — Final Checklist

```bash
# Toàn bộ 13 services chạy:
docker-compose up -d
mvn clean install -DskipTests
# Start all services...

# Smoke test checklist:
□ User register + email verify + login (JWT + cookie)
□ Google OAuth2 login
□ Court search (geo + filter) + slot grid display
□ Court booking → Bank QR → upload proof → STAFF confirm → slot reserved
□ Create match (Host) → Bank QR → STAFF confirm → match OPEN
□ Join match (Player) → Bank QR → STAFF confirm → slot counter +1 (real-time)
□ Match complete → escrow settlement queue
□ Coach search (Elasticsearch) + enroll + review
□ Event ticket purchase
□ Notification push/email on key events
□ Admin panel: proof review queue + confirm/reject + manual refund
□ JWT blacklist on logout
□ Rate limiting works (429 on exceed)
□ Eureka dashboard: all services UP
□ Zipkin: distributed traces visible
```

---

## Quy trình làm việc với Claude Code

### Mỗi khi bắt đầu service mới:
```
1. Đọc rule file liên quan trong .claude/rules/
2. Prompt: "Tạo [service-name]. Đọc .claude/rules/[relevant].md trước."
3. Implement entity → repository → service → controller → test
4. Verify đăng ký Eureka thành công trước khi sang service tiếp
```

### Debug pattern:
```bash
# Check service logs
mvn -pl {service} spring-boot:run 2>&1 | grep -E "ERROR|WARN|Started"

# Check Kafka events
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.host.confirmed --from-beginning

# Check Redis keys
docker exec -it redis redis-cli keys "lock:*"
docker exec -it redis redis-cli keys "payment:countdown:*"
```

### Thứ tự ưu tiên khi bị block:
1. Đọc lại rule file liên quan
2. Kiểm tra service đã register Eureka chưa
3. Kiểm tra Kafka consumer group offset
4. Kiểm tra Redis key còn sống chưa

---

## Scope ngoài 4 tuần (backlog)

Những item này hoàn thành sau khi core flow chạy ổn định:

- [ ] ai-service: RAG chatbot thực sự (LLM integration)
- [ ] Waiting list cho matches (khi match FULL)
- [ ] Push notifications mobile (FCM deep integration)
- [ ] `@AuditLog` aspect ghi audit_logs cho admin operations
- [ ] Production hardening: HTTPS, env secrets, Kubernetes manifests
- [ ] Load testing (Gatling/k6) cho booking race conditions
- [ ] Swagger UI đầy đủ cho tất cả services
- [ ] E2E tests (Playwright/Cypress)
