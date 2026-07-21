"""httpx wrapper that reaches services through the gateway and forwards the user's JWT.

Non-2xx responses are parsed from the platform error shape `{code, message, timestamp}`
into a typed `ToolError` so the agent can react (e.g. 403 EMAIL_VERIFIED, 409 slot taken)
instead of silently swallowing the failure.
"""

from __future__ import annotations

import time
from typing import Any

import httpx

from app.config import get_settings
from app.security.context import get_bearer


class ToolError(Exception):
    def __init__(self, status_code: int, code: str | None, message: str) -> None:
        self.status_code = status_code
        self.code = code
        self.message = message
        super().__init__(f"[{status_code}] {code}: {message}")


def _audit_call(
    method: str, path: str, args: Any, status: int, code: str | None, latency_ms: int
) -> None:
    """Record every downstream call to the active run recorder (§11.1). Lazy import keeps the
    HTTP layer decoupled; a no-op when no run is being recorded (e.g. standalone tool tests)."""
    try:
        from app.assistant.audit import note_tool_call

        note_tool_call(
            f"{method} {path}",
            args,
            ok=200 <= status < 400,
            code=code,
            latency_ms=latency_ms,
        )
    except Exception:  # noqa: BLE001 — auditing must never affect the call itself
        pass


async def request(
    method: str,
    path: str,
    *,
    params: dict[str, Any] | None = None,
    json: Any | None = None,
) -> httpx.Response:
    settings = get_settings()
    headers = {"Authorization": f"Bearer {get_bearer()}"}
    t0 = time.monotonic()
    status = 0
    code: str | None = None
    try:
        async with httpx.AsyncClient(
            base_url=settings.gateway_url, timeout=settings.http_timeout_seconds
        ) as client:
            resp = await client.request(method, path, params=params, json=json, headers=headers)
        status = resp.status_code
        if resp.status_code >= 400:
            message = resp.text
            try:
                body = resp.json()
                code = body.get("code")
                message = body.get("message", message)
            except Exception:  # noqa: BLE001 — non-JSON error body; keep raw text
                pass
            raise ToolError(resp.status_code, code, message)
        return resp
    finally:
        _audit_call(
            method, path, {"params": params, "json": json}, status, code,
            int((time.monotonic() - t0) * 1000),
        )
