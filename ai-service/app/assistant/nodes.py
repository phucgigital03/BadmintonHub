"""LangGraph nodes: propose path (Day 2) + human-in-loop WRITE path (Day 3 / Phase 4).

perceive → memory_load → gate{clarify | search} → agent (ReAct) → rank_propose
    → human_review (interrupt) → guardrail (CODE) → hold → payment.

Money boundary (§0.3): the WRITE tools run only after the human confirmed at the interrupt
AND the deterministic guardrail passed. No code path here — or anywhere in this service —
calls the payment /confirm endpoint; confirming money stays with STAFF in the old flow.
"""

from __future__ import annotations

import asyncio
import unicodedata
from datetime import date

import structlog
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.graph import END
from langgraph.types import Command, interrupt

from app.assistant import audit, prompts, vi_parse
from app.assistant import guardrail as checks
from app.assistant import preferences as prefs_mod
from app.assistant.knowledge import KnowledgeHit
from app.assistant.models import AgentState, AgentTurn, BookingIntent, ConfirmDecision
from app.assistant.ranker import RankResult, rank
from app.assistant.tools import READ_TOOLS, execute_tool_call, search_knowledge
from app.config import get_settings
from app.security.context import get_claims
from app.tools import booking_tools, schemas
from app.tools.http_client import ToolError

log = structlog.get_logger(__name__)

# Criteria the user MUST supply before we search — missing → ask, never fabricate.
MANDATORY = ["sport", "date", "time_from", "time_to"]

_CLARIFY_PROMPTS = {
    "sport": "Bạn muốn đặt môn nào — Pickleball hay Badminton?",
    "date": "Bạn muốn đặt vào ngày nào?",
    "time_from": "Bạn muốn chơi khung giờ nào (ví dụ 18h–20h)?",
    "time_to": "Bạn muốn chơi đến mấy giờ?",
}

# --- routing (§ Day 4 route node: booking vs knowledge, CONTEXT-AWARE) ---------------

# Stages that mean "a booking is in progress" — a follow-up here defaults to the booking
# branch so a loop-back edit ("đổi qua 19h") is never misrouted to knowledge.
ACTIVE_STAGES = {"gather", "search", "propose", "await_confirm", "held"}

# Static-knowledge topics ONLY (policy / facilities / payment method / promotions).
# NOTE: price & availability are LIVE data (tool-query) — never routed to the corpus (§6.1).
_KNOWLEDGE_KW = (
    "huy",  # hủy
    "hoan",  # hoàn / hoàn tiền
    "chinh sach",  # chính sách
    "quy dinh",  # quy định
    "dia chi",  # địa chỉ
    "mo cua",  # mở cửa
    "gio mo",  # giờ mở
    "tien ich",  # tiện ích
    "thanh toan",  # thanh toán
    "chuyen khoan",  # chuyển khoản
    "qr",
    "bien lai",  # biên lai
    "khuyen mai",  # khuyến mãi
    "uu dai",  # ưu đãi
    "gap nhan vien",  # gặp nhân viên
)
# Booking intent signals — presence of any means the message is about a booking, not a policy Q.
# (NOT the bare word "sân" — it appears in policy questions too, e.g. "chính sách hủy sân".)
_BOOKING_KW = ("dat san", "dat cho", "doi qua", "doi gio", "doi san", "doi ngay", "giu cho")


def _norm(text: str) -> str:
    """Lowercase + strip Vietnamese accents (đ→d) for keyword matching."""
    lowered = text.lower().replace("đ", "d")
    decomposed = unicodedata.normalize("NFD", lowered)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn")


def _has_booking_signal(text: str) -> bool:
    normalized = _norm(text)
    if any(kw in normalized for kw in _BOOKING_KW):
        return True
    # vi_parse deterministic signals (time window / budget / sport / relative date)
    fr, to = vi_parse.parse_time_window(text)
    return bool(
        fr or to or vi_parse.parse_budget(text) or vi_parse.parse_sport(text)
        or vi_parse.resolve_relative_date(text, date.today())
    )


