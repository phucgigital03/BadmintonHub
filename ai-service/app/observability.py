"""Tracing — OpenTelemetry→Zipkin (service hops) and LangSmith (LLM prompts/responses).

Two different questions, two different tools:
  • Zipkin answers "which service was slow" — spans over HTTP calls, no prompt content.
  • LangSmith answers "what did the model actually see and say" — one trace per turn, a tree of
    graph nodes, each LLM call carrying its verbatim prompt + raw response + tokens.

Neither may block boot: any import/exporter/config problem is logged and swallowed so
GET /health stays up even when Zipkin or LangSmith is unreachable.
"""

from __future__ import annotations

import os
import socket
from typing import TYPE_CHECKING
from urllib.parse import urlparse

from app.config import get_settings
from app.logging import get_logger

if TYPE_CHECKING:
    from fastapi import FastAPI

log = get_logger(__name__)


def _zipkin_reachable(endpoint: str, timeout: float = 0.5) -> bool:
    """Quick TCP probe so a DOWN Zipkin degrades SILENTLY instead of spamming the OTel
    background export thread with tracebacks (the setup try/except below can't catch those).
    Dev-acceptable one-shot at boot: start Zipkin before ai-service to get traces."""
    parsed = urlparse(endpoint)
    host = parsed.hostname or "localhost"
    port = parsed.port or 9411
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def configure_langsmith() -> None:
    """Export the LangSmith settings into `os.environ` so tracing turns on for every LLM call.

    Why this function has to exist: pydantic-settings parses the root .env into the `Settings`
    OBJECT — it never populates `os.environ`. But `langsmith.utils.get_env_var` reads `os.environ`
    (namespaces LANGSMITH_* then LANGCHAIN_*) and is `@lru_cache`d, so the values must be in the
    process environment BEFORE the first traced run. Hence: called at import time in main.py.

    Nothing else is needed — langchain-core auto-installs the tracer when it sees the flag, so
    every `perceive` / `agent` LLM call and every LangGraph node is captured with no code changes
    at the call sites. Already-exported shell vars win when the setting is off (we don't clear
    them), so `LANGSMITH_TRACING=true uv run ...` keeps working as a one-off.
    """
    settings = get_settings()
    if not settings.langsmith_tracing:
        return
    if not settings.langsmith_api_key or settings.langsmith_api_key == "FILL_IN":
        # Half-enabling is worse than off: langchain would try to ship every run and fail on each
        # one. Surface it on the alert channel (ERROR) and stay off.
        log.error(
            "langsmith.missing_api_key",
            hint="set LANGSMITH_API_KEY in .env (get one at smith.langchain.com)",
        )
        return
    os.environ["LANGSMITH_TRACING"] = "true"
    os.environ["LANGSMITH_API_KEY"] = settings.langsmith_api_key
    os.environ["LANGSMITH_PROJECT"] = settings.langsmith_project
    os.environ["LANGSMITH_ENDPOINT"] = settings.langsmith_endpoint
    # Loud on purpose: tracing ships conversation content (prompts + replies, unmasked) to an
    # external service. That must never be a surprise in a log nobody reads.
    log.warning(
        "langsmith.enabled",
        project=settings.langsmith_project,
        endpoint=settings.langsmith_endpoint,
        note="prompts + model replies are sent to LangSmith — dev only",
    )


def configure_observability(app: FastAPI) -> None:
    settings = get_settings()
    if not settings.otel_enabled:
        log.info("observability.disabled", reason="otel_enabled=false")
        return
    if not _zipkin_reachable(settings.zipkin_url):
        # No exporter → no background span export → no traceback spam. Enable Zipkin
        # (docker compose up -d zipkin) or set OTEL_ENABLED=false to silence intentionally.
        log.warning("observability.zipkin_unreachable", endpoint=settings.zipkin_url)
        return
    try:
        from opentelemetry import trace
        from opentelemetry.exporter.zipkin.json import ZipkinExporter
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
        from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor

        provider = TracerProvider(
            resource=Resource.create({"service.name": settings.service_name})
        )
        provider.add_span_processor(
            BatchSpanProcessor(ZipkinExporter(endpoint=settings.zipkin_url))
        )
        trace.set_tracer_provider(provider)
        FastAPIInstrumentor.instrument_app(app)
        HTTPXClientInstrumentor().instrument()
        log.info("observability.configured", exporter="zipkin", endpoint=settings.zipkin_url)
    except Exception as exc:  # noqa: BLE001 — best-effort, never crash on tracing
        log.warning("observability.disabled", error=str(exc))
