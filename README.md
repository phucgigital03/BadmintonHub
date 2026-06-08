<div align="center">

# BadmintonHub

**A production-grade microservices platform for badminton court booking, player matchmaking, coach enrollment, and social events in Vietnam.**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.1-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=flat-square&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-3.6-231F20?style=flat-square&logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

</div>

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Services](#services)
- [Tech Stack](#tech-stack)
- [Key Engineering Decisions](#key-engineering-decisions)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)

---

## Overview

BadmintonHub solves a real problem in Vietnam's badminton community: fragmented court booking, no reliable player matchmaking, and zero-trust payment experiences. The platform handles the full lifecycle from court discovery and slot booking to automated escrow, player matching with real-time slot tracking, coach enrollment, and event ticketing.

**What makes this project interesting from an engineering standpoint:**

- **13 independent microservices** communicating via Kafka events and Spring Cloud Gateway
- **Event-driven Saga** to coordinate the multi-step match booking flow across 4 services
- **Outbox Pattern** ensuring at-least-once Kafka delivery without distributed transactions
- **Prepay + Escrow model** where a court owner only receives funds when a match completes
- **Real-time slot counters** using Redis atomic `INCR`/`DECR` with race-condition protection
- **RAG-powered AI chatbot** built on top of OpenAI for court/match recommendations

---

## Architecture

```
                               ┌──────────────────────┐
                               │     React Frontend    │
                               │  (Vite + TypeScript)  │
                               └──────────┬───────────┘
                                          │ HTTP / WebSocket
                               ┌──────────▼───────────┐
                               │      API Gateway      │  ← JWT validation
                               │   Spring Cloud GW     │  ← Rate limiting
                               │      Port 3000        │  ← lb:// routing
                               └──────────┬───────────┘
                                          │
          ┌───────────────────────────────┼───────────────────────────┐
          │                               │                           │
┌─────────▼────────┐          ┌──────────▼──────────┐    ┌──────────▼────────┐
│   user-service   │          │   court-service      │    │  booking-service  │
│   Port 3001      │          │   Port 3002           │    │  Port 3003        │
│ Auth · JWT · OAuth│          │ Slots · Geo · Reviews│    │ Bookings · Saga   │
└──────────────────┘          └─────────────────────┘    └───────────────────┘
          │                               │                           │
          │              ┌────────────────┴───────────────────────────┤
          │              │                                            │
┌─────────▼────────┐  ┌──▼──────────────┐  ┌──────────────────┐  ┌──▼─────────────┐
│matchmaking-svc   │  │ payment-service  │  │  escrow-service  │  │  coach-service │
│  Port 3004       │  │  Port 3006       │  │  Port 3007       │  │  Port 3005     │
│Outbox · Socket.io│  │ Bank QR · Proof  │  │ Hold · Reimburse │  │ ES · Enroll   │
└─────────┬────────┘  └──────┬──────────┘  └──────────────────┘  └────────────────┘
          │                  │
          │    ┌─────────────┴────────────────────────────────────────┐
          │    │                    Apache Kafka                       │
          └────►         Event Bus (12 topics + DLT)                  │
               └──────────────────────────────────────────────────────┘
                          │              │              │
               ┌──────────▼──┐  ┌───────▼──────┐  ┌──▼──────────────┐
               │notification │  │ event-service│  │   ai-service    │
               │  Port 3008  │  │  Port 3009   │  │   Port 3010     │
               │SendGrid·FCM │  │ Tickets·Events│  │ RAG · OpenAI   │
               └─────────────┘  └──────────────┘  └─────────────────┘
```

**Service Discovery:** All services register with Spring Cloud Netflix Eureka (port 8761). The Gateway resolves every upstream via `lb://service-name` — no hardcoded URLs anywhere.

**Distributed Tracing:** Zipkin traces span across all 13 services, giving end-to-end visibility into every request.

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| `eureka-server` | 8761 | Service registry — health, load balancing |
| `api-gateway` | 3000 | JWT auth filter, rate limiting, `lb://` routing |
| `user-service` | 3001 | Registration, email verify, login, refresh token, OAuth2 Google |
| `court-service` | 3002 | Courts, time slots, auto-generation, geo search, reviews |
| `booking-service` | 3003 | Court bookings, cancellation policy (24h/2h/0h tiers), idempotency |
| `matchmaking-service` | 3004 | Player matches, Saga coordinator, Outbox publisher, Socket.io |
| `coach-service` | 3005 | Coach profiles, Elasticsearch full-text search, enrollments |
| `payment-service` | 3006 | Bank QR generation, proof upload (Cloudinary), STAFF confirmation |
| `escrow-service` | 3007 | Fund holding, Host reimbursement per Player join, manual settlement |
| `notification-service` | 3008 | SendGrid email + FCM push via Kafka, read status in MongoDB |
| `event-service` | 3009 | Social/competitive events, ticket sales |
| `ai-service` | 3010 | RAG chatbot for court & match recommendations |
| `common` | — | Shared: `BaseAuditEntity`, `GlobalExceptionHandler`, DTOs, exceptions |
| `common-security` | — | Shared `JwtUtil` (HS256 verify, web/JPA-free) — used by the Gateway + every service for JWT re-validation |

---

## Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Language | Java 21 (Virtual Threads ready) |
| Framework | Spring Boot 3.2 · Spring Cloud 2023.0.1 |
| API Gateway | Spring Cloud Gateway |
| Service Discovery | Spring Cloud Netflix Eureka |
| Message Broker | Apache Kafka 3.6 (Confluent) + Zookeeper |
| Cache & Locks | Redis 7 (SETNX distributed locks, atomic counters, JWT blacklist) |
| Primary DB | PostgreSQL 15 — one isolated database per service (×9 instances) |
| Document DB | MongoDB 7 — notification read-receipts |
| Search | Elasticsearch 8.13 — coach full-text & filter search |
| Distributed Tracing | Zipkin |
| Resilience | Resilience4j — circuit breaker, retry, rate limiter |
| Security | Spring Security · JWT (HS256) · Google OAuth2 · RBAC |
| File Storage | Cloudinary — payment proof screenshots |
| Email | SendGrid |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| AI | OpenAI API — RAG over court/match knowledge base |
| Build | Maven (multi-module parent POM) |

### Frontend
| Layer | Technology |
|---|---|
| Language | TypeScript 5 |
| Framework | React 18 + Vite |
| Styling | Tailwind CSS |
| Server State | React Query (TanStack Query) |
| Client State | Zustand |
| Forms | React Hook Form + Zod |
| HTTP | Axios (with silent refresh interceptor) |
| Real-time | Socket.io — live slot counters on match rooms |
| Routing | React Router v6 |
| i18n | react-i18next (Vietnamese + English) |

### Infrastructure
| Tool | Purpose |
|---|---|
| Docker Compose | Runs 15 containers: 9× PostgreSQL, Redis, MongoDB, Kafka, Zookeeper, Zipkin, Elasticsearch |
| spring-dotenv | `.env` file loaded into Spring Environment at startup |

---

## Key Engineering Decisions

### 1. Database-per-Service (Strict Isolation)
Every service owns its own PostgreSQL database. Cross-service references are plain `UUID` columns — no foreign key constraints across service boundaries. Consistency is achieved through Kafka events and the Saga pattern, not shared tables.

### 2. Outbox Pattern (matchmaking-service)
Kafka events are written to an `outbox_events` table **in the same `@Transactional`** as the business record. A 3-second scheduled job polls pending events and publishes them. This eliminates the dual-write problem and guarantees at-least-once delivery with zero distributed transactions.

### 3. Idempotency Guards (booking-service, escrow-service)
A `processed_events` table (keyed by Kafka record key) ensures each Kafka message is handled exactly once, even if replayed from the Dead Letter Topic.

### 4. Zombie Event Detection (matchmaking-service)
When a late Kafka event arrives for an already-`CANCELLED` match, the service publishes a compensating event instead of processing it. Stale events never corrupt match state.

### 5. Prepay + Escrow Payment Model
The Host pays the full `court_price` upfront. Funds are held in escrow. As each Player joins and confirms payment, escrow reimburses the Host proportionally. The Court Owner is only paid when the match reaches `COMPLETED` status — protecting all parties.

### 6. No Third-Party Payment Gateway
Payment is Bank QR + manual proof upload (Cloudinary) + STAFF confirmation. Refunds are tracked in a `manual_refunds` table and executed manually by staff. This eliminates payment gateway fees and compliance complexity for the Vietnamese market.

### 7. Redis Distributed Locking for Slot Race Conditions
When two players attempt to join the last match slot simultaneously, a Redis `SETNX` lock (5s TTL) serializes access. If Redis is unavailable, a Resilience4j circuit breaker falls back to `SELECT FOR UPDATE` on a PostgreSQL lock table.

### 8. JWT Architecture (defense in depth)
- **Access token**: 15-minute TTL, validated at the Gateway **and** re-validated by each downstream service
- **Refresh token**: 30-day TTL, stored as bcrypt hash in the DB, delivered via `HttpOnly SameSite=Strict` cookie
- **Logout blacklist**: `jti` stored in Redis with TTL = remaining access token lifetime; the Gateway rejects blacklisted tokens (fail-open if Redis is down)
- The Gateway forwards only the `Authorization: Bearer` token (no `X-User-Id`/`X-User-Roles` headers). Each service re-validates that token with the shared `common-security` `JwtUtil` and derives identity from the verified claims — the token is the single source of identity
- **Rate limiting**: Spring Cloud Gateway's built-in `RequestRateLimiter` (Redis token-bucket), keyed by userId (client IP for public paths)

### 9. Kafka Dead Letter Topics + Exponential Backoff
Every Kafka consumer is configured with a `DefaultErrorHandler` that retries 3 times (2s → 4s → 8s exponential backoff) before routing the message to `{topic}.DLT`. An admin endpoint allows DLT replay.

### 10. Snapshot Fields
`matches.court_price` is captured at match creation and never updated. Match fee calculations are always based on the snapshot — price changes to a court never affect in-progress matches.

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
# Fill in: GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, SENDGRID_API_KEY,
#           CLOUDINARY_*, OPENAI_API_KEY, FCM_SERVER_KEY
```

### 2. Start infrastructure (15 containers)

```bash
docker compose up -d
# PostgreSQL ×9 · Redis · MongoDB · Kafka · Zookeeper · Zipkin · Elasticsearch
```

### 3. Build all modules

```bash
mvn clean install -DskipTests
# Builds 14 modules including common shared library
```

### 4. Run services

```bash
# Start Eureka first
mvn -pl eureka-server spring-boot:run

# Then any service (in separate terminals)
mvn -pl api-gateway spring-boot:run
mvn -pl user-service spring-boot:run
mvn -pl court-service spring-boot:run
# ...etc
```

### 5. Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev          # http://localhost:5173
```

### Observability

| Tool | URL |
|---|---|
| Eureka Dashboard | http://localhost:8761 |
| Zipkin Tracing | http://localhost:9411 |
| Elasticsearch | http://localhost:9200 |

---

## API Reference

All routes go through the API Gateway at `http://localhost:3000`.

### Authentication (`user-service`)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register with email + password |
| `GET` | `/api/auth/verify-email?token=` | Verify email address |
| `POST` | `/api/auth/login` | Login → access token + refresh cookie |
| `POST` | `/api/auth/refresh` | Silent token refresh via cookie |
| `POST` | `/api/auth/logout` | Blacklist `jti` in Redis |
| `POST` | `/api/auth/google` | OAuth2 Google login (`{ idToken }`) |

### Court Booking (`booking-service`)
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/courts?district=&type=&date=` | Search courts (Redis-cached 60s) |
| `GET` | `/api/courts/{id}/slots?date=` | View available time slots |
| `POST` | `/api/bookings` | Book a slot (email verified users only) |
| `DELETE` | `/api/bookings/{id}` | Cancel booking (tiered refund policy) |

### Matchmaking (`matchmaking-service`)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/matches` | Create match (Host pays court_price via escrow) |
| `GET` | `/api/matches?date=&level=` | Browse open matches |
| `POST` | `/api/matches/{id}/join` | Join match (email verified, max 5/min rate limit) |
| `DELETE` | `/api/matches/{id}` | Cancel match (Host only) |

### Payments (`payment-service`)
| Method | Path | Description |
|---|---|---|
| `POST` | `/api/payments/initiate` | Generate Bank QR + countdown |
| `POST` | `/api/payments/{id}/proof` | Upload transfer screenshot (Cloudinary) |
| `POST` | `/api/payments/{id}/confirm` | STAFF/ADMIN: confirm payment |
| `POST` | `/api/payments/{id}/reject` | STAFF/ADMIN: reject → release slot |
| `POST` | `/api/payments/{id}/refund` | STAFF/ADMIN: record manual bank refund |

### Coach Search (`coach-service`)
| Method | Path | Description |
|---|---|---|
| `GET` | `/api/coaches?q=&district=&level=` | Elasticsearch full-text coach search |
| `POST` | `/api/coaches/{id}/enroll` | Enroll with a coach |

---

## Project Structure

```
badmintonhub/
├── pom.xml                      ← Parent POM (Java 21, Spring Boot 3.2, Spring Cloud 2023)
├── docker-compose.yml           ← All infrastructure (15 containers)
├── .env.example                 ← Environment variable template
│
├── common/                      ← Shared library (no Spring Boot main)
│   └── src/main/java/com/badmintonhub/common/
│       ├── entity/BaseAuditEntity.java
│       ├── exception/           ← ApiException + 5 subtypes
│       ├── dto/response/        ← ErrorResponse · PageResponse<T>
│       └── handler/GlobalExceptionHandler.java
│
├── eureka-server/               ← Service registry (port 8761)
├── api-gateway/                 ← JWT filter, rate limit, lb:// routing (port 3000)
├── user-service/                ← Auth, JWT, OAuth2 (port 3001)
├── court-service/               ← Courts, slots, geo, reviews (port 3002)
├── booking-service/             ← Bookings, saga, idempotency (port 3003)
├── matchmaking-service/         ← Matches, Outbox, Socket.io (port 3004)
├── coach-service/               ← Profiles, Elasticsearch (port 3005)
├── payment-service/             ← Bank QR, Cloudinary, STAFF flow (port 3006)
├── escrow-service/              ← Holding, reimbursement, settlement (port 3007)
├── notification-service/        ← SendGrid, FCM, MongoDB (port 3008)
├── event-service/               ← Events, tickets (port 3009)
├── ai-service/                  ← RAG chatbot, OpenAI (port 3010)
│
└── frontend/                    ← React 18 + Vite + TypeScript
    └── src/
        ├── api/                 ← axiosClient.ts + per-resource API functions
        ├── components/          ← SlotGrid, PaymentScreen, MatchCard, ...
        ├── pages/               ← One file per route
        ├── store/               ← Zustand: authStore, matchStore, notificationStore
        ├── hooks/               ← useMatchSocket, useAuth, useNotifications
        └── i18n/                ← vi.json, en.json
```

---

<div align="center">

Built with Java 21 · Spring Boot 3.2 · React 18 · Apache Kafka · PostgreSQL · Redis

</div>
