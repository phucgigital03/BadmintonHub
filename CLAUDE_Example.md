# 🏸 BadmintonHub — CLAUDE.md

> **Complete project reference for AI-assisted development.**  


---

## 1. Project Overview

**BadmintonHub** is a microservices-based platform for managing badminton courts, bookings, player matchmaking, coach enrollment, and payments in Vietnam.

> **Matchmaking Model**: Prepay + Escrow — Host pays full court fee upfront (deposited into Escrow); each Player reimburses the Host proportionally; Court Owner is paid only when the match is COMPLETED.

- **Backend**: Spring Boot 3.x + Java 21, Maven Multi-Module
- **Frontend**: React 18 + Vite + Tailwind CSS + TypeScript
- **Service Discovery**: Spring Cloud Netflix Eureka (eureka-server port 8761, all services auto-register)
- **Messaging**: Apache Kafka (Saga Pattern, Outbox Pattern)
- **Databases**: PostgreSQL (per service) · Redis · MongoDB · Elasticsearch
- **Payment**: Bank Transfer + QR Code — user scans bank QR → uploads proof screenshot → STAFF confirms manually (no 3rd-party payment API required)
- **File Storage**: Cloudinary (court images, user avatars)
- **Auth**: JWT (Access 15m + Refresh 30d HttpOnly Cookie) · Google OAuth2
- **Email**: SendGrid · **Push**: Firebase Cloud Messaging (FCM)
- **Estimated build time**: 8–10 weeks (including production hardening)

---

## 2. Tech Stack

### Backend (Spring Boot)

| Layer | Technology | Spring Module | Notes |
|---|---|---|---|
| Service Discovery | Spring Cloud Netflix Eureka | `spring-cloud-starter-netflix-eureka-server` / `-client` | `eureka-server` (port 8761) · all services auto-register · Gateway routes via `lb://service-name` |
| API Gateway | Spring Cloud Gateway | `spring-cloud-starter-gateway` | JWT filter, rate limit, routing via Eureka `lb://`, circuit breaker |
| REST API | Spring Web MVC | `spring-boot-starter-web` | Controllers, DTO, Bean Validation |
| Database ORM | Spring Data JPA + Hibernate | `spring-boot-starter-data-jpa` | Entity, Repository, `created_at`/`updated_at` auditing |
| Security | Spring Security + JWT + OAuth2 | `spring-boot-starter-security`, `spring-boot-starter-oauth2-client` | Auth, RBAC, JWT (15m access / 30d refresh HttpOnly cookie), Google OAuth |
| Messaging | Spring Kafka | `spring-kafka` | `@KafkaListener`, `KafkaTemplate`, DLQ, exponential backoff |
| Cache | Spring Data Redis | `spring-boot-starter-data-redis` | `RedisTemplate`, `@Cacheable`, distributed locks, token blacklist |
| Scheduler | Spring Scheduler | `spring-boot-starter` (built-in) | `@Scheduled` cron jobs (outbox, slot gen, cleanup) |
| NoSQL | Spring Data MongoDB | `spring-boot-starter-data-mongodb` | Notification templates + history |
| Search | Spring Data Elasticsearch | `spring-data-elasticsearch` | Coach search full-text |
| File Upload | Cloudinary SDK | `cloudinary-http44` | Court images, user avatars (max 5MB, jpeg/png/webp) |
| Email | SendGrid Java SDK | `sendgrid-java` | Booking confirmation, email verification, password reset |
| Push Notifications | Firebase Admin SDK | `firebase-admin` | FCM push to mobile/web via `fcm_token` |
| Resilience | Resilience4j | `resilience4j-spring-boot3` | Circuit breaker for Redis fallback |
| Tracing | Micrometer Tracing + Zipkin | `micrometer-tracing-bridge-otel` | Distributed trace across all services |
| Testing | JUnit 5 + Mockito | `spring-boot-starter-test` | Unit, Integration, `@MockBean` |
| Monitoring | Spring Actuator + Micrometer | `spring-boot-starter-actuator` | Health, metrics, tracing |
| Docs | SpringDoc OpenAPI | `springdoc-openapi-starter` | Swagger UI auto-gen |

### Frontend

| Layer | Technology |
|---|---|
| Framework | React 18 + Vite |
| Language | TypeScript |
| Styling | Tailwind CSS |
| State | Zustand (`authStore`, `matchStore`, `notificationStore`) |
| Data Fetching | React Query (`@tanstack/react-query`) |
| HTTP Client | Axios with JWT interceptor + auto-refresh on 401 |
| Real-time | Socket.io |
| Routing | React Router v6 (`createBrowserRouter`) |
| i18n | react-i18next (VI / EN) |
| Maps | react-leaflet + OpenStreetMap (geo court search) |

---

## 3. Project Structure (Multi-Module Maven)

```
badminton-hub/
├── pom.xml                    # Parent POM (Java 21, Spring Boot 3.2.x, spring-cloud 2023.x)
├── common/                    # Shared DTOs, exceptions, utils, audit base entity
├── eureka-server/             # Spring Cloud Netflix Eureka — service registry (port 8761)
├── api-gateway/               # Spring Cloud Gateway — JWT filter, rate limiting, circuit breaker, lb:// routing via Eureka
├── user-service/              # Auth (JWT + OAuth2 Google), Profile, Refresh Token, Email Verify, Forgot Password
├── court-service/             # Court catalog, time slots, slot auto-gen scheduler, court reviews, Cloudinary upload, geo search
├── booking-service/           # Reservations, distributed lock, idempotency, cancellation policy
├── matchmaking-service/       # Open matches, Saga, Outbox, Socket.io, waiting list
├── coach-service/             # Coach profiles, Elasticsearch, enrollments (with payment), reviews
├── payment-service/           # Bank QR + proof upload + STAFF manual confirm + manual refund
├── escrow-service/            # Escrow: hold court_price, reimburse Host per join, settle Court Owner on COMPLETED
├── notification-service/      # Kafka consumers, MongoDB templates, SendGrid email, FCM push, DLQ
├── event-service/             # Social/competitive events, ticket sales, event_db (port 3009)
├── ai-service/                # RAG, schedule recommendations, chatbot (port 3010)
├── docker-compose.yml         # PostgreSQL x9, Redis, MongoDB, Kafka, Zookeeper, Zipkin, Eureka
└── frontend/                  # React 18 + Vite
    └── src/
        ├── api/               # axiosClient.ts — Axios + JWT interceptor + silent refresh
        ├── components/        # Reusable UI components
        ├── pages/             # HomePage, CourtsPage, CourtDetailPage, MatchesPage,
        │                      # MatchDetailPage, CoachesPage, CoachDetailPage,
        │                      # DashboardPage, ChatPage, AdminPage
        ├── store/             # authStore.ts, matchStore.ts, notificationStore.ts (Zustand)
        ├── hooks/             # useSocket.ts, useAuth.ts, useNotifications.ts
        └── App.tsx            # Router config (includes /admin/* routes)
```

---

## 4. Service Configuration Reference

### Sample `application.yml` (per service)

```yaml
spring:
  application:
    name: booking-service          # MUST match Eureka registration name
  datasource:
    url: jdbc:postgresql://localhost:5432/booking_db
    username: ${DB_USER}
    password: ${DB_PASS}
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: booking-service
      auto-offset-reset: earliest
  data:
    redis:
      host: localhost
      port: 6379
server:
  port: 3003

# Eureka client config — required on every service except eureka-server itself
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true        # Use IP instead of hostname (important in Docker)
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

### `eureka-server` `application.yml`

```yaml
spring:
  application:
    name: eureka-server
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false    # Server does not register itself
    fetch-registry: false
  server:
    wait-time-in-ms-when-sync-empty: 0
    enable-self-preservation: false  # Disable in dev; enable in prod
```

### API Gateway routes (Eureka `lb://` load-balanced)

```yaml
# api-gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: lb://user-service        # Eureka resolves to actual instance IP:port
          predicates:
            - Path=/api/auth/**, /api/users/**
        - id: court-service
          uri: lb://court-service
          predicates:
            - Path=/api/courts/**, /api/events/**
        - id: booking-service
          uri: lb://booking-service
          predicates:
            - Path=/api/bookings/**
        - id: matchmaking-service
          uri: lb://matchmaking-service
          predicates:
            - Path=/api/matches/**
        - id: payment-service
          uri: lb://payment-service
          predicates:
            - Path=/api/payments/**, /api/bank-accounts/**
        - id: escrow-service
          uri: lb://escrow-service
          predicates:
            - Path=/api/escrow/**
        - id: coach-service
          uri: lb://coach-service
          predicates:
            - Path=/api/coaches/**
        - id: notification-service
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**
        - id: event-service
          uri: lb://event-service
          predicates:
            - Path=/api/events/**
        - id: ai-service
          uri: lb://ai-service
          predicates:
            - Path=/api/ai/**
```

### Frontend `.env`

```
VITE_API_URL=http://localhost:3000       # API Gateway
VITE_WS_URL=http://localhost:3004        # matchmaking-service Socket.io
```

### Service Port Map

