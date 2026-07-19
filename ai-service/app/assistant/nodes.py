"""LangGraph nodes: propose path (Day 2) + human-in-loop WRITE path (Day 3 / Phase 4).

perceive → memory_load → gate{clarify | search} → agent (ReAct) → rank_propose
    → human_review (interrupt) → guardrail (CODE) → hold → payment.

Money boundary (§0.3): the WRITE tools run only after the human confirmed at the interrupt
AND the deterministic guardrail passed. No code path here — or anywhere in this service —
calls the payment /confirm endpoint; confirming money stays with STAFF in the old flow.
"""

from __future__ import annotations

from datetime import date

import structlog
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.graph import END
from langgraph.types import Command, interrupt

from app.assistant import guardrail as checks
from app.assistant import prompts, vi_parse
from app.assistant.models import AgentState, AgentTurn, BookingIntent, ConfirmDecision
from app.assistant.ranker import RankResult, rank
from app.assistant.tools import READ_TOOLS, execute_tool_call
from app.security.context import get_claims
from app.tools import booking_tools, schemas
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

        # deterministic authority for the time/money-critical fields (+ obvious sport
        # keywords, so the concierge still works when the LLM is down)
        det_date = vi_parse.resolve_relative_date(text, today)
        det_from, det_to = vi_parse.parse_time_window(text)
        det_budget = vi_parse.parse_budget(text)
        det_sport = vi_parse.parse_sport(text)

        parsed = await self._llm_parse(text, today)
        # deterministic values win for date/time/budget/sport when present
        if det_date is not None:
            parsed.date = det_date
        if det_from is not None:
            parsed.time_from = det_from
        if det_to is not None:
            parsed.time_to = det_to
        if det_budget is not None:
            parsed.budget_max = det_budget
        if det_sport is not None:
            parsed.sport = det_sport

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
            # ERROR (not warning): a dead/out-of-quota key silently degrades the assistant —
            # this is the platform's alert channel (alert on ERROR logs). Log exc_type too so
            # an integration bug (KeyError/TypeError) is not misread as "the LLM is down".
            log.error("perceive.llm_parse_failed", exc_type=type(exc).__name__, error=str(exc))
            return BookingIntent()

    # --- memory_load ------------------------------------------------------------
    async def memory_load(self, state: AgentState) -> dict:
        """Fill empty context from the user's own data (§3): the club from the most recent
        booking (else the single club) and the default contact name/phone (§11.4 —
        UserResponse has no phone, so the latest booking is the only source).
        user_preferences (L2) arrives Day 4."""
        intent = state["intent"]
        update: dict = {"intent": intent}
        need_club = intent.club_id is None
        need_contact = not state.get("default_contact")
        if not (need_club or need_contact):
            return update
        try:
            bookings = await booking_tools.get_user_bookings()
            latest = bookings.content[0] if bookings.content else None
            if need_contact and latest and (latest.customer_name or latest.customer_phone):
                update["default_contact"] = {
                    "name": latest.customer_name,
                    "phone": latest.customer_phone,
                }
            if need_club:
                if latest and latest.club_id:
                    intent.club_id = latest.club_id
                else:
                    clubs = await booking_tools.search_clubs(sport=intent.sport)
                    if clubs.content:
                        intent.club_id = clubs.content[0].id
        except ToolError as exc:
            log.warning("memory_load.tool_error", code=exc.code, status=exc.status_code)
        return update

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
            try:
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
            except Exception as exc:  # noqa: BLE001 — LLM planning is best-effort; the direct
                # grid read below still serves the deterministic path (LLM down ≠ service down).
                # ERROR + exc_type so a broken key surfaces on the alert channel, and an
                # integration bug isn't misattributed to the LLM being down.
                log.error("agent.llm_error", exc_type=type(exc).__name__, error=str(exc))

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
        if result.proposal is not None:
            contact = state.get("default_contact") or {}
            result.proposal.customer_name = result.proposal.customer_name or contact.get("name")
            result.proposal.customer_phone = result.proposal.customer_phone or contact.get("phone")
        turn = _turn_from_result(result)
        return {
            "candidates": result.candidates,
            "proposal": result.proposal,
            "turn": turn,
            "stage": "await_confirm" if result.kind == "proposal" else "gather",
            "messages": [AIMessage(content=turn.content)],
        }

    # --- human_review (interrupt — the human gate before ANY write, §0.3) --------
    async def human_review(self, state: AgentState) -> Command:
        """Pause the graph and surface the proposal; resume with a ConfirmDecision.

        On resume this node re-executes and interrupt() returns the decision payload:
        confirmed → guardrail; edits → loop back to perceive (the edit text becomes a new
        user message, so the intent MERGES and a fresh proposal comes back to this gate);
        declined → end the turn, nothing written.
        """
        proposal = state["proposal"]
        card = {
            "type": "proposal",
            "proposal": proposal.model_dump(mode="json") if proposal else None,
        }
        decision = ConfirmDecision.model_validate(interrupt(card))

        if decision.confirmed:
            update: dict = {"stage": "await_confirm"}
            if proposal and (decision.customer_name or decision.customer_phone):
                update["proposal"] = proposal.model_copy(
                    update={
                        "customer_name": decision.customer_name or proposal.customer_name,
                        "customer_phone": decision.customer_phone or proposal.customer_phone,
                    }
                )
            return Command(goto="guardrail", update=update)

        if decision.edits:
            return Command(
                goto="perceive", update={"messages": [HumanMessage(content=decision.edits)]}
            )

        turn = AgentTurn(
            content="Đã bỏ đề xuất này. Bạn muốn đổi giờ, đổi sân hay tìm ngày khác?",
            suggested_actions=["Đổi giờ", "Đổi sân", "Đổi ngày"],
        )
        return Command(
            goto=END,
            update={
                "turn": turn,
                "stage": "propose",
                "messages": [AIMessage(content=turn.content)],
            },
        )

    # --- guardrail (deterministic CODE gate — never the LLM, §0.3 rule 5) --------
    async def guardrail(self, state: AgentState) -> Command:
        """Money gate before create_booking_hold. Order matters:
        email_verified → contact → budget → reuse-pending (idempotent confirm) → grid re-check.
        Bounces go back to human_review (a NEW interrupt) so /confirm stays usable; a lost
        slot re-runs the agent for a fresh proposal."""
        intent = state["intent"]
        proposal = state["proposal"]

        for failure in (
            checks.check_email_verified(get_claims()),
            checks.check_contact(proposal),
            checks.check_budget(proposal, intent),
        ):
            if failure is not None:
                return _bounce_to_review(failure.message)

        # idempotency BEFORE the grid re-check: our own PENDING hold shows RESERVED on the
        # grid — re-checking first would misread it as "slot lost" and loop forever.
        try:
            bookings = await booking_tools.get_user_bookings()
            reusable = checks.find_reusable_pending(proposal, bookings.content)
            if reusable is not None:
                log.info("guardrail.reuse_pending", booking_id=str(reusable.id))
                return Command(goto="payment", update={"hold": reusable.model_dump(mode="json")})

            fresh = await booking_tools.get_day_grid(intent.club_id, intent.date, intent.sport)
        except ToolError as exc:
            log.warning("guardrail.tool_error", code=exc.code, status=exc.status_code)
            return _bounce_to_review(
                "Hệ thống đang bận, mình chưa kiểm tra lại được lịch sân. Bạn thử xác nhận lại nhé."
            )

        if checks.check_slots_still_available(proposal, fresh) is not None:
            note = "Một số ô vừa có người giữ mất — mình tìm lại phương án khác cho bạn nhé."
            return Command(
                goto="agent",
                update={"stage": "search", "messages": [AIMessage(content=note)]},
            )
        return Command(goto="hold")

    # --- hold (WRITE #1 — only reachable through human_review + guardrail) -------
    async def hold(self, state: AgentState) -> Command:
        proposal = state["proposal"]
        try:
            booking = await booking_tools.create_booking_hold(
                proposal.club_id,
                proposal.date,
                proposal.items,
                proposal.customer_name,
                proposal.customer_phone,
            )
        except ToolError as exc:
            log.warning("hold.tool_error", code=exc.code, status=exc.status_code)
            if exc.status_code == 409:
                # lost the race despite the re-check — the DB UNIQUE is the real arbiter
                note = "Ô vừa bị giữ trước một bước — mình tìm phương án khác cho bạn nhé."
                return Command(
                    goto="agent",
                    update={"stage": "search", "messages": [AIMessage(content=note)]},
                )
            if exc.status_code == 403:
                return _bounce_to_review(
                    "Hệ thống báo tài khoản chưa đủ điều kiện giữ chỗ (cần xác thực email). "
                    "Bạn xác thực xong hãy bấm xác nhận lại nhé."
                )
            return _bounce_to_review(
                "Chưa giữ chỗ được do lỗi hệ thống. Bạn thử xác nhận lại trong giây lát nhé."
            )
        return Command(goto="payment", update={"hold": booking.model_dump(mode="json")})

    # --- payment (WRITE #2 — Bank-QR initiate; NEVER /confirm) --------------------
    async def payment(self, state: AgentState) -> Command:
        booking = schemas.BookingResponse.model_validate(state["hold"])
        try:
            pay = await booking_tools.initiate_payment(booking.id)
        except ToolError as exc:
            log.warning("payment.tool_error", code=exc.code, status=exc.status_code)
            turn = AgentTurn(
                content=(
                    "Đã giữ chỗ nhưng chưa mở được màn thanh toán "
                    f"({exc.code or exc.status_code}). Bạn bấm xác nhận lại để thử lần nữa nhé."
                ),
                suggested_actions=["Xác nhận giữ chỗ"],
            )
            # keep the hold; bounce to a new interrupt → next confirm reuses it (guardrail
            # finds the PENDING booking) and just retries initiate.
            return Command(
                goto="human_review",
                update={
                    "turn": turn,
                    "stage": "held",
                    "messages": [AIMessage(content=turn.content)],
                },
            )

        # §11.4 — the money shown comes from the server (booking.totalPrice / payment.amount),
        # never from the agent's own estimate.
        amount = pay.amount if pay.amount is not None else booking.total_price
        content = (
            f"Đã giữ chỗ thành công (đơn PENDING, giữ 10 phút). "
            f"Tổng tiền chính xác: {int(amount):,}đ. "
            f"Vui lòng chuyển khoản với nội dung {pay.order_code} — màn QR đang mở."
        )
        turn = AgentTurn(
            content=content,
            cards=[
                {
                    "type": "payment",
                    "booking": booking.model_dump(mode="json"),
                    "payment": pay.model_dump(mode="json"),
                }
            ],
        )
        return Command(
            goto=END,
            update={
                "payment": pay.model_dump(mode="json"),
                "turn": turn,
                "stage": "payment",
                "messages": [AIMessage(content=content)],
            },
        )


def gate(state: AgentState) -> str:
    """Conditional edge after memory_load: ask for missing mandatory criteria, else search."""
    intent = state["intent"]
    return "clarify" if _compute_missing(intent) else "search"


def after_propose(state: AgentState) -> str:
    """Conditional edge after rank_propose: a concrete proposal goes to the human gate."""
    return "review" if state.get("proposal") is not None else "end"


def _bounce_to_review(message: str) -> Command:
    """Guardrail/hold refusal → explain + pause at a NEW interrupt so /confirm stays usable
    (the user can fix the condition — verify email, add contact, raise budget — and confirm
    again on the same session). Nothing was written."""
    turn = AgentTurn(content=message, suggested_actions=["Xác nhận giữ chỗ", "Đổi giờ"])
    return Command(
        goto="human_review",
        update={
            "turn": turn,
            "stage": "await_confirm",
            "messages": [AIMessage(content=message)],
        },
    )


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
