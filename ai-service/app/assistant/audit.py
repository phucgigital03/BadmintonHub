"""Per-run audit snapshot → `agent_run_log` (§11.1) + per-session token accounting.

Every turn runs inside `record_run(...)`, which installs a `RunRecord` on a contextvar. Nodes
append their tool calls + token usage to it (like the auth contextvar pattern); on exit the
recorder reads the final graph state, masks PII, and writes one row: intent + tool calls&results
+ proposal + decision + model + prompt version + latency. The write is fail-safe — a broken
audit sink logs and is swallowed, never breaking a turn. The audit sink is injectable so tests
assert the row offline.
"""

from __future__ import annotations

import time
import uuid
from contextlib import asynccontextmanager
from contextvars import ContextVar
from dataclasses import dataclass, field
from typing import Any, Protocol

import structlog

from app.assistant import llm, sessions
from app.assistant.prompts import PROMPT_VERSION
from app.security.pii import mask_phone, scrub

log = structlog.get_logger(__name__)


@dataclass
class RunRecord:
    session_id: str
    user_id: str
    model: str
    prompt_version: str
    input_text: str = ""
    tool_calls: list[dict] = field(default_factory=list)
    tokens: int = 0
    latency_ms: int = 0

    def add_tool_call(
        self,
        name: str,
        args: Any,
        *,
        ok: bool,
        code: str | None = None,
        latency_ms: int | None = None,
    ) -> None:
        self.tool_calls.append(
            {"name": name, "args": scrub(args), "ok": ok, "code": code, "latencyMs": latency_ms}
        )

    def add_tokens(self, n: int | None) -> None:
        try:
            self.tokens += max(0, int(n or 0))
        except (TypeError, ValueError):
            pass


# The run being recorded for the current async task (None outside a record_run block).
current_run: ContextVar[RunRecord | None] = ContextVar("current_run", default=None)


class AuditSink(Protocol):
    async def write(self, row: dict) -> None: ...


class NullAuditSink:
    """No-op sink (CLI / tests that don't assert the audit row)."""

    async def write(self, row: dict) -> None:  # noqa: D102
        return None


class SqlAuditSink:
    """Default sink — one INSERT into agent_run_log on ai_db."""

    async def write(self, row: dict) -> None:
        from app.db import get_sessionmaker
        from app.models.agent_run_log import AgentRunLog

        async with get_sessionmaker()() as session:
            session.add(
                AgentRunLog(
                    session_id=uuid.UUID(row["session_id"]),
                    user_id=uuid.UUID(row["user_id"]),
                    model=row["model"],
                    prompt_version=row["prompt_version"],
                    input_text=row["input_text"],
                    output_summary=row["output_summary"],
                    tool_calls=row["tool_calls"],
                    intent=row["intent"],
                    proposal=row["proposal"],
                    decision=row["decision"],
                    latency_ms=row["latency_ms"],
                )
            )
            await session.commit()


def _dump(obj: Any) -> Any:
    """Model → JSON dict; passthrough dict; else None."""
    if obj is None:
        return None
    if hasattr(obj, "model_dump"):
        return obj.model_dump(mode="json")
    return obj if isinstance(obj, dict) else None


def _turn_content(turn: Any) -> str | None:
    if turn is None:
        return None
    if hasattr(turn, "content"):
        return turn.content
    if isinstance(turn, dict):
        return turn.get("content")
    return None


def _decision(values: dict) -> str:
    """A short outcome label for the run (audit-friendly, queryable)."""
    if values.get("payment"):
        return "booked"
    stage = values.get("stage") or ""
    if stage in {"await_confirm", "held"}:
        return "proposed"
    if stage == "escalated":
        return "escalated"
    return stage or "answered"


def build_row(rec: RunRecord, values: dict) -> dict:
    """Assemble the DB row from the recorder + final state, masking every PII sink."""
    content = _turn_content(values.get("turn"))
    return {
        "session_id": rec.session_id,
        "user_id": rec.user_id,
        "model": rec.model,
        "prompt_version": rec.prompt_version,
        "input_text": mask_phone(rec.input_text or ""),
        "output_summary": mask_phone(content) if content else None,
        "tool_calls": rec.tool_calls,  # args already scrubbed on capture
        "intent": scrub(_dump(values.get("intent"))),
        "proposal": scrub(_dump(values.get("proposal"))),
        "decision": _decision(values),
        "latency_ms": rec.latency_ms,
    }


@asynccontextmanager
async def record_run(
    *, session_id: str, user_id: str, sink: AuditSink, get_final_values, input_text: str = ""
):
    """Record one turn. `get_final_values` is an async callable returning the final AgentState
    dict (read after the graph settles). Token usage collected on the recorder is committed to
    the session so the next begin_turn can enforce the budget."""
    rec = RunRecord(
        session_id=session_id,
        user_id=user_id,
        model=llm.model_label(),
        prompt_version=PROMPT_VERSION,
        input_text=input_text,
    )
    token = current_run.set(rec)
    t0 = time.monotonic()
    try:
        yield rec
    finally:
        rec.latency_ms = int((time.monotonic() - t0) * 1000)
        current_run.reset(token)
        try:
            sessions.record_tokens(session_id, rec.tokens)
        except Exception:  # noqa: BLE001 — token bookkeeping is best-effort
            pass
        try:
            values = await get_final_values()
            await sink.write(build_row(rec, values or {}))
        except Exception as exc:  # noqa: BLE001 — audit must NEVER break a turn (fail-safe)
            log.error("audit.write_failed", exc_type=type(exc).__name__, error=str(exc))


def note_tool_call(
    name: str, args: Any, *, ok: bool, code: str | None = None, latency_ms: int | None = None
) -> None:
    """Node helper — append a tool call to the active run recorder (no-op if none active)."""
    rec = current_run.get()
    if rec is not None:
        rec.add_tool_call(name, args, ok=ok, code=code, latency_ms=latency_ms)


def note_tokens(usage: Any) -> None:
    """Node helper — add LLM token usage to the active run recorder (accepts usage_metadata dict
    or an int; no-op if none active)."""
    rec = current_run.get()
    if rec is None:
        return
    if isinstance(usage, dict):
        rec.add_tokens(usage.get("total_tokens") or usage.get("input_tokens"))
    else:
        rec.add_tokens(usage)
