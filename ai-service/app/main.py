"""FastAPI application entrypoint.

Day 1: foundation only — /health, structured logging, OTel→Zipkin, Eureka registration in
the lifespan. Conversational endpoints (/api/ai/assistant/**) arrive Day 3.
"""

from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI

from app import eureka
from app.config import get_settings
from app.logging import configure_logging, get_logger
from app.observability import configure_observability

configure_logging()
log = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    log.info(
        "ai-service.starting",
        port=settings.service_port,
        provider=settings.llm_provider,
    )
    await eureka.register()
    yield
    await eureka.deregister()
    log.info("ai-service.stopped")


def create_app() -> FastAPI:
    app = FastAPI(title="BadmintonHub ai-service", version="0.1.0", lifespan=lifespan)
    configure_observability(app)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    return app


app = create_app()
