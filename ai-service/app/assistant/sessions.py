"""In-memory session registry (Day 3) — who owns which assistant session, with a TTL.

Matches the MemorySaver checkpointer: both live in-process, so a restart forgets sessions
→ GET /{sessionId} returns 404 and the FE falls back to creating a new session (the A→B
fallback of Day 5). Day 4 moves both to Postgres; Day 6 syncs the TTL with PII retention.

Ownership check returns NOT-FOUND (never 403) for someone else's session — don't leak
that the id exists.
"""

from __future__ import annotations

import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass, field

from app.assistant.limits import Caps, LimitExceeded
from app.config import get_settings


class SessionNotFound(Exception):
    """Unknown session id, or a session owned by another user (404 — don't leak)."""


class SessionExpired(Exception):
    """The session existed but is past its TTL (410 — the FE should start a new one)."""


@dataclass
class _Session:
    user_id: str
    created_at: float = field(default_factory=time.monotonic)
    last_seen: float = field(default_factory=time.monotonic)
    turns: int = 0  # message turns spent this session (max_turns cap)
    tokens_used: int = 0  # accumulated LLM tokens this session (token_budget cap)


_sessions: dict[str, _Session] = {}


def create(user_id: str) -> str:
    session_id = str(uuid.uuid4())
    _sessions[session_id] = _Session(user_id=user_id)
    return session_id


def resolve(session_id: str, user_id: str) -> str:
    """Validate existence + ownership + TTL; refresh last_seen. Returns the session id."""
    session = _sessions.get(session_id)
    if session is None or session.user_id != user_id:
        raise SessionNotFound(session_id)
    ttl_seconds = get_settings().session_ttl_minutes * 60
    if time.monotonic() - session.last_seen > ttl_seconds:
        del _sessions[session_id]
        raise SessionExpired(session_id)
    session.last_seen = time.monotonic()
    return session_id


def begin_turn(session_id: str, user_id: str, caps: Caps) -> _Session:
    """Resolve the session then enforce the per-session cost caps (§11.3) before a turn runs.

    Raises SessionNotFound/SessionExpired (→ 404/410) or LimitExceeded (→ graceful stop)."""
    resolve(session_id, user_id)
    session = _sessions[session_id]
    if session.turns >= caps.max_turns_per_session:
        raise LimitExceeded(
            "max_turns", f"Session reached the {caps.max_turns_per_session}-turn limit."
        )
    if session.tokens_used >= caps.token_budget_per_session:
        raise LimitExceeded(
            "token_budget", f"Session reached the {caps.token_budget_per_session}-token budget."
        )
    session.turns += 1
    return session


def record_tokens(session_id: str, n: int) -> None:
    """Add this turn's token usage to the session (best-effort — no session → no-op)."""
    session = _sessions.get(session_id)
    if session is not None and n:
        session.tokens_used += max(0, int(n))


def sweep(on_evict: Callable[[str], None] | None = None) -> list[str]:
    """Evict every session past its TTL (retention/PII §11.6) and return the evicted ids.

    Bounds in-memory growth AND lets the caller purge each expired checkpointer thread. `on_evict`
    is a sync callback; the app's sweeper additionally deletes thread state (async, best-effort)."""
    ttl_seconds = get_settings().session_ttl_minutes * 60
    now = time.monotonic()
    expired = [sid for sid, s in _sessions.items() if now - s.last_seen > ttl_seconds]
    for sid in expired:
        del _sessions[sid]
        if on_evict is not None:
            on_evict(sid)
    return expired


def clear() -> None:
    """Test helper — forget everything (simulates a restart)."""
    _sessions.clear()
