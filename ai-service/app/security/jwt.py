"""HS256 JWT verification — re-validates the forwarded Bearer token (defense in depth).

Matches the platform contract (common-security JwtUtil): HS256 over the raw UTF-8 bytes
of JWT_SECRET, claims `sub` (userId), `roles` (List[str]), `email_verified` (bool), `jti`.
"""

from __future__ import annotations

import jwt

from app.config import get_settings


class JwtError(Exception):
    """Raised when a token is missing required claims, expired, or has a bad signature."""


def verify(token: str) -> dict:
    settings = get_settings()
    try:
        return jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=["HS256"],
            options={"require": ["sub", "exp"]},
        )
    except jwt.PyJWTError as exc:
        raise JwtError(str(exc)) from exc


def user_id_of(claims: dict) -> str | None:
    return claims.get("sub")


def roles_of(claims: dict) -> list[str]:
    return list(claims.get("roles") or [])


def is_email_verified(claims: dict) -> bool:
    return bool(claims.get("email_verified", False))
