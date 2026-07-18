"""End-to-end (offline): fake model + respx grid → a proposal CARD, and never a WRITE."""

import uuid
from datetime import date, time
from decimal import Decimal

import httpx
import respx
from langchain_core.messages import AIMessage

from app.assistant.graph import build_graph, run_turn
from app.assistant.models import BookingIntent
from tests.assistant._fakes import FakeModel
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


@respx.mock
async def test_graph_produces_proposal_card_and_no_write():
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    model = FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1)))
    graph = build_graph(model)

    state = await run_turn(
        graph, session_id="s1", bearer=TEST_BEARER, text="đặt pickleball 18-20h dưới 200k"
    )

    turn = state["turn"]
    assert turn.cards and turn.cards[0]["type"] == "proposal"
    assert state["proposal"] is not None
    assert state["proposal"].total_price == Decimal("160000")
    # money-safety: read-only, nothing written this turn
    assert all(c.request.method == "GET" for c in respx.calls)


@respx.mock
async def test_graph_react_tool_call_path_gathers_grid():
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    tool_call = AIMessage(
        content="",
        tool_calls=[{
            "name": "get_day_grid",
            "args": {"club_id": h.CLUB_ID, "date": "2026-08-01", "sport": "PICKLEBALL"},
            "id": "call-1",
            "type": "tool_call",
        }],
    )
    model = FakeModel(
        BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1)),
        tool_responses=[tool_call, AIMessage(content="đã tra xong")],
    )
    graph = build_graph(model)

    state = await run_turn(
        graph, session_id="s2", bearer=TEST_BEARER, text="đặt pickleball 18-20h dưới 200k"
    )
    # the proposal came from the slots the ReAct tool call captured
    assert state["proposal"] is not None
    assert state["proposal"].total_price == Decimal("160000")


@respx.mock
async def test_missing_sport_asks_clarify_without_searching():
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    model = FakeModel(BookingIntent(date=date(2026, 8, 1)))  # sport not provided
    graph = build_graph(model)

    state = await run_turn(
        graph, session_id="c1", bearer=TEST_BEARER, text="đặt sân 18-20h"
    )

    assert state.get("proposal") is None
    assert "môn" in state["turn"].content.lower()
    # never hit the grid (no /slots call registered → any call would error)
    assert all("/slots" not in str(c.request.url) for c in respx.calls)


@respx.mock
async def test_loopback_merges_prior_intent():
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    model = FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1)))
    graph = build_graph(model)

    await run_turn(graph, session_id="l1", bearer=TEST_BEARER, text="đặt pickleball 18-20h dưới 200k")
    state = await run_turn(graph, session_id="l1", bearer=TEST_BEARER, text="đổi qua 19h")

    intent = state["intent"]
    assert intent.time_from == time(19, 0)   # edited
    assert intent.time_to == time(20, 0)     # preserved
    assert intent.sport == "PICKLEBALL"       # preserved
