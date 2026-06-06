# BadmintonHub — 4-Week Implementation Guide

> **Mục tiêu**: Build từ scratch đến complete trong 4 tuần với Claude Code.
> **Rules**: `.claude/rules/` — đọc trước khi bắt đầu mỗi service.
> **Nguyên tắc**: Mỗi ngày = 1 deliverable có thể chạy và test được.

---

## Cách dùng prompt trong tài liệu này

Mỗi ngày có một khối **`Prompt Claude Code`** đã viết sẵn — copy nguyên văn vào Claude Code để chạy task đó.

Quy ước trong mọi prompt:
- Luôn mở đầu bằng dòng `Đọc trước: .claude/rules/...` để Claude load đúng rule.
- Tên entity / endpoint / Redis key / Kafka topic trong prompt **khớp 100%** với registry trong rules — không tự đổi tên.
- Sau mỗi prompt, chạy phần **Định nghĩa Done** để tự kiểm tra trước khi sang ngày tiếp theo.
- Không bao giờ vi phạm 10 rule "Never Violate" trong `.claude/rules/architecture.md`.

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
- [ ] Tạo parent `pom.xml` (Java 21, Spring Boot 3.2.5, Spring Cloud 2023.0.1)
- [ ] Tạo 13 module rỗng: `common`, `eureka-server`, `api-gateway`, `user-service`, `court-service`, `booking-service`, `matchmaking-service`, `coach-service`, `payment-service`, `escrow-service`, `notification-service`, `event-service`, `ai-service`
- [ ] Tạo `docker-compose.yml`: PostgreSQL ×9, Redis 7, MongoDB 7, Kafka 3.6 + Zookeeper, Zipkin, Elasticsearch
- [ ] Mỗi service: `application.yml` cơ bản (port, datasource, kafka bootstrap, eureka client)
- [ ] Run: `docker-compose up -d && mvn clean install -DskipTests`

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/architecture.md và .claude/rules/eureka-config.md.

Tạo skeleton cho Maven multi-module project BadmintonHub. Yêu cầu:

1. Parent pom.xml:
   - Java 21, Spring Boot 3.2.5 (parent), Spring Cloud 2023.0.1 (dependencyManagement BOM).
   - <packaging>pom</packaging>, liệt kê đủ 13 <module>: common, eureka-server,
     api-gateway, user-service, court-service, booking-service, matchmaking-service,
     coach-service, payment-service, escrow-service, notification-service,
     event-service, ai-service.
   - Common dependencyManagement: lombok, mapstruct.

