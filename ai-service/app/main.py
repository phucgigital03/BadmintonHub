"""FastAPI application entrypoint.

/health + the assistant endpoints (/api/ai/assistant/** — §9), structured logging,
OTel→Zipkin, Eureka registration in the lifespan.
"""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from datetime import UTC, datetime

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from app import eureka
from app.api.assistant import router as assistant_router
from app.assistant import audit, sessions
from app.assistant.rate_limit import RedisRateLimiter, build_redis_client
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
    _setup_hardening(app, settings)
    app.state.sweeper = asyncio.create_task(_session_sweeper(settings))
    yield
    await _teardown(app)
    await _teardown_graph(app)
    await eureka.deregister()
    log.info("ai-service.stopped")


def _setup_hardening(app: FastAPI, settings) -> None:
    """Install the Day-6 collaborators the endpoints read from app.state (§11)."""
    app.state.audit_sink = audit.SqlAuditSink()
    try:
        app.state.redis = build_redis_client(settings.redis_url)
        app.state.rate_limiter = RedisRateLimiter(
            app.state.redis, limit=settings.ai_rate_limit_per_minute
        )
    except Exception as exc:  # noqa: BLE001 — degrade: no Redis → AllowAll (fail-open), stay up
        log.error("rate_limit.redis_init_failed", exc_type=type(exc).__name__, error=str(exc))
        app.state.redis = None
        app.state.rate_limiter = None


async def _session_sweeper(settings) -> None:
    """Periodically evict expired sessions (retention/PII §11.6) and purge their checkpointer
    thread state. Bounds in-memory growth; an expired session already 404/410s on access."""
    while True:
        try:
            await asyncio.sleep(settings.session_sweep_interval_seconds)
            expired = sessions.sweep()
            if not expired:
                continue
            from app.assistant.graph import get_default_graph

            checkpointer = getattr(get_default_graph(), "checkpointer", None)
            for sid in expired:
                if checkpointer is not None and hasattr(checkpointer, "adelete_thread"):
                    try:
                        await checkpointer.adelete_thread(sid)
                    except Exception as exc:  # noqa: BLE001 — thread purge is best-effort
                        log.warning("session_sweeper.thread_purge_failed", error=str(exc))
            log.info("session_sweeper.evicted", count=len(expired))
        except asyncio.CancelledError:
            raise
        except Exception as exc:  # noqa: BLE001 — the sweeper must never die on a transient error
            log.error("session_sweeper.error", exc_type=type(exc).__name__, error=str(exc))


async def _teardown(app: FastAPI) -> None:
    task = getattr(app.state, "sweeper", None)
    if task is not None:
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass
    redis = getattr(app.state, "redis", None)
    if redis is not None:
        await redis.aclose()


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
