"""Route node (Day 4): context-aware classification booking vs static-knowledge."""

import uuid
from datetime import date

import httpx
import respx

from app.assistant.graph import build_graph, run_turn
from app.assistant.knowledge import KnowledgeHit
from app.assistant.models import BookingIntent
from app.assistant.nodes import classify_route
from tests.assistant._fakes import FakeModel, fake_knowledge
from tests.assistant.test_interrupt_flow import _grid_window
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h

_INTENT = BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1), club_id=uuid.UUID(h.CLUB_ID))


# --- classify_route (deterministic, context-aware) --------------------------------


def test_fresh_policy_question_routes_to_knowledge():
    assert classify_route(None, None, "chính sách hủy sân thế nào?") == "knowledge"


def test_loopback_edit_stays_on_booking():
    # mid-booking "đổi qua 19h" must NOT be misrouted (no knowledge keyword anyway)
    assert classify_route(_INTENT, "await_confirm", "đổi qua 19h") == "booking"


def test_clarify_answer_stays_on_booking():
    assert classify_route(_INTENT, "gather", "pickleball") == "booking"


def test_price_question_is_live_data_not_knowledge():
    # price/availability is a LIVE tool-query, never the corpus (§6.1)
    assert classify_route(None, None, "giá sân pickleball tối mai bao nhiêu?") == "booking"


def test_midbooking_knowledge_word_without_question_stays_booking():
    # a passing mention of "thanh toán" mid-booking that isn't a question → booking
    assert classify_route(_INTENT, "await_confirm", "thanh toán chuyển khoản nhé") == "booking"


def test_midbooking_knowledge_question_routes_to_knowledge():
    assert classify_route(_INTENT, "await_confirm", "hủy trước 2 tiếng có hoàn không?") == "knowledge"


# --- graph-level routing ----------------------------------------------------------


@respx.mock
async def test_graph_routes_fresh_policy_to_knowledge_node():
    hit = KnowledgeHit(content="Hủy trước hơn 24 giờ hoàn 100%.", source="cancellation_policy.md", score=0.9)
    graph = build_graph(FakeModel(BookingIntent()), knowledge=fake_knowledge([hit]))

    state = await run_turn(
        graph, session_id="r1", bearer=TEST_BEARER, text="chính sách hủy sân thế nào?"
    )

    assert state["stage"] == "knowledge"
    assert state.get("proposal") is None
    assert "Nguồn" in state["turn"].content
    # a knowledge turn never queries booking endpoints
    assert all("/api/bookings" not in str(c.request.url) for c in respx.calls)


@respx.mock
async def test_graph_routes_booking_message_to_perceive():
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    hit = KnowledgeHit(content="...", source="x.md", score=0.9)
    graph = build_graph(
        FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))),
        knowledge=fake_knowledge([hit]),
    )

    state = await run_turn(
        graph, session_id="r2", bearer=TEST_BEARER, text="đặt pickleball 18-20h dưới 200k"
    )

    assert state["proposal"] is not None  # went through the booking branch
