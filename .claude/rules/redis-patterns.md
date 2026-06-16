---
description: Redis key naming, TTLs, distributed lock implementation, atomic counters, and Resilience4j circuit breaker fallback for BadmintonHub.
globs: **/*Redis*.java, **/*Cache*.java, **/*Lock*.java, **/*Distributed*.java, **/*Scheduler*.java
alwaysApply: false
---

# Redis Patterns

## Key Registry

| Key Pattern | TTL | Purpose | Service |
|---|---|---|---|
| `lock:slot:{slotId}` | 5s | Distributed lock — 1 ô 30' khi đặt. Booking nhiều ô → khoá **TẤT CẢ** `slotId` (all-or-nothing) **sau khi** đã gọi Feign lấy grid, bao quanh giao dịch ghi ngắn (header+items+outbox). KHÔNG giữ lock qua Feign. Fail-open nếu Redis chết (`booking_items.slot_id` UNIQUE là chốt thật) | booking-service, court-service |
| `lock:slot:{slotId}:match_create` | 10min | Mỗi ô 30' match giữ (N ô/trận, qua `match_slots`) trong lúc Host chờ STAFF confirm proof | matchmaking-service |
| `lock:match:{matchId}` | 5s | Distributed lock — match joining (prevents race on last player slot) | matchmaking-service |
| `payment:countdown:{paymentId}` | 10min | Payment screen countdown end time (ISO timestamp value) | payment-service |
| `match:{matchId}:slots` | none | Atomic counter — số **NGƯỜI chơi** (`filled_slots` ≤ `total_slots`) · `INCR`/`DECR`. KHÁC bảng `match_slots` (ô thời gian 30') | matchmaking-service |
| `event:{eventId}:sold` | none | Atomic counter — số **vé đã bán** (`tickets_sold` ≤ `total_tickets`) · `INCR`/`DECR` chống oversell | event-service |
| `session:blacklist:{jti}` | = JWT TTL | Revoked JWT access token IDs | user-service |
| `email:verify:{token}` | 24h | Email verification token → userId | user-service |
| `password:reset:{token}` | 1h | Password reset token → userId | user-service |
| `clubs:{district}:{sport}` | 60s | Cached **club** geo-search results (venue list theo quận + môn) | court-service |
| `rate_limit:{userId}` | 60s | Global rate limit *intent* — api-gateway actually uses Spring Cloud Gateway's built-in `RequestRateLimiter` (token-bucket); live keys are `request_rate_limiter.{route}.{tokens\|timestamp}`, keyed by userId (or client IP on public paths) | api-gateway |
| `rate_limit:booking:{userId}` | 60s | Max 10 booking-create attempts/min — chống 1 user squat lưới ô (fail-open; gateway `RequestRateLimiter` là lớp 2) | booking-service |
| `rate_limit:join:{userId}` | 60s | Max 5 join attempts/min | matchmaking-service |
| `rate_limit:proof:{userId}` | 300s | Max 3 proof uploads per 5 min | payment-service |
| `rate_limit:review:{userId}` | 86400s | Max 2 reviews/day per coach | coach-service |
| `rate_limit:register:{ip}` | 3600s | Max 5 registrations/hour per IP | user-service |

## Distributed Lock (SETNX)

```java
// Acquire lock
public boolean acquireRedisLock(String key, int ttlSeconds) {
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue()
            .setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds))
    );
}

// Release lock
public void releaseRedisLock(String key) {
    redisTemplate.delete(key);
}

// Usage in service
public void joinMatch(UUID matchId, UUID userId) {
    String lockKey = "lock:match:" + matchId;
    if (!acquireRedisLock(lockKey, 5)) {
        throw new ConflictException("MATCH_LOCKED", "Another operation in progress");
    }
    try {
        // critical section
    } finally {
        releaseRedisLock(lockKey);
    }
}
```

## Atomic Slot Counter

```java
// Increment and check atomically
Long count = redisTemplate.opsForValue().increment("match:" + matchId + ":slots");
if (count > match.getTotalSlots()) {
    redisTemplate.opsForValue().decrement("match:" + matchId + ":slots");
    throw new ConflictException("MATCH_FULL", "No slots remaining");
}

// Compensate on failure
redisTemplate.opsForValue().decrement("match:" + matchId + ":slots");
```

## Resilience4j Circuit Breaker Fallback

If Redis is unavailable, fall back to DB-level `SELECT FOR UPDATE`:

```java
@CircuitBreaker(name = "redis", fallbackMethod = "acquireDbLock")
public boolean acquireRedisLock(String key, int ttlSeconds) {
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(ttlSeconds))
    );
}

public boolean acquireDbLock(String key, int ttlSeconds, Exception ex) {
    log.warn("Redis unavailable, falling back to DB lock for key={}", key);
    return dbLockRepository.tryAcquire(key, ttlSeconds);  // SELECT FOR UPDATE on lock_table
}
```

Resilience4j config in `application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      redis:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

## Token Storage Pattern

```java
// Store email verify token
redisTemplate.opsForValue().set(
    "email:verify:" + token, userId.toString(), Duration.ofHours(24)
);

// Verify and consume
String userId = redisTemplate.opsForValue().get("email:verify:" + token);
if (userId == null) throw new InvalidTokenException();
redisTemplate.delete("email:verify:" + token);  // single-use
```

## JWT Blacklist (Logout)

```java
// On logout: blacklist the jti until the token would have expired
redisTemplate.opsForValue().set(
    "session:blacklist:" + jti, "1",
    Duration.between(Instant.now(), tokenExpiry)
);

// In JWT filter: reject if blacklisted
if (Boolean.TRUE.equals(redisTemplate.hasKey("session:blacklist:" + jti))) {
    throw new UnauthorizedException("Token revoked");
}
```
