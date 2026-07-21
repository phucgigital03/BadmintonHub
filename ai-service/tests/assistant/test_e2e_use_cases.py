"""Day-7 runtime e2e (offline) — one traceable test per UC-CS-01..08 (§16 Day 7, §17 task 3).

Each test drives the real code path with the offline fakes and asserts the UC's one-line behavior
from §0.1, so the suite doubles as a UC→evidence matrix for the thesis. The LIVE runtime e2e (the
same 8 UCs against the running stack + real Gemini) is the manual runbook in GO_LIVE_CHECKLIST.md.
"""

from __future__ import annotations

import uuid
from datetime import date, time
from decimal import Decimal

import httpx
import respx

from app.assistant import preferences as prefs_mod
from app.assistant.graph import build_graph, has_pending_interrupt, resume_confirm, run_turn
from app.assistant.knowledge import KnowledgeHit
from app.assistant.models import BookingIntent, ConfirmDecision
from app.assistant.nodes import AssistantNodes, compose_knowledge_turn

# Reuse the ASGI client + JWT helpers for the endpoint-driven UCs (session lifecycle, escalate).
from tests.api.test_assistant_api import auth, client  # noqa: F401 — `client` is a fixture
from tests.assistant._fakes import FakeModel, FakePreferenceStore, fake_knowledge
from tests.assistant.test_interrupt_flow import (
    _grid_window,
    _mock_reads,
    _no_payment_confirm_called,
)
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h

USER = "11111111-1111-1111-1111-111111111111"
VERIFIED = {"sub": USER, "email_verified": True}


def _grid_alt(date_str: str = "2026-08-01") -> dict:
    """A grid where the requested 18–20h window is gone but a same-length 20–22h block remains."""
    cells = [
        {"id": str(uuid.uuid4()), "date": date_str, "startTime": s, "endTime": e,
         "status": "AVAILABLE", "price": "40000"}
        for s, e in [
            ("20:00:00", "20:30:00"), ("20:30:00", "21:00:00"),
            ("21:00:00", "21:30:00"), ("21:30:00", "22:00:00"),
        ]
    ]
    return {
        "date": date_str, "dayType": "WEEKEND",
        "courts": [{"id": h.COURT_ID, "courtNumber": "Sân 1", "sport": "PICKLEBALL",
                    "type": "OUTDOOR", "slots": cells}],
    }


# --- UC-CS-01 · session init -------------------------------------------------------


async def test_uc_cs_01_session_init_creates_thread_ready_to_converse(client):  # noqa: F811
    """UC-CS-01: opening the widget creates a session (thread with state), ready to chat."""
    resp = await client.post("/api/ai/assistant/sessions", headers=auth())
    assert resp.status_code == 200
    sid = resp.json()["sessionId"]

    state = await client.get(f"/api/ai/assistant/{sid}", headers=auth())
    assert state.status_code == 200
    body = state.json()
    assert body["stage"] == "new" and body["transcript"] == [] and body["awaitingConfirm"] is False


# --- UC-CS-02 · slot-filling (ask, never fabricate) --------------------------------


@respx.mock
async def test_uc_cs_02_slot_filling_asks_and_never_searches():
    """UC-CS-02: a vague request missing a mandatory field → ask, don't invent (no grid query)."""
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    graph = build_graph(FakeModel(BookingIntent(date=date(2026, 8, 1))))  # sport missing

    state = await run_turn(graph, session_id="uc2", bearer=TEST_BEARER, text="đặt sân 18-20h")

    assert state.get("proposal") is None
    assert "môn" in state["turn"].content.lower()  # asked for the missing sport
    assert all("/slots" not in str(c.request.url) for c in respx.calls)  # never fabricated a search


# --- UC-CS-03 · rank + propose from the live grid ----------------------------------


@respx.mock
async def test_uc_cs_03_ranks_and_proposes_from_live_grid():
    """UC-CS-03: with criteria complete, query the real grid, rank, and propose the best option."""
    _mock_reads()
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))

    state = await run_turn(
        graph, session_id="uc3", bearer=TEST_BEARER, text="đặt pickleball 18-20h dưới 200k"
    )

    card = state["turn"].cards[0]
    assert card["type"] == "proposal"
    assert state["proposal"].total_price == Decimal("160000")  # from the grid, not invented
    assert all(c.request.method == "GET" for c in respx.calls)


# --- UC-CS-04 · alternatives when the window is full -------------------------------


@respx.mock
async def test_uc_cs_04_offers_alternatives_when_window_full():
    """UC-CS-04: requested window unavailable → propose grid-backed alternatives (change time)."""
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_alt())
    )
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))

    state = await run_turn(graph, session_id="uc4", bearer=TEST_BEARER, text="đặt pickleball 18-20h")

    assert state.get("proposal") is None  # no exact fit
    assert state["turn"].cards and state["turn"].cards[0]["type"] == "alternative"
    assert all(c.request.method == "GET" for c in respx.calls)


