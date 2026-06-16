---
description: Core architecture rules for BadmintonHub — module map, ports, and the 10 constraints that must never be violated across all services.
alwaysApply: true
---

# BadmintonHub — Architecture

Microservices platform for badminton court booking, matchmaking, coach enrollment, and events in Vietnam.
Full design reference: `CLAUDE_Example.md` · ERDs: `ERD_All_Services.md` · Use Cases: `UC_*.md`

## Module Map & Ports

| Module | Port | Responsibility |
|---|---|---|
| `eureka-server` | 8761 | Service registry |
| `api-gateway` | 3000 | JWT filter · rate limiting · `lb://` routing |
| `user-service` | 3001 | Auth · JWT · OAuth2 · Refresh Token · Email Verify |
| `court-service` | 3002 | Courts · Slots · Slot auto-gen · Reviews · Geo search |
| `booking-service` | 3003 | Court bookings · Cancellation policy · Idempotency guard |
| `matchmaking-service` | 3004 | Matches · Saga · Outbox · Socket.io · Waiting list |
| `coach-service` | 3005 | Coach profiles · Elasticsearch · Enrollments |
| `payment-service` | 3006 | Bank QR · Proof upload · STAFF confirm · Manual refund |
| `escrow-service` | 3007 | Hold court fee · Reimburse Host · Settle · Refund |
| `notification-service` | 3008 | SendGrid · FCM · Kafka DLQ · Read status (MongoDB) |
| `event-service` | 3009 | Social/competitive events · Ticket sales |
| `ai-service` | 3010 | RAG chatbot |
| `common` | — | Shared DTOs · exceptions · audit base entity |

## Rules — Never Violate

1. **No cross-database FK constraints.** Cross-service refs are UUID only. Consistency via Kafka + Saga.
2. **No VNPay or third-party payment API.** Payment = Bank QR + proof upload + STAFF manual confirm. Refunds = `manual_refunds` table + STAFF executes bank transfer manually.
3. **Never hardcode service URLs.** Always `lb://service-name` in Gateway routes. Services resolve via Eureka.
4. **Outbox Pattern in `matchmaking-service`, `booking-service`, and `payment-service`.** Save `OutboxEvent` in the same `@Transactional` as the business record. Never publish Kafka directly from a service method. (booking-service uses it for the `booking.slot.changed` slot-hold Saga — one message per slot keyed by `slotId` so HELD/RELEASED stay ordered — plus the `booking.payment.orphaned` / `booking.refund.required` compensations; payment-service for all `payment.*` events.)
5. **Idempotency guard in `booking-service`, `escrow-service`, `court-service`, and `payment-service`.** Check `processed_events` before handling any Kafka event. (court-service consumes the slot-hold events; booking-service also consumes the `payment.player.*` events; payment-service consumes the `booking.payment.orphaned` + `booking.refund.required` compensations.)
6. **Zombie event check in `matchmaking-service` (and the booking↔payment loop).** If a record is `CANCELLED` when a late event arrives, publish a compensating event — never process the stale event. (booking-service emits `booking.payment.orphaned` when `payment.player.confirmed` lands on an already-cancelled booking, and `booking.refund.required` when a CONFIRMED booking is cancelled within the refund window — both make payment-service flag the payment for a manual refund so the money is never silently lost. Every booking↔payment status transition takes a `SELECT … FOR UPDATE` row lock so a concurrent cancel/confirm can't lose-update each other.)
7. **Never silently drop Kafka failures.** After 3 retries (exponential backoff 2s/4s/8s), route to `.DLT` topic via `DeadLetterPublishingRecoverer`.
8. **Soft delete only.** `users.deleted_at`, `coaches.deleted_at` — never hard delete user or coach data.
9. **`court_price` is a snapshot.** Captured at match creation, immutable afterward — never read live from court-service.
10. **Only `is_email_verified=true` users** can create bookings or join matches — enforce via `@PreAuthorize`.

## Build & Run

```bash
docker-compose up -d                        # start all infra
mvn clean install -DskipTests               # build all modules
mvn -pl user-service spring-boot:run        # run one service
cd frontend && npm install && npm run dev   # frontend
```

docker-compose includes: PostgreSQL 15 ×9 · Redis 7 · MongoDB 7 · Kafka 3.6 · Zookeeper · Zipkin · Eureka
