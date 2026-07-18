"""OpenTelemetry tracing → Zipkin (best-effort).

Tracing must never block boot: any import/exporter problem is logged and swallowed so
GET /health stays up even when Zipkin is unreachable.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from app.config import get_settings
from app.logging import get_logger

if TYPE_CHECKING:
    from fastapi import FastAPI

log = get_logger(__name__)


def configure_observability(app: FastAPI) -> None:
    settings = get_settings()
    if not settings.otel_enabled:
        log.info("observability.disabled", reason="otel_enabled=false")
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
