"""Semantic validation of tool inputs BEFORE they hit the real gateway (§11.2).

The agent turns free text into tool arguments, so those arguments are hostile input: a
prompt-injection ("đặt 20 sân", "ngân sách vô hạn") or a hallucinated value must be rejected at
the tool boundary, not sent to booking/payment. Pydantic already coerces types (UUID/date); this
adds the range/enum/shape checks. On violation → `ToolError("INVALID_TOOL_INPUT")` so the agent
degrades gracefully instead of the platform 4xx-ing on garbage.

Money-safety note: this is defense-in-depth. The real gate against escalation stays the CODE
`guardrail` + the `human_review` interrupt — a bad value that slips through here still cannot
move money without a human confirm.
"""

from __future__ import annotations

import re
from typing import Any

from app.tools.http_client import ToolError

SPORTS = {"PICKLEBALL", "BADMINTON"}
MAX_ITEMS = 20  # booking-service cap parity
MAX_NAME = 100
MAX_NOTE = 500
_PHONE_RE = re.compile(r"^(?:\+?84|0)\d{8,10}$")


def _bad(message: str) -> ToolError:
    return ToolError(400, "INVALID_TOOL_INPUT", message)


def sport(value: str | None, *, required: bool) -> str | None:
    """Normalize + enum-check a sport. None allowed only when not required."""
    if value is None or value == "":
        if required:
            raise _bad("Thiếu môn thể thao (PICKLEBALL hoặc BADMINTON).")
        return None
    normalized = value.strip().upper()
    if normalized not in SPORTS:
        raise _bad(f"Môn không hợp lệ: {value!r} (chỉ PICKLEBALL hoặc BADMINTON).")
    return normalized


def name(value: str) -> str:
    v = (value or "").strip()
    if not v:
        raise _bad("Thiếu tên liên hệ.")
    if len(v) > MAX_NAME:
        raise _bad("Tên liên hệ quá dài.")
    return v


def phone(value: str) -> str:
    v = re.sub(r"[\s.\-()]", "", value or "")
    if not _PHONE_RE.match(v):
        raise _bad("Số điện thoại không hợp lệ.")
    return v


def note(value: str | None) -> str | None:
    if value is None:
        return None
    if len(value) > MAX_NOTE:
        raise _bad("Ghi chú quá dài.")
    return value


def items(value: list[Any]) -> list[Any]:
    if not value:
        raise _bad("Phải chọn ít nhất 1 ô để giữ chỗ.")
    if len(value) > MAX_ITEMS:
        raise _bad(f"Không thể giữ quá {MAX_ITEMS} ô trong một đơn.")
    return value


def coordinate(lat: float | None, lng: float | None, radius: float | None) -> None:
    if lat is not None and not (-90.0 <= lat <= 90.0):
        raise _bad("Vĩ độ ngoài phạm vi.")
    if lng is not None and not (-180.0 <= lng <= 180.0):
        raise _bad("Kinh độ ngoài phạm vi.")
    if radius is not None and not (0 < radius <= 100_000):
        raise _bad("Bán kính tìm kiếm ngoài phạm vi.")
