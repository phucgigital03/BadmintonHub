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

> **Entity model notes**: `court_db` owns `clubs` (venue · 1 CLB) → `courts` (physical *Sân*) → `time_slots`, plus `court_pricing_rules` (multi-dimensional price) and `club_reviews`. `booking_db` models an order as a **header** (`bookings`) + **N line items** (`booking_items`, one atomic 30-min slot each).

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

`booking-service`, `escrow-service`, and `court-service` each have a `processed_events` table. Check before processing any Kafka event (court-service consumes the `booking.slot.held` / `booking.slot.released` Saga events). booking-service additionally owns an `outbox_events` table for reliable event publishing (Outbox Pattern).

```sql
CREATE TABLE processed_events (
    event_id     VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

Cleanup scheduler: delete rows older than 7 days (`@Scheduled(cron = "0 0 3 * * *")`).

## Snapshot Fields

Price/time is **looked up from court-service once, then frozen** on the owning record. Never re-read live from court-service afterward.

- `matches.court_price` = snapshot of the applicable `court_pricing_rules.price_per_hour × duration` at the moment the match is created. Immutable afterward.
- `booking_items.price` (+ `court_name`, `start_time`, `end_time`) = snapshot of the matching `court_pricing_rules` row for that 30-min cell, captured **at booking time**.
- `bookings.total_price` = `SUM(booking_items.price)` for the order. `bookings.earliest_start_time` = snapshot of the earliest item's start — the reference point for the cancellation policy.

## State Machines

### `bookings.status`
```
PENDING → CONFIRMED → COMPLETED
        ↘ CANCELLED
```
A booking is a **header** (`bookings`) + **N atomic 30-min line items** (`booking_items`, one `time_slots` row each). The whole order is **one payment** and is cancelled **atomically** (all items or none).

Cancellation refund policy: reference point = `bookings.earliest_start_time`; refund = % × `bookings.total_price` where >24h = 100%, 2–24h = 50%, <2h = 0%.

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

- Index all UUID columns used as cross-service references (`user_id`, `club_id`, `match_id`, etc.)
- Index `status` columns on high-query tables (`payments`, `bookings`, `matches`)
- Composite index on `(court_id, date, status)` for `time_slots` — frequent slot availability queries
- Index `booking_items(booking_id)` and `booking_items(slot_id)` — order line items + slot reservation lookups
- Index `clubs(district, is_active)` and `courts(club_id)` — venue search + sân listing
- Unique constraint: `court_pricing_rules(club_id, sport, day_type, start_time, customer_type)` (one price per dimension), `club_reviews.booking_id`, `coach_reviews.enrollment_id` (1 review per booking/enrollment)
