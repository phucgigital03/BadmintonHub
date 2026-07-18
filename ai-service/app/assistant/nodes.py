"""LangGraph nodes for the read-only propose path (Day 2 / Phase 3).

perceive → memory_load → gate{clarify | search} → agent (ReAct) → rank_propose.
No WRITE, no interrupt, no money. The LLM parses fuzzy intent and plans grid queries; CODE
owns dates/budget (vi_parse) and the ranking (ranker) so the model is never the authority on
time or price.
"""

from __future__ import annotations

from datetime import date

import structlog
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from app.assistant import prompts, vi_parse
from app.assistant.models import AgentState, AgentTurn, BookingIntent
from app.assistant.ranker import RankResult, rank
from app.assistant.tools import READ_TOOLS, execute_tool_call
from app.tools import booking_tools
from app.tools.http_client import ToolError

log = structlog.get_logger(__name__)

# Criteria the user MUST supply before we search — missing → ask, never fabricate.
MANDATORY = ["sport", "date", "time_from", "time_to"]
MAX_REACT_STEPS = 3

_CLARIFY_PROMPTS = {
    "sport": "Bạn muốn đặt môn nào — Pickleball hay Badminton?",
    "date": "Bạn muốn đặt vào ngày nào?",
    "time_from": "Bạn muốn chơi khung giờ nào (ví dụ 18h–20h)?",
    "time_to": "Bạn muốn chơi đến mấy giờ?",
}


def _last_user_text(state: AgentState) -> str:
    for msg in reversed(state.get("messages", [])):
        if isinstance(msg, HumanMessage):
            return msg.content if isinstance(msg.content, str) else str(msg.content)
    return ""


def _compute_missing(intent: BookingIntent) -> list[str]:
    return [f for f in MANDATORY if getattr(intent, f) is None]


