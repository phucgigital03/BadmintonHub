"""rate_limit:ai:{userId} (§11.3) — over-limit → 429, Redis down → fail-open (never block)."""

import pytest

from app.assistant.rate_limit import RedisRateLimiter
from tests.api.test_assistant_api import auth, client  # noqa: F401 — reuse the ASGI client fixture
from tests.assistant._fakes import FakeRateLimiter, FakeRedis

# --- RedisRateLimiter unit: counter + fail-open --------------------------------------


async def test_redis_limiter_allows_up_to_limit_then_blocks():
    limiter = RedisRateLimiter(FakeRedis(), limit=2)
    assert await limiter.allow("u1") is True
    assert await limiter.allow("u1") is True
    assert await limiter.allow("u1") is False  # 3rd over the cap of 2
    # a different user has an independent counter
    assert await limiter.allow("u2") is True


async def test_redis_limiter_fails_open_when_redis_down():
    limiter = RedisRateLimiter(FakeRedis(raises=True), limit=1)
    # Redis raising must NOT block the assistant — allow through
    assert await limiter.allow("u1") is True
    assert await limiter.allow("u1") is True


# --- endpoint: over-limit → 429 ------------------------------------------------------


@pytest.fixture
def over_limit_client(client):  # noqa: F811 — extends the imported client fixture
    from app.api.assistant import get_rate_limiter

    client._transport.app.dependency_overrides[get_rate_limiter] = lambda: FakeRateLimiter(
        allowed=False
    )
    return client


async def test_messages_over_rate_limit_returns_429(over_limit_client):
    resp = await over_limit_client.post("/api/ai/assistant/sessions", headers=auth())
    sid = resp.json()["sessionId"]
    resp = await over_limit_client.post(
        f"/api/ai/assistant/{sid}/messages", headers=auth(), json={"text": "đặt sân"}
    )
    assert resp.status_code == 429
    assert resp.json()["code"] == "TOO_MANY_REQUESTS"


# --- endpoint: per-session turn cap → graceful SSE stop (not a crash) ----------------


async def test_messages_turn_cap_returns_graceful_stop(client):  # noqa: F811
    from app.assistant import sessions

    resp = await client.post("/api/ai/assistant/sessions", headers=auth())
    sid = resp.json()["sessionId"]
    # pretend the session already burned all its turns
    sessions._sessions[sid].turns = 10_000
    resp = await client.post(
        f"/api/ai/assistant/{sid}/messages", headers=auth(), json={"text": "đặt sân"}
    )
    assert resp.status_code == 200  # graceful, not an error status
    body = resp.text
    assert "limit_reached" in body
    assert "Gặp nhân viên" in body
    # crucially: the graph never ran → no downstream WRITE possible
    assert "proposal" not in body
