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
    await _setup_graph(app, settings)
    yield
    await _teardown_graph(app)
    await eureka.deregister()
    log.info("ai-service.stopped")


async def _setup_graph(app: FastAPI, settings) -> None:
    """Install the default graph (Postgres-checkpointed for warm-start across restart) + the
    shared knowledge service. Falls back to an in-memory checkpointer if Postgres is unavailable
    so the service still runs (degrade, never crash)."""
    from app.assistant.graph import build_default_graph, set_default_graph
    from app.assistant.knowledge import get_default_knowledge_service

    app.state.knowledge = get_default_knowledge_service()
    app.state.checkpointer_pool = None
    try:
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        from psycopg.rows import dict_row
        from psycopg_pool import AsyncConnectionPool

        conninfo = settings.ai_db_url.replace("+asyncpg", "")
        pool = AsyncConnectionPool(
            conninfo=conninfo,
            max_size=10,
            open=False,
            kwargs={"autocommit": True, "prepare_threshold": 0, "row_factory": dict_row},
        )
        await pool.open()
        saver = AsyncPostgresSaver(pool)
        await saver.setup()
        set_default_graph(build_default_graph(checkpointer=saver))
        app.state.checkpointer_pool = pool
        log.info("checkpointer.postgres_ready")
    except Exception as exc:  # noqa: BLE001 — degrade to MemorySaver, keep the service up
        log.error(
            "checkpointer.postgres_failed_fallback_memory",
            exc_type=type(exc).__name__,
            error=str(exc),
        )
        set_default_graph(build_default_graph())


async def _teardown_graph(app: FastAPI) -> None:
    pool = getattr(app.state, "checkpointer_pool", None)
    if pool is not None:
        await pool.close()


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
