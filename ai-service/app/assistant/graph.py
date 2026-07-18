"""Assemble the read-only propose graph + a run_turn helper (used by tests and the CLI).

The SSE endpoint is Day 3; Day 2 exercises the graph through run_turn(). A MemorySaver
checkpointer (keyed thread_id=session_id) persists AgentState across turns so a follow-up edit
("đổi qua 19h") MERGES into the prior intent instead of re-parsing. The postgres checkpointer
replaces MemorySaver on Day 4.
"""

from __future__ import annotations

from functools import lru_cache

from langchain_core.messages import HumanMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, StateGraph

from app.assistant.models import AgentState
from app.assistant.nodes import AssistantNodes, gate
from app.security.context import set_auth

# How many recent messages the LLM prompts should keep (windowing prep; summarization = Day 4).
RECENT_WINDOW = 12


def build_graph(model=None):
    """Compile the perceive→memory_load→gate→agent→rank_propose graph. Pass a model or None."""
    nodes = AssistantNodes(model)
    sg = StateGraph(AgentState)
    sg.add_node("perceive", nodes.perceive)
    sg.add_node("memory_load", nodes.memory_load)
    sg.add_node("ask_clarify", nodes.ask_clarify)
    sg.add_node("agent", nodes.agent)
    sg.add_node("rank_propose", nodes.rank_propose)

    sg.set_entry_point("perceive")
    sg.add_edge("perceive", "memory_load")
    sg.add_conditional_edges("memory_load", gate, {"clarify": "ask_clarify", "search": "agent"})
    sg.add_edge("ask_clarify", END)
    sg.add_edge("agent", "rank_propose")
    sg.add_edge("rank_propose", END)

    return sg.compile(checkpointer=MemorySaver())


@lru_cache
def get_default_graph():
    """Default graph backed by the real (configured) chat model — for the CLI / Day-3 endpoint."""
    from app.assistant.llm import get_chat_model

    return build_graph(get_chat_model())


def recent_messages(state: AgentState, n: int = RECENT_WINDOW) -> list:
    """Keep only the last N messages — never feed the whole transcript to the LLM."""
    return state.get("messages", [])[-n:]


async def run_turn(
    graph,
    *,
    session_id: str,
    bearer: str,
    text: str,
    user_id: str = "",
    claims: dict | None = None,
) -> AgentState:
    """Run one conversational turn. Sets the auth context so tools forward the user JWT."""
    set_auth(bearer, claims or {})
    config = {"configurable": {"thread_id": session_id}}
    inputs: dict = {
        "messages": [HumanMessage(content=text)],
        "session_id": session_id,
        "user_id": user_id,
        "jwt": bearer,
    }
    return await graph.ainvoke(inputs, config)
