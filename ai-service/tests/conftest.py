"""Test config: deterministic env + a per-test auth context.

Env is set BEFORE app modules read settings; GATEWAY_URL is pinned to a stable host so
respx routes match. jwt_secret has no default in Settings, so it must be present here.
"""

from __future__ import annotations

import os

os.environ.setdefault("JWT_SECRET", "test-secret-0123456789-0123456789-0123456789-xyz")
os.environ.setdefault("GATEWAY_URL", "http://gateway.test")
os.environ.setdefault("OTEL_ENABLED", "false")  # no Zipkin in unit tests

import pytest  # noqa: E402

from app.config import get_settings  # noqa: E402
from app.security.context import current_bearer, current_claims  # noqa: E402

get_settings.cache_clear()

TEST_BEARER = "test.jwt.token"


@pytest.fixture(autouse=True)
def auth_context():
    """Populate the request-scoped auth context so tools can forward a Bearer token."""
    tok = current_bearer.set(TEST_BEARER)
    cl = current_claims.set({"sub": "11111111-1111-1111-1111-111111111111", "roles": ["ROLE_USER"], "email_verified": True})
    yield
    current_bearer.reset(tok)
    current_claims.reset(cl)
