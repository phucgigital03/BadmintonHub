---
description: Database design rules for BadmintonHub — cross-service UUID references, soft delete, state machines, and per-service database ownership.
globs: **/*.java, **/*.sql, **/application.yml
alwaysApply: false
---

# Database Design Rules

## Core Principle: Database-per-Service

Each service owns exactly one database. No shared tables. No cross-database JOINs.

| Service | Database | Engine |
|---|---|---|
| user-service | user_db | PostgreSQL |
| court-service | court_db | PostgreSQL |
| booking-service | booking_db | PostgreSQL |
| matchmaking-service | matchmaking_db | PostgreSQL |
| payment-service | payment_db | PostgreSQL |
| escrow-service | escrow_db | PostgreSQL |
| coach-service | coach_db | PostgreSQL + Elasticsearch |
| event-service | event_db | PostgreSQL |
| notification-service | notification_db | MongoDB |

## Cross-Service References

Cross-service IDs are stored as plain `UUID` columns — **no FK constraint, no JPA relationship**.

```java
// CORRECT — cross-service reference
@Column(nullable = false, columnDefinition = "uuid COMMENT 'ref users.id · cross-service UUID'")
private UUID userId;

// WRONG — never do this across service boundaries
@ManyToOne
@JoinColumn(name = "user_id")
private User user;   // User is in a different database!
```

SQL column comment format: `'ref {table}.{column} · cross-service UUID'`

Within-service FK constraints and JPA relationships are normal and encouraged.

## Soft Delete

```java
// Entity
@Column(name = "deleted_at")
private LocalDateTime deletedAt;

// Repository filter (Hibernate)
@Where(clause = "deleted_at IS NULL")
public class User { ... }

// Service
user.setDeletedAt(LocalDateTime.now());
userRepository.save(user);
// Never: userRepository.delete(user)
```

Tables with soft delete: `users`, `coaches` — all others use status fields instead.

## Idempotency Guard Tables

`booking-service` and `escrow-service` each have a `processed_events` table. Check before processing any Kafka event:

```sql
CREATE TABLE processed_events (
    event_id     VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

Cleanup scheduler: delete rows older than 7 days (`@Scheduled(cron = "0 0 3 * * *")`).

## Snapshot Fields

`matches.court_price` = snapshot of `courts.price_per_hour × duration` at the moment the match is created. **Never** update it afterward, never read live from court-service during match operations.

## State Machines

### `bookings.status`
```
PENDING → CONFIRMED → COMPLETED
        ↘ CANCELLED
```
Cancellation refund policy: >24h = 100%, 2–24h = 50%, <2h = 0%

### `matches.status`
```
PENDING_PAYMENT → OPEN → FULL → COMPLETED
                ↘              ↘ CANCELLED
                  CANCELLED (proof EXPIRED or REJECTED)
```

### `payments.status`
```
PENDING → PROOF_SUBMITTED → CONFIRMED
                           ↘ EXPIRED   (scheduler timeout or STAFF reject)
CONFIRMED → REFUNDED
```

### `coaches.status`
```
PENDING_APPROVAL → ACTIVE ↔ SUSPENDED
```

### `event_tickets.status`
```
PENDING → CONFIRMED
        ↘ CANCELLED → REFUNDED
```

## Audit Columns

All entities must have `created_at` and `updated_at` via `BaseAuditEntity` from `common` module. Tables that need a full admin audit trail also write to `audit_logs` in `user_db` via `@AuditLog` aspect.

## Index Guidelines

- Index all UUID columns used as cross-service references (`user_id`, `match_id`, etc.)
- Index `status` columns on high-query tables (`payments`, `bookings`, `matches`)
- Composite index on `(court_id, date, status)` for `time_slots` — frequent slot availability queries
- Unique constraint: `court_reviews.booking_id`, `coach_reviews.enrollment_id` (1 review per booking/enrollment)