| Service | Port | Notes |
|---|---|---|
| eureka-server | 8761 | Service registry — dashboard at http://localhost:8761 |
| api-gateway | 3000 | Circuit breaker + granular rate limiting + Eureka `lb://` routing |
| user-service | 3001 | JWT + Refresh Token + OAuth2 + Email Verify + Forgot PW |
| court-service | 3002 | Slot auto-gen + Cloudinary upload + Court reviews + Geo search |
| booking-service | 3003 | Cancellation policy + Audit log |
| matchmaking-service | 3004 | Saga + Outbox + Socket.io + Waiting list |
| coach-service | 3005 | Elasticsearch + Enrollment payment |
| payment-service | 3006 | Bank QR + Proof upload + STAFF manual confirm + Manual refund |
| escrow-service | 3007 | Hold / reimburse / settle / refund |
| notification-service | 3008 | SendGrid + FCM + DLQ + Read-status |
| event-service | 3009 | Social/competitive events · Ticket sales |
| ai-service | 3010 | RAG chatbot |

---

## 5. Database Design (Database-per-Service)

> Cross-service references are **UUID only** — no FK constraints across databases. Consistency enforced via Kafka events + Saga Pattern.

### `user_db` — user-service (PostgreSQL)

| Table | Key Columns |
|---|---|
| `users` | `id UUID PK`, `email UK`, `password_hash` *(nullable for OAuth users)*, `full_name`, `phone`, `skill_level (BEGINNER\|INTERMEDIATE\|ADVANCED\|PRO)`, `avatar_url`, `is_active`, `deleted_at`, **`is_email_verified BOOL DEFAULT FALSE`**, **`email_verify_token`**, **`email_verify_expiry`**, **`refresh_token_hash`**, **`refresh_token_expiry`**, **`google_id UK`**, **`auth_provider (LOCAL\|GOOGLE)`**, **`fcm_token`**, **`notification_email_enabled BOOL`**, **`notification_push_enabled BOOL`**, `created_at`, `updated_at` |
| `roles` | `id UUID PK`, `name UK (ADMIN\|STAFF\|COACH\|USER)` |
| `user_roles` | `user_id FK`, `role_id FK`, `assigned_at`, `assigned_by` |
| `audit_logs` | `id UUID PK`, `actor_id`, `action VARCHAR(100)`, `resource VARCHAR(50)`, `resource_id UUID`, `old_value TEXT (JSON)`, `new_value TEXT (JSON)`, `ip_address`, `created_at` |

### `court_db` — court-service (PostgreSQL)

| Table | Key Columns |
|---|---|
| `courts` | `id UUID PK`, `created_by`, `name`, `address`, `district`, `price_per_hour`, `type (SYNTHETIC\|WOOD\|CONCRETE)`, `images (JSON)`, `is_active`, **`latitude DECIMAL(9,6)`**, **`longitude DECIMAL(9,6)`**, **`rating DECIMAL(3,2) DEFAULT 0.00`**, **`total_reviews INT DEFAULT 0`**, `created_at`, `updated_at` |
| `time_slots` | `id UUID PK`, `court_id FK`, `blocked_by`, `date`, `start_time`, `end_time`, `status (AVAILABLE\|RESERVED\|BLOCKED\|EVENT)`, `event_id` *(nullable)* |
| `court_reviews` | `id UUID PK`, `court_id`, `user_id`, `booking_id UK` *(1 booking = 1 review)*, `rating SMALLINT (1–5)`, `comment`, `created_at` |

### `booking_db` — booking-service (PostgreSQL)

| Table | Key Columns |
|---|---|
| `bookings` | `id UUID PK`, `user_id`, `slot_id`, `court_id`, `cancelled_by`, `total_price`, `refund_amount`, `status (PENDING\|CONFIRMED\|CANCELLED\|COMPLETED)`, `cancel_reason`, **`match_start_time TIMESTAMP`** *(snapshot for cancellation policy)*, `created_at`, `cancelled_at`, `updated_at` |
| `processed_events` | `event_id PK`, `processed_at` — idempotency guard (cleaned up after 7 days) |

### `matchmaking_db` — matchmaking-service (PostgreSQL)

| Table | Key Columns |
|---|---|
| `matches` | `id UUID PK`, `court_id`, `host_id`, `closed_by`, `date`, `start_time`, `total_slots`, `filled_slots`, `price_per_person`, `court_price` *(snapshot at creation)*, `skill_required`, `status (PENDING_PAYMENT\|OPEN\|FULL\|CANCELLED\|COMPLETED)` |
| `match_participants` | `match_id FK`, `user_id`, `joined_at`, `payment_id` |
| `outbox_events` | `id UUID PK`, `topic`, `payload JSON`, `status (PENDING\|SENT)` — Outbox Pattern |

### `escrow_db` — escrow-service (PostgreSQL)

| Table | Key Columns |
|---|---|
| `escrow_accounts` | `id UUID PK`, `match_id UK`, `court_owner_id`, `amount` *(court_price held)*, `released_amount`, `status (HOLDING\|PARTIALLY_RELEASED\|SETTLED\|REFUNDED)`, `created_at`, `settled_at` |
| `escrow_transactions` | `id UUID PK`, `escrow_id FK`, `type (HOST_DEPOSIT\|PLAYER_REIMBURSEMENT\|COURT_OWNER_SETTLEMENT\|HOST_REFUND\|PLAYER_REFUND)`, `from_party`, `to_party`, `amount`, `reference_payment_id`, `created_at` |

### `payment_db` — payment-service (PostgreSQL)

