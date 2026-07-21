"""Day-7 eval harness (offline, always CI) — the labeled scorecards behind §15.

Runs the labeled corpora in `_eval.py` against the DETERMINISTIC pipeline (vi_parse + the CODE
router + the graph with a fake model). Because date/time/budget parsing and every money decision
are CODE — not the LLM — these evals are reproducible and free, yet they measure exactly the
acceptance items §15 cares about ("parse đúng intent", "tool plan đúng"). The optional live
harness (`test_eval_live.py`) re-runs the intent set through the real Gemini model for the
fuzzy fields (implied sport / party size) — see `RUN_LIVE_EVAL`.
"""

from __future__ import annotations

import httpx
import respx

from app.assistant.graph import build_graph, run_turn
from app.assistant.knowledge import KnowledgeHit
from app.assistant.models import BookingIntent
from app.assistant.nodes import classify_route
from tests.assistant import _eval
from tests.assistant._fakes import FakeModel, fake_knowledge
from tests.assistant.test_interrupt_flow import _grid_window
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h

_ACTIVE_INTENT = BookingIntent(sport="PICKLEBALL")


# --- intent parsing (deterministic slice) -----------------------------------------


def test_intent_parse_deterministic_slice():
    """Every labeled utterance → the exact date/time/budget/sport `vi_parse` must produce.
    This is the free, reproducible half of §15 'parse đúng intent tiếng Việt'."""
    results: list[tuple[str, bool]] = []
    failures: list[str] = []
    for case in _eval.INTENT_CASES:
        got = _eval.deterministic_intent(case.text, case.today)
        ok = True
        for key, want in case.expected.items():
            if got[key] != want:
                ok = False
                failures.append(f"{case.label}: {key} expected {want!r} got {got[key]!r}")
        results.append((case.label, ok))

    print(_eval.scorecard("Intent parse (deterministic slice)", results))
    passed, total, rate = _eval.score(results)
    assert rate == 1.0, f"{total - passed} intent case(s) failed:\n" + "\n".join(failures)


# --- routing (booking vs knowledge) -----------------------------------------------


def test_route_classification():
    """The CODE router sends policy/facility questions to knowledge and everything with a booking
    signal (incl. price — a LIVE query, §6.1) to booking. Half of §15 'tool plan đúng'."""
    results: list[tuple[str, bool]] = []
    failures: list[str] = []
    for case in _eval.ROUTE_CASES:
        intent = _ACTIVE_INTENT if case.intent else None
        got = classify_route(intent, case.stage, case.text)
        ok = got == case.expected
        results.append((case.label, ok))
        if not ok:
            failures.append(f"{case.label}: expected {case.expected} got {got}")

    print(_eval.scorecard("Route classification", results))
    passed, total, rate = _eval.score(results)
    assert rate == 1.0, "route failures:\n" + "\n".join(failures)


# --- tool plan (graph outcome) -----------------------------------------------------


def _outcome(state: dict) -> str:
    if state.get("stage") == "knowledge":
        return "knowledge"
    if state.get("proposal") is not None and state.get("stage") == "await_confirm":
        return "proposal"
    return "clarify"


@respx.mock
async def test_tool_plan_outcomes():
    """Drive the full graph offline for each planned utterance and assert the outcome
    (proposal / clarify / knowledge) — and that the turn wrote NOTHING (all GET)."""
    respx.get(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(200, json=h.booking_page()))
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=_grid_window())
    )
    hit = KnowledgeHit(content="Hủy trước hơn 24 giờ hoàn 100%.", source="cancellation_policy.md", score=0.9)

    results: list[tuple[str, bool]] = []
    failures: list[str] = []
    for case in _eval.TOOLPLAN_CASES:
        knowledge = fake_knowledge([hit]) if case.knowledge_hit else None
        graph = build_graph(FakeModel(BookingIntent(**case.intent_kwargs)), knowledge=knowledge)
        state = await run_turn(
            graph, session_id=f"tp-{case.label}", bearer=TEST_BEARER, text=case.text
        )
        got = _outcome(state)
        ok = got == case.expected_outcome
        results.append((case.label, ok))
        if not ok:
            failures.append(f"{case.label}: expected {case.expected_outcome} got {got}")

    print(_eval.scorecard("Tool plan (graph outcome)", results))
    passed, total, rate = _eval.score(results)
    assert rate == 1.0, "tool-plan failures:\n" + "\n".join(failures)
    # money-safety across the whole plan corpus: not one WRITE
    assert all(c.request.method == "GET" for c in respx.calls)
