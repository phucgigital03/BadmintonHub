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
from dataclasses import dataclass, field

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


def clear() -> None:
    """Test helper — forget everything (simulates a restart)."""
    _sessions.clear()
