---
description: Resilience4j circuit breakers, distributed locking fallback, Kafka retry/DLQ, scheduler patterns, and timeout auto-cancel for BadmintonHub.
globs: **/*Service*.java, **/*Config*.java, **/*Scheduler*.java, **/*CircuitBreaker*.java
alwaysApply: false
---

# Resilience Patterns

## Resilience4j Circuit Breaker (Redis Fallback)

Primary lock via Redis SETNX; if Redis is down, fall back to DB `SELECT FOR UPDATE`:

```java
@CircuitBreaker(name = "redis", fallbackMethod = "acquireDbLock")
public boolean acquireRedisLock(String key, int ttlSeconds) {
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds))
    );
}

public boolean acquireDbLock(String key, int ttlSeconds, Exception ex) {
    log.warn("Redis circuit open, using DB lock fallback for key={}", key);
    return dbLockRepository.tryAcquire(key, ttlSeconds);
}
```

`application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      redis:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
```

## Kafka Error Handler with Exponential Backoff + DLQ

Configure in every service's `KafkaConfig`:

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

    ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);  // 2s → 4s → 8s
    backOff.setMaxAttempts(3);

    return new DefaultErrorHandler(recoverer, backOff);
}
```

After 3 failures the message lands in `{topic}.DLT` for manual replay. Never silently swallow exceptions.

## Scheduler Patterns

### Payment Expiry (payment-service)
```java
@Scheduled(fixedDelay = 60_000)  // every 1 min
@Transactional
public void expireStalePayments() {
    List<Payment> stale = paymentRepo.findByStatusAndExpiresAtBefore(
        PaymentStatus.PENDING, LocalDateTime.now());
    stale.forEach(p -> {
        p.setStatus(PaymentStatus.EXPIRED);
        paymentRepo.save(p);
        kafkaTemplate.send("payment.host.expired", p.getMatchId().toString());
    });
}
```

### Match Timeout (matchmaking-service)
```java
@Scheduled(cron = "0 */5 * * * *")  // every 5 min
@Transactional
public void cancelExpiredMatches() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
    matchRepo.findByStatusAndCreatedAtBefore(MatchStatus.PENDING_PAYMENT, cutoff)
        .forEach(match -> {
            match.setStatus(MatchStatus.CANCELLED);
            matchRepo.save(match);
            redisTemplate.delete("lock:slot:" + match.getSlotId() + ":match_create");
            kafkaTemplate.send("match.cancelled", objectMapper.writeValueAsString(
                new MatchCancelledEvent(match.getId(), CancelReason.PAYMENT_TIMEOUT)));
        });
}
```

### Outbox Publisher (matchmaking-service)
```java
@Scheduled(fixedDelay = 3000)  // every 3s
@Transactional
public void publishPendingOutboxEvents() {
    outboxRepo.findByStatus(OutboxStatus.PENDING).forEach(event -> {
        kafkaTemplate.send(event.getTopic(), event.getPayload());
        event.setStatus(OutboxStatus.SENT);
        event.setSentAt(LocalDateTime.now());
        outboxRepo.save(event);
    });
}
```

### Slot Auto-Generation (court-service)
```java
@Scheduled(cron = "0 0 0 * * *")  // midnight daily
public void generateSlotsFor30Days() {
    courtRepo.findAllByIsActiveTrue().forEach(court ->
        slotGenerationService.generate(court,
            LocalDate.now().plusDays(1), LocalDate.now().plusDays(30),
            "06:00", "22:00", 60)
    );
}
```

### Cleanup Schedulers
```java
// matchmaking-service — outbox cleanup
@Scheduled(cron = "0 0 2 * * *")  // 2am daily
public void cleanOldOutboxEvents() {
    outboxRepo.deleteByStatusAndCreatedAtBefore("SENT", LocalDateTime.now().minusDays(30));
}

// booking-service + escrow-service — processed_events cleanup
@Scheduled(cron = "0 0 3 * * *")  // 3am daily
public void cleanOldProcessedEvents() {
    processedEventRepo.deleteByProcessedAtBefore(LocalDateTime.now().minusDays(7));
}
```

## Feign Client Retry

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 5000
```

Add `@Retry(name = "feign-default")` on Feign client methods that call non-idempotent operations.
