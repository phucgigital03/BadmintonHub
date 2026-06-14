---
description: Kafka topics, Outbox Pattern, idempotency, zombie event guard, DLQ configuration, and error handling for BadmintonHub messaging.
globs: **/*Kafka*.java, **/*Consumer*.java, **/*Listener*.java, **/*Event*.java, **/*Outbox*.java, **/*Producer*.java
alwaysApply: false
---

# Kafka Patterns

## Topic Registry

| Topic | Producer | Consumers |
|---|---|---|
| `payment.host.confirmed` | payment-service (Outbox) | matchmaking-service, escrow-service |
| `payment.host.expired` | payment-service (Outbox) | matchmaking-service |
| `payment.player.confirmed` | payment-service (Outbox) | booking-service, escrow-service |
| `payment.player.expired` | payment-service (Outbox) | matchmaking-service, booking-service |
| `payment.proof.submitted` | payment-service (Outbox) | notification-service, booking-service |
| `payment.refund.processed` | payment-service (Outbox) | notification-service |
| `payment.refund.queued` | escrow-service | notification-service, payment-service |
| `match.slot.joined` | matchmaking-service (Outbox) | booking-service, payment-service |
| `match.cancelled` | matchmaking-service | notification-service, booking-service, escrow-service |
| `match.completed` | matchmaking-service | escrow-service, notification-service |
| `match.compensate.slot` | matchmaking-service | booking-service, escrow-service |
| `booking.slot.confirmed` | booking-service | matchmaking-service, notification-service, escrow-service |
| `booking.slot.held` | booking-service (Outbox) | court-service |
| `booking.slot.released` | booking-service (Outbox) | court-service |
| `booking.payment.orphaned` | booking-service (Outbox) | payment-service |
| `escrow.host.reimbursed` | escrow-service | notification-service |

DLT suffix: `{topic}.DLT` (e.g. `payment.host.confirmed.DLT`)

## Outbox Pattern (matchmaking-service + booking-service + payment-service)

Save `OutboxEvent` in the **same `@Transactional`** as the business record. A `@Scheduled` job (every 3s) polls `outbox_events WHERE status='PENDING'` and publishes to Kafka.

> **booking-service** uses the Outbox for the slot-hold Saga: `create` / `cancel` / hold-expiry write a
> `booking.slot.held` / `booking.slot.released` row in the same transaction as the booking change, so
> court-service is reliably told to flip the slot RESERVED↔AVAILABLE (the grid reflects the hold).
>
> **payment-service** writes every `payment.*` event (proof.submitted / host|player.confirmed /
> host|player.expired / refund.processed) to the Outbox in the same transaction as the `payments.status`
> change — confirm/reject/refund and the expiry scheduler never call `KafkaTemplate` directly.

```java
// Inside MatchService.joinMatch() — one transaction
@Transactional
public void joinMatch(UUID matchId, UUID userId) {
    Match match = matchRepo.findById(matchId).orElseThrow();
    match.setFilledSlots(match.getFilledSlots() + 1);
    matchRepo.save(match);

    // Outbox — same transaction, guarantees at-least-once delivery
    outboxRepo.save(OutboxEvent.builder()
        .topic("match.slot.joined")
        .payload(objectMapper.writeValueAsString(new MatchSlotJoinedEvent(matchId, userId)))
        .status(OutboxStatus.PENDING)
        .build());
}

// OutboxPublisherScheduler
@Scheduled(fixedDelay = 3000)
@Transactional
public void publishPendingEvents() {
    outboxRepo.findByStatus(OutboxStatus.PENDING).forEach(event -> {
        kafkaTemplate.send(event.getTopic(), event.getPayload());
        event.setStatus(OutboxStatus.SENT);
        event.setSentAt(LocalDateTime.now());
        outboxRepo.save(event);
    });
}
```

Outbox cleanup: delete SENT events older than 30 days (`@Scheduled(cron = "0 0 2 * * *")`).

## Idempotency Guard (booking-service, escrow-service, court-service, payment-service)

Always check `processed_events` before processing. (payment-service also consumes — the
`booking.payment.orphaned` compensation — so it owns a `processed_events` table too.) Use the Kafka record key or a UUID from the event payload as `event_id`.

```java
@KafkaListener(topics = "payment.player.confirmed", groupId = "booking-service")
public void onPaymentConfirmed(ConsumerRecord<String, String> record, Acknowledgment ack) {
    String eventId = record.key();
    if (processedEventRepo.existsById(eventId)) {
        ack.acknowledge();
        return;   // already processed — safe to skip
    }
    try {
        // ... business logic
        processedEventRepo.save(new ProcessedEvent(eventId));
        ack.acknowledge();
    } catch (Exception e) {
        // do NOT ack — let retry mechanism handle it
        throw e;
    }
}
```

## Zombie Event Check (matchmaking-service)

When a late event arrives for an already-CANCELLED match, publish a compensating event and return immediately.

```java
@KafkaListener(topics = "booking.slot.confirmed", groupId = "matchmaking-service")
public void onSlotConfirmed(SlotConfirmedEvent event, Acknowledgment ack) {
    Match match = matchRepo.findById(event.getMatchId()).orElseThrow();
    if (match.getStatus() == MatchStatus.CANCELLED) {
        kafkaTemplate.send("match.compensate.slot",
            objectMapper.writeValueAsString(new CompensateSlotEvent(event.getMatchId(), event.getUserId())));
        ack.acknowledge();
        return;   // zombie — do not process
    }
    // normal processing...
}
```

## DLQ + Error Handler (notification-service and all consumers)

Configure globally in each service's `KafkaConfig`:

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

    ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);  // 2s, 4s, 8s
    backOff.setMaxAttempts(3);

    return new DefaultErrorHandler(recoverer, backOff);
}
```

Admin replay endpoint: `POST /api/admin/kafka/replay?topic={topic.DLT}`

## Consumer Group IDs

Each service uses its own name as the consumer group ID (matches `spring.application.name`):
- `booking-service`, `matchmaking-service`, `escrow-service`, `notification-service`, `payment-service`
