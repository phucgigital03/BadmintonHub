---
description: Redis key naming, TTLs, distributed lock implementation, atomic counters, and Resilience4j circuit breaker fallback for BadmintonHub.
globs: **/*Redis*.java, **/*Cache*.java, **/*Lock*.java, **/*Distributed*.java, **/*Scheduler*.java
alwaysApply: false
---

# Redis Patterns

## Key Registry

| Key Pattern | TTL | Purpose | Service |
|---|---|---|---|
| `lock:slot:{slotId}` | 5s | Distributed lock — slot booking | booking-service, court-service |
| `lock:slot:{slotId}:match_create` | 10min | Slot held while Host awaits payment proof confirmation | matchmaking-service |
| `lock:match:{matchId}` | 5s | Distributed lock — match joining (prevents race on last slot) | matchmaking-service |
| `payment:countdown:{paymentId}` | 10min | Payment screen countdown end time (ISO timestamp value) | payment-service |
| `match:{matchId}:slots` | none | Atomic slot counter — `INCR`/`DECR` for real-time count | matchmaking-service |
| `session:blacklist:{jti}` | = JWT TTL | Revoked JWT access token IDs | user-service |
| `email:verify:{token}` | 24h | Email verification token → userId | user-service |
| `password:reset:{token}` | 1h | Password reset token → userId | user-service |
| `courts:{district}:{type}:{date}` | 60s | Cached court search results | court-service |
| `rate_limit:{userId}` | 60s | Global rate limit *intent* — api-gateway actually uses Spring Cloud Gateway's built-in `RequestRateLimiter` (token-bucket); live keys are `request_rate_limiter.{route}.{tokens\|timestamp}`, keyed by userId (or client IP on public paths) | api-gateway |
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