| Table | Key Columns |
|---|---|
| `bank_accounts` | `id UUID PK`, `bank_name` *(e.g. Shinhan Bank VN)*, `account_number UK`, `account_name`, `qr_image_url` *(Cloudinary)*, `is_active BOOL DEFAULT TRUE`, `created_at` — platform bank accounts displayed on payment screen |
| `payments` | `id UUID PK`, `order_code UK` *(sequential e.g. #184 — displayed on screen and used as transfer note)*, `booking_id`, `match_id`, `enrollment_id`, `user_id`, `bank_account_id FK`, `amount`, `refund_amount`, `payment_type (BOOKING\|MATCH_HOST\|MATCH_PLAYER\|COACH_ENROLLMENT\|EVENT_TICKET)`, `status (PENDING\|PROOF_SUBMITTED\|CONFIRMED\|EXPIRED\|REFUNDED)`, `expires_at` *(NOT NULL — countdown end time, e.g. NOW() + 10min)*, `confirmed_by` *(ref users.id — STAFF/ADMIN)*, `confirmed_at`, `reject_reason`, `created_at`, `updated_at` |
| `payment_proofs` | `id UUID PK`, `payment_id FK`, `image_url` *(Cloudinary URL of transfer screenshot)*, `uploaded_by` *(ref users.id)*, `uploaded_at NOT NULL`, `reviewed_by` *(ref users.id — nullable)*, `reviewed_at` *(nullable)*, `review_note` *(nullable)* |
| `manual_refunds` | `id UUID PK`, `payment_id FK`, `amount NOT NULL`, `refund_method (BANK_TRANSFER)`, `to_bank_name`, `to_account_number`, `to_account_name`, `refund_note`, `processed_by` *(ref users.id — STAFF/ADMIN)*, `processed_at NOT NULL`, `created_at` |

### `coach_db` — coach-service (PostgreSQL + Elasticsearch)

| Table | Key Columns |
|---|---|
| `coaches` | `id UUID PK`, `user_id UK`, `approved_by`, `specialty (SINGLES\|DOUBLES\|FOOTWORK\|SMASH\|DEFENSE)`, `hourly_rate`, `rating (0.0–5.0)`, `total_reviews`, `bio`, `certifications (JSON)`, `status (PENDING_APPROVAL\|ACTIVE\|SUSPENDED)`, `deleted_at` |
| `coach_schedules` | `id UUID PK`, `coach_id FK`, `day_of_week`, `available_from`, `available_to` |
| `coach_enrollments` | `id UUID PK`, `coach_id FK`, `user_id`, `slot_id`, **`payment_id`**, **`total_paid DECIMAL`**, `status (PENDING\|CONFIRMED\|CANCELLED\|COMPLETED)`, `cancelled_at` |
| `coach_reviews` | `id UUID PK`, `coach_id FK`, `user_id`, `rating (1–5)`, `comment`, `is_flagged`, `flagged_by` |

### `notification_db` — notification-service (MongoDB)

| Collection | Key Fields |
|---|---|
| `notification_templates` | `event_type (SLOT_JOINED\|PAYMENT_CONFIRMED\|MATCH_CANCELLED\|EMAIL_VERIFY\|PASSWORD_RESET\|BOOKING_RECEIPT)`, `title_template`, `body_template`, `channel (EMAIL\|PUSH\|SMS)` |
| `notification_history` | `event_type`, `user_id`, `match_id`, `booking_id`, `rendered_title`, `rendered_body`, `status (SENT\|FAILED)`, **`is_read BOOL DEFAULT FALSE`**, **`read_at`**, `created_at` |

### Redis Key Patterns

| Key | Type | Purpose | TTL |
|---|---|---|---|
| `lock:slot:{slotId}` | String | Distributed lock — slot booking | 5s |
| `lock:slot:{slotId}:match_create` | String | Distributed lock — slot held while Host creates match (awaiting proof confirmation) | **10 min** |
| `lock:match:{matchId}` | String | Distributed lock — match joining | 5s |
| `payment:countdown:{paymentId}` | String (timestamp) | Payment screen countdown — expires_at timestamp | **10 min** |
| `courts:{district}:{type}:{date}` | String (JSON) | Cached court search results | 60s |
| `match:{matchId}:slots` | String (INT) | Atomic slot counter via INCR | None |
| `rate_limit:{userId}` | String (INT) | API Gateway global rate limiting | 60s |
| `rate_limit:join:{userId}` | String (INT) | Max 5 join attempts/min | 60s |
| `rate_limit:proof:{userId}` | String (INT) | Max 3 proof uploads per 5 min (anti-spam) | 300s |
| `rate_limit:review:{userId}` | String (INT) | Max 2 reviews/day per coach | 86400s |
| `rate_limit:register:{ip}` | String (INT) | Max 5 registrations/hour per IP | 3600s |
| `session:blacklist:{jti}` | String | Revoked JWT access token IDs | = JWT TTL |
| `email:verify:{token}` | String (userId) | Email verification token | 24h |
| `password:reset:{token}` | String (userId) | Password reset token | 1h |

---

## 6. RBAC — Roles & Permissions

### Roles

| Role | Spring Authority | Who | Auto-assigned on Register |
|---|---|---|---|
| `ADMIN` | `ROLE_ADMIN` | System administrator | ❌ Manual only |
| `STAFF` | `ROLE_STAFF` | Badminton center staff | ❌ Manual only |
| `COACH` | `ROLE_COACH` | Approved professional coach | ❌ Via approval flow |
| `USER` | `ROLE_USER` | Regular customer | ✅ Auto-assigned |

### Permission Matrix (enforced via `@PreAuthorize` — NOT stored in DB)

#### User Service
| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `POST /api/auth/register` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/auth/verify-email?token=` | Public | | | |
| `POST /api/auth/refresh` | ✅ (HttpOnly cookie) | ✅ | ✅ | ✅ |
| `POST /api/auth/forgot-password` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/auth/reset-password` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/auth/google` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/users/{id}` | ✅ own | ✅ own | ✅ all | ✅ all |
| `PATCH /api/users/{id}` | ✅ own | ✅ own | ✅ all | ✅ all |
| `POST /api/upload/image` | ✅ | ✅ | ✅ | ✅ |
| `GET /api/users` (list) | ❌ | ❌ | ✅ | ✅ |
| `DELETE /api/users/{id}` | ❌ | ❌ | ❌ | ✅ |
| `POST /api/users/{id}/roles` | ❌ | ❌ | ✅ | ✅ |

#### Court Service
| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `GET /api/courts` (with `?lat&lng&radius`) | ✅ | ✅ | ✅ | ✅ |
| `POST /api/courts` | ❌ | ❌ | ✅ | ✅ |
| `PATCH /api/courts/{id}` | ❌ | ❌ | ✅ | ✅ |
| `DELETE /api/courts/{id}` (soft) | ❌ | ❌ | ❌ | ✅ |
| `PATCH /courts/slots/{id}/block` | ❌ | ❌ | ✅ | ✅ |
| `POST /api/courts/{id}/generate-slots` | ❌ | ❌ | ✅ | ✅ |
| `POST /api/courts/{id}/reviews` | ✅ own booking | ✅ | ❌ | ❌ |
| `GET /api/courts/{id}/reviews` | ✅ | ✅ | ✅ | ✅ |

#### Booking Service
| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `POST /api/bookings` | ✅ (email verified only) | ✅ | ✅ | ✅ |
| `GET /api/bookings/{id}` | ✅ own | ✅ own | ✅ all | ✅ all |
| `GET /api/bookings` (list, paginated) | ❌ | ❌ | ✅ | ✅ |
| `PATCH /api/bookings/{id}/cancel` | ✅ own | ✅ own | ✅ any | ✅ any |
| `DELETE /api/bookings/{id}` | ❌ | ❌ | ❌ | ✅ |

#### Matchmaking Service
| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `GET /api/matches` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/matches` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/matches/{id}/join` | ✅ | ✅ | ❌ | ❌ |
| `PATCH /api/matches/{id}/cancel` | ✅ own | ✅ own | ✅ any | ✅ any |
| `GET /api/matches` (all statuses) | ❌ | ❌ | ✅ | ✅ |

#### Payment Service
| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `POST /api/payments/initiate` | ✅ | ✅ | ❌ | ❌ — creates PENDING payment + returns bank QR info + countdown |
| `GET /api/payments/{id}` | ✅ own | ✅ own | ✅ all | ✅ all — includes bank account + order_code + expires_at |
| `POST /api/payments/{id}/proof` | ✅ own | ✅ own | ❌ | ❌ — upload transfer screenshot (multipart) → status → PROOF_SUBMITTED |
| `POST /api/payments/{id}/confirm` | ❌ | ❌ | ✅ | ✅ — STAFF confirms after checking bank statement → status → CONFIRMED |
| `POST /api/payments/{id}/reject` | ❌ | ❌ | ✅ | ✅ — STAFF rejects bad proof → status → EXPIRED, slot released |
| `POST /api/payments/{id}/refund` | ❌ | ❌ | ✅ | ✅ — manual bank transfer refund, records in manual_refunds |
| `GET /api/payments/pending-proofs` | ❌ | ❌ | ✅ | ✅ — admin queue of PROOF_SUBMITTED payments awaiting review |
| `GET /api/bank-accounts` | ✅ | ✅ | ✅ | ✅ — list active platform bank accounts for QR display |

#### Coach Service
| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `GET /api/coaches` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/coaches` (apply) | ❌ | ✅ own | ❌ | ✅ |
| `PATCH /api/coaches/{id}/approve` | ❌ | ❌ | ❌ | ✅ |
| `PATCH /api/coaches/{id}/suspend` | ❌ | ❌ | ✅ | ✅ |
| `POST /api/coaches/{id}/enroll` | ✅ (→ bank QR payment screen) | ❌ | ❌ | ❌ |
| `GET /api/coaches/{id}/enrollments` | ❌ | ✅ own | ✅ | ✅ |
| `POST /api/coaches/{id}/reviews` | ✅ (completed enrollment only) | ❌ | ❌ | ❌ |

#### Notification Service
| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `GET /api/notifications` | ✅ own | ✅ own | ✅ | ✅ |
| `POST /api/notifications/{id}/read` | ✅ own | ✅ own | ✅ | ✅ |
| `POST /api/notifications/read-all` | ✅ own | ✅ own | ✅ | ✅ |

---

## 7. State Machines

### `bookings.status`
```
PENDING ──► CONFIRMED ──► COMPLETED
   │
   └──► CANCELLED  (USER own | STAFF any | ADMIN any)
             │
             └── Cancellation Policy (manual bank refund by STAFF):
                  > 24h before:   100% refund
                  2h – 24h:        50% refund
                  < 2h:             0% refund
```

### `matches.status`
```
PENDING_PAYMENT ──► OPEN ──► FULL ──► COMPLETED
     │                │        │
     │                └────────┴──► CANCELLED  (HOST own | STAFF any | ADMIN any)
     └──► CANCELLED  (Proof EXPIRED or REJECTED | Timeout 10 min — Host never uploaded proof)
```

> **Prepay + Escrow**: Host must complete bank transfer + upload proof → STAFF confirms → `PENDING_PAYMENT → OPEN`.  
> Court Owner receives funds only when `match.status = COMPLETED` (manual settlement by STAFF/ADMIN).

### `coaches.status`
```
PENDING_APPROVAL ──► ACTIVE ──► SUSPENDED
                                    │
                                    └──► ACTIVE  (reinstate by ADMIN)
```

### `payments.status`
| Value | Triggered by |
|---|---|
| `PENDING` | System on `POST /api/payments/initiate` — shows bank QR + countdown timer |
| `PROOF_SUBMITTED` | User uploads transfer screenshot via `POST /api/payments/{id}/proof` |
| `CONFIRMED` | STAFF manually confirms after checking bank statement via admin panel |
| `EXPIRED` | Scheduler: payment not confirmed before `expires_at` → slot released |
| `REFUNDED` | STAFF manually processes bank transfer refund + records in `manual_refunds` |

---

## 8. Saga Pattern — Join Match Flow

The most complex technical area. When a user joins a match, 4 services + Escrow are involved. Any step can fail; the Saga's **compensating transactions** restore consistency.

### 8.1 Pre-Saga: Host Creates Match (Bank QR Prepay)

Before the match becomes `OPEN`, the Host must complete the **Bank Transfer Payment flow**:

| Step | Actor | Action |
|---|---|---|
| 1 | Host | Submits create-match form → `POST /api/matches` → match `PENDING_PAYMENT` |
| 2 | System | Creates `payments` record (status=PENDING, expires_at=NOW()+10min) → acquires Redis lock `lock:slot:{slotId}:match_create` TTL 10min |
| 3 | System | Returns payment screen data: `{ orderId, orderCode (#184), bankName, accountNumber, accountName, qrImageUrl, amount, expiresAt }` |
| 4 | Host | Scans QR / copies bank info → transfers `court_price` with transfer note = `orderCode` |
| 5 | Host | Uploads transfer screenshot via `POST /api/payments/{id}/proof` → payment `PROOF_SUBMITTED` |
| 6 | STAFF | Sees notification in admin panel → checks bank statement → clicks **Confirm** |
| 7 | payment-service | `payment.status → CONFIRMED` → publishes `match.host.payment.confirmed` Kafka event |
| 8 | escrow-service | Creates `escrow_accounts` record (status=HOLDING, amount=court_price) |
| 9 | System | `match.status → OPEN`, `time_slot → RESERVED`, Host auto-joins as `filled_slots=1`, Redis lock released |

> ⏱️ If proof not uploaded **before `expires_at`** (10 min), Scheduler auto-cancels: payment→EXPIRED, match→CANCELLED, slot released.

### 8.2 Saga — Player Joins Match (Bank QR Payment)

| Step | Service | Forward Action | Compensating Transaction |
|---|---|---|---|
| 1 | matchmaking-service | Redis `INCR` slot counter, check `<= totalSlots`; save `OutboxEvent` in same `@Transactional` | Redis `DECR` — return slot; delete `OutboxEvent` |
| 2 | payment-service | Create PENDING payment for `price_per_person` → return bank QR screen to Player; Player transfers + uploads proof → STAFF confirms → `payment → CONFIRMED` | If EXPIRED/REJECTED → release slot; publish compensating event |
| 2b | escrow-service | On `payment.player.confirmed`: record `PLAYER_REIMBURSEMENT`: credit `price_per_person` → Host wallet | Debit back from Host wallet; reverse Escrow log |
| 3 | booking-service | Write `MatchParticipant` to DB | Delete `MatchParticipant`, reset slot |
| 4 | notification-service | Push/email alert to Host and Player | Send cancellation notification (never silently drop) |

### 8.3 Escrow Settlement

> **Note**: All fund movements are tracked in `escrow_transactions`. Actual bank transfers are manual (STAFF initiates via admin panel + records in `manual_refunds`).

| Event | Escrow Action |
|---|---|
| Host bank transfer confirmed by STAFF | `escrow_accounts.status = HOLDING`; `court_price` logically held |
| Each Player bank transfer confirmed by STAFF | `PLAYER_REIMBURSEMENT` logged → Host wallet balance updated (`price_per_person` per join) |
| `match.status → COMPLETED` | `COURT_OWNER_SETTLEMENT` logged → STAFF manually transfers `court_price` to Court Owner's bank account |
| Host cancels match | `HOST_REFUND` + `PLAYER_REFUND` logged → STAFF manually transfers: each Player 100%; Host: `court_price − Σ(reimbursements)` |
| Player cancels (> 24h) | 100% refund logged → STAFF manually transfers to Player |
| Player cancels (2–24h) | 50% refund logged → STAFF manually transfers 50% |
| Player cancels (< 2h) | 0% refund — no transfer needed |

---

## 9. Distributed Systems Patterns

### 9.1 Transactional Outbox Pattern (matchmaking-service)

Prevents the Dual-Write Problem (DB save + Kafka send in separate transactions).

**Rule**: Save the `OutboxEvent` in the **same `@Transactional`** as the business record. A `@Scheduled` job (every 3s) polls `outbox_events WHERE status='PENDING'`, publishes to Kafka, marks as `SENT`.

```java
// matchmaking_db — guarantees at-least-once delivery
CREATE TABLE outbox_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic      VARCHAR(100) NOT NULL,        -- e.g. "match.slot.joined"
    payload    TEXT NOT NULL,               -- JSON
    status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | SENT
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at    TIMESTAMP
);
```

### 9.2 Idempotency Guard (booking-service)

Prevents duplicate processing when Kafka re-delivers messages.

```java
// booking_db — prevents duplicate event processing
CREATE TABLE processed_events (
    event_id     VARCHAR(255) PRIMARY KEY,  -- Kafka offset or UUID
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Pattern**: Before processing any Kafka event, check `processedEventRepo.existsById(event.getEventId())`. If exists → `ack.acknowledge()` and return early.

### 9.3 Distributed Lock for Slot Booking (Redis SETNX)

Prevents race condition where two users simultaneously claim the last slot.

- Key: `lock:slot:{slotId}` or `lock:match:{matchId}`
- TTL: **5 seconds**
- Implementation: Redis `SETNX` (SET if Not eXists)

### 9.4 Kafka Error Handling (notification-service)

Configure `DefaultErrorHandler` with exponential backoff. When retries are exhausted, the `Recoverer` **must** publish a compensating event — never silently drop.

```java
ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);  // 2s base, 2x multiplier
backOff.setMaxAttempts(3);  // Max 3 retries
// Recoverer: call compensationService.publishNotificationFailed(...)
```

### 9.5 Timeout Scheduler (matchmaking-service)

Auto-cancels stale PENDING_PAYMENT matches to prevent permanently stuck state.

```java
@Scheduled(cron = "0 */5 * * * *")  // Every 5 minutes
public void cancelExpiredMatches() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
    // Find PENDING_PAYMENT matches older than 15 min → CANCELLED + publish MatchCancelledEvent
    // Also release Redis lock:slot:{slotId}:match_create
}
```

### 9.7 Outbox & Event Cleanup Schedulers

Prevent unbounded table growth in production.

```java
// matchmaking-service — clean sent outbox events after 30 days
@Scheduled(cron = "0 0 2 * * *")
public void cleanOldSentOutboxEvents() {
    outboxRepo.deleteByStatusAndCreatedAtBefore("SENT", LocalDateTime.now().minusDays(30));
}

// booking-service — clean processed_events after 7 days (safe after Kafka retention)
@Scheduled(cron = "0 0 3 * * *")
public void cleanOldProcessedEvents() {
    processedEventRepo.deleteByProcessedAtBefore(LocalDateTime.now().minusDays(7));
}
```

### 9.8 Slot Auto-Generation (court-service)

Generates time slots in bulk so STAFF does not need to create them manually.

```java
// court-service — auto-generate 30 days of slots for all active courts
@Scheduled(cron = "0 0 0 * * *")   // Midnight every day
public void generateSlotsFor30Days() {
    courtRepo.findAllByIsActiveTrue().forEach(court -> {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to   = LocalDate.now().plusDays(30);
        slotGenerationService.generate(court, from, to, "06:00", "22:00", 60 /*minutes*/);
    });
}

// Manual trigger: POST /api/courts/{id}/generate-slots
// Body: { fromDate, toDate, openTime, closeTime, slotDurationMinutes }
```

### 9.9 Redis Circuit Breaker Fallback (Resilience4j)

If Redis is unavailable, fall back to DB-level optimistic locking instead of failing.

```java
@CircuitBreaker(name = "redis", fallbackMethod = "acquireDbLock")
public boolean acquireRedisLock(String key, int ttlSeconds) {
    return Boolean.TRUE.equals(redisTemplate.opsForValue()
        .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds)));
}

