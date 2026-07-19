"""Mixed-intent (Day 4): a side knowledge question must NOT reset the booking flow.

Two places answer knowledge mid-booking: (1) while paused at the interrupt, run_turn answers
inline and keeps the interrupt; (2) inside the agent node, search_knowledge is a bound tool the
ReAct loop can call and then continue to the proposal.
"""

from datetime import date
from decimal import Decimal

import httpx
import respx
from langchain_core.messages import AIMessage

from app.assistant.graph import build_graph, has_pending_interrupt, resume_confirm, run_turn
from app.assistant.knowledge import KnowledgeHit, KnowledgeService
from app.assistant.models import BookingIntent, ConfirmDecision
from tests.assistant._fakes import FakeEmbedder, FakeKnowledgeStore, FakeModel, fake_knowledge
from tests.assistant.test_interrupt_flow import _mock_reads
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h

VERIFIED = {"sub": "11111111-1111-1111-1111-111111111111", "email_verified": True}
_POLICY = KnowledgeHit(
    content="Hủy trước hơn 24 giờ hoàn 100%; 2–24 giờ hoàn 50%; dưới 2 giờ không hoàn.",
    source="cancellation_policy.md",
    score=0.92,
)


@respx.mock
async def test_knowledge_question_while_paused_keeps_interrupt_and_confirm_still_works():
    _mock_reads()
    respx.post(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(201, json=h.booking()))
    respx.post(f"{h.BASE}/api/payments/initiate").mock(
        return_value=httpx.Response(200, json=h.payment())
    )
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))
    svc = fake_knowledge([_POLICY])

    await run_turn(graph, session_id="m1", bearer=TEST_BEARER, text="đặt pickleball 18-20h")
    assert await has_pending_interrupt(graph, "m1")

    # side knowledge question mid-await → answered inline, WITHOUT resuming
    state = await run_turn(
        graph,
        session_id="m1",
        bearer=TEST_BEARER,
        text="hủy trước 2 tiếng có hoàn?",
        knowledge=svc,
    )
    assert "Nguồn: cancellation_policy.md" in state["turn"].content
    assert await has_pending_interrupt(graph, "m1")  # proposal STILL pending — flow not reset
    assert all(c.request.method == "GET" for c in respx.calls)  # nothing written

    # the still-pending proposal can be confirmed afterwards → hold + payment
    confirmed = await resume_confirm(
        graph,
        session_id="m1",
        bearer=TEST_BEARER,
        decision=ConfirmDecision(confirmed=True),
        claims=VERIFIED,
    )
    assert confirmed["stage"] == "payment"
    assert confirmed["payment"]["order_code"] == "#184"


@respx.mock
async def test_agent_answers_side_question_and_still_proposes():
    _mock_reads()
    embedder = FakeEmbedder()
    svc = KnowledgeService(embedder, FakeKnowledgeStore([_POLICY]))

    knowledge_call = AIMessage(
        content="",
        tool_calls=[{"name": "search_knowledge", "args": {"query": "hủy có hoàn?"}, "id": "k1", "type": "tool_call"}],
    )
    grid_call = AIMessage(
        content="",
        tool_calls=[{
            "name": "get_day_grid",
            "args": {"club_id": h.CLUB_ID, "date": "2026-08-01", "sport": "PICKLEBALL"},
            "id": "g1",
            "type": "tool_call",
        }],
    )
    model = FakeModel(
        BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1)),
        tool_responses=[knowledge_call, grid_call, AIMessage(content="xong")],
    )
    graph = build_graph(model, knowledge=svc)

    state = await run_turn(
        graph,
        session_id="m2",
        bearer=TEST_BEARER,
        text="đặt pickleball 18-20h dưới 200k, mà hủy có hoàn không?",
    )

    assert embedder.queries == ["hủy có hoàn?"]  # the side question was dispatched in-flow
    assert state["proposal"] is not None  # booking flow continued, not reset
    assert state["proposal"].total_price == Decimal("160000")