def _looks_like_question(normalized: str) -> bool:
    return "?" in normalized or any(
        q in normalized for q in ("khong", "the nao", "bao lau", "bao nhieu", "co hoan", "co duoc")
    )


def classify_route(intent: BookingIntent | None, stage: str | None, text: str) -> str:
    """"knowledge" or "booking" — CONTEXT-AWARE (looks at intent/stage, not just the sentence).

    A message routes to knowledge only if it has a policy/facilities keyword AND no booking
    signal. Mid-booking (an intent exists and stage is active) we're stricter: a knowledge word
    that isn't actually a question stays on the booking branch, so a loop-back edit like
    "đổi qua 19h" (or a passing mention) is never misrouted. Otherwise → booking (perceive
    still slot-fills / asks)."""
    normalized = _norm(text)
    has_kw = any(kw in normalized for kw in _KNOWLEDGE_KW)
    if not has_kw or _has_booking_signal(text):
        return "booking"
    active = intent is not None and (stage in ACTIVE_STAGES)
    if active and not _looks_like_question(normalized):
        return "booking"
    return "knowledge"


def _knowledge_sources(hits: list[KnowledgeHit]) -> str:
    seen: list[str] = []
    for h in hits:
        if h.source not in seen:
            seen.append(h.source)
    return ", ".join(seen)


def compose_knowledge_turn(hits: list[KnowledgeHit], min_score: float | None = None) -> AgentTurn:
    """Grounded answer built ONLY from retrieved chunks + a source citation. Below-threshold /
    empty → do NOT fabricate; offer escalate. Template-based (deterministic, cheap, no LLM
    tokens) so the answer can never drift from the corpus."""
    floor = min_score if min_score is not None else get_settings().rag_min_score
    good = [h for h in hits if h.score >= floor]
    if not good:
        return AgentTurn(
            content=(
                "Mình chưa có thông tin này trong tài liệu của câu lạc bộ. "
                "Bạn muốn gặp nhân viên hỗ trợ để được giải đáp không?"
            ),
            suggested_actions=["Gặp nhân viên"],
        )
    body = "\n\n".join(h.content for h in good[:2])
    citation = _knowledge_sources(good[:2])
    return AgentTurn(
        content=f"Theo tài liệu của câu lạc bộ:\n\n{body}\n\n(Nguồn: {citation})",
        suggested_actions=["Đặt sân", "Gặp nhân viên"],
    )


def _last_user_text(state: AgentState) -> str:
    for msg in reversed(state.get("messages", [])):
        if isinstance(msg, HumanMessage):
            return msg.content if isinstance(msg.content, str) else str(msg.content)
    return ""


def _compute_missing(intent: BookingIntent) -> list[str]:
    return [f for f in MANDATORY if getattr(intent, f) is None]


