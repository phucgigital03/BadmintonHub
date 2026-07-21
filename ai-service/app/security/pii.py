"""PII masking (§11.6) — the agent handles phone numbers + booking intent.

Two sinks matter: structured logs/traces and the `agent_run_log` audit rows. Both must never
store a raw phone. `mask_phone` collapses VN phone-like digit runs to their last 2 digits;
`scrub` walks a dict/list masking phone-bearing keys and phone-like string values; `redact_pii`
is a structlog processor that runs on every log event.
"""

from __future__ import annotations

import re
from typing import Any

# VN phone: optional +84 or leading 0, then 8–10 more digits (10–11 total), not glued to more
# digits (so 6-digit prices / UUID hex fragments are left alone).
_PHONE_RE = re.compile(r"(?<![\d+])(?:\+?84|0)\d{8,10}(?!\d)")

# Log/audit keys whose *value* is always PII → fully redacted regardless of content.
_PII_KEYS = {"phone", "customer_phone", "customerphone", "customername", "customer_name"}


def mask_phone(text: str) -> str:
    """Replace every VN phone-like run in *text* with `****` + its last 2 digits."""

    def _sub(m: re.Match[str]) -> str:
        digits = re.sub(r"\D", "", m.group(0))
        return "*" * (len(digits) - 2) + digits[-2:] if len(digits) >= 4 else m.group(0)

    return _PHONE_RE.sub(_sub, text)


def scrub(obj: Any) -> Any:
    """Deep-copy *obj* with phone-bearing keys redacted and phone-like strings masked.

    Used before an intent/proposal/tool-call blob is written to `agent_run_log` so the DB never
    holds a raw phone (name is redacted too — same PII class)."""
    if isinstance(obj, dict):
        out: dict = {}
        for k, v in obj.items():
            if isinstance(k, str) and k.lower() in _PII_KEYS:
                out[k] = "***" if v is not None else None
            else:
                out[k] = scrub(v)
        return out
    if isinstance(obj, list):
        return [scrub(v) for v in obj]
    if isinstance(obj, str):
        return mask_phone(obj)
    return obj


def redact_pii(logger: Any, method_name: str, event_dict: dict) -> dict:
    """structlog processor: redact known PII keys, mask phone-like text in every string value
    (incl. the event message + `error=str(exc)` strings that could carry user text)."""
    for k, v in list(event_dict.items()):
        if isinstance(k, str) and k.lower() in _PII_KEYS:
            event_dict[k] = "***"
        elif isinstance(v, str):
            event_dict[k] = mask_phone(v)
    return event_dict
