"""Cost / loop caps (§11.3) — forcing each cap makes the turn stop, never crash."""

import types
import uuid
from datetime import date

import httpx
import pytest
import respx
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.errors import GraphRecursionError

from app.assistant import graph as graph_mod
from app.assistant import sessions
from app.assistant.graph import build_graph, run_config, run_turn
from app.assistant.limits import Caps, LimitExceeded, caps_from_settings, graceful_stop_turn
from app.assistant.models import BookingIntent
from app.assistant.nodes import AssistantNodes
from app.config import get_settings
from tests.assistant._fakes import FakeModel
from tests.assistant.test_graph import _grid_window
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h


def _caps(**kw) -> Caps:
    base = dict(
        max_turns_per_session=3,
        token_budget_per_session=1000,
        max_tool_calls_per_turn=6,
        max_react_steps=3,
        recursion_limit=15,
        llm_timeout_seconds=25.0,
        tool_timeout_seconds=12.0,
    )
    base.update(kw)
    return Caps(**base)


# --- per-session caps: turns + token budget -----------------------------------------


def test_caps_from_settings_reads_config():
    c = caps_from_settings()
    assert c.max_turns_per_session >= 1
    assert c.recursion_limit >= 1
    assert c.token_budget_per_session >= 1


def test_begin_turn_enforces_turn_cap():
    sessions.clear()
    sid = sessions.create("u1")
    caps = _caps(max_turns_per_session=2)
    sessions.begin_turn(sid, "u1", caps)
    sessions.begin_turn(sid, "u1", caps)
    with pytest.raises(LimitExceeded) as ei:
        sessions.begin_turn(sid, "u1", caps)
    assert ei.value.reason == "max_turns"
    sessions.clear()


def test_begin_turn_enforces_token_budget():
    sessions.clear()
    sid = sessions.create("u1")
    sessions.record_tokens(sid, 1500)  # already over the 1000 budget
    with pytest.raises(LimitExceeded) as ei:
        sessions.begin_turn(sid, "u1", _caps(token_budget_per_session=1000))
    assert ei.value.reason == "token_budget"
    sessions.clear()


# --- ReAct tool-call cap (bites before the step limit would) -------------------------


@respx.mock
async def test_react_tool_call_cap_stops_the_loop():
    grid = respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    # a model that ALWAYS asks for 3 tool calls → 3 steps × 3 = 9 requested, but the cap is 6.
    tcs = [
        {
            "name": "get_day_grid",
            "args": {"club_id": h.CLUB_ID, "date": "2026-08-01", "sport": "PICKLEBALL"},
            "id": f"c{i}",
            "type": "tool_call",
        }
        for i in range(3)
    ]
    model = FakeModel(BookingIntent(), tool_responses=[AIMessage(content="", tool_calls=tcs)])
    nodes = AssistantNodes(model)
    intent = BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1), club_id=uuid.UUID(h.CLUB_ID))

    out = await nodes.agent({"intent": intent})

    assert out["stage"] == "search"
    # executed exactly max_tool_calls_per_turn (6), never the 9 the model kept requesting
    assert grid.call_count == get_settings().max_tool_calls_per_turn == 6


# --- recursion limit ----------------------------------------------------------------


def test_config_carries_recursion_limit():
    cfg = run_config("sid")
    assert cfg["recursion_limit"] == get_settings().graph_recursion_limit


@respx.mock
async def test_recursion_limit_enforced_by_langgraph():
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))
    inputs = {
        "messages": [HumanMessage(content="đặt pickleball 18-20h")],
        "session_id": "r1",
        "user_id": "",
        "jwt": TEST_BEARER,
    }
    with pytest.raises(GraphRecursionError):
        await graph.ainvoke(inputs, {"configurable": {"thread_id": "r1"}, "recursion_limit": 1})


@respx.mock
async def test_run_turn_maps_recursion_to_graceful(monkeypatch):
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    # force a tiny recursion limit via run_config → the graph overruns → graceful stop
    monkeypatch.setattr(graph_mod, "get_settings", lambda: types.SimpleNamespace(graph_recursion_limit=1))
    graph = build_graph(FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))))

    state = await run_turn(graph, session_id="r2", bearer=TEST_BEARER, text="đặt pickleball 18-20h")

    assert state["stage"] == "error"
    assert state["turn"].suggested_actions == ["Gặp nhân viên"]


def test_graceful_stop_turn_shape():
    turn = graceful_stop_turn("recursion")
    assert "Gặp nhân viên" in turn.suggested_actions
    assert turn.content


# --- retention / TTL sweep (§11.6) ---------------------------------------------------


def test_sweep_evicts_expired_and_calls_on_evict():
    sessions.clear()
    fresh = sessions.create("u1")
    old = sessions.create("u2")
    ttl = get_settings().session_ttl_minutes * 60
    sessions._sessions[old].last_seen -= ttl + 1

    seen: list[str] = []
    evicted = sessions.sweep(on_evict=seen.append)

    assert evicted == [old] and seen == [old]
    assert old not in sessions._sessions
    assert fresh in sessions._sessions
    sessions.clear()