public boolean acquireDbLock(String key, int ttlSeconds, Exception ex) {
    // Fallback: SELECT FOR UPDATE on a lock_table row
    log.warn("Redis unavailable, using DB lock fallback for key={}", key);
    return dbLockRepository.tryAcquire(key, ttlSeconds);
}
```

### 9.10 Kafka Dead Letter Queue (DLQ)

After 3 retry failures, events are routed to a DLQ topic for manual replay — never silently dropped.

```java
// notification-service KafkaConfig
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (r, e) -> new TopicPartition(r.topic() + ".DLT", r.partition()));
    ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);
    backOff.setMaxAttempts(3);
    return new DefaultErrorHandler(recoverer, backOff);
}
// Admin can replay DLT messages via POST /api/admin/kafka/replay?topic=...
```

### 9.6 Zombie Event Check (matchmaking-service)

When Service B wakes up and processes a stale Kafka event for an already-CANCELLED match, do NOT proceed — publish a compensating event instead.

```java
@KafkaListener(topics = "booking.slot.confirmed", groupId = "matchmaking-service")
public void onSlotConfirmed(SlotConfirmedEvent event) {
    Match match = matchRepo.findById(event.getMatchId())...;
    if (match.getStatus() == MatchStatus.CANCELLED) {
        // ZOMBIE: publish CompensateSlotEvent to "match.compensate.slot"
        return;
    }
    // Normal processing...
}
```

---

## 10. Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `payment.host.confirmed` | payment-service | matchmaking-service, escrow-service | STAFF confirmed Host bank proof → Escrow records deposit, match → OPEN |
| `payment.host.expired` | payment-service (Scheduler) | matchmaking-service | Host proof not uploaded before `expires_at` → match → CANCELLED, slot released |
| `payment.player.confirmed` | payment-service | booking-service, escrow-service | STAFF confirmed Player bank proof → write Escrow PLAYER_REIMBURSEMENT |
| `payment.player.expired` | payment-service (Scheduler) | matchmaking-service, booking-service | Player proof expired → release slot counter |
| `match.slot.joined` | matchmaking-service (Outbox) | booking-service, payment-service | Slot reserved → trigger Player payment screen |
| `match.cancelled` | matchmaking-service | notification-service, booking-service, escrow-service | Notify all → log refunds in escrow → STAFF action queue for manual refund |
| `booking.slot.confirmed` | booking-service | matchmaking-service, notification-service, escrow-service | Participant written → Escrow reimburses Host |
| `match.completed` | matchmaking-service | escrow-service, notification-service | Log COURT_OWNER_SETTLEMENT → STAFF action queue for manual bank transfer to Court Owner |
| `match.compensate.slot` | matchmaking-service | booking-service, escrow-service | Zombie event compensation |
| `escrow.host.reimbursed` | escrow-service | notification-service | Notify Host wallet credited |
| `payment.proof.submitted` | payment-service | notification-service | Notify STAFF of new pending proof via push/email |
| `payment.refund.queued` | escrow-service | notification-service, payment-service | STAFF action needed: process manual bank refund |

---

## 11. Frontend Pages & Routes

| Route | Page | Description |
|---|---|---|
| `/` | `HomePage` | Hero banner, court search bar (district + date + type), recent open matches |
| `/courts` | `CourtsPage` | Grid of courts, filter sidebar (district, type, price range, geo radius), infinite scroll |
| `/courts/:id` | `CourtDetailPage` | Court photos, info, `SlotGrid` by date, court reviews + rating |
| `/matches` | `MatchesPage` | Match table, filter by skill level + date + sport + price, live "X slots left" badge |
| `/matches/:id` | `MatchDetailPage` | Match info, participants, real-time slot counter (Socket.io), Join Match button, Host escrow status |
| `/coaches` | `CoachesPage` | Coach card grid, search by specialty + available day |
| `/coaches/:id` | `CoachDetailPage` | Coach profile, rating, schedule, Enroll button (→ payment) |
| `/dashboard` | `DashboardPage` | Upcoming bookings, history, weekly stats, notification bell + unread count *(requires verified login)* |
| `/notifications` | `NotificationsPage` | Full notification list with read/unread status *(requires login)* |
| `/chat` | `ChatPage` | AI chatbot bubble UI → `POST /api/ai/chat` *(requires login)* |
| `/admin` | `AdminPage` | Admin dashboard: user list, courts, bookings, payments, coaches — STAFF/ADMIN only |
| `/admin/users` | `AdminUsersPage` | Search users, assign roles, soft delete |
| `/admin/courts` | `AdminCourtsPage` | Create/edit courts, generate slots, view reviews |
| `/admin/bookings` | `AdminBookingsPage` | All bookings, force cancel, record manual refund |
| `/admin/matches` | `AdminMatchesPage` | All match statuses, force close |
| `/admin/coaches` | `AdminCoachesPage` | Approve / suspend coaches |
| `/admin/payments` | `AdminPaymentsPage` | **Proof review queue** (PROOF_SUBMITTED list), Confirm / Reject proof, Record manual refund |

---

## 12. Claude Code Prompts (Cheat Sheet)

> Prompts are grouped by severity to match the production-readiness roadmap.

---

### 🔴 CRITICAL — Authentication & Security

#### Refresh Token System
> Implement Refresh Token in `user-service`. Add `refresh_token_hash VARCHAR(255)` and `refresh_token_expiry TIMESTAMP` to `users` table. On login: generate 30-day UUID refresh token, store bcrypt hash in DB, set as HttpOnly SameSite=Strict cookie. `POST /api/auth/refresh`: read cookie → verify hash → issue new 15-minute Access Token. `POST /api/auth/logout`: delete cookie + add jti to Redis blacklist. Show complete `JwtService`, `AuthController`, and Spring Security filter config.

#### Email Verification
> Add email verification to `user-service`. Add `is_email_verified BOOLEAN DEFAULT FALSE`, `email_verify_token VARCHAR(255)`, `email_verify_expiry TIMESTAMP` to `users` table. On `POST /api/auth/register`: generate UUID token, store in Redis with key `email:verify:{token}` TTL 24h, send via SendGrid with link `/api/auth/verify-email?token=`. Implement `GET /api/auth/verify-email?token=` handler. Add `@PreAuthorize` guard: only `is_email_verified=true` users can create bookings or join matches.

#### Forgot / Reset Password
> Implement forgot password in `user-service`. `POST /api/auth/forgot-password` Body `{ email }`: generate UUID token, store in Redis `password:reset:{token}` TTL 1h, send SendGrid email with link. `POST /api/auth/reset-password` Body `{ token, newPassword }`: verify token in Redis → bcrypt hash → UPDATE `users.password_hash` → delete Redis key → blacklist all active refresh tokens for this user. Add JUnit 5 tests for token expiry and reuse prevention.

#### Google OAuth2 Login
> Add Google OAuth to `user-service`. Add `google_id VARCHAR(255) UNIQUE`, `auth_provider VARCHAR(20) DEFAULT 'LOCAL'` to `users` table. `password_hash` becomes nullable. Implement `POST /api/auth/google Body: { idToken }`: verify Google ID token via Google's tokeninfo endpoint → upsert user (match on email) → issue JWT + Refresh Token. Configure `spring-boot-starter-oauth2-client` with client-id and client-secret from Google Cloud Console.

#### Manual Refund Flow (Bank Transfer)
> Implement manual refund in `payment-service`. `POST /api/payments/{id}/refund` (STAFF/ADMIN only): body `{ amount, toBankName, toAccountNumber, toAccountName, refundNote }`. Insert into `manual_refunds` table. Update `payments.status = REFUNDED`, set `payments.refund_amount`. Publish Kafka event `payment.refund.processed` → notification-service notifies user. Add `GET /api/payments/{id}/refund` to retrieve refund record. Show full `RefundController`, `ManualRefundService`, and JUnit 5 test for duplicate refund guard (a REFUNDED payment must reject a second refund attempt with 409).

---

### 🟠 HIGH — Operational Essentials

#### Slot Auto-Generation
> Implement `court-service` `SlotGenerationService.generate(courtId, fromDate, toDate, openTime, closeTime, slotDurationMinutes)`. Skip dates that already have slots for this court. Manual trigger: `POST /api/courts/{id}/generate-slots`. Auto-trigger: `@Scheduled(cron="0 0 0 * * *")` generates slots 30 days ahead for all active courts. Add `@DataJpaTest` for duplicate-skip logic.

#### File Upload — Cloudinary
> Implement `POST /api/upload/image` in `user-service` (or a shared upload endpoint at gateway). Accept `multipart/form-data`. Validate: mime type must be jpeg/png/webp, max 5MB. Upload to Cloudinary using Java SDK. Return `{ url, publicId }`. Add Cloudinary config to `application.yml` with `${CLOUDINARY_CLOUD_NAME}`, `${CLOUDINARY_API_KEY}`, `${CLOUDINARY_API_SECRET}`. Show validation + upload + response DTO.

#### Real Email + Push Notifications
> Implement notification channels in `notification-service`. Email: use SendGrid Java SDK. Push: use Firebase Admin SDK FCM. Add `fcm_token VARCHAR(500)` to `users` table (updated on app launch via `PATCH /api/users/{id}/fcm-token`). Template rendering: replace `{{matchId}}`, `{{courtName}}` placeholders from `notification_templates` MongoDB collection. Show `NotificationChannelRouter` that picks EMAIL vs PUSH based on template config and user preferences (`notification_email_enabled`, `notification_push_enabled`).

#### Cancellation Policy — Court Booking
> Implement `booking-service` `CancellationPolicyService.calculateRefundAmount(booking, cancelledAt)`. Snapshot `match_start_time` into `bookings` table at creation. Rules: >24h before → 100% refund; 2h–24h → 50%; <2h → 0%. On cancel: call `payment-service` refund API with calculated amount. Add JUnit 5 tests for all 3 policy tiers plus edge cases (exactly 24h, exactly 2h).

#### Slot Lock During Match Creation
> In `matchmaking-service` `MatchService.createMatch()`, after validating the slot: acquire Redis lock `lock:slot:{slotId}:match_create` with TTL 15 minutes (Host's payment window). If lock fails → 409 Conflict. When `match.host.payment.confirmed` arrives → reserve slot permanently and release lock. In `cancelExpiredMatches()` scheduler: also release the Redis lock for timed-out matches. Show complete flow with Resilience4j circuit breaker fallback.

#### Input Validation
> Add comprehensive Bean Validation to all request DTOs. `CreateMatchRequest`: `@Future` on `date`, `@Min(2) @Max(16)` on `totalSlots` plus custom `@Even` validator (only 2,4,6,8,10,12), `@Min(0) @Max(10_000_000)` on `pricePerPerson`. `RegisterRequest`: `@Size(min=8)` and `@Pattern` for strong password, `@Pattern(regexp="^(0|\\+84)[0-9]{8,9}$")` for VN phone. `CreateCourtRequest`: `@NotBlank` on name/address, `@DecimalMin("0")` on pricePerHour. Show custom `@Even` validator implementation.

#### Audit Log
> Implement audit logging in `user-service`. Create `audit_logs` table with: `actor_id`, `action`, `resource`, `resource_id`, `old_value TEXT (JSON)`, `new_value TEXT (JSON)`, `ip_address`, `created_at`. Create `@Aspect` `AuditLogAspect` that intercepts `@AuditLog`-annotated service methods, captures before/after state as JSON snapshots, and writes to `audit_logs`. Apply to: `cancelBooking`, `refundPayment`, `approveCoach`, `suspendCoach`, `assignRole`. Show aspect + annotation + `AdminAuditController GET /api/admin/audit-logs?resource=&actorId=`.

---

### 🟡 MEDIUM — Quality Features

#### Court Rating & Reviews
> Implement court reviews in `court-service`. Create `court_reviews` table: `court_id`, `user_id`, `booking_id UNIQUE`, `rating SMALLINT (1–5)`, `comment`, `created_at`. Add `rating DECIMAL(3,2)` and `total_reviews INT` to `courts` table. `POST /api/courts/{id}/reviews`: validate user has a COMPLETED booking for this court (1 review per booking). On save: recalculate `courts.rating` average. `GET /api/courts/{id}/reviews?page=0&size=10`. Show JPA query for average calculation.

#### Coach Enrollment with Payment
> Add payment to `coach-service` enrollment flow. `POST /api/coaches/{id}/enroll` → calls `payment-service` to create PENDING payment → returns `{ enrollmentId, paymentId, orderCode, bankName, accountNumber, qrImageUrl, expiresAt }` (Bank QR screen). STAFF confirms proof → `payment.status=CONFIRMED` → Kafka `payment.player.confirmed` → `coach_enrollments.status=CONFIRMED`, set `payment_id` and `total_paid`. On cancel: STAFF records manual refund per cancellation policy. `PATCH /api/coaches/{id}/enrollments/{enrollId}/cancel`. Show `EnrollmentService` with idempotency guard.

#### Notification Read Status
> Add read/unread status to `notification-service`. Add `is_read BOOL DEFAULT FALSE` and `read_at TIMESTAMP` to `notification_history` MongoDB collection. Endpoints: `GET /api/notifications?isRead=false&page=0&size=20` (returns unread count in header `X-Unread-Count`), `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`. Frontend: show badge count via `useNotifications()` hook polling every 30s (or via Socket.io event push).

#### Geo Search for Courts
> Add geo search to `court-service`. Add `latitude DECIMAL(9,6)` and `longitude DECIMAL(9,6)` to `courts` table. Create `PostGIS` extension or use earth distance formula via `ll_to_earth`. `GET /api/courts?lat=10.7769&lng=106.7009&radius=5000` (radius in meters) → sort by distance ASC, return `distanceMeters` field in response. Frontend: add "Tìm sân gần tôi" button using `navigator.geolocation.getCurrentPosition()`.

#### Admin Dashboard UI
> Create `AdminPage.tsx` in React 18 + TypeScript + Tailwind CSS. Route: `/admin` (protected, `ROLE_STAFF` or `ROLE_ADMIN`). Left sidebar nav: Users, Courts, Bookings, Matches, Coaches, Payments. Each section: data table with search, sort, pagination (`page`, `size` params). Actions per section: Users (assign role, soft delete), Courts (create, edit, generate slots), Bookings (force cancel + issue refund), Matches (force close), Coaches (approve, suspend), Payments (proof review queue, confirm/reject, record manual bank refund). Use `react-hot-toast` for action feedback.

---

### 🟢 INFRASTRUCTURE — Service Discovery (Eureka)

#### Bootstrap Eureka Server
> Create `eureka-server` Maven module. Add `spring-cloud-starter-netflix-eureka-server` dependency. Main class: `@SpringBootApplication @EnableEurekaServer`. `application.yml`: `server.port=8761`, `eureka.client.register-with-eureka=false`, `eureka.client.fetch-registry=false`, `eureka.server.enable-self-preservation=false`. Add `spring-boot-starter-actuator` for health endpoint. Show complete module structure including `pom.xml` inheriting from parent. Verify at http://localhost:8761.

#### Register All Services with Eureka
> Add `spring-cloud-starter-netflix-eureka-client` to each service's `pom.xml` (user, court, booking, matchmaking, payment, escrow, coach, notification, event, ai). In each `application.yml` add: `eureka.client.service-url.defaultZone=http://localhost:8761/eureka/`, `eureka.instance.prefer-ip-address=true`, `eureka.instance.lease-renewal-interval-in-seconds=10`. No `@EnableDiscoveryClient` annotation needed (Spring Boot auto-configures). Show a single service's full config as the reference template.

