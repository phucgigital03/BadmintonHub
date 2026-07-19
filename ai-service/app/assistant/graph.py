"""Assemble the concierge graph + run/resume helpers (used by the API, tests and the CLI).

Day 3 (Phase 4): the propose path now flows into human_review — a LangGraph interrupt that
pauses the thread until the user confirms — then the deterministic guardrail, then the two
WRITE tools (hold → payment). A MemorySaver checkpointer (keyed thread_id=session_id)
persists AgentState across turns AND across the interrupt/resume boundary. The postgres
checkpointer replaces MemorySaver on Day 4.
"""

from __future__ import annotations

from functools import lru_cache

from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, StateGraph
from langgraph.types import Command, StateSnapshot

from app.assistant.models import AgentState, ConfirmDecision
from app.assistant.nodes import AssistantNodes, after_propose, gate
from app.security.context import set_auth

# How many recent messages the LLM prompts should keep (windowing prep; summarization = Day 4).
RECENT_WINDOW = 12


def build_graph(model=None):
    """Compile the full Day-3 graph. Pass a chat model or None (deterministic-only)."""
    nodes = AssistantNodes(model)
    sg = StateGraph(AgentState)
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

    sg.set_entry_point("perceive")
    sg.add_edge("perceive", "memory_load")
    sg.add_conditional_edges("memory_load", gate, {"clarify": "ask_clarify", "search": "agent"})
    sg.add_edge("ask_clarify", END)
    sg.add_edge("agent", "rank_propose")
    sg.add_conditional_edges("rank_propose", after_propose, {"review": "human_review", "end": END})

    return sg.compile(checkpointer=MemorySaver())


@lru_cache
def get_default_graph():
    """Default graph backed by the real (configured) chat model — shared by API + CLI."""
    from app.assistant.llm import get_chat_model

    return build_graph(get_chat_model())


def recent_messages(state: AgentState, n: int = RECENT_WINDOW) -> list:
    """Keep only the last N messages — never feed the whole transcript to the LLM."""
    return state.get("messages", [])[-n:]


def _config(session_id: str) -> dict:
    return {"configurable": {"thread_id": session_id}}


async def run_turn(
    graph,
    *,
    session_id: str,
    bearer: str,
    text: str,
    user_id: str = "",
    claims: dict | None = None,
) -> AgentState:
    """Run one conversational turn. Sets the auth context so tools forward the user JWT.

    If the thread is paused at human_review, free text resumes it as a non-confirm edit
    ("đổi qua 19h") — one single path through the graph, never a second run over a paused
    thread.
    """
    set_auth(bearer, claims or {})
    if await has_pending_interrupt(graph, session_id):
        resume = ConfirmDecision(confirmed=False, edits=text)
        return await graph.ainvoke(Command(resume=resume.model_dump()), _config(session_id))
    inputs: dict = {
        "messages": [HumanMessage(content=text)],
        "session_id": session_id,
        "user_id": user_id,
        "jwt": bearer,
    }
    return await graph.ainvoke(inputs, _config(session_id))


async def resume_confirm(
    graph,
    *,
    session_id: str,
    bearer: str,
    decision: ConfirmDecision,
    claims: dict | None = None,
) -> AgentState:
    """Resume a thread paused at human_review with the user's decision (§9 /confirm)."""
    set_auth(bearer, claims or {})
    return await graph.ainvoke(Command(resume=decision.model_dump()), _config(session_id))


async def get_thread_state(graph, session_id: str) -> StateSnapshot:
    return await graph.aget_state(_config(session_id))


def snapshot_has_interrupt(snapshot: StateSnapshot) -> bool:
    return any(task.interrupts for task in snapshot.tasks)


async def has_pending_interrupt(graph, session_id: str) -> bool:
    """True when the thread is paused at human_review awaiting a ConfirmDecision."""
    return snapshot_has_interrupt(await get_thread_state(graph, session_id))
