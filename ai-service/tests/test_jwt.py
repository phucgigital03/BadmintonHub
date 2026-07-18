import time

import jwt
import pytest

from app.config import get_settings
from app.security.jwt import JwtError, is_email_verified, roles_of, user_id_of, verify


def _mint(claims: dict, secret: str | None = None) -> str:
    secret = secret or get_settings().jwt_secret
    return jwt.encode(claims, secret, algorithm="HS256")


def test_verify_valid_token_returns_claims():
    token = _mint(
        {
            "sub": "u-1",
            "roles": ["ROLE_USER"],
            "email_verified": True,
            "jti": "j-1",
            "exp": int(time.time()) + 900,
        }
    )
    claims = verify(token)
    assert user_id_of(claims) == "u-1"
    assert roles_of(claims) == ["ROLE_USER"]
    assert is_email_verified(claims) is True


def test_verify_expired_token_raises():
    token = _mint({"sub": "u-1", "exp": int(time.time()) - 10})
    with pytest.raises(JwtError):
        verify(token)


def test_verify_bad_signature_raises():
    token = _mint({"sub": "u-1", "exp": int(time.time()) + 900}, secret="a-different-wrong-secret-value-here")
    with pytest.raises(JwtError):
        verify(token)


def test_verify_missing_sub_raises():
    token = _mint({"roles": [], "exp": int(time.time()) + 900})
    with pytest.raises(JwtError):
        verify(token)
