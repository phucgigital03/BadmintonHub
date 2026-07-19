"""FastAPI application entrypoint.

/health + the assistant endpoints (/api/ai/assistant/** — §9), structured logging,
OTel→Zipkin, Eureka registration in the lifespan.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import UTC, datetime

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from app import eureka
from app.api.assistant import router as assistant_router
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

    @app.exception_handler(HTTPException)
    async def platform_error_shape(request: Request, exc: HTTPException) -> JSONResponse:
        """Errors leave in the platform shape {code, message, timestamp} — not {"detail": …}."""
        detail = exc.detail
        if not (isinstance(detail, dict) and "code" in detail):
            detail = {
                "code": "ERROR",
                "message": str(detail),
                "timestamp": datetime.now(UTC).isoformat(),
            }
        return JSONResponse(status_code=exc.status_code, content=detail)

    @app.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "UP"}

    app.include_router(assistant_router)
    return app


app = create_app()
