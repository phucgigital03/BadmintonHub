"""FastAPI auth dependency — re-validates the forwarded Bearer token (defense in depth).

The gateway already verified the JWT, but every service re-validates (platform rule).
On success the token + claims go into the request-scoped contextvars so the booking tools
forward the user's own JWT (act-as-user, §0.3 rule 1). Error bodies use the platform shape
{code, message, timestamp}.
"""

from __future__ import annotations

from datetime import UTC, datetime

from fastapi import HTTPException, Request

from app.security import jwt
from app.security.context import set_auth


def error_body(code: str, message: str) -> dict:
    return {"code": code, "message": message, "timestamp": datetime.now(UTC).isoformat()}


async def require_user(request: Request) -> dict:
    """Extract + verify the Bearer token; set the auth context; return the claims."""
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        raise HTTPException(401, detail=error_body("TOKEN_MISSING", "Missing Bearer token"))
    token = header.removeprefix("Bearer ").strip()
    try:
        claims = jwt.verify(token)
    except jwt.JwtError as exc:
        raise HTTPException(401, detail=error_body("TOKEN_INVALID", str(exc))) from exc
    set_auth(token, claims)
    return claims
