"""HMAC JWT verification — re-validates the forwarded Bearer token (defense in depth).

Matches the platform contract (common-security JwtUtil): HMAC over the raw UTF-8 bytes of
JWT_SECRET, claims `sub` (userId), `roles` (List[str]), `email_verified` (bool), `jti`.
The Java side signs with jjwt's `signWith(key)`, which picks the HMAC strength from the
key length (a ≥64-byte JWT_SECRET yields HS512 tokens) — so accept the whole HS family,
same key.
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
            algorithms=["HS256", "HS384", "HS512"],
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