#### API Gateway Load-Balanced Routing via Eureka
> Configure `api-gateway` to route using Eureka `lb://` URIs instead of hardcoded `http://localhost:PORT`. Replace all static `uri:` entries in `application.yml` routes with `uri: lb://service-name` (where `service-name` matches `spring.application.name` of the target service). Add `spring-cloud-starter-netflix-eureka-client` to gateway's `pom.xml`. Add `@LoadBalanced` to `WebClient.Builder` bean if used. Show complete gateway `application.yml` with all 10 service routes.

#### Eureka in Docker Compose
> Add `eureka-server` container to `docker-compose.yml`. Service name: `eureka-server`, image: build from `./eureka-server`, ports: `8761:8761`, healthcheck: `curl -f http://localhost:8761/actuator/health`. Update all other service containers to add env var `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/` and `depends_on: eureka-server`. Show the full relevant docker-compose snippet.

---

### ⚙️ CORE PATTERNS (existing)

#### Fix Race Condition
> There's a race condition in `matchmaking-service` `MatchService.joinMatch()` — two users can claim the last slot simultaneously. Fix using Redis SETNX distributed lock with 5s TTL and Resilience4j circuit breaker fallback to DB SELECT FOR UPDATE. Show the full fixed method.

#### Add Outbox Pattern
> Add Transactional Outbox Pattern to `matchmaking-service`. Create `OutboxEvent` entity, `OutboxRepository`, `OutboxPublisherScheduler` (`@Scheduled` every 3s). Add cleanup scheduler: delete SENT events older than 30 days. Modify `MatchService.joinMatch()` to save outbox event in same `@Transactional` as match update.

