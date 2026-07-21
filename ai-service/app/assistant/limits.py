"""Cost / loop caps (§11.3) — every LLM & tool call is real money, so nothing runs unbounded.

`Caps` bundles the configured limits (tests build a tiny `Caps` to force a stop). The caps are
enforced in three places: the graph `recursion_limit` (config), the ReAct tool-call/step cap
(nodes), and the per-session turn + token budget (sessions.begin_turn). Over any of them → a
polite `AgentTurn` inviting escalation — never a crash, never a blind hold.
"""

from __future__ import annotations

from dataclasses import dataclass

from app.assistant.models import AgentTurn
from app.config import get_settings


@dataclass(frozen=True)
class Caps:
    max_turns_per_session: int
    token_budget_per_session: int
    max_tool_calls_per_turn: int
    max_react_steps: int
    recursion_limit: int
    llm_timeout_seconds: float
    tool_timeout_seconds: float


def caps_from_settings(settings=None) -> Caps:
    s = settings or get_settings()
    return Caps(
        max_turns_per_session=s.max_turns_per_session,
        token_budget_per_session=s.token_budget_per_session,
        max_tool_calls_per_turn=s.max_tool_calls_per_turn,
        max_react_steps=s.max_react_steps,
        recursion_limit=s.graph_recursion_limit,
        llm_timeout_seconds=s.llm_timeout_seconds,
        tool_timeout_seconds=s.tool_timeout_seconds,
    )


class LimitExceeded(Exception):
    """A per-session cap was hit (reason ∈ {max_turns, token_budget}) — stop this turn politely."""

    def __init__(self, reason: str, message: str) -> None:
        self.reason = reason
        self.message = message
        super().__init__(f"{reason}: {message}")


def graceful_stop_turn(reason: str) -> AgentTurn:
    """The reply shown when a cost/loop cap (or a recursion runaway) stops a turn."""
    return AgentTurn(
        content=(
            "Xin lỗi, phiên trò chuyện này đã đạt giới hạn xử lý. "
            "Bạn mở phiên mới để thử lại, hoặc bấm 'Gặp nhân viên' để được hỗ trợ trực tiếp nhé."
        ),
        suggested_actions=["Gặp nhân viên"],
    )
