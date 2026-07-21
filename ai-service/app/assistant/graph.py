"""Assemble the concierge graph + run/resume helpers (used by the API, tests and the CLI).

Day 3 (Phase 4): the propose path now flows into human_review — a LangGraph interrupt that
pauses the thread until the user confirms — then the deterministic guardrail, then the two
WRITE tools (hold → payment). A MemorySaver checkpointer (keyed thread_id=session_id)
persists AgentState across turns AND across the interrupt/resume boundary. The postgres
checkpointer replaces MemorySaver on Day 4.
"""

from __future__ import annotations

import structlog
from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.errors import GraphRecursionError
from langgraph.graph import END, StateGraph
from langgraph.types import Command, StateSnapshot

from app.assistant import audit, limits
from app.assistant.models import AgentState, ConfirmDecision
from app.assistant.nodes import (
    AssistantNodes,
    after_propose,
    classify_route,
    compose_knowledge_turn,
    gate,
    route_decision,
)
from app.config import get_settings
from app.security.context import set_auth

log = structlog.get_logger(__name__)

# How many recent messages the LLM prompts should keep (windowing prep; summarization = Day 4).
RECENT_WINDOW = 12


def build_graph(model=None, *, checkpointer=None, prefs_store=None, knowledge=None):
    """Compile the full Day-4 graph. Every collaborator is injectable (tests pass fakes and
    stay offline); the checkpointer defaults to MemorySaver (the app injects AsyncPostgresSaver
    for warm-start across restart)."""
    nodes = AssistantNodes(model, prefs_store=prefs_store, knowledge=knowledge)
    sg = StateGraph(AgentState)
    # Day 4 — route entry: static-knowledge question vs booking (context-aware).
    sg.add_node("route", nodes.route)
    sg.add_node("knowledge", nodes.knowledge)
    sg.add_node("perceive", nodes.perceive)
    sg.add_node("memory_load", nodes.memory_load)
    sg.add_node("ask_clarify", nodes.ask_clarify)
    sg.add_node("agent", nodes.agent)
    sg.add_node("rank_propose", nodes.rank_propose)
    # Day 3 — human gate + money path. These nodes route themselves via Command(goto=…):
    # human_review → guardrail | perceive | END · guardrail → hold | payment | agent |
    # human_review · hold → payment | agent | human_review · payment → END | human_review.
    sg.add_node("human_review", nodes.human_review)
    sg.add_node("guardrail", nodes.guardrail)
    sg.add_node("hold", nodes.hold)
    sg.add_node("payment", nodes.payment)

    sg.set_entry_point("route")
    sg.add_conditional_edges(
        "route", route_decision, {"knowledge": "knowledge", "booking": "perceive"}
    )
    sg.add_edge("knowledge", END)
    sg.add_edge("perceive", "memory_load")
    sg.add_conditional_edges("memory_load", gate, {"clarify": "ask_clarify", "search": "agent"})
    sg.add_edge("ask_clarify", END)
    sg.add_edge("agent", "rank_propose")
    sg.add_conditional_edges("rank_propose", after_propose, {"review": "human_review", "end": END})

    return sg.compile(checkpointer=checkpointer or MemorySaver())


_default_graph = None


def build_default_graph(*, checkpointer=None):
    """Real graph — configured chat model + Gemini embeddings/pgvector knowledge + SQL prefs."""
    from app.assistant.knowledge import get_default_knowledge_service
    from app.assistant.llm import get_chat_model
    from app.assistant.preferences import get_default_preference_store

    return build_graph(
        get_chat_model(),
        checkpointer=checkpointer,
        prefs_store=get_default_preference_store(),
        knowledge=get_default_knowledge_service(),
    )


def set_default_graph(graph) -> None:
    """The app lifespan installs the Postgres-checkpointed graph here (shared by the API)."""
    global _default_graph
    _default_graph = graph


def get_default_graph():
    """The installed default graph; falls back to a MemorySaver-backed build (CLI / no lifespan)."""
    global _default_graph
    if _default_graph is None:
        _default_graph = build_default_graph()
    return _default_graph


def recent_messages(state: AgentState, n: int = RECENT_WINDOW) -> list:
    """Keep only the last N messages — never feed the whole transcript to the LLM."""
    return state.get("messages", [])[-n:]


def _config(session_id: str) -> dict:
    # recursion_limit caps the self-looping money path (guardrail↔agent↔human_review) so a
    # runaway can't burn tokens forever (§11.3). Over it → GraphRecursionError → graceful stop.
    return {
        "configurable": {"thread_id": session_id},
        "recursion_limit": get_settings().graph_recursion_limit,
    }