#### Zombie Event Check
> In `matchmaking-service`, add Zombie Event check to the `onSlotConfirmed` `@KafkaListener`. If match status is `CANCELLED` when event arrives, publish `CompensateSlotEvent` to `'match.compensate.slot'` topic instead of processing.

#### Timeout Scheduler
> Add a `@Scheduled` job in `matchmaking-service` that runs every 5 minutes. Cancel all `PENDING_PAYMENT` matches older than 15 minutes. Publish `MatchCancelledEvent` to Kafka for each cancelled match. Also release Redis lock `lock:slot:{slotId}:match_create`. Add `@DataJpaTest` for the repository query.

#### Kafka DLQ + Error Handler
> Configure `DefaultErrorHandler` in `notification-service` `KafkaConfig`. Max 3 retries with exponential backoff (2s base, 2x multiplier). Use `DeadLetterPublishingRecoverer` to route failed events to `{topic}.DLT` topic. Add `POST /api/admin/kafka/replay?topic=` endpoint for manual DLT replay. Show complete `@Bean` config.

#### Escrow Service — Host Deposit
> Implement `escrow-service` `EscrowService.recordHostDeposit(matchId, courtOwnerId, courtPrice)`. When `match.host.payment.confirmed` Kafka event arrives: create `EscrowAccount` (status=HOLDING, amount=courtPrice). Include `@Transactional` + idempotency guard via `processed_events` table.

#### Escrow Service — Player Reimbursement
> Implement `escrow-service` `EscrowService.reimburseHost(escrowId, playerId, pricePerPerson)`. Triggered by `booking.slot.confirmed` Kafka event. Create `EscrowTransaction` (type=PLAYER_REIMBURSEMENT). Credit `pricePerPerson` to Host wallet. Publish `escrow.host.reimbursed` event for notification.

#### Escrow Service — Court Owner Settlement
> Implement `escrow-service` `EscrowService.settleCourtOwner(matchId)`. Triggered by `match.completed` Kafka event. Log `COURT_OWNER_SETTLEMENT` `EscrowTransaction`. Update `EscrowAccount.status = SETTLED`. Publish `payment.refund.queued` Kafka event → notification-service alerts STAFF to manually transfer `court_price` to Court Owner's bank account. Include `@Transactional` + idempotency guard. Show full service + test.

#### Escrow Service — Refund on Cancel
> Implement `escrow-service` `EscrowService.refundAll(matchId)`. Triggered by `match.cancelled`. For each Player: log `PLAYER_REFUND` `EscrowTransaction`. For Host: log `HOST_REFUND` for `court_price − Σ(reimbursements)`. Update `EscrowAccount.status = REFUNDED`. Publish `payment.refund.queued` events → STAFF sees refund action queue in `/admin/payments` and manually executes bank transfers + records in `manual_refunds`. Write individual `EscrowTransaction` records.

#### Create Match with Prepay + Slot Lock
> Implement `matchmaking-service` `MatchService.createMatch(hostId, request)`. Steps: (1) Acquire Redis lock `lock:slot:{slotId}:match_create` TTL 15 min; (2) Create match `status=PENDING_PAYMENT`, snapshot `court_price`; (3) Save `OutboxEvent` in same `@Transactional`; (4) Return `{ matchId, paymentUrl }`. On `match.host.payment.confirmed`: `status=OPEN`, Host auto-joins (`filled_slots=1`), reserve slot, release Redis lock.

#### React MatchDetailPage (with Escrow Status)
> Create `MatchDetailPage.tsx`. Display match info, skill badge, price/person, host info, participant avatars, real-time slot counter (Socket.io). If Host is viewing: show `"💰 Đã đặt cọc 400,000 VND · Đã hoàn: X VND"` from `/api/matches/:id/escrow-summary`. Show Join Match button if slot available + user not joined + `is_email_verified=true`. On click: call `joinMutation`, show loading, handle success/error with `react-hot-toast`.

#### React CreateMatchModal (Prepay)
> Create `CreateMatchModal.tsx`. Fields: court search, date + time slot (timeline grid), sport type, match format, skill level, `total_slots` (2/4/6/8), `price_per_person` (auto-suggest = `court_price ÷ total_slots`). Show live cost summary: `"💰 Bạn cần đặt cọc: X VND · Hoàn lại: Y VND/người join"`. Warn if `price_per_person × total_slots < court_price`. On submit: call `POST /api/matches` → redirect to Bank QR payment screen (show bank info + QR image + countdown 10 min + proof upload zone).

#### Write All Tests (booking-service)
> Write complete JUnit 5 tests for `booking-service` `BookingService` and `CancellationPolicyService`. Test: successful booking (slot available), slot already reserved (409), distributed lock timeout, DB save failure triggers compensating event, cancellation >24h (100% refund), cancellation 2–24h (50% refund), cancellation <2h (0% refund). Use `@ExtendWith(MockitoExtension.class)` and mock manual refund service calls.

---

## 13. Build Timeline (Production-Hardened)

> ⚠️ Updated timeline accounts for all Critical + High severity production requirements.

### Phase 0 — Pre-Launch Hardening (~1 week, parallel with Phase 1)

