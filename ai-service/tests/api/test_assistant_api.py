"""API tests (§9): sessions · messages (SSE) · confirm · escalate · GET — real HS256 JWTs.

The ASGI client hits the app in-process (respx does not intercept an explicit
ASGITransport), while the booking tools' own httpx clients ARE intercepted by respx —
so these tests exercise auth, session fallback codes and the full confirm path offline.
"""

import json
import time as time_
import uuid
from datetime import date

import httpx
import jwt as pyjwt
import pytest
import respx

from app.assistant import sessions
from app.assistant.graph import build_graph
from app.assistant.models import BookingIntent
from app.config import get_settings
from tests.assistant._fakes import FakeModel
from tests.assistant.test_interrupt_flow import _grid_window
from tests.tools import _helpers as h

USER_A = "11111111-1111-1111-1111-111111111111"
USER_B = "22222222-2222-2222-2222-222222222222"


def mint(sub=USER_A, email_verified=True, exp_delta=3600):
    return pyjwt.encode(
        {
            "sub": sub,
            "roles": ["ROLE_USER"],
            "email_verified": email_verified,
            "jti": str(uuid.uuid4()),
            "exp": int(time_.time()) + exp_delta,
        },
        get_settings().jwt_secret,
        algorithm="HS256",
    )


def auth(token=None):
    return {"Authorization": f"Bearer {token or mint()}"}


@pytest.fixture
async def client():
    from app.api.assistant import get_graph
    from app.main import create_app

    app = create_app()
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))
    app.dependency_overrides[get_graph] = lambda: graph
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as c:
        yield c
    sessions.clear()


def sse_events(text: str) -> list[tuple[str, str]]:
    events, current = [], None
    for line in text.splitlines():
        if line.startswith("event: "):
            current = line[len("event: "):]
        elif line.startswith("data: ") and current:
            events.append((current, line[len("data: "):]))
    return events


async def _open_session(client) -> str:
    resp = await client.post("/api/ai/assistant/sessions", headers=auth())
    assert resp.status_code == 200
    return resp.json()["sessionId"]


# --- auth -------------------------------------------------------------------------

async def test_missing_token_401_platform_shape(client):
    resp = await client.post("/api/ai/assistant/sessions")
    assert resp.status_code == 401
    body = resp.json()
    assert body["code"] == "TOKEN_MISSING" and "timestamp" in body


async def test_expired_token_401(client):
    resp = await client.post(
        "/api/ai/assistant/sessions", headers=auth(mint(exp_delta=-60))
    )
    assert resp.status_code == 401
    assert resp.json()["code"] == "TOKEN_INVALID"


# --- session lifecycle: 200 / 404 / 410 (FE Day-5 A→B fallback) --------------------

async def test_new_session_then_get_state(client):
    sid = await _open_session(client)
    resp = await client.get(f"/api/ai/assistant/{sid}", headers=auth())
    assert resp.status_code == 200
    body = resp.json()
    assert body["transcript"] == [] and body["stage"] == "new"
    assert body["awaitingConfirm"] is False


async def test_unknown_session_404(client):
    resp = await client.get(f"/api/ai/assistant/{uuid.uuid4()}", headers=auth())
    assert resp.status_code == 404
    assert resp.json()["code"] == "SESSION_NOT_FOUND"


async def test_other_users_session_404_not_leaked(client):
    sid = await _open_session(client)
    resp = await client.get(f"/api/ai/assistant/{sid}", headers=auth(mint(sub=USER_B)))
    assert resp.status_code == 404


async def test_expired_session_410(client):
    sid = await _open_session(client)
    sessions._sessions[sid].last_seen -= get_settings().session_ttl_minutes * 60 + 1
    resp = await client.get(f"/api/ai/assistant/{sid}", headers=auth())
    assert resp.status_code == 410
    assert resp.json()["code"] == "SESSION_EXPIRED"


# --- confirm gate ------------------------------------------------------------------

async def test_confirm_without_pending_proposal_409(client):
    sid = await _open_session(client)
    resp = await client.post(
        f"/api/ai/assistant/{sid}/confirm", headers=auth(), json={"confirmed": True}
    )
    assert resp.status_code == 409
    assert resp.json()["code"] == "NO_PENDING_PROPOSAL"


# --- full path: message (SSE) → proposal → confirm → booking+payment ---------------

@respx.mock
async def test_message_sse_then_confirm_returns_booking_and_payment(client):
    respx.get(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(200, json=h.booking_page())
    )
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    held = h.booking()
    held["totalPrice"] = "999000"
    respx.post(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(201, json=held))
    respx.post(f"{h.BASE}/api/payments/initiate").mock(
        return_value=httpx.Response(200, json=h.payment())
    )

    sid = await _open_session(client)
    resp = await client.post(
        f"/api/ai/assistant/{sid}/messages",
        headers=auth(),
        json={"text": "đặt pickleball 18-20h dưới 200k"},
    )
    assert resp.status_code == 200
    events = dict(sse_events(resp.text))
    assert "turn" in events and "done" in events
    turn_payload = json.loads(events["turn"])
    assert turn_payload["awaitingConfirm"] is True
    assert turn_payload["turn"]["cards"][0]["type"] == "proposal"
    # money-safety over the wire: the SSE turn wrote nothing
    assert all(c.request.method == "GET" for c in respx.calls)

    resp = await client.post(
        f"/api/ai/assistant/{sid}/confirm", headers=auth(), json={"confirmed": True}
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["booking"]["status"] == "PENDING"
    assert body["booking"]["totalPrice"] == "999000"  # authoritative, camelCase
    assert body["payment"]["qrImageUrl"].startswith("https://")
    assert body["awaitingConfirm"] is False
    # §0.3 — no code path ever touches a payment /confirm endpoint
    assert all("/confirm" not in str(c.request.url) for c in respx.calls)

    resp = await client.get(f"/api/ai/assistant/{sid}", headers=auth())
    transcript = resp.json()["transcript"]
    assert transcript[0]["role"] == "user"
    assert any(m["role"] == "assistant" for m in transcript)


# --- escalate (FE-driven, §11.5) ---------------------------------------------------

@respx.mock
async def test_escalate_returns_summary_only(client):
    respx.get(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(200, json=h.booking_page())
    )
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    sid = await _open_session(client)
    await client.post(
        f"/api/ai/assistant/{sid}/messages", headers=auth(), json={"text": "đặt pickleball 18-20h"}
    )
    resp = await client.post(f"/api/ai/assistant/{sid}/escalate", headers=auth())
    assert resp.status_code == 200
    summary = resp.json()["summary"]
    assert "PICKLEBALL" in summary and "18:00" in summary
    # escalate never calls chat-service (or any WRITE) — FE opens the STAFF widget itself
    assert all(c.request.method == "GET" for c in respx.calls)
