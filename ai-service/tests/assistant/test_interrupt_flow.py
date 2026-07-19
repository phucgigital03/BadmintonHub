"""Human-in-loop flow (Day 3): interrupt pause → confirm → guardrail → hold → payment.

All offline: FakeModel + respx. The money-safety assertions are the point — nothing is
written before the human confirms, and NO call ever touches a payment /confirm path.
"""

import json
import uuid
from datetime import date
from decimal import Decimal

import httpx
import respx

from app.assistant.graph import build_graph, has_pending_interrupt, resume_confirm, run_turn
from app.assistant.models import BookingIntent, ConfirmDecision
from app.assistant.nodes import AssistantNodes
from app.security.context import set_auth
from tests.assistant._fakes import FakeModel
from tests.assistant.test_guardrail import proposal as make_proposal
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h


def _grid_window(date_str="2026-08-01", price="40000"):
    cells = [
        {"id": str(uuid.uuid4()), "date": date_str, "startTime": s, "endTime": e,
         "status": "AVAILABLE", "price": price}
        for s, e in [
            ("18:00:00", "18:30:00"), ("18:30:00", "19:00:00"),
            ("19:00:00", "19:30:00"), ("19:30:00", "20:00:00"),
        ]
    ]
    return {
        "date": date_str, "dayType": "WEEKEND",
        "courts": [
            {"id": h.COURT_ID, "courtNumber": "Sân 1", "sport": "PICKLEBALL",
             "type": "OUTDOOR", "slots": cells}
        ],
    }


def _graph():
    return build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))


def _mock_reads(grid=None):
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=grid or _grid_window())
    )


def _no_payment_confirm_called():
    return all("/confirm" not in str(c.request.url) for c in respx.calls)


VERIFIED_CLAIMS = {"sub": "11111111-1111-1111-1111-111111111111", "email_verified": True}


# --- interrupt pauses BEFORE any write --------------------------------------------

@respx.mock
async def test_proposal_pauses_at_interrupt_and_writes_nothing():
    _mock_reads()
    graph = _graph()
    state = await run_turn(
        graph, session_id="i1", bearer=TEST_BEARER, text="đặt pickleball 18-20h dưới 200k"
    )
    assert state["proposal"] is not None
    assert state["stage"] == "await_confirm"
    assert await has_pending_interrupt(graph, "i1")
    # money-safety: nothing written while awaiting the human
    assert all(c.request.method == "GET" for c in respx.calls)


# --- confirm → guardrail → hold → payment (QR) ------------------------------------

@respx.mock
async def test_confirm_creates_hold_then_payment_with_authoritative_price():
    _mock_reads()
    held = h.booking()
    held["totalPrice"] = "999000"  # server-computed price ≠ the agent's 160000 estimate
    hold_route = respx.post(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(201, json=held)
    )
    pay = h.payment()
    pay["amount"] = "999000"
    initiate_route = respx.post(f"{h.BASE}/api/payments/initiate").mock(
        return_value=httpx.Response(200, json=pay)
    )

    graph = _graph()
    await run_turn(graph, session_id="c1", bearer=TEST_BEARER, text="đặt pickleball 18-20h")
    state = await resume_confirm(
        graph,
        session_id="c1",
        bearer=TEST_BEARER,
        decision=ConfirmDecision(confirmed=True),
        claims=VERIFIED_CLAIMS,
    )

    assert hold_route.call_count == 1 and initiate_route.call_count == 1
    assert state["stage"] == "payment"
    assert state["hold"]["status"] == "PENDING"
    assert state["payment"]["order_code"] == "#184"
    # §11.4 — the user sees the SERVER total, never the agent's estimate
    assert "999,000" in state["turn"].content
    # §11.4 — contact defaulted from the latest booking (memory_load), no new endpoint
    body = json.loads(hold_route.calls[0].request.content)
    assert body["customerName"] == "Nguyễn Văn A"
    assert body["customerPhone"] == "0901234567"
    assert not await has_pending_interrupt(graph, "c1")
    assert _no_payment_confirm_called()


# --- decline / edit keep everything read-only -------------------------------------

@respx.mock
async def test_decline_writes_nothing_and_clears_interrupt():
    _mock_reads()
    graph = _graph()
    await run_turn(graph, session_id="d1", bearer=TEST_BEARER, text="đặt pickleball 18-20h")
    state = await resume_confirm(
        graph, session_id="d1", bearer=TEST_BEARER, decision=ConfirmDecision(confirmed=False)
    )
    assert state.get("hold") is None and state.get("payment") is None
    assert not await has_pending_interrupt(graph, "d1")
    assert all(c.request.method == "GET" for c in respx.calls)


@respx.mock
async def test_free_text_while_paused_becomes_edit_and_reproposes():
    _mock_reads()
    graph = _graph()
    await run_turn(graph, session_id="e1", bearer=TEST_BEARER, text="đặt pickleball 18-20h")
    assert await has_pending_interrupt(graph, "e1")

    state = await run_turn(graph, session_id="e1", bearer=TEST_BEARER, text="đổi qua 19h")

    assert state["intent"].time_from.hour == 19  # merged edit, not a re-parse
    assert state["proposal"] is not None
    assert state["proposal"].total_price == Decimal("80000")  # 19–20h = 2 cells
    assert await has_pending_interrupt(graph, "e1")  # a NEW human gate for the new proposal
    assert all(c.request.method == "GET" for c in respx.calls)


# --- guardrail node routing (direct, deterministic) --------------------------------

def _state_with(proposal, **intent_kw):
    intent = BookingIntent(
        sport="PICKLEBALL", date=date(2026, 8, 1),
        club_id=uuid.UUID(h.CLUB_ID), **intent_kw,
    )
    return {"intent": intent, "proposal": proposal}


async def test_guardrail_blocks_over_budget_without_any_http():
    nodes = AssistantNodes(None)
    cmd = await nodes.guardrail(_state_with(make_proposal("160000"), budget_max=100_000))
    assert cmd.goto == "human_review"
    assert "vượt ngân sách" in cmd.update["turn"].content


async def test_guardrail_blocks_unverified_email():
    set_auth(TEST_BEARER, {"sub": "u", "email_verified": False})
    nodes = AssistantNodes(None)
    cmd = await nodes.guardrail(_state_with(make_proposal()))
    assert cmd.goto == "human_review"
    assert "xác thực email" in cmd.update["turn"].content


@respx.mock
async def test_guardrail_reroutes_to_agent_when_slot_lost():
    # fresh grid no longer contains the proposed slots → re-propose, never POST
    respx.get(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(200, json={"content": [], "totalElements": 0})
    )
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    nodes = AssistantNodes(None)
    cmd = await nodes.guardrail(_state_with(make_proposal()))
    assert cmd.goto == "agent"
    assert all(c.request.method == "GET" for c in respx.calls)


@respx.mock
async def test_guardrail_reuses_own_pending_hold():
    # the user's PENDING booking already holds one of the proposed slots → skip create
    page = h.booking_page()  # helper booking is PENDING and holds SLOT_ID
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=page))
    nodes = AssistantNodes(None)
    prop = make_proposal(slot_ids=(uuid.UUID(h.SLOT_ID),))
    cmd = await nodes.guardrail(_state_with(prop))
    assert cmd.goto == "payment"
    assert cmd.update["hold"]["id"] == h.BOOKING_ID