| Priority | Task | Effort |
|---|---|---|
| 🟢 I-00 | Eureka Server + all services register + Gateway `lb://` routing | 0.5 day |
| 🔴 C-01 | Refresh Token (15m access / 30d HttpOnly cookie) | 1 day |
| 🔴 C-02 | Email Verification (`is_email_verified`, SendGrid link) | 1 day |
| 🔴 C-03 | Forgot / Reset Password (Redis token TTL 1h) | 0.5 day |
| 🔴 C-04 | Manual Refund flow (Bank Transfer — `manual_refunds` table + STAFF queue) | 0.5 day |
| 🔴 C-05 | Cancellation Policy for Court Booking (time-based refund) | 0.5 day |
| 🔴 C-06 | Slot Lock on Match Create (Redis TTL 15min during Host payment) | 0.5 day |

### Phase 1 — Core Backend (Weeks 1–4)

| Week | Focus | Services | Goal |
|---|---|---|---|
| **Week 1** | Scaffold + **Eureka Server** + User (with OAuth, Refresh Token, Email Verify) + Court + Gateway (`lb://` routing) | `eureka-server`, `user`, `court`, `api-gateway` | Service discovery + Auth + OAuth working |
| **Week 2** | Booking (cancellation policy) + Matchmaking (Prepay + Escrow + Outbox) | `booking`, `matchmaking`, `escrow` | Join match + lock + escrow |
| **Week 3** | Payment (real refund) + Notification (SendGrid + FCM) + Kafka full flow | `payment`, `notification` | End-to-end payment + real notifications |
| **Week 4** | Saga robustness (Timeout + Zombie + DLQ + Redis fallback) + Slot Auto-Gen + Cloudinary | `matchmaking`, `booking`, `court` | All failure cases + file upload |

### Phase 2 — Extended Features (Weeks 5–7)

| Week | Focus | Services | Goal |
|---|---|---|---|
| **Week 5** | Coach (enrollment with payment) + Event Service (ticket sales) + AI Service + Audit Log | `coach`, `event-service`, `ai-service` | Coach search + coach payment + event tickets + chatbot |
| **Week 6** | Court Reviews + Geo Search + Notification Read Status + Waiting List | `court`, `notification`, `matchmaking` | Medium priority features |
| **Week 7** | Google OAuth + Granular Rate Limiting + Distributed Tracing (Zipkin) | `user`, `api-gateway` | Security + observability |

### Phase 3 — Frontend + Admin + Launch (Weeks 8–10)

| Week | Focus | Goal |
|---|---|---|
| **Week 8** | Frontend: Core pages (Home, Courts, Matches, Coaches, Dashboard) | Full web UI |
| **Week 9** | Frontend: Admin Dashboard (users, bookings, payments, courts, coaches) | STAFF/ADMIN can operate without Postman |
| **Week 10** | Tests (70/20/10 pyramid) + Docker Compose + Deploy + Monitoring | Production ready |

---

## 15. Booking Flow UI — Đặt Lịch (Court Booking)

> **Reference**: Screenshots from the production app at `datlich.alobo.vn/userBooking`.  
> This section documents the complete user-facing booking flow with two modes.

---

### 15.1 Booking Type Selection Modal — "Chọn hình thức đặt"

When a user taps **ĐẶT LỊCH** on a court detail page, a bottom-sheet modal appears with **two booking modes**:

| Option | Title (VI) | Title (EN) | Badge | Description |
|---|---|---|---|---|
| **A** | Đặt lịch ngày trực quan | Visual Day Booking | — | Book multiple time slots across multiple courts via a timeline grid |
| **B** | Đặt lịch sự kiện | Event Booking | 🆕 New | Join social or competitive events organized by the court owner |

**UI Details:**
- Option A card: light green background (`#e8f5e9`), green title, arrow CTA button
- Option B card: light pink/lavender background (`#fce4ec`), pink title, pink arrow CTA, "New" starburst badge (red)
- Modal has an `×` close button (top-right)
- Selecting either option navigates to the respective booking sub-flow

**React Component:**
```tsx
// BookingTypeModal.tsx
interface BookingTypeModalProps {
  courtId: string;
  isOpen: boolean;
  onClose: () => void;
  onSelectVisual: () => void;   // → /courts/:id/booking/visual
  onSelectEvent: () => void;    // → /courts/:id/booking/events
}
```

---

### 15.2 Visual Day Booking — "Đặt lịch ngày trực quan"

**Route**: `/courts/:id/booking/visual`  
**Purpose**: User selects specific courts and specific time windows for a chosen date.

#### UI Layout

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ← (back)          Đặt lịch ngày trực quan            [04/06/2026 📅]       │
├──────────────────────────────────────────────────────────────────────────────┤
│  ■ Trống  ■ Đã đặt  ■ Khoá  ! Sự kiện    [Xem sân & bảng giá]              │
├──────────────────────────────────────────────────────────────────────────────┤
│  ⚠️ Lưu ý: Nếu bạn cần đặt lịch tháng vui lòng liên hệ 0908 334 461...     │
├──────────────────────────────────────────────────────────────────────────────┤
│  Timeline Grid (horizontal scroll, time axis 5:00 → 22:00 in 30-min steps)  │
│  ┌────────┬──────────────────────────────────────────────────────┐          │
│  │ Sân 1  │ [free slots...] [booked:red] [event:purple]...       │          │
│  │ Sân 2  │ [free slots...] [booked:red] ...                     │          │
│  │ Sân 3  │ [free slots...] [event:purple] ...                   │          │
│  │ Sân 4  │ [free slots...] [booked:red] ...                     │          │
│  │ Sân 5  │ [booked:red] [free...] [event:purple] ...            │          │
│  └────────┴──────────────────────────────────────────────────────┘          │
├──────────────────────────────────────────────────────────────────────────────┤
│                            [slider / scroll indicator]                        │
│                         ╔══════════════════════╗                             │
│                         ║      TIẾP THEO       ║  (yellow CTA, full width)  │
│                         ╚══════════════════════╝                             │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Slot Color Legend

| Color | Status | `time_slots.status` value | Interaction |
|---|---|---|---|
| White / Light green | Available (Trống) | `AVAILABLE` | ✅ Tappable — user selects |
| Red / Pink | Already booked (Đã đặt) | `RESERVED` | ❌ Non-interactive |
| Grey | Blocked (Khoá) | `BLOCKED` | ❌ Non-interactive |
| Purple / Magenta | Event slot (Sự kiện) | `EVENT` *(new status)* | ℹ️ Shows event info tooltip |

> **Note**: The `EVENT` status is a new value added to `time_slots.status` enum in `court_db` to support event booking display on the visual grid.

#### Data Model Addition

```sql
-- Add EVENT to court_db time_slots status enum
ALTER TYPE slot_status ADD VALUE 'EVENT';

-- Optional: link the event ticket back to time_slots
ALTER TABLE time_slots ADD COLUMN event_id UUID REFERENCES events(id);
```

#### API Endpoints

```
GET  /api/courts/:courtId/slots?date=2026-06-04
     → Returns all time_slots for all courts for the given date
     → Response includes status (AVAILABLE | RESERVED | BLOCKED | EVENT)
     → EVENT slots include: event_id, event_title, ticket_price, slots_filled, total_slots

POST /api/bookings/visual
     Body: { courtId, slotIds: UUID[], date }
     → Creates PENDING booking, returns bookingId → navigate to payment
```

#### React Component

```tsx
// VisualBookingPage.tsx
interface TimeSlot {
  id: string;
  courtId: string;
  courtName: string;          // e.g. "Sân 1"
  startTime: string;          // "HH:mm"
  endTime: string;
  status: "AVAILABLE" | "RESERVED" | "BLOCKED" | "EVENT";
  eventId?: string;
  eventTitle?: string;        // shown on EVENT blocks, e.g. "[Xé vé] - SOCIAL: 0/16"
}

// Zustand store slice
interface VisualBookingStore {
  selectedDate: Date;
  selectedSlots: TimeSlot[];
  setDate: (date: Date) => void;
  toggleSlot: (slot: TimeSlot) => void;
  clearSelection: () => void;
}
```

---

### 15.3 Event Booking — "Đặt lịch sự kiện"

**Route**: `/courts/:id/booking/events`  
**Purpose**: Browse and purchase tickets to pre-organized social or competitive events.

#### UI Layout

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ← (back)              Đặt lịch sự kiện              [04/06 – 10/06  📅]   │
├──────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────┐  ┌─────────────────────────────┐           │
│  │ #2748: [Xé vé] - SOCIAL    │  │ #2749: [Xé vé] - SOCIAL    │           │
│  │ 04/06/2026                  │  │ 04/06/2026                  │           │
│  │ 19h–22h | Sân 1 - Sân 2    │  │ 20h–22h | Sân 3-4-5         │           │
│  │ 🏓 Pickleball  [1.0→2.5]   │  │ 🏓 Pickleball  [1.0→2.5]   │           │
│  │ [0/16]              ℹ️     │  │ [0/24]              ℹ️     │           │
│  │                    60k/Vé → │  │                    40k/Vé → │           │
│  └─────────────────────────────┘  └─────────────────────────────┘           │
│  ┌─────────────────────────────┐  ┌─────────────────────────────┐           │
│  │ #2746: [Xé vé] - SOCIAL    │  │ #2747: [Xé vé] - SOCIAL    │           │
│  │ 06/06/2026                  │  │ 06/06/2026                  │           │
│  │ 5h–8h | Sân 1-2-3-4-5      │  │ 13h–17h | Sân 1-2-3-4      │           │
│  │ 🏓 Pickleball  [1.0→2.5]   │  │ 🏓 Pickleball  [1.0→2.5]   │           │
│  │ [0/40]              ℹ️     │  │ [0/40]              ℹ️     │           │
│  │                    50k/Vé → │  │                    50k/Vé → │           │
│  └─────────────────────────────┘  └─────────────────────────────┘           │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Event Card Fields