class AssistantNodes:
    """Holds the (injectable) chat model so tests can pass a fake and stay offline."""

    def __init__(self, model=None):
        self.model = model

    # --- perceive ---------------------------------------------------------------
    async def perceive(self, state: AgentState) -> dict:
        text = _last_user_text(state)
        today = date.today()

        # deterministic authority for the time/money-critical fields
        det_date = vi_parse.resolve_relative_date(text, today)
        det_from, det_to = vi_parse.parse_time_window(text)
        det_budget = vi_parse.parse_budget(text)

        parsed = await self._llm_parse(text, today)
        # deterministic values win for date/time/budget when present
        if det_date is not None:
            parsed.date = det_date
        if det_from is not None:
            parsed.time_from = det_from
        if det_to is not None:
            parsed.time_to = det_to
        if det_budget is not None:
            parsed.budget_max = det_budget

        prior = state.get("intent")
        merged = prior.merge(parsed) if prior else parsed
        merged.missing = _compute_missing(merged)
        return {"intent": merged, "stage": "gather"}

    async def _llm_parse(self, text: str, today: date) -> BookingIntent:
        if self.model is None:
            return BookingIntent()
        try:
            structured = self.model.with_structured_output(BookingIntent)
            result = await structured.ainvoke(
                [SystemMessage(content=prompts.perceive_system(today)), HumanMessage(content=text)]
            )
            intent = (
                result
                if isinstance(result, BookingIntent)
                else BookingIntent.model_validate(result)
            )
            intent.missing = []
            return intent
        except Exception as exc:  # noqa: BLE001 — LLM parse is best-effort; deterministic still applies
            log.warning("perceive.llm_parse_failed", error=str(exc))
            return BookingIntent()

    # --- memory_load ------------------------------------------------------------
    async def memory_load(self, state: AgentState) -> dict:
        """Fill empty context from the user's own data (§3). Day 2: resolve the club from the
        most recent booking, else the single club. user_preferences (L2) arrives Day 4."""
        intent = state["intent"]
        if intent.club_id is not None:
            return {"intent": intent}
        try:
            bookings = await booking_tools.get_user_bookings()
            if bookings.content and bookings.content[0].club_id:
                intent.club_id = bookings.content[0].club_id
            else:
                clubs = await booking_tools.search_clubs(sport=intent.sport)
                if clubs.content:
                    intent.club_id = clubs.content[0].id
        except ToolError as exc:
            log.warning("memory_load.tool_error", code=exc.code, status=exc.status_code)
        return {"intent": intent}

    # --- ask_clarify ------------------------------------------------------------
    async def ask_clarify(self, state: AgentState) -> dict:
        intent = state["intent"]
        missing = _compute_missing(intent)
        field = missing[0] if missing else "sport"
        question = _CLARIFY_PROMPTS.get(field, "Bạn có thể cho biết thêm chi tiết không?")
        turn = AgentTurn(content=question, suggested_actions=[])
        return {
            "turn": turn,
            "stage": "gather",
            "messages": [AIMessage(content=question)],
        }

    # --- agent (ReAct over READ tools) ------------------------------------------
    async def agent(self, state: AgentState) -> dict:
        intent = state["intent"]
        collected: list = []

        if self.model is not None:
            directive = (
                f"CLB {intent.club_id}, ngày {intent.date}, môn {intent.sport}, "
                f"khung giờ {intent.time_from}–{intent.time_to}. Hãy tra lịch sân còn trống."
            )
            convo = [SystemMessage(content=prompts.AGENT_SYSTEM), HumanMessage(content=directive)]
            llm = self.model.bind_tools(READ_TOOLS)
            for _ in range(MAX_REACT_STEPS):
                ai = await llm.ainvoke(convo)
                convo.append(ai)
                tool_calls = getattr(ai, "tool_calls", None) or []
                if not tool_calls:
                    break
                for call in tool_calls:
                    typed, content = await self._run_tool(call)
                    if typed is not None:
                        collected = typed
                    convo.append(ToolMessage(content=content, tool_call_id=call["id"]))

        # guarantee grid data even if the LLM planned no call (single-club direct read)
        if not collected and intent.club_id and intent.date:
            try:
                collected = await booking_tools.get_day_grid(
                    intent.club_id, intent.date, intent.sport
                )
            except ToolError as exc:
                log.warning("agent.grid_error", code=exc.code, status=exc.status_code)
                collected = []
        return {"raw_slots": collected, "stage": "search"}

    async def _run_tool(self, call: dict):
        try:
            return await execute_tool_call(call["name"], call["args"])
        except ToolError as exc:
            log.warning("agent.tool_error", tool=call.get("name"), code=exc.code)
            return None, f'{{"error": "{exc.code}"}}'

    # --- rank + propose (CODE) --------------------------------------------------
    async def rank_propose(self, state: AgentState) -> dict:
        intent = state["intent"]
        slots = state.get("raw_slots") or []
        result: RankResult = rank(slots, intent)
        turn = _turn_from_result(result)
        return {
            "candidates": result.candidates,
            "proposal": result.proposal,
            "turn": turn,
            "stage": "propose" if result.kind == "proposal" else "gather",
            "messages": [AIMessage(content=turn.content)],
        }


def gate(state: AgentState) -> str:
    """Conditional edge after memory_load: ask for missing mandatory criteria, else search."""
    intent = state["intent"]
    return "clarify" if _compute_missing(intent) else "search"


def _turn_from_result(result: RankResult) -> AgentTurn:
    if result.kind == "proposal" and result.proposal is not None:
        card = {
            "type": "proposal",
            "proposal": result.proposal.model_dump(mode="json"),
            "option": result.candidates[0].model_dump(mode="json") if result.candidates else None,
        }
        return AgentTurn(
            content=result.message,
            cards=[card],
            suggested_actions=["Xác nhận giữ chỗ", "Đổi giờ", "Đổi sân"],
        )
    if result.kind == "alternatives":
        cards = [
            {"type": "alternative", "option": o.model_dump(mode="json")} for o in result.candidates
        ]
        return AgentTurn(
            content=result.message,
            cards=cards,
            suggested_actions=["Đổi giờ", "Đổi sân", "Tăng ngân sách"],
        )
    return AgentTurn(content=result.message, suggested_actions=["Đổi ngày", "Đổi môn"])
