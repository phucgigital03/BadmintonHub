<div align="center">

# BadmintonHub

**A microservices platform for badminton court booking, real-time support chat, and Bank-QR payments in Vietnam — built solo, backend-first, with production-grade reliability patterns at every layer.**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.1-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.6-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-FF6600?style=flat-square&logo=rabbitmq&logoColor=white)](https://www.rabbitmq.com/)
[![MongoDB](https://img.shields.io/badge/MongoDB-7-47A248?style=flat-square&logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)

[![CI](https://github.com/phucgigital03/BadmintonHub/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/phucgigital03/BadmintonHub/actions/workflows/ci.yml)
[![Terraform](https://github.com/phucgigital03/BadmintonHub/actions/workflows/terraform.yml/badge.svg?branch=main)](https://github.com/phucgigital03/BadmintonHub/actions/workflows/terraform.yml)

**[📦 GitOps Repository](https://github.com/phucgigital03/BadmintonHub-GitOps)** · **[🚀 CI/CD Overview](https://claude.ai/code/artifact/7e3f764d-8809-40d0-9510-cb54693cdfcd)**

*Deployment lives in a separate GitOps repo — Terraform → EKS · GitHub Actions → ECR · Helm + ArgoCD.*

</div>

---

## Table of Contents

- [Overview](#overview)
- [Engineering Highlights](#engineering-highlights)
- [Architecture](#architecture)
- [Services](#services)
- [Tech Stack](#tech-stack)
- [Key Engineering Decisions](#key-engineering-decisions)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Roadmap](#roadmap)
- [Known Gaps](#known-gaps)
- [Project Structure](#project-structure)

---

## Overview

BadmintonHub is a microservices system for badminton court booking, real-time customer support, and Bank-QR payments, built from scratch for the Vietnamese market. It's built **backend-first**: every service is designed against a written spec, implemented, tested against real infrastructure (PostgreSQL / Redis / MongoDB / Kafka via Testcontainers — never H2, never a mocked database), and verified end-to-end before the next one starts.

**6 services are fully implemented and tested.** 5 more are scaffolded — registered with Eureka, routed through the Gateway, each with a dedicated database — as an architecture roadmap (see [Roadmap](#roadmap)). Nothing below is aspirational; every claim is backed by a controller, a test file, or a config you can go read right now.

---

## Engineering Highlights

What I'd point a reviewer to first:

- **Zero silent Kafka failures.** Every consumer that can fail is backed by a Dead Letter Topic *and* a dedicated monitor (`SlotDeadLetterMonitor`, `PaymentDeadLetterMonitor`, `BookingDeadLetterMonitor`) — a poisoned message never just disappears, it surfaces as an alertable `[DLT]` log line.
- **Transactional Outbox, not `KafkaTemplate.send()`.** `booking-service`, `payment-service`, and `escrow-service` write every event to an `outbox_events` row in the *same transaction* as the business change; a 3-second scheduler publishes it and marks it `SENT`. No dual-write problem, guaranteed at-least-once delivery.
- **Idempotency by construction.** A `processed_events` guard table in 4 services makes replayed or duplicated Kafka messages provably safe to re-consume.
- **5 rounds of self-directed money-safety audits** on the booking↔payment saga before calling it pilot-ready — each pass re-read the real code against the spec hunting one specific failure class, and found real bugs: a payment proof uploaded *after* the hold window expired used to be silently rejected with the money already transferred (fixed with a salvage path that flags the payment for manual refund instead of dropping it); a booking cancel racing a payment confirm could lose-update each other (fixed with `SELECT … FOR UPDATE` row locks on every money-touching transition); a payment confirmed *after* its booking was already cancelled used to leave an orphaned "confirmed" payment (fixed with zombie-event compensation that flags it for review instead of silently processing it). Nothing on the money path fails silently anymore.
- **Real-time support chat on MongoDB + STOMP over WebSocket**, horizontally scalable through a RabbitMQ STOMP relay with no sticky sessions, atomic `findAndModify` state transitions (Mongo's answer to `SELECT FOR UPDATE`), a partial-unique index that closes an open/duplicate-thread race condition, and per-frame WebSocket authorization so one user can't spoof another's delivery receipt.
- **Defense-in-depth JWT.** Every token is validated at the Gateway *and* independently re-validated by the receiving service — no service trusts a forwarded identity header.
- **Test discipline.** Integration tests run against real Testcontainers-backed PostgreSQL, Redis, and MongoDB — never H2, never a mocked database dressed up as an "integration test".

---

## Architecture

```
                               ┌──────────────────────┐
                               │     React Frontend    │
                               │   (Vite + React 19)   │
                               └──────────┬───────────┘
                                          │ HTTP / STOMP-WS / Socket.io
                               ┌──────────▼───────────┐
                               │      API Gateway      │  ← JWT validation
                               │   Spring Cloud GW     │  ← rate limiting
                               │      Port 3000        │  ← lb:// routing
                               └──────────┬───────────┘
                                          │
   ┌───────────┬───────────────┬─────────┼─────────┬──────────────┬────────────┐
   │           │               │                   │              │            │
┌──▼───┐   ┌───▼────┐    ┌─────▼───┐        ┌──────▼────┐   ┌─────▼────┐  ┌────▼────┐
│ user │   │ court  │    │ booking │        │  payment   │   │  escrow  │  │  chat   │
│ 3001 │   │ 3002   │    │  3003   │        │   3006     │   │  3007    │  │  3011   │
│Auth/ │   │Slots/  │    │Saga/    │        │Bank-QR/CSV │   │ Ledger/  │  │STOMP/WS │
│JWT   │   │Geo     │    │Redis lock│       │reconcile   │   │ Outbox   │  │+ Mongo  │
└──────┘   └───┬────┘    └────┬────┘        └─────┬──────┘   └────┬─────┘  └────┬────┘
               │               │                   │               │            │
               └───────┬───────┴───────────────────┴───────────────┘            │
                       │                                                        │
                 Apache Kafka                                          RabbitMQ (STOMP relay)
           (Outbox + DLT on every producer path)                     horizontal chat scaling
                       │
                       ▼
        Eureka (registry, 8761) · Zipkin (tracing, 9411)

  ┌───────────────────────────────────────────────────────────────────────┐
  │  Roadmap — scaffolded, not yet implemented (Eureka + Gateway wired)   │
  │  matchmaking (3004) · coach (3005) · notification (3008)             │
  │  event (3009) · ai (3010)                                            │
  └───────────────────────────────────────────────────────────────────────┘
```

**Service Discovery:** every service registers with Spring Cloud Netflix Eureka (8761); the Gateway resolves upstreams via `lb://service-name` — no hardcoded URLs anywhere.

---

## Services

### ✅ Built & Tested

| Service | Port | What it does | Tests |
|---|---|---|---|
| `user-service` | 3001 | Register, email verify, JWT login/refresh/logout, Google OAuth2, forgot/reset password, user CRUD | — *(see [Known Gaps](#known-gaps))* |
| `court-service` | 3002 | Club/court catalog, 30-min slot grid auto-generation, geo search, pricing rules, DLT monitor | 2 test files |
| `booking-service` | 3003 | Multi-slot booking orders, Redis distributed locking, Outbox saga → court-service, tiered cancellation refunds, rate limiting | 2 test files · 9 unit tests |
| `payment-service` | 3006 | Bank-QR payment flow, Cloudinary proof upload, STAFF confirm/reject/refund, bank-statement CSV reconciliation import & lookup | 7 test files · 23 unit + 5 integration tests |
| `escrow-service` | 3007 | Prepay + escrow ledger for match payments (host deposit → player reimbursement → court-owner settlement) | dormant *(see [Known Gaps](#known-gaps))* |
| `chat-service` | 3011 | Real-time STAFF↔customer support chat over STOMP/WebSocket, image attachments, rate limiting, RabbitMQ relay for horizontal scale | 9 test files · 21 unit + 22 integration tests |

### 🚧 Roadmap — scaffolded only

See [Roadmap](#roadmap) below.

---

## Tech Stack

### Backend

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 · Spring Cloud 2023.0.1 |
| API Gateway | Spring Cloud Gateway (reactive, WebFlux) |
| Service Discovery | Spring Cloud Netflix Eureka |
| Message Broker | Apache Kafka 3.6 + Zookeeper (event bus) · RabbitMQ 3 with STOMP plugin (chat WebSocket relay) |
| Cache & Locks | Redis 7 — distributed locks (`SETNX`), rate limiting, JWT blacklist |
| Relational DB | PostgreSQL 15 — one isolated database per service (×9 instances) |
| Document DB | MongoDB 7 — ×2 instances (chat messages, notification read-receipts) |
| Resilience | Resilience4j — circuit breaker + retry, Redis-lock-to-DB-lock fallback |
| Security | Spring Security · JWT (HS256) · Google OAuth2 · method-level RBAC |
| File Storage | Cloudinary — payment proof screenshots & chat images |
| Email | SendGrid (`user-service`, console-log fallback in dev) |
| Distributed Tracing | Zipkin |
| Build | Maven — 16-module multi-module parent POM |

### Frontend

| Layer | Technology |
|---|---|
| Language | TypeScript 6 |
| Framework | React 19 + Vite |
| Styling | Tailwind CSS v4 |
| Server State | TanStack Query v5 |
| Client State | Zustand v5 |
| Forms | React Hook Form + Zod v4 |
| HTTP | Axios — silent-refresh interceptor on 401 |
| Real-time | `@stomp/stompjs` (support chat) · Socket.io-client (matchmaking, currently mock data) |
| Routing | React Router v7 |
| i18n | react-i18next (Vietnamese + English) |

### Infrastructure

| Tool | Purpose |
|---|---|
| Docker Compose | 18 containers: 9× PostgreSQL, 2× MongoDB, Redis, Kafka + Zookeeper, RabbitMQ, Zipkin, Elasticsearch, Kafka UI |
| spring-dotenv | Loads the root `.env` into every service's Spring Environment at startup |

*Elasticsearch is provisioned in Docker Compose and already declared in `coach-service`'s POM for the roadmap coach-search feature — it isn't exercised by any built service yet.*

---

## Key Engineering Decisions

### 1. Database-per-service, strict isolation
Every service owns its own database (PostgreSQL or MongoDB). Cross-service references are plain `UUID` columns — no foreign keys across service boundaries, no cross-database joins. Consistency comes from Kafka events, never shared tables.

### 2. Transactional Outbox (`booking-service`, `payment-service`, `escrow-service`)
Kafka events are written to an `outbox_events` table in the **same `@Transactional`** as the business record. A scheduled job polls pending rows every 3 seconds and publishes them. This removes the dual-write problem and guarantees at-least-once delivery without a distributed transaction.

### 3. Idempotency guards (`court-service`, `booking-service`, `payment-service`, `escrow-service`)
A `processed_events` table, keyed by event id, makes every Kafka consumer safe to re-run — replays from a Dead Letter Topic never double-apply a state change.

### 4. Dead Letter Topics with active monitoring
Every consumer retries 3× with exponential backoff (2s → 4s → 8s) before a `DeadLetterPublishingRecoverer` routes the message to `{topic}.DLT`. Three services (`court`, `booking`, `payment`) run a dedicated monitor that turns a dead-lettered message into an `ERROR`-level log line — a failure is never just dropped silently.

### 5. Five rounds of money-safety auditing on the booking↔payment saga
Before calling the booking flow pilot-ready, I re-read the real implementation against the spec five separate times, each pass hunting one specific failure class. Concrete bugs found and fixed: a payment proof uploaded *after* the hold window expired used to be silently rejected with the money already transferred (fixed with a salvage path that flags the payment for manual refund instead of dropping it); a booking cancel racing a payment confirm could lose-update each other (fixed with `SELECT … FOR UPDATE` row locks on every money-touching transition); a payment confirmed *after* its booking was already cancelled used to leave an orphaned "confirmed" payment (fixed with zombie-event compensation). Every money-touching transition is now either row-locked or paired with a compensating event.

### 6. Prepay + Escrow model (`escrow-service`)
Designed so a court owner is only paid once a match reaches `COMPLETED` — the host's upfront payment sits in escrow and is reimbursed proportionally as each player pays in. Fully implemented (ledger, 5 transaction types, Outbox, idempotency) but currently dormant: it has no events to consume until `matchmaking-service` (roadmap) exists to produce them.

### 7. No third-party payment gateway
Payment is Bank QR + manual proof upload (Cloudinary) + STAFF confirmation — no VNPay, no Stripe, no payment API integration. Refunds are staff-executed bank transfers, recorded in a `manual_refunds` table. A deliberate scope choice for the Vietnamese SMB court-owner market.

### 8. Real-time chat: MongoDB atomic transitions, not application-level locking
`chat-service` has no SQL row locks available — it's Mongo — so every conversation-state transition (claim / transfer / release / close) uses an atomic `findAndModify` with the expected prior state in the filter, Mongo's equivalent of `SELECT … FOR UPDATE`. A partial-unique index on `customerId` where `status IN (OPEN, ASSIGNED)` guarantees a customer can never have two active support threads, even under concurrent "start chat" clicks.

### 9. Defense-in-depth JWT
- **Access token:** 15-minute TTL, validated at the Gateway *and* re-validated by every downstream service against the shared `common-security` `JwtUtil`.
- **Refresh token:** 30-day TTL, stored as a bcrypt hash, delivered via an `HttpOnly SameSite=Strict` cookie, rotated on every refresh.
- **Logout blacklist:** revoked `jti` stored in Redis with TTL = remaining token lifetime (fail-open if Redis is down — the Gateway must stay up).
- The Gateway forwards only the raw `Authorization: Bearer` token — never `X-User-Id` / `X-User-Roles` headers. The token is the single source of identity.

### 10. Horizontal scaling for chat without sticky sessions
Swapping Spring's in-memory STOMP broker for `enableStompBrokerRelay` pointed at RabbitMQ's STOMP plugin means multiple `chat-service` instances can share WebSocket fan-out through the broker's user registry — no sticky-session load balancer required, and the application/security code didn't have to change to get there.

---

## Getting Started

### Prerequisites
- Java 21
- Maven 3.9+
- Docker & Docker Compose
- Node.js 20+ (frontend)

### 1. Clone & configure environment
```bash
git clone https://github.com/your-username/badmintonhub.git
cd badmintonhub
cp .env.example .env
# Required for the 6 built services: JWT_SECRET, GOOGLE_CLIENT_ID/SECRET,
# SENDGRID_API_KEY, CLOUDINARY_*, RABBITMQ_*, MONGODB_URI / MONGODB_CHAT_URI
```

### 2. Start infrastructure (18 containers)
```bash
docker compose up -d
# 9× PostgreSQL · 2× MongoDB · Redis · Kafka + Zookeeper · RabbitMQ · Zipkin · Elasticsearch · Kafka UI
```

### 3. Build all modules
```bash
mvn clean install -DskipTests
# 16 modules: common(-security/-test), eureka-server, api-gateway,
# and 12 business services (6 built, 5 scaffolded)
```

### 4. Run services
```bash
mvn -pl eureka-server spring-boot:run     # start first

# then, each in its own terminal
mvn -pl api-gateway spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl court-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl escrow-service spring-boot:run
mvn -pl chat-service spring-boot:run
```

### 5. Frontend
```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

### 6. Run the test suite
```bash
mvn -pl payment-service verify     # unit + Testcontainers integration tests
mvn -pl chat-service verify        # same, against real Mongo + Redis containers
```

### Observability

| Tool | URL |
|---|---|
| Eureka Dashboard | http://localhost:8761 |
| Zipkin Tracing | http://localhost:9411 |
| Kafka UI | http://localhost:8080 |
| RabbitMQ Management | http://localhost:15672 |

---

## API Reference

All routes go through the API Gateway at `http://localhost:3000`.

### Auth & Users — `user-service`
| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register with email + password |
| GET | `/api/auth/verify-email` | Verify email via token |
| POST | `/api/auth/login` | Login, sets refresh-token cookie |
| POST | `/api/auth/google` | Google OAuth2 login |
| POST | `/api/auth/forgot-password` | Trigger reset email (no user-enumeration) |
| POST | `/api/auth/reset-password` | Reset password with token |
| POST | `/api/auth/refresh` | Rotate access/refresh token via cookie |
| POST | `/api/auth/logout` | Revoke JWT `jti`, clear cookie |
| GET / PATCH | `/api/users/{id}` | Get / update user (self or STAFF/ADMIN) |
| GET | `/api/users` | Paged user list (STAFF/ADMIN) |
| DELETE | `/api/users/{id}` | Soft-delete user (ADMIN) |

### Courts & Clubs — `court-service`
| Method | Path | Description |
|---|---|---|
| GET | `/api/clubs` | Search clubs (district / sport / geo, Redis-cached) |
| GET | `/api/clubs/{id}` | Get club by id |
| GET | `/api/clubs/{id}/courts` | List courts of a club |
| GET | `/api/clubs/{id}/slots` | Visual day-booking slot grid |
| POST | `/api/clubs` · `/api/clubs/{id}/pricing` | Create club / pricing rule (STAFF/ADMIN) |
| POST | `/api/clubs/{id}/generate-slots` | Generate next-30-days slots (STAFF/ADMIN) |
| POST | `/api/courts` | Add court to a club (STAFF/ADMIN) |
| PATCH | `/api/courts/slots/{slotId}/block` | Block a slot (STAFF/ADMIN) |

### Bookings — `booking-service`
| Method | Path | Description |
|---|---|---|
| POST | `/api/bookings` | Create booking order (email-verified only) |
| GET | `/api/bookings/{id}` · `/api/bookings` | Get / list bookings (owner or STAFF/ADMIN) |
| POST | `/api/bookings/{id}/cancel` | Cancel booking (tiered refund policy) |
| POST | `/api/bookings/{id}/begin-payment` | Payment-service handshake — re-anchors the slot hold |

### Payments & Reconciliation — `payment-service`
| Method | Path | Description |
|---|---|---|
| POST | `/api/payments/initiate` | Generate Bank QR + countdown |
| POST | `/api/payments/{id}/proof` | Upload transfer screenshot (Cloudinary) |
| GET | `/api/payments/{id}/proofs` | List proofs for a payment |
| POST | `/api/payments/{id}/confirm` \| `/reject` \| `/refund` | STAFF/ADMIN payment actions |
| GET | `/api/payments/pending-review` \| `/refund-required` | STAFF review queues |
| POST | `/api/bank-transactions/import` | Import bank-statement CSV, deduped by `bank_ref` |
| GET | `/api/bank-transactions/lookup` | Look up a transaction by order code + amount |

### Escrow — `escrow-service`
| Method | Path | Description |
|---|---|---|
| GET | `/api/escrow/settlements/pending` \| `/refunds/pending` | STAFF settlement/refund queues |
| GET | `/api/escrow/{matchId}` | Get escrow ledger for a match |

### Real-time Chat — `chat-service`
| Method | Path | Description |
|---|---|---|
| POST | `/api/chat/conversations` | Open (or resume) my support thread |
| GET | `/api/chat/conversations` | Inbox / unassigned queue (STAFF) or my threads |
| GET | `/api/chat/conversations/{id}/messages` | Keyset message history |
| POST | `/api/chat/conversations/{id}/messages` | Send text (idempotent by `clientMsgId`) |
| POST | `/api/chat/conversations/{id}/images` | Send image attachment (multipart) |
| PATCH | `/api/chat/conversations/{id}/read` | Mark thread read |
| POST | `/api/chat/conversations/{id}/claim` \| `/transfer` \| `/release` \| `/close` | STAFF thread lifecycle |
| STOMP | `/app/conv/{id}/delivered` \| `/typing` | Delivery ACK · typing indicator (WebSocket) |

---

## Roadmap

These 5 services are scaffolded — registered with Eureka, routed through the Gateway, with a dedicated database provisioned — but contain no business logic yet beyond the `@SpringBootApplication` entrypoint:

| Service | Port | Planned scope |
|---|---|---|
| `matchmaking-service` | 3004 | Saga-coordinated match creation & the prepay economy — the Kafka producer that wakes `escrow-service` up |
| `coach-service` | 3005 | Elasticsearch-backed coach search & enrollment (dependency already declared) |
| `notification-service` | 3008 | Kafka-driven SendGrid/FCM delivery, MongoDB read-receipts |
| `event-service` | 3009 | Social/competitive event ticketing |
| `ai-service` | 3010 | **Designed, not built:** a LangGraph agent that reconciles a payment-proof screenshot against real bank statements — reads the receipt with a multimodal LLM, checks it against `payment-service`'s bank-transaction lookup endpoint (already live), and hands staff a deterministic HIGH/MEDIUM/LOW match verdict instead of auto-approving anything |

---

## Known Gaps

Being upfront about what isn't done yet:

- **No automated tests** for `user-service` or `escrow-service` — every other built service has a Testcontainers-backed unit + integration suite.
- **`escrow-service` is functionally dormant** — the ledger logic is fully implemented, but it has no Kafka producer to feed it since `matchmaking-service` doesn't exist yet.
- **Frontend mock data** still backs the matchmaking, coach, and event pages (shown with a visible "sample data" banner); auth, court browsing/booking, and payments are wired to real APIs end-to-end.
- **No CI pipeline** — tests run locally via `mvn verify` before every commit.
- **No `LICENSE` file** yet.

---

## Project Structure

```
badmintonhub/
├── pom.xml                      ← parent POM (Java 21, Spring Boot 3.2.5, Spring Cloud 2023.0.1)
├── docker-compose.yml           ← all infrastructure (18 containers)
├── .env.example                 ← environment variable template
│
├── common/                      ← shared DTOs, exceptions, GlobalExceptionHandler, BaseAuditEntity
├── common-security/             ← shared JwtUtil (web/JPA-free — used by Gateway + every service)
├── common-test/                 ← Testcontainers base classes + JwtTestTokens (shared test infra)
│
├── eureka-server/                ← service registry (port 8761)
├── api-gateway/                  ← JWT filter, rate limit, lb:// routing (port 3000)
│
├── user-service/                 ← ✅ Auth, JWT, OAuth2 (port 3001)
├── court-service/                ← ✅ Courts, slots, geo, pricing (port 3002)
├── booking-service/               ← ✅ Bookings, Saga, idempotency (port 3003)
├── payment-service/               ← ✅ Bank QR, Cloudinary, reconciliation (port 3006)
├── escrow-service/                ← ✅ Ledger (dormant), Outbox (port 3007)
├── chat-service/                  ← ✅ STOMP/WebSocket, MongoDB (port 3011)
│
├── matchmaking-service/           ← 🚧 scaffold (port 3004)
├── coach-service/                 ← 🚧 scaffold (port 3005)
├── notification-service/          ← 🚧 scaffold (port 3008)
├── event-service/                 ← 🚧 scaffold (port 3009)
├── ai-service/                    ← 🚧 scaffold (port 3010)
│
└── frontend/                      ← React 19 + Vite + TypeScript
    └── src/
        ├── api/                  ← axiosClient.ts + per-resource API functions
        ├── components/           ← SlotGrid, PaymentScreen, ChatThread, ...
        ├── pages/                ← one file per route
        ├── store/                ← Zustand: authStore, bookingStore, notificationStore
        ├── hooks/                ← useMatchSocket, useAuth, useNotifications
        └── i18n/                 ← vi.json, en.json
```

---

<div align="center">

Built solo, backend-first — Java 21 · Spring Boot · React 19 · Kafka · PostgreSQL · MongoDB · Redis

</div>