2. Mỗi module con: pom.xml tối thiểu (parent = badmintonhub), main class
   @SpringBootApplication theo package com.badmintonhub.{service}, và application.yml với:
     - server.port đúng theo bảng port trong architecture.md
     - spring.application.name = tên service (dùng cho lb://)
     - eureka client config y như eureka-config.md (trừ eureka-server)
   common không cần main class / không phải Spring Boot app, chỉ là library jar.

3. docker-compose.yml ở root, services:
   - 9 PostgreSQL 15 riêng biệt cho 9 DB (user_db, court_db, booking_db,
     matchmaking_db, payment_db, escrow_db, coach_db, event_db) + 1 cho ai nếu cần —
     theo bảng Database-per-Service trong database.md. Mỗi PG map port host khác nhau.
   - Redis 7, MongoDB 7 (notification_db), Kafka 3.6 + Zookeeper, Zipkin, Elasticsearch 8.
   - healthcheck cho từng infra service.

Đừng implement business logic — chỉ scaffold để `mvn clean install -DskipTests` pass
và `docker-compose up -d` lên đủ container.
```

**Định nghĩa Done**:
```bash
docker-compose up -d && docker-compose ps   # tất cả container healthy
mvn clean install -DskipTests               # BUILD SUCCESS, 13 modules
```

---

### Day 2: common module + eureka-server

**Mục tiêu**: `eureka-server` chạy tại http://localhost:8761.

Tasks:
- [ ] `common`: `BaseAuditEntity`, `ApiException`, `ErrorResponse`, `PageResponse<T>`
- [ ] `eureka-server`: `@EnableEurekaServer` + config
- [ ] Verify dashboard hiển thị, 0 instance

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md, .claude/rules/eureka-config.md.

PHẦN A — module common (shared library, không phải Spring Boot app):
- BaseAuditEntity: @MappedSuperclass, @EntityListeners(AuditingEntityListener.class),
  field createdAt (@CreatedDate) + updatedAt (@LastModifiedDate), cột created_at / updated_at.
- ErrorResponse: record { String code, String message, Instant timestamp } — đúng shape
  error response trong java-spring.md.
- ApiException: RuntimeException base mang field code (String) + httpStatus; thêm các subclass
  hay dùng: NotFoundException, ConflictException, UnauthorizedException, ValidationException.
- PageResponse<T>: record bọc Page<T> -> { content, page, size, totalElements, totalPages }.
- GlobalExceptionHandler dạng @RestControllerAdvice tái sử dụng được: map ApiException ->
  ErrorResponse với đúng httpStatus, map MethodArgumentNotValidException -> 400.
  (Để class này trong common, các service import lại.)

PHẦN B — module eureka-server:
- Dependency: spring-cloud-starter-netflix-eureka-server + actuator (theo eureka-config.md).
- Main class @SpringBootApplication @EnableEurekaServer.
- application.yml: port 8761, register-with-eureka:false, fetch-registry:false,
  enable-self-preservation:false (dev), wait-time-in-ms-when-sync-empty:0.

Không thêm @EnableJpaAuditing ở đây — sẽ bật ở từng service có DB.
```

**Định nghĩa Done**:
```bash
mvn -pl eureka-server spring-boot:run
# http://localhost:8761 → dashboard hiện, "Instances currently registered with Eureka" = trống
```

---

### Day 3: api-gateway

**Mục tiêu**: Gateway chạy port 3000, JWT filter hoạt động, route đến tất cả service qua `lb://`.

Tasks:
- [ ] Dependencies gateway + eureka client + redis
- [ ] 10 route `lb://`
- [ ] `JwtAuthenticationFilter` + Redis blacklist + forward headers
- [ ] `RateLimitFilter`

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/eureka-config.md, .claude/rules/rbac-security.md, .claude/rules/redis-patterns.md.

Implement api-gateway (Spring Cloud Gateway, reactive WebFlux):

1. Dependencies: spring-cloud-starter-gateway, spring-cloud-starter-netflix-eureka-client,
   spring-boot-starter-data-redis-reactive, jjwt (hoặc nimbus) để verify JWT.

2. application.yml: copy CHÍNH XÁC khối routes trong eureka-config.md — đủ 10 route
   (user, court, booking, matchmaking, payment, escrow, coach, notification, event, ai),
   tất cả dùng uri: lb://{service-name}, predicates Path đúng như rule. Eureka client config.

3. JwtAuthenticationFilter (GlobalFilter, order cao nhất):
   - Bỏ qua các path public: /api/auth/**, /actuator/health.
   - Với path còn lại: lấy Bearer token, verify chữ ký + hạn (HS256, secret từ env JWT_SECRET).
   - Check Redis key "session:blacklist:{jti}" (redis-patterns.md) — nếu tồn tại -> 401.
   - Hợp lệ: forward downstream 2 header X-User-Id và X-User-Roles (đọc từ claims).
   - Token thiếu/sai/hết hạn -> trả 401 ngay tại Gateway, không route tiếp.

4. RateLimitFilter (GlobalFilter sau JWT filter):
   - Redis INCR key "rate_limit:{userId}" TTL 60s (redis-patterns.md), max 100 req/phút.
   - Vượt -> 429 với ErrorResponse { code:"RATE_LIMIT_EXCEEDED" }.

Gateway là single auth boundary — service phía sau KHÔNG re-validate JWT (rbac-security.md).
```

**Định nghĩa Done**:
```bash
mvn -pl api-gateway spring-boot:run
curl -i http://localhost:3000/api/courts   # → 503 (chưa có court-service) = route ĐÚNG
curl -i http://localhost:3000/api/bookings # → 401 (thiếu JWT) = filter ĐÚNG
```

---

### Day 4: user-service — Auth core

**Mục tiêu**: Register → Email verify → Login → JWT token hoạt động.

Tasks:
- [ ] Entity: `User`, `Role`, `UserRole`, `AuditLog`
- [ ] register / verify-email / login / refresh / logout
- [ ] Gateway header filter (không re-validate JWT)

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md,
.claude/rules/rbac-security.md, .claude/rules/redis-patterns.md.

Implement user-service auth core (DB = user_db, PostgreSQL). Bật @EnableJpaAuditing.

ENTITY (extend BaseAuditEntity, PK UUID @GeneratedValue UUID):
- User: email (unique), passwordHash (nullable cho OAuth), fullName, phone,
  isEmailVerified (default false), authProvider (enum LOCAL/GOOGLE), googleId (unique nullable),
  refreshTokenHash, fcmToken, deletedAt (soft delete + @Where("deleted_at IS NULL")).
- Role (ADMIN/STAFF/COACH/USER), UserRole join (within-service FK OK).
- AuditLog (sẽ dùng sau, tạo sẵn bảng).

ENDPOINT (base /api/auth, error shape { code, message, timestamp }):
- POST /register: validate body (@Valid), BCrypt hash password, gán ROLE_USER,
  sinh token verify -> lưu Redis "email:verify:{token}" TTL 24h (redis-patterns.md),
  gửi email qua SendGrid. Rate limit Redis "rate_limit:register:{ip}" TTL 3600s max 5.
- GET /verify-email?token=: đọc Redis token (single-use, delete sau khi đọc),
  set isEmailVerified=true.
- POST /login: verify credential -> access token JWT HS256 TTL 15m (claims: sub=userId,
  roles, jti) + refresh token TTL 30d (lưu BCrypt hash vào users.refresh_token_hash,
  trả về cookie HttpOnly SameSite=Strict).
- POST /refresh: đọc refresh cookie, so hash -> cấp access token mới.
- POST /logout: thêm jti vào Redis "session:blacklist:{jti}" TTL = thời gian còn lại của token.

SECURITY (rbac-security.md "Spring Security Config per service"):
- GatewayHeaderAuthFilter: đọc X-User-Id + X-User-Roles -> dựng Authentication.
  KHÔNG re-validate JWT. csrf disable, session STATELESS, /actuator/health permitAll.
- Bean @authService.isEmailVerified(authentication) cho @PreAuthorize dùng sau.

Test (java-spring.md naming methodName_scenario_expectedResult): register, verify, login happy path.
```

**Định nghĩa Done**:
```bash
curl -X POST http://localhost:3000/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'   # → 201
# verify email bằng token → login → nhận JWT → gọi endpoint authenticated qua Gateway OK
```

---

### Day 5: user-service — OAuth2 + Profile + court-service scaffold

**Mục tiêu**: Google login hoạt động; court-service đăng ký Eureka.

Tasks:
- [ ] Google OAuth2 + forgot/reset password
- [ ] Profile endpoints (own-resource guard)
- [ ] Cloudinary upload
- [ ] court-service scaffold + Eureka

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/rbac-security.md, .claude/rules/redis-patterns.md, .claude/rules/java-spring.md.

PHẦN A — user-service bổ sung:
- POST /api/auth/google body { idToken }: verify idToken qua Google tokeninfo endpoint,
  upsert user match theo email; user mới -> authProvider=GOOGLE, passwordHash=null,
  set googleId (unique). Cấp JWT + refresh token y như login local (rbac-security.md).
- POST /api/auth/forgot-password: sinh token -> Redis "password:reset:{token}" TTL 1h, gửi email.
- POST /api/auth/reset-password body { token, newPassword }: consume Redis token (single-use),
  cập nhật passwordHash.
- GET /api/users/{id}, PATCH /api/users/{id}: @PreAuthorize own-resource OR STAFF/ADMIN
  ("#id == authentication.principal.id or hasAnyRole('STAFF','ADMIN')").
- GET /api/users (list all): @PreAuthorize hasAnyRole('STAFF','ADMIN'), trả Page<T>.
- DELETE /api/users/{id}: @PreAuthorize hasRole('ADMIN'), SOFT delete (set deletedAt).
- POST /api/upload/image (multipart): Cloudinary upload -> trả { url }.

PHẦN B — court-service scaffold (DB = court_db):
- Entity skeleton (extend BaseAuditEntity, PK UUID): Court, TimeSlot, CourtReview.
  Cross-service ref ownerId/userId là UUID thuần, comment 'ref users.id · cross-service UUID',
  KHÔNG @ManyToOne cross-service (database.md).
- Eureka client config (eureka-config.md), GatewayHeaderAuthFilter giống user-service.
- Chưa cần business logic — chỉ cần service start + register Eureka UP.

Verify: Eureka dashboard hiện user-service và court-service đều UP.
```

**Định nghĩa Done**:
```bash
# Google login trả JWT; forgot/reset password chạy; Eureka 4 service UP
curl http://localhost:8761  # user-service, court-service, api-gateway visible
```

---

### Week 1 Checkpoint

```bash
docker-compose up -d
mvn -pl eureka-server spring-boot:run &
mvn -pl api-gateway spring-boot:run &
mvn -pl user-service spring-boot:run &
mvn -pl court-service spring-boot:run &

curl -X POST http://localhost:3000/api/auth/register -d '{"email":"test@test.com","password":"123456","fullName":"Test User"}'
curl -X POST http://localhost:3000/api/auth/login -d '{"email":"test@test.com","password":"123456"}'
# Eureka: http://localhost:8761 → 4 services registered
```

---

## Week 2 — Booking Core

### Day 6: court-service — Courts + Slots

**Mục tiêu**: CRUD courts, slot auto-generation, geo search.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md,
.claude/rules/redis-patterns.md, .claude/rules/resilience.md, .claude/rules/rbac-security.md.

Implement đầy đủ court-service (court_db):

ENTITY: Court (name, address, district, lat, lng, type, pricePerHour, isActive, ownerId UUID
cross-service, imageUrls), TimeSlot (courtId, date, startTime, endTime, status enum
AVAILABLE/RESERVED/BLOCKED/EVENT), CourtReview (courtId, bookingId UNIQUE, userId, rating, comment).

ENDPOINT:
- POST /api/courts (@PreAuthorize hasAnyRole('STAFF','ADMIN')): tạo court + upload ảnh Cloudinary.
- GET /api/courts?lat=&lng=&radius=&district=&type=&date=: geo search (Haversine), filter,
  cache Redis "courts:{district}:{type}:{date}" TTL 60s (redis-patterns.md). Trả Page<T>.
- GET /api/courts/{id}/slots?date=: list slot theo ngày kèm status.
- GET /api/courts/{id}/slots/{slotId}: chi tiết 1 slot (cho Feign từ booking-service).
- POST /api/courts/{id}/generate-slots (STAFF): manual trigger sinh slot.
- PATCH /api/courts/slots/{id}/block (STAFF): set slot BLOCKED.

SCHEDULER (resilience.md "Slot Auto-Generation"):
- @Scheduled(cron="0 0 0 * * *"): với mọi court isActive=true, sinh slot 30 ngày tới
  (now+1 .. now+30), khung 06:00–22:00, mỗi slot 60 phút, status AVAILABLE.

INDEX (database.md): composite (court_id, date, status) trên time_slots; index ownerId.
Unique constraint court_reviews.booking_id.

Test: tạo court -> generate-slots -> query theo date thấy slot AVAILABLE.
```

**Định nghĩa Done**: tạo court → slots auto-gen → `GET /api/courts/{id}/slots?date=` trả slot AVAILABLE.

---

### Day 7: booking-service — Reservation + distributed lock

**Mục tiêu**: Book slot với Redis lock, không race condition.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/redis-patterns.md,
.claude/rules/resilience.md, .claude/rules/kafka-patterns.md, .claude/rules/database.md,
.claude/rules/rbac-security.md.

Implement booking-service (booking_db):

ENTITY: Booking (userId UUID, courtId UUID, slotId UUID — tất cả cross-service UUID,
status enum PENDING/CONFIRMED/COMPLETED/CANCELLED, amount, refundAmount).
ProcessedEvent (event_id PK varchar, processedAt) — idempotency guard (database.md).

ENDPOINT:
- POST /api/bookings (@PreAuthorize "hasAnyRole('USER','COACH') and
  @authService.isEmailVerified(authentication)" — rule #10):
  1. acquireRedisLock "lock:slot:{slotId}" TTL 5s (redis-patterns.md).
  2. Feign CourtServiceClient (lb://court-service) GET /api/courts/{courtId}/slots/{slotId}
     validate slot AVAILABLE. KHÔNG hardcode http://localhost.
  3. insert Booking PENDING, releaseRedisLock trong finally.
  Bọc acquireRedisLock bằng @CircuitBreaker(name="redis", fallbackMethod="acquireDbLock")
  (resilience.md) — fallback DB SELECT FOR UPDATE.
- PATCH /api/bookings/{id}/cancel: cancellation policy (database.md):
  >24h trước slot = refund 100%, 2–24h = 50%, <2h = 0%. Set status CANCELLED + refundAmount.
- GET /api/bookings (STAFF/ADMIN list all; user xem own) — @PreAuthorize theo rbac-security.md.

KAFKA CONSUMER (kafka-patterns.md, manual ack, idempotency guard):
- @KafkaListener topic "payment.player.confirmed" groupId "booking-service":
  check ProcessedEvent(record.key()) trước; nếu chưa -> Booking CONFIRMED, gọi court-service
  set slot RESERVED, save ProcessedEvent, ack. Lỗi -> KHÔNG ack (để retry).
- Sau confirm, publish "booking.slot.confirmed" (consumer: matchmaking, notification, escrow).
- KafkaConfig: DefaultErrorHandler exponential backoff 2s/4s/8s, 3 retries, DLT (resilience.md).

Test: 2 request đồng thời cùng slotId -> chỉ 1 thành công, 1 nhận CONFLICT.
```

**Định nghĩa Done**: 2 concurrent booking cùng slot → đúng 1 PENDING; consume `payment.player.confirmed` → CONFIRMED idempotent.

---

### Day 8: payment-service — Bank QR flow

**Mục tiêu**: Toàn bộ payment flow PENDING → PROOF_SUBMITTED → CONFIRMED.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/payment.md (BẮT BUỘC), .claude/rules/kafka-patterns.md,
.claude/rules/redis-patterns.md, .claude/rules/resilience.md, .claude/rules/rbac-security.md.

QUAN TRỌNG: KHÔNG dùng VNPay hay bất kỳ payment gateway nào. Chỉ Bank QR + STAFF confirm thủ công.

Implement payment-service (payment_db):

ENTITY: BankAccount (bankName, accountNumber, accountName, qrImageUrl),
Payment (userId UUID, paymentType enum BOOKING/MATCH_HOST/MATCH_PLAYER/COACH_ENROLLMENT/EVENT_TICKET,
referenceId UUID — booking/match/enrollment liên quan, matchId UUID nullable, amount,
orderCode SERIAL, status enum PENDING/PROOF_SUBMITTED/CONFIRMED/EXPIRED/REFUNDED, expiresAt,
refundAmount), PaymentProof (paymentId, imageUrl, uploadedAt), ManualRefund (paymentId,
amount, toBankName, toAccountNumber, toAccountName, refundNote, executedBy).

ENDPOINT (payment.md flow):
- POST /api/payments/initiate: insert Payment PENDING, expiresAt = now+10min,
  orderCode sequential, set Redis "payment:countdown:{paymentId}" = expiresAt (ISO) TTL 10min.
  Trả { orderId, orderCode, bankName, accountNumber, accountName, qrImageUrl, amount, expiresAt }.
- POST /api/payments/{id}/proof (multipart): rate limit Redis "rate_limit:proof:{userId}" TTL 300s
  max 3; Cloudinary upload -> insert PaymentProof -> status PROOF_SUBMITTED ->
  Kafka "payment.proof.submitted" (consumer notification).
- POST /api/payments/{id}/confirm (@PreAuthorize hasAnyRole('STAFF','ADMIN')):
  status CONFIRMED -> publish "payment.host.confirmed" (type MATCH_HOST) hoặc
  "payment.player.confirmed" (type MATCH_PLAYER/BOOKING/COACH_ENROLLMENT/EVENT_TICKET).
- POST /api/payments/{id}/reject (STAFF/ADMIN): status EXPIRED ->
  "payment.player.expired" hoặc "payment.host.expired" tùy type.
- POST /api/payments/{id}/refund (STAFF/ADMIN) body { amount, toBankName, toAccountNumber,
  toAccountName, refundNote }: insert ManualRefund, status REFUNDED, refundAmount=amount,
  Kafka "payment.refund.queued" (consumer notification, payment). STAFF chuyển khoản tay.
- GET /api/payments/pending-proofs (STAFF): queue PROOF_SUBMITTED để review.
- GET /api/bank-accounts: list tài khoản nhận tiền.

SCHEDULER (resilience.md): @Scheduled(fixedDelay=60000) PENDING quá expiresAt -> EXPIRED ->
publish payment.{host|player}.expired tùy type.

KafkaConfig DLT error handler như resilience.md. Tuân state machine payments.status trong database.md.
```

**Định nghĩa Done**: initiate → proof → STAFF confirm phát đúng topic; scheduler expire PENDING quá hạn.

---

### Day 9: escrow-service

**Mục tiêu**: Escrow hold → reimburse Host → settle Court Owner.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/payment.md (Prepay + Escrow Model), .claude/rules/kafka-patterns.md,
.claude/rules/database.md, .claude/rules/rbac-security.md.

Implement escrow-service (escrow_db):

ENTITY: EscrowAccount (matchId UUID cross-service, hostId UUID, courtPrice, status enum
HOLDING/SETTLED/REFUNDED, hostReimbursed), EscrowTransaction (escrowAccountId, type enum
HOST_HOLD/PLAYER_REIMBURSEMENT/COURT_OWNER_SETTLEMENT/HOST_REFUND/PLAYER_REFUND, amount,
relatedUserId UUID, note, createdAt), ProcessedEvent (idempotency guard — database.md).

KAFKA CONSUMER (kafka-patterns.md, manual ack, LUÔN check ProcessedEvent trước — rule #5):
- "payment.host.confirmed": tạo EscrowAccount status=HOLDING, courtPrice = amount,
  log EscrowTransaction HOST_HOLD.
- "payment.player.confirmed": log PLAYER_REIMBURSEMENT, cập nhật reimburse Host theo tỉ lệ.
- "match.completed": log COURT_OWNER_SETTLEMENT -> đẩy vào STAFF manual settlement queue,
  EscrowAccount -> SETTLED.
- "match.cancelled": log HOST_REFUND + PLAYER_REFUND -> STAFF manual refund queue.
- "booking.slot.confirmed": dùng nếu cần đối soát booking (idempotent).

ENDPOINT:
- GET /api/escrow/settlements/pending (STAFF/ADMIN): queue COURT_OWNER_SETTLEMENT chờ chi.
- GET /api/escrow/refunds/pending (STAFF/ADMIN): queue refund chờ chi.
- GET /api/escrow/{matchId}: trạng thái escrow của 1 match.

Court Owner CHỈ nhận tiền khi match COMPLETED và STAFF settle tay (payment.md). KafkaConfig DLT.

Test: bắn mock các Kafka event -> kiểm tra escrow_transactions ghi đúng type & amount,
gửi trùng event_id -> không double-count (idempotency).
```

**Định nghĩa Done**: mock các event → `escrow_transactions` đúng; gửi lại cùng event_id → không nhân đôi.

---

### Day 10: integration test Week 2 + court reviews

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md (Testing), .claude/rules/database.md, .claude/rules/redis-patterns.md.

PHẦN A — court review:
- POST /api/courts/{id}/reviews (@PreAuthorize hasAnyRole('USER','COACH')):
  chỉ cho review khi có booking COMPLETED của user đó cho court này; unique 1 review / booking
  (court_reviews.booking_id UNIQUE — database.md). Cập nhật rating trung bình của court.

PHẦN B — integration test (@SpringBootTest + @Testcontainers PostgreSQL + Redis,
KHÔNG dùng H2 — java-spring.md):
Viết test mô phỏng end-to-end luồng đặt sân:
  1. STAFF tạo court -> generate slots.
  2. User (email verified) POST /api/bookings -> Payment PENDING + bank QR.
  3. POST proof -> PROOF_SUBMITTED.
  4. STAFF confirm -> Kafka payment.player.confirmed.
  5. booking-service consume -> Booking CONFIRMED, slot RESERVED.
  6. escrow ghi transaction đúng.
Assert mỗi bước + verify Kafka event propagate đúng (dùng embedded Kafka hoặc Testcontainers Kafka).
Fix mọi bug phát hiện được.
```

**Định nghĩa Done**: integration test xanh; review chỉ tạo được sau booking COMPLETED.

---

### Week 2 Checkpoint

```
User đăng ký → verify email → tìm court → xem slot → đặt slot
→ nhận bank QR + countdown → upload proof → STAFF confirm
→ booking CONFIRMED, slot blocked, escrow HOLDING
```

---

## Week 3 — Matchmaking + Notifications + Coaches

### Day 11: matchmaking-service — Match creation + Host payment

**Mục tiêu**: Host tạo match, trả tiền qua Bank QR, match → OPEN.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md (Outbox + Zombie — rule #4, #6),
.claude/rules/redis-patterns.md, .claude/rules/resilience.md, .claude/rules/database.md,
.claude/rules/rbac-security.md.

Implement matchmaking-service phần tạo match (matchmaking_db):

ENTITY: Match (hostId UUID, courtId UUID, slotId UUID — cross-service UUID,
courtPrice SNAPSHOT (rule #9 — chốt lúc tạo, immutable), pricePerPerson, totalSlots,
filledSlots, skillLevel, status enum PENDING_PAYMENT/OPEN/FULL/COMPLETED/CANCELLED),
MatchParticipant (matchId, userId UUID, role enum HOST/PLAYER, joinedAt),
OutboxEvent (topic, payload, status enum PENDING/SENT, sentAt, createdAt).

ENDPOINT:
- POST /api/matches (@PreAuthorize hasAnyRole('USER','COACH')):
  1. acquireRedisLock "lock:slot:{slotId}:match_create" TTL 10min (redis-patterns.md).
  2. snapshot courtPrice qua Feign court-service (chốt cứng vào Match).
  3. tạo Match PENDING_PAYMENT + MatchParticipant(HOST).
  4. tạo Payment record type MATCH_HOST (gọi payment-service initiate).
  KHÔNG publish Kafka trực tiếp — dùng Outbox (rule #4).

OUTBOX (kafka-patterns.md + resilience.md):
- OutboxPublisherScheduler @Scheduled(fixedDelay=3000): poll OutboxEvent status=PENDING ->
  kafkaTemplate.send -> set SENT + sentAt.

KAFKA CONSUMER (manual ack):
- "payment.host.confirmed": ZOMBIE CHECK trước (rule #6 — nếu Match CANCELLED thì publish
  compensating event "match.compensate.slot" rồi return). Nếu hợp lệ: Match -> OPEN,
  gọi court-service set slot RESERVED, Host đã ở trong participants, INCR
  Redis "match:{matchId}:slots".

SCHEDULER (resilience.md "Match Timeout"): @Scheduled(cron="0 */5 * * * *")
PENDING_PAYMENT quá 10min -> CANCELLED, delete Redis lock:slot:{slotId}:match_create,
publish "match.cancelled" (reason PAYMENT_TIMEOUT) qua Outbox.

KafkaConfig DLT. State machine matches.status theo database.md.
```

**Định nghĩa Done**: tạo match → payment MATCH_HOST → STAFF confirm → Match OPEN; timeout scheduler cancel match treo.

---

### Day 12: matchmaking-service — Player joins (Saga)

**Mục tiêu**: Saga join match với Redis atomic counter, compensating transactions.

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md, .claude/rules/redis-patterns.md (Atomic Slot Counter),
.claude/rules/rbac-security.md.

Bổ sung matchmaking-service phần join (Saga):

ENDPOINT POST /api/matches/{id}/join (@PreAuthorize "hasAnyRole('USER','COACH') and
@authService.isEmailVerified(authentication)" — STAFF/ADMIN KHÔNG được join, rule rbac-security.md):
  1. acquireRedisLock "lock:match:{matchId}" TTL 5s.
  2. INCR "match:{matchId}:slots"; nếu > totalSlots -> DECR + throw ConflictException MATCH_FULL.
  3. rate limit Redis "rate_limit:join:{userId}" TTL 60s max 5.
  4. Trong 1 @Transactional: save MatchParticipant(PLAYER) + OutboxEvent
     (topic "match.slot.joined", consumer booking + payment).
  5. release lock trong finally.
  6. tạo Payment type MATCH_PLAYER amount = pricePerPerson.

KAFKA CONSUMER (manual ack, zombie check rule #6):
- "booking.slot.confirmed": nếu Match CANCELLED -> publish "match.compensate.slot" + return.
  Hợp lệ: filledSlots++; nếu filledSlots == totalSlots -> status FULL. Emit Socket.io.
- "payment.player.expired": COMPENSATE -> DECR "match:{matchId}:slots", remove MatchParticipant,
  filledSlots-- (idempotent, an toàn khi đến muộn).

SOCKET.IO: khi slot counter đổi (join confirm / compensate) emit "slot-updated"
{ filledSlots, totalSlots, status } tới room match:{matchId} (frontend.md useMatchSocket).

ENDPOINT GET /api/matches?status=OPEN&date=&skillLevel=: list match filter, Page<T>.

Test: nhiều request join đồng thời slot cuối -> chỉ 1 thành công (atomic counter + lock).
```

**Định nghĩa Done**: join concurrency an toàn; `payment.player.expired` compensate đúng; Socket.io emit `slot-updated`.

---

### Day 13: notification-service

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md (DLQ — rule #7), .claude/rules/architecture.md.

Implement notification-service (notification_db = MongoDB):

DOCUMENT: NotificationTemplate (code, channel enum PUSH/EMAIL, titleTemplate, bodyTemplate),
NotificationHistory (userId UUID, templateCode, channel, payload, isRead, sentAt).

SEED templates: SLOT_JOINED, PAYMENT_CONFIRMED, MATCH_CANCELLED, EMAIL_VERIFY,
PASSWORD_RESET, BOOKING_RECEIPT, PROOF_SUBMITTED, REFUND_QUEUED.

KAFKA CONSUMER (groupId notification-service, manual ack):
- "payment.proof.submitted" -> push STAFF "có proof mới cần review".
- "payment.host.confirmed" -> push/email Host "match đã OPEN".
- "payment.player.confirmed" -> push/email Player "đã join thành công".
- "match.cancelled" -> push/email tất cả participants.
- "payment.refund.queued" -> push/email user "refund đang xử lý".
- "escrow.host.reimbursed" -> push Host.
Gửi qua SendGrid (email) + FCM (push). Lưu NotificationHistory.

DLQ (rule #7, resilience.md): KafkaConfig DefaultErrorHandler exponential backoff 2s/4s/8s,
3 retries, DeadLetterPublishingRecoverer route "{topic}.DLT". KHÔNG nuốt exception.

ENDPOINT:
- GET /api/notifications (own, Page<T>), POST /api/notifications/{id}/read,
  POST /api/notifications/read-all.
- PATCH /api/users/{id}/fcm-token: cập nhật FCM token (gọi user-service hoặc lưu local).
```

**Định nghĩa Done**: các event tạo NotificationHistory + gửi; lỗi 3 lần → vào `.DLT`.

---

### Day 14: coach-service

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/java-spring.md, .claude/rules/database.md, .claude/rules/kafka-patterns.md,
.claude/rules/redis-patterns.md, .claude/rules/rbac-security.md, .claude/rules/payment.md.

Implement coach-service (coach_db PostgreSQL + Elasticsearch):

ENTITY: Coach (userId UUID, specialty, hourlyRate, bio, district, rating, status enum
PENDING_APPROVAL/ACTIVE/SUSPENDED, deletedAt soft delete + @Where), CoachSchedule
(coachId, dayOfWeek, startTime, endTime), CoachEnrollment (coachId, studentId UUID,
status enum PENDING/CONFIRMED/COMPLETED/CANCELLED, amount), CoachReview (coachId,
enrollmentId UNIQUE, studentId, rating, comment).

ELASTICSEARCH: index document Coach khi create/update; search theo specialty,
hourlyRate range, rating, district.

ENDPOINT:
- POST /api/coaches (apply, @PreAuthorize hasRole('COACH')): status PENDING_APPROVAL.
- PATCH /api/coaches/{id}/approve (@PreAuthorize hasRole('ADMIN')): -> ACTIVE.
- PATCH /api/coaches/{id}/suspend (@PreAuthorize hasAnyRole('STAFF','ADMIN')): -> SUSPENDED.
- GET /api/coaches?specialty=&minRate=&maxRate=&minRating=&district=: Elasticsearch search.
- POST /api/coaches/{id}/enroll (@PreAuthorize hasAnyRole('USER','COACH')):
  tạo CoachEnrollment PENDING + Payment type COACH_ENROLLMENT (payment.md) -> trả bank QR.
- POST /api/coaches/{id}/reviews: chỉ enrollment COMPLETED; rate limit
  Redis "rate_limit:review:{userId}" TTL 86400s max 2/ngày; unique 1 review/enrollment.

KAFKA CONSUMER: "payment.player.confirmed" (type COACH_ENROLLMENT) -> Enrollment CONFIRMED
(idempotent). KafkaConfig DLT. State machine coaches.status theo database.md.
```

**Định nghĩa Done**: apply → ADMIN approve → ACTIVE; enroll → bank QR → confirm → CONFIRMED; ES search trả đúng.

---

### Day 15: Week 3 integration + admin endpoints

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/kafka-patterns.md, .claude/rules/resilience.md (Cleanup Schedulers),
.claude/rules/rbac-security.md.

PHẦN A — match lifecycle:
- PATCH /api/matches/{id}/cancel (@PreAuthorize host OR STAFF/ADMIN): status CANCELLED ->
  publish "match.cancelled" qua Outbox (consumer notification, booking, escrow) -> escrow
  refund queue + notifications.
- PATCH /api/matches/{id}/complete (@PreAuthorize hasAnyRole('STAFF','ADMIN')): status COMPLETED
  (chỉ từ FULL/OPEN) -> publish "match.completed" qua Outbox -> escrow settlement queue.

PHẦN B — admin & cleanup:
- POST /api/admin/kafka/replay?topic={topic.DLT} (@PreAuthorize hasRole('ADMIN')):
  đọc DLT, gửi lại vào topic gốc (kafka-patterns.md replay endpoint).
- Outbox cleanup (matchmaking): @Scheduled(cron="0 0 2 * * *") delete OutboxEvent SENT > 30 ngày.
- ProcessedEvent cleanup (booking + escrow): @Scheduled(cron="0 0 3 * * *") delete > 7 ngày.

PHẦN C — integration test toàn bộ Saga join-match (Testcontainers): host tạo match -> confirm ->
OPEN -> player join -> confirm -> filledSlots++ -> FULL -> complete -> escrow settlement.
Verify zombie check (event đến sau khi CANCELLED -> compensate, không xử lý stale — rule #6).
```

**Định nghĩa Done**: cancel/complete phát đúng event; DLQ replay chạy; cleanup scheduler hoạt động; Saga test xanh.

---

### Week 3 Checkpoint

```
Host tạo match → Bank QR → STAFF confirm → match OPEN (Socket.io broadcast)
Player join → Bank QR → STAFF confirm → slot counter INCR (Socket.io)
Match FULL → notification; STAFF complete → escrow settlement queue
Coach: enroll → Bank QR → confirm → enrollment CONFIRMED
```

---

## Week 4 — Frontend + Event Service + Polish

### Day 16: Frontend scaffold + auth pages

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md.

Scaffold frontend (Vite + React 18 + TypeScript + Tailwind) theo File Structure trong frontend.md:

- src/api/axiosClient.ts: baseURL = import.meta.env.VITE_API_URL; request interceptor gắn
  Bearer accessToken từ authStore; response interceptor silent refresh khi 401
  (POST /api/auth/refresh, cookie-based, _retry flag). MỌI call HTTP đi qua client này.
- src/store/authStore.ts (Zustand): { accessToken, user, setAuth, clearAuth }. KHÔNG để
  server data trong Zustand — dùng React Query cho data từ API.
- Pages LoginPage, RegisterPage: React Hook Form + Zod (resolver zodResolver).
- ProtectedRoute + RoleGuard components.
- src/App.tsx: React Router v6 createBrowserRouter — public + protected routes.
- i18n react-i18next: src/i18n/vi.json + en.json; mọi string qua t('key'), không hardcode.
- react-hot-toast cho feedback. QueryClientProvider bọc app.

Test: register -> verify email -> login -> redirect dashboard.
```

**Định nghĩa Done**: register/login chạy trên browser, silent refresh 401 hoạt động.

---

### Day 17: Frontend — Courts + Visual Booking Grid

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md (Slot Color Convention, Payment Screen, React Query).

- CourtsPage: search form (district, type, date) qua React Query useQuery; react-leaflet map
  với pin court.
- CourtDetailPage: thông tin court + component SlotGrid.
- SlotGrid: lưới slot theo thời gian, màu theo status dùng ĐÚNG slotColors trong frontend.md
  (AVAILABLE bg-white hover green / RESERVED bg-red-200 / BLOCKED bg-gray-300 / EVENT bg-purple-200).
- Click slot AVAILABLE -> booking modal -> useMutation POST /api/bookings ->
  invalidateQueries(['court', id]).
- PaymentScreen component theo interface PaymentScreenProps trong frontend.md (paymentId,
  orderCode, bankName, accountNumber, accountName, qrImageUrl, amount, expiresAt):
  hiện bank info + QR + countdown timer + upload zone; multipart POST /api/payments/{id}/proof;
  disable nút confirm tới khi chọn ảnh.
- toast.success / toast.error qua react-hot-toast (errors lấy err.response?.data?.message).

Test: tìm sân -> click slot -> đặt -> PaymentScreen -> upload ảnh.
```

**Định nghĩa Done**: đặt sân từ browser → PaymentScreen → upload proof OK.

---

### Day 18: Frontend — Matchmaking + Real-time

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md (Socket.io useMatchSocket, React Query, forms).

- MatchesPage: list open matches, filter date + skill level (useQuery).
- MatchDetailPage: match info + slot counter + nút join.
- src/hooks/useMatchSocket.ts: io(VITE_WS_URL) (matchmaking-service:3004), emit
  'join-match-room' matchId, listen 'slot-updated' -> queryClient.setQueryData(['match', id], ...).
  Socket CHỈ dùng qua hook này, không gọi trong component.
- Create match modal: React Hook Form + Zod (totalSlots chẵn 2..16, pricePerPerson >=0,
  date tương lai) -> submit -> PaymentScreen (type MATCH_HOST).
- Join match -> POST /api/matches/{id}/join -> PaymentScreen (amount = pricePerPerson).
- notificationStore (Zustand) + NotificationBell badge count.

Test: 2 tab cùng join 1 match -> slot counter cập nhật real-time đồng thời.
```

**Định nghĩa Done**: slot counter realtime giữa 2 tab; tạo/join match → PaymentScreen.

---

### Day 19: Frontend — Admin panel + Coach pages

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/frontend.md, .claude/rules/rbac-security.md (Permission Matrix).

- AdminPage (RoleGuard roles={['STAFF','ADMIN']}):
  • Tab Proof Review: GET /api/payments/pending-proofs, xem ảnh proof, nút Confirm
    (POST /api/payments/{id}/confirm) / Reject (POST /api/payments/{id}/reject).
  • Tab Matches: list all matches + status filter, force cancel.
  • Tab Bookings: list all bookings.
  • Tab Refunds: form manual refund (toBankName, toAccountNumber, toAccountName, amount,
    refundNote) -> POST /api/payments/{id}/refund.
- CoachesPage: search (specialty, hourlyRate, rating) — Elasticsearch-powered.
- CoachDetailPage: profile + schedule + nút enroll -> PaymentScreen (type COACH_ENROLLMENT).
- DashboardPage (User): my bookings, my matches, my enrollments, my notifications.

Test: STAFF login -> thấy pending proofs -> confirm -> user nhận notification.
```

**Định nghĩa Done**: STAFF confirm proof từ UI → user nhận notification; coach search + enroll chạy.

---

### Day 20: event-service + ai-service stub + final integration

**Prompt Claude Code**:
```
Đọc trước: .claude/rules/database.md (event_tickets state machine), .claude/rules/payment.md,
.claude/rules/kafka-patterns.md, .claude/rules/frontend.md.

PHẦN A — event-service (event_db):
- Entity: Event (name, description, courtId UUID, startTime, endTime, ticketPrice, totalTickets,
  type enum SOCIAL/COMPETITIVE), EventTicket (eventId, userId UUID, status enum
  PENDING/CONFIRMED/CANCELLED/REFUNDED — database.md).
- POST /api/events (@PreAuthorize hasAnyRole('STAFF','ADMIN')): tạo event + block time_slots
  của court (status=EVENT qua court-service).
- POST /api/events/{id}/tickets/purchase (@PreAuthorize hasAnyRole('USER','COACH')):
  tạo Payment type EVENT_TICKET -> trả Bank QR.
- @KafkaListener "payment.player.confirmed" (type EVENT_TICKET) -> ticket CONFIRMED (idempotent).
- KafkaConfig DLT.

PHẦN B — ai-service stub:
- GET /api/ai/chat: trả static response (placeholder cho RAG — backlog).

PHẦN C — frontend EventsPage: list events, buy ticket -> PaymentScreen.

PHẦN D — end-to-end smoke test toàn bộ luồng (court booking, match host+join, coach enroll,
event ticket, STAFF admin ops). Fix critical bug.
```

**Định nghĩa Done**: mua vé event chạy end-to-end; ai stub trả response; smoke test toàn hệ thống pass.

---

### Week 4 Checkpoint — Final Checklist

```bash
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
1. Mở phần ngày tương ứng trong file này, copy khối "Prompt Claude Code".
2. Claude sẽ tự đọc rule file ghi trong dòng "Đọc trước:".
3. Implement theo thứ tự: entity → repository → service → controller → test.
4. Chạy phần "Định nghĩa Done" trước khi sang ngày tiếp theo.
5. Verify service register Eureka UP trước khi qua service kế.
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