async def run_turn(
    graph,
    *,
    session_id: str,
    bearer: str,
    text: str,
    user_id: str = "",
    claims: dict | None = None,
    knowledge=None,
    sink=None,
) -> AgentState:
    """Run one conversational turn. Sets the auth context so tools forward the user JWT.

    If the thread is paused at human_review, free text normally resumes it as a non-confirm
    edit ("đổi qua 19h"). But a MIXED-INTENT knowledge question mid-await ("hủy trước 2 tiếng
    có hoàn?") must be answered WITHOUT resuming — the proposal stays pending and /confirm keeps
    working (§ Day 4). Otherwise it's one single path through the graph. Wrapped in an audit
    recorder (Day 6) — pass a `sink` to persist the run snapshot.
    """
    set_auth(bearer, claims or {})
    audit_sink = sink or audit.NullAuditSink()

    async def _final_values() -> dict:
        return (await get_thread_state(graph, session_id)).values

    async with audit.record_run(
        session_id=session_id,
        user_id=user_id,
        sink=audit_sink,
        get_final_values=_final_values,
        input_text=text,
    ):
        try:
            if await has_pending_interrupt(graph, session_id):
                snapshot = await get_thread_state(graph, session_id)
                stage = snapshot.values.get("stage")
                intent = snapshot.values.get("intent")
                if knowledge is not None and classify_route(intent, stage, text) == "knowledge":
                    return await answer_knowledge_while_paused(snapshot, knowledge, text)
                resume = ConfirmDecision(confirmed=False, edits=text)
                return await graph.ainvoke(
                    Command(resume=resume.model_dump()), _config(session_id)
                )
            inputs: dict = {
                "messages": [HumanMessage(content=text)],
                "session_id": session_id,
                "user_id": user_id,
                "jwt": bearer,
            }
            return await graph.ainvoke(inputs, _config(session_id))
        except GraphRecursionError:
            # cost/loop cap tripped — stop politely, never crash (§11.3)
            log.warning("run_turn.recursion_limit", session_id=session_id)
            return {"turn": limits.graceful_stop_turn("recursion"), "stage": "error"}


async def answer_knowledge_while_paused(
    snapshot: StateSnapshot, knowledge, text: str
) -> AgentState:
    """Answer a side knowledge question without touching the paused thread — the interrupt stays
    pending so the booking flow is never reset. (The side Q&A is intentionally NOT written back
    into the persisted transcript, to keep the pause 100% intact.)"""
    hits: list = []
    try:
        hits = await knowledge.search_knowledge(text)
    except Exception:  # noqa: BLE001 — embed rate-limit/network → degrade to escalate offer
        hits = []
    turn = compose_knowledge_turn(hits)
    values = dict(snapshot.values)
    values["turn"] = turn
    return values  # type: ignore[return-value]


async def resume_confirm(
    graph,
    *,
    session_id: str,
    bearer: str,
    decision: ConfirmDecision,
    claims: dict | None = None,
    user_id: str = "",
    sink=None,
) -> AgentState:
    """Resume a thread paused at human_review with the user's decision (§9 /confirm). Audited."""
    set_auth(bearer, claims or {})
    audit_sink = sink or audit.NullAuditSink()

    async def _final_values() -> dict:
        return (await get_thread_state(graph, session_id)).values

    label = "confirm" if decision.confirmed else (decision.edits or "decline")
    async with audit.record_run(
        session_id=session_id,
        user_id=user_id,
        sink=audit_sink,
        get_final_values=_final_values,
        input_text=label,
    ):
        try:
            return await graph.ainvoke(Command(resume=decision.model_dump()), _config(session_id))
        except GraphRecursionError:
            log.warning("resume_confirm.recursion_limit", session_id=session_id)
            return {"turn": limits.graceful_stop_turn("recursion"), "stage": "error"}


async def get_thread_state(graph, session_id: str) -> StateSnapshot:
    return await graph.aget_state(_config(session_id))


def snapshot_has_interrupt(snapshot: StateSnapshot) -> bool:
    return any(task.interrupts for task in snapshot.tasks)


async def has_pending_interrupt(graph, session_id: str) -> bool:
    """True when the thread is paused at human_review awaiting a ConfirmDecision."""
    return snapshot_has_interrupt(await get_thread_state(graph, session_id))