class AssistantNodes:
    """Holds the injectable collaborators (chat model · preference store · knowledge service)
    so tests can pass fakes and stay entirely offline."""

    def __init__(self, model=None, *, prefs_store=None, knowledge=None):
        self.model = model
        self.prefs_store = prefs_store
        # NOTE: attribute name must NOT collide with the `knowledge` node method below —
        # `nodes.knowledge` must resolve to the bound method for add_node("knowledge", ...).
        self.knowledge_svc = knowledge

    # --- route (entry: booking vs static-knowledge, context-aware) --------------
    async def route(self, state: AgentState) -> dict:
        """No-op node — the classification lives in the conditional edge (route_decision) so it
        can read the freshest state. Kept as an explicit entry node for graph clarity."""
        return {}

    # --- knowledge (RAG answer, grounded + cited, never fabricated) --------------
    async def knowledge(self, state: AgentState) -> dict:
        text = _last_user_text(state)
        hits: list[KnowledgeHit] = []
        if self.knowledge_svc is not None:
            try:
                hits = await self.knowledge_svc.search_knowledge(text)
            except Exception as exc:  # noqa: BLE001 — embed rate-limit/network → degrade, don't crash
                log.error("knowledge.search_failed", exc_type=type(exc).__name__, error=str(exc))
        turn = compose_knowledge_turn(hits)
        return {"turn": turn, "stage": "knowledge", "messages": [AIMessage(content=turn.content)]}

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
            # §11.2 — user text goes in as DATA inside delimiters, not as instructions.
            result = await asyncio.wait_for(
                structured.ainvoke(
                    [
                        SystemMessage(content=prompts.perceive_system(today)),
                        HumanMessage(content=prompts.wrap_user_text(text)),
                    ]
                ),
                timeout=get_settings().llm_timeout_seconds,
            )
            audit.note_tokens(max(1, len(text) // 4))  # structured output loses usage → estimate
            intent = (
                result
                if isinstance(result, BookingIntent)
                else BookingIntent.model_validate(result)
            )
            intent.missing = []
            return intent
        except Exception as exc:  # noqa: BLE001 — LLM parse (incl. timeout) is best-effort;
            # deterministic vi_parse still applies, so the concierge degrades, never crashes.
            # ERROR (not warning): a dead/out-of-quota key silently degrades the assistant —
            # this is the platform's alert channel (alert on ERROR logs). Log exc_type too so
            # an integration bug (KeyError/TypeError) is not misread as "the LLM is down".
            log.error("perceive.llm_parse_failed", exc_type=type(exc).__name__, error=str(exc))
            return BookingIntent()

    # --- memory_load ------------------------------------------------------------
    async def memory_load(self, state: AgentState) -> dict:
        """Fill empty context from the user's own data (§3): the club from the most recent
        booking (else the single club), the default contact name/phone (§11.4 — UserResponse
        has no phone, so the latest booking is the only source), and — when a preference store
        is wired — the L2 learned preferences (usual club/sport/time/budget) that personalize
        the proposal without overriding anything the user stated this turn."""
        intent = state["intent"]
        update: dict = {"intent": intent}
        user_id = state.get("user_id", "")
        need_club = intent.club_id is None
        need_contact = not state.get("default_contact")

        cached: list | None = None

        async def _bookings() -> list:
            nonlocal cached
            if cached is None:
                cached = (await booking_tools.get_user_bookings()).content
            return cached

        if need_club or need_contact:
            try:
                content = await _bookings()
                latest = content[0] if content else None
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

        # L2 personalization (only when a preference store is injected — offline tests skip it)
        if self.prefs_store is not None:
            try:
                snapshot = await self.prefs_store.load(user_id) if user_id else None
                if snapshot is None or snapshot.is_empty():
                    derived = prefs_mod.derive_from_bookings(await _bookings())
                    if user_id and not derived.is_empty():
                        await self.prefs_store.upsert(user_id, derived)
                    snapshot = derived
                self._apply_preferences(intent, snapshot)
                note = prefs_mod.personalization_note(snapshot)
                update["preferences"] = snapshot.model_dump(mode="json")
                if note:
                    update["personalization_note"] = note
            except ToolError as exc:
                log.warning("memory_load.prefs_tool_error", code=exc.code, status=exc.status_code)
            except Exception as exc:  # noqa: BLE001 — personalization is best-effort, never fatal
                log.warning("memory_load.prefs_error", exc_type=type(exc).__name__, error=str(exc))
        return update

    @staticmethod
    def _apply_preferences(intent: BookingIntent, snapshot: prefs_mod.PreferenceSnapshot) -> None:
        """Fill ONLY the gaps the user didn't state — never override an explicit criterion."""
        if intent.club_id is None and snapshot.preferred_club_id:
            intent.club_id = snapshot.preferred_club_id
        if intent.sport is None and snapshot.preferred_sport:
            intent.sport = snapshot.preferred_sport
        if intent.time_from is None and intent.time_to is None:
            window = snapshot.window_times()
            if window:
                intent.time_from, intent.time_to = window
        if intent.budget_max is None and snapshot.budget_max:
            intent.budget_max = snapshot.budget_max
        intent.missing = _compute_missing(intent)

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
            system = prompts.AGENT_SYSTEM
            note = state.get("personalization_note")
            if note:
                system += prompts.personalization_line(note)
            convo = [SystemMessage(content=system), HumanMessage(content=directive)]
            # mixed-intent: the agent can also answer a passing knowledge question in-flow
            tools = READ_TOOLS + ([search_knowledge] if self.knowledge_svc is not None else [])
            llm = self.model.bind_tools(tools)
            settings = get_settings()
            tool_calls_made = 0  # total across iterations — cost/loop cap (§11.3)
            try:
                for _ in range(settings.max_react_steps):
                    ai = await asyncio.wait_for(
                        llm.ainvoke(convo), timeout=settings.llm_timeout_seconds
                    )
                    audit.note_tokens(getattr(ai, "usage_metadata", None))
                    convo.append(ai)
                    tool_calls = getattr(ai, "tool_calls", None) or []
                    if not tool_calls:
                        break
                    stop = False
                    cap = settings.max_tool_calls_per_turn
                    for call in tool_calls:
                        if tool_calls_made >= cap:
                            log.warning("agent.tool_cap_reached", cap=cap)
                            stop = True
                            break
                        tool_calls_made += 1
                        typed, content = await self._run_tool(call)
                        if typed is not None:
                            collected = typed
                        convo.append(ToolMessage(content=content, tool_call_id=call["id"]))
                    if stop:
                        break
            except Exception as exc:  # noqa: BLE001 — LLM planning (incl. timeout) is best-effort;
                # the direct
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
        name = call["name"]
        args = call.get("args", {})
        # search_knowledge dispatches to the INJECTED knowledge service (offline test uses a fake),
        # not the module-level default — so the ranker/tool contract stays testable.
        if name == "search_knowledge" and self.knowledge_svc is not None:
            import json

            try:
                hits = await self.knowledge_svc.search_knowledge(args.get("query", ""))
                payload = [h.model_dump(mode="json") for h in hits]
                return None, json.dumps(payload, ensure_ascii=False)
            except Exception as exc:  # noqa: BLE001 — knowledge is a side-answer; never break booking
                log.warning("agent.knowledge_error", exc_type=type(exc).__name__)
                return None, '{"error": "KNOWLEDGE_UNAVAILABLE"}'
        try:
            return await asyncio.wait_for(
                execute_tool_call(name, args), timeout=get_settings().tool_timeout_seconds
            )
        except ToolError as exc:
            log.warning("agent.tool_error", tool=name, code=exc.code)
            return None, f'{{"error": "{exc.code}"}}'
        except (TimeoutError, asyncio.TimeoutError) as exc:  # noqa: UP041 — tool call hard-capped
            log.warning("agent.tool_timeout", tool=name, exc_type=type(exc).__name__)
            return None, '{"error": "TOOL_TIMEOUT"}'

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
        await self._remember_preferences(state, booking)
        return Command(
            goto=END,
            update={
                "payment": pay.model_dump(mode="json"),
                "turn": turn,
                "stage": "payment",
                "messages": [AIMessage(content=content)],
            },
        )

    async def _remember_preferences(self, state: AgentState, booking: schemas.BookingResponse):
        """Capture the just-booked preferences (§6 L2) — sport comes from the intent (known),
        club/time/budget from the confirmed booking. Best-effort: never block the QR."""
        if self.prefs_store is None:
            return
        user_id = state.get("user_id", "")
        if not user_id:
            return
        intent = state.get("intent")
        sport = intent.sport if intent else None
        try:
            await self.prefs_store.upsert(user_id, prefs_mod.snapshot_from_booking(sport, booking))
        except Exception as exc:  # noqa: BLE001 — learning is best-effort, money path must not fail
            log.warning(
                "payment.remember_prefs_failed", exc_type=type(exc).__name__, error=str(exc)
            )


def route_decision(state: AgentState) -> str:
    """Conditional edge after the entry `route` node: static-knowledge question vs booking."""
    return classify_route(state.get("intent"), state.get("stage"), _last_user_text(state))


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