# --- UC-CS-05 · confirm → hold + QR (never auto-confirm money) ----------------------


@respx.mock
async def test_uc_cs_05_confirm_holds_and_opens_qr_never_confirms_money():
    """UC-CS-05: on the user's confirm → create the PENDING hold + open Bank-QR; never /confirm."""
    _mock_reads()
    respx.post(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(201, json=h.booking()))
    respx.post(f"{h.BASE}/api/payments/initiate").mock(
        return_value=httpx.Response(200, json=h.payment())
    )
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))

    await run_turn(graph, session_id="uc5", bearer=TEST_BEARER, text="đặt pickleball 18-20h")
    state = await resume_confirm(
        graph, session_id="uc5", bearer=TEST_BEARER,
        decision=ConfirmDecision(confirmed=True), claims=VERIFIED,
    )

    assert state["hold"]["status"] == "PENDING"
    assert state["payment"]["order_code"] == "#184"  # Bank-QR opened
    assert state["turn"].cards[-1]["type"] == "payment"
    assert not await has_pending_interrupt(graph, "uc5")
    assert _no_payment_confirm_called()  # §0.3 — no code path confirms money


# --- UC-CS-06 · personalization from history ---------------------------------------


async def test_uc_cs_06_personalizes_from_history():
    """UC-CS-06: remembered club/sport/time/budget fill the gaps the user didn't state."""
    snap = prefs_mod.PreferenceSnapshot(
        preferred_club_id=uuid.UUID(h.CLUB_ID), preferred_sport="PICKLEBALL",
        preferred_time_window="18:00-20:00", budget_max=200_000,
    )
    nodes = AssistantNodes(None, prefs_store=FakePreferenceStore({USER: snap}))
    intent = BookingIntent(date=date(2026, 8, 1), club_id=uuid.UUID(h.CLUB_ID))
    state = {"intent": intent, "user_id": USER, "default_contact": {"name": "x", "phone": "y"}}

    update = await nodes.memory_load(state)

    filled = update["intent"]
    assert filled.sport == "PICKLEBALL"
    assert filled.time_from == time(18, 0) and filled.time_to == time(20, 0)
    assert filled.budget_max == 200_000
    assert update["personalization_note"]  # surfaced to the user


# --- UC-CS-07 · escalate to STAFF with context -------------------------------------


@respx.mock
async def test_uc_cs_07_escalate_returns_context_summary(client):  # noqa: F811
    """UC-CS-07: escalate hands the STAFF widget a context summary (FE-driven, no WRITE)."""
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    sid = (await client.post("/api/ai/assistant/sessions", headers=auth())).json()["sessionId"]
    await client.post(
        f"/api/ai/assistant/{sid}/messages", headers=auth(), json={"text": "đặt pickleball 18-20h"}
    )

    resp = await client.post(f"/api/ai/assistant/{sid}/escalate", headers=auth())

    assert resp.status_code == 200
    summary = resp.json()["summary"]
    assert "PICKLEBALL" in summary and "18:00" in summary  # context carried over
    assert all(c.request.method == "GET" for c in respx.calls)  # escalate never writes


# --- UC-CS-08 · RAG knowledge (cited, never fabricated) ----------------------------


@respx.mock
async def test_uc_cs_08_rag_cites_source_and_never_fabricates():
    """UC-CS-08: a policy question routes to knowledge → grounded answer WITH a source citation;
    a below-threshold retrieval never fabricates — it offers to escalate."""
    hit = KnowledgeHit(
        content="Hủy trước hơn 24 giờ được hoàn 100% tổng tiền.",
        source="cancellation_policy.md", score=0.9,
    )
    graph = build_graph(FakeModel(BookingIntent()), knowledge=fake_knowledge([hit]))

    state = await run_turn(
        graph, session_id="uc8", bearer=TEST_BEARER, text="chính sách hủy sân thế nào?"
    )
    assert state["stage"] == "knowledge"
    assert "hoàn 100%" in state["turn"].content
    assert "Nguồn: cancellation_policy.md" in state["turn"].content  # cited
    assert all("/api/bookings" not in str(c.request.url) for c in respx.calls)

    # below the cosine floor → do NOT answer from an irrelevant chunk; offer a human
    weak = compose_knowledge_turn([KnowledgeHit(content="không liên quan", source="x.md", score=0.1)])
    assert "chưa có thông tin" in weak.content.lower()
    assert "Gặp nhân viên" in weak.suggested_actions
    assert "x.md" not in weak.content
