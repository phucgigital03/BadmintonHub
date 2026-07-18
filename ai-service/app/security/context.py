"""Request-scoped auth context.

The user's Bearer token is stashed in a ContextVar so tools can forward it (act-as-user)
without threading a token argument through every call — keeping tool signatures identical
to the MCP contract (§5). The FastAPI request dependency that sets this arrives with the
assistant endpoints (Day 3); Day 1 sets it directly in tests.
"""

from __future__ import annotations

from contextvars import ContextVar

current_bearer: ContextVar[str | None] = ContextVar("current_bearer", default=None)
current_claims: ContextVar[dict | None] = ContextVar("current_claims", default=None)


def set_auth(bearer: str, claims: dict) -> None:
    current_bearer.set(bearer)
    current_claims.set(claims)


def get_bearer() -> str:
    token = current_bearer.get()
    if not token:
        raise RuntimeError(
            "No bearer token in context — a tool was called outside an authenticated request"
        )
    return token


def get_claims() -> dict:
    return current_claims.get() or {}
