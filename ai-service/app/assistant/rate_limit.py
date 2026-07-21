"""rate_limit:ai:{userId} (§7, §11.3) — the Python mirror of Java BookingRateLimiter.

INCR a per-user counter with a 60s TTL; over the cap → the message endpoint returns 429. Every
LLM turn costs money, so a spammer must be throttled. **Fail-open**: if Redis is down the counter
can't be read → allow the request (the gateway's own RequestRateLimiter is the second layer);
the assistant must not go dark because Redis blinked.
"""

from __future__ import annotations

from typing import Any, Protocol

import structlog

log = structlog.get_logger(__name__)


class RateLimiter(Protocol):
    async def allow(self, user_id: str) -> bool: ...


class RedisRateLimiter:
    def __init__(self, redis: Any, *, limit: int, window_seconds: int = 60) -> None:
        self._redis = redis
        self._limit = limit
        self._window = window_seconds

    async def allow(self, user_id: str) -> bool:
        key = f"rate_limit:ai:{user_id}"
        try:
            n = await self._redis.incr(key)
            if n == 1:
                await self._redis.expire(key, self._window)
            return n <= self._limit
        except Exception as exc:  # noqa: BLE001 — Redis down → fail-open (never block the assistant)
            log.warning("rate_limit.redis_unavailable_fail_open", error=str(exc))
            return True


class AllowAllRateLimiter:
    """No-op limiter (fallback when Redis is unconfigured — e.g. no lifespan)."""

    async def allow(self, user_id: str) -> bool:  # noqa: D102
        return True


def build_redis_client(url: str) -> Any:
    """Lazy async redis client (import kept local so tests never need the redis server)."""
    import redis.asyncio as redis_asyncio

    return redis_asyncio.from_url(url, decode_responses=True)