| Field | Description | Example |
|---|---|---|
| `#<id>` | Sequential event number | `#2748` |
| `type` | Ticket format label | `[Xé vé]` = ticket-based entry |
| `format` | Social vs. competitive | `SOCIAL` |
| `date` | Event date | `04/06/2026` |
| `timeRange` | Start – end time | `19h – 22h` |
| `courts` | Courts involved | `Sân 1 - Sân 2` |
| `sport` | Sport type icon + label | 🏓 `Pickleball` |
| `skillRange` | Skill level bracket | `1.0 → 2.5` (DUPR rating) |
| `ticketsSold` | Current / total tickets | `0/16` |
| `pricePerTicket` | Cost per ticket | `60k/Vé` |
| `→` button | Navigate to event detail + ticket purchase | |
| `ℹ️` button | Show event info tooltip/modal | |

#### Database — `event_db` (new schema in `court-service` or separate `event-service`)

```sql
CREATE TABLE events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id          UUID NOT NULL,           -- References court in court_db
    created_by        UUID NOT NULL,           -- Staff or Admin user_id
    event_number      SERIAL UNIQUE,           -- Sequential display ID (#2748)
    title             VARCHAR(255) NOT NULL,   -- "[Xé vé] - SOCIAL"
    format            VARCHAR(50) NOT NULL,    -- SOCIAL | COMPETITIVE
    ticket_type       VARCHAR(50) NOT NULL,    -- XE_VE (ticket-based)
    sport             VARCHAR(50) NOT NULL,    -- PICKLEBALL | BADMINTON | etc.
    skill_min         DECIMAL(3,1),            -- Min DUPR/skill rating (e.g. 1.0)
    skill_max         DECIMAL(3,1),            -- Max DUPR/skill rating (e.g. 2.5)
    event_date        DATE NOT NULL,
    start_time        TIME NOT NULL,
    end_time          TIME NOT NULL,
    courts_involved   TEXT[],                  -- ["Sân 1", "Sân 2"]
    total_tickets     INT NOT NULL,
    tickets_sold      INT NOT NULL DEFAULT 0,
    price_per_ticket  INT NOT NULL,            -- in VND thousands (e.g. 60000)
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN | FULL | CANCELLED | COMPLETED
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE event_tickets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES events(id),
    user_id     UUID NOT NULL,              -- Buyer
    quantity    INT NOT NULL DEFAULT 1,
    total_paid  INT NOT NULL,
    payment_id  UUID,                       -- References payment_db
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | CONFIRMED | CANCELLED | REFUNDED
    purchased_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### API Endpoints

```
GET  /api/events?courtId=&dateFrom=&dateTo=&sport=&status=OPEN
     → List events for date range, optional filters
     → Returns EventCard[] sorted by event_date ASC

GET  /api/events/:eventId
     → Full event detail including participant count, courts, description

POST /api/events/:eventId/tickets
     Body: { quantity: 1 }
     → Purchase ticket(s) — checks availability, initiates payment
     → Returns { ticketId, paymentId, orderCode, bankName, accountNumber, qrImageUrl, expiresAt }
     → Frontend shows Bank QR payment screen; STAFF confirms proof

GET  /api/events/:eventId/my-tickets
     → User's purchased tickets for this event (requires auth)
```

#### Date Range Picker

The top-right date range picker (e.g. `04/06 – 10/06 📅`) filters the event list to a 7-day window. Default: current week.

```tsx
// EventBookingPage.tsx
interface EventBookingPageProps {
  courtId: string;
}

interface EventCard {
  id: string;
  eventNumber: number;         // Display as #2748
  title: string;               // "[Xé vé] - SOCIAL"
  format: "SOCIAL" | "COMPETITIVE";
  sport: string;               // "Pickleball"
  sportIcon: string;           // emoji or asset URL
  skillRange: [number, number]; // [1.0, 2.5]
  eventDate: string;           // "04/06/2026"
  timeRange: string;           // "19h – 22h"
  courts: string[];            // ["Sân 1", "Sân 2"]
  ticketsSold: number;
  totalTickets: number;
  pricePerTicket: number;      // VND
}
```

---

### 15.4 Booking Flow State Machine (Updated)

```
USER taps "ĐẶT LỊCH"
        │
        ▼
[Chọn hình thức đặt] modal
        │
   ┌────┴────┐
   │         │
   ▼         ▼
VISUAL     EVENT
BOOKING    BOOKING
   │         │
   │         ├─ Browse event list (week filter)
   │         ├─ View event detail (ℹ️)
   │         └─ Purchase ticket → Bank QR Payment
   │                   │         (upload proof → STAFF confirm)
   ▼                   │
Select courts          │
+ time slots           │
on timeline grid       │
   │                   │
   ▼                   ▼
[TIẾP THEO]      CONFIRMED TICKET
   │              (email + push notification)
   ▼
Bank QR Payment
(upload proof → STAFF confirm)
   │
   ▼
CONFIRMED BOOKING
(email + push notification)
```

---

### 15.5 Updated RBAC for Events

| Endpoint | USER | COACH | STAFF | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `GET /api/events` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/events` (create) | ❌ | ❌ | ✅ | ✅ |
| `PATCH /api/events/:id` | ❌ | ❌ | ✅ | ✅ |
| `DELETE /api/events/:id` | ❌ | ❌ | ❌ | ✅ |
| `POST /api/events/:id/tickets` (buy) | ✅ | ✅ | ❌ | ❌ |
| `GET /api/events/:id/tickets` (all) | ❌ | ❌ | ✅ | ✅ |
| `GET /api/events/:id/my-tickets` | ✅ own | ✅ own | ✅ | ✅ |

---

### 15.6 Claude Code Prompts — Booking Flow

#### Event Booking List Page
> Create `EventBookingPage.tsx` in React 18 + TypeScript + Tailwind CSS. Route: `/courts/:courtId/booking/events`. Fetch events from `GET /api/events?courtId=&dateFrom=&dateTo=` using `useQuery`. Show a week date-range picker at top-right. Render a 2-column grid of `EventCard` components. Each card: dark green background, event number (#2748), title, date, time range, courts, sport icon+name, skill range badge (e.g. "1.0→2.5"), sold/total counter, price/ticket, info icon button, and arrow CTA button. Show "sold out" overlay when `ticketsSold >= totalTickets`.

#### Visual Timeline Grid Component
> Create `VisualBookingGrid.tsx` in React 18 + TypeScript + Tailwind CSS. Props: `courts: Court[]`, `slots: TimeSlot[]`, `selectedSlots: string[]`, `onToggleSlot: (slotId) => void`. Render a horizontal-scrollable grid: rows = courts (Sân 1–N), columns = 30-min time windows from 05:00 to 23:00. Color cells by status: white=AVAILABLE (selectable), red=RESERVED, grey=BLOCKED, purple=EVENT (show event title on hover). Selected cells highlighted in green. Show legend bar at top. Fixed left column for court labels. Bottom sticky "TIẾP THEO" yellow button disabled until ≥1 slot selected.

#### Booking Type Selection Modal
> Create `BookingTypeModal.tsx` in React 18 + TypeScript + Tailwind CSS. A centered modal with `×` close button. Two option cards: (1) green-tinted card "Đặt lịch ngày trực quan" with description and green arrow button; (2) pink-tinted card "Đặt lịch sự kiện" with description, pink arrow button, and a red "New" starburst badge at top-right corner. Clicking each card calls `onSelectVisual()` or `onSelectEvent()` respectively.

---

## 14. Setup & Prerequisites

```bash
# Install Claude Code
npm install -g @anthropic/claude-code
# Set API key in ~/.zshrc
export ANTHROPIC_API_KEY=sk-ant-...

# Prerequisites
# Java 21 (JDK) — https://adoptium.net
# Maven 3.9+ — https://maven.apache.org
# Docker Desktop — https://www.docker.com/products/docker-desktop
# Node.js 20 LTS — https://nodejs.org
# IntelliJ IDEA (recommended)

# Verify
java -version && mvn -version && docker --version

# Bootstrap project
mkdir badminton-hub && cd badminton-hub
git init
docker-compose up -d    # Start all infra (PG, Redis, Mongo, Kafka)
claude                  # Start Claude Code session
```

### Initial Claude Code Scaffold Prompt
> Create a Maven multi-module Spring Boot 3.x project for BadmintonHub. Parent `pom.xml` with Java 21, Spring Boot 3.2.x, `spring-cloud 2023.x` BOM. Modules: `common`, `eureka-server`, `api-gateway`, `user-service`, `court-service`, `booking-service`, `matchmaking-service`, `coach-service`, `payment-service`, `escrow-service`, `notification-service`, `event-service`, `ai-service`. `eureka-server`: add only `spring-cloud-starter-netflix-eureka-server` + `spring-boot-starter-actuator`, annotate main class `@EnableEurekaServer`, port 8761. All other services (except `common`): `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-cloud-starter-netflix-eureka-client`, `spring-kafka`, postgresql driver, lombok, `spring-boot-starter-test`. `api-gateway`: replace web with `spring-cloud-starter-gateway`, add `spring-cloud-starter-netflix-eureka-client`, configure routes via `lb://service-name`. Add `docker-compose.yml` with: PostgreSQL 15 (9 separate databases), Redis 7, MongoDB 7, Kafka 3.6, Zookeeper, Zipkin, and `eureka-server` container (built from `./eureka-server`, `depends_on` for all services). Add `.gitignore` for Maven.
