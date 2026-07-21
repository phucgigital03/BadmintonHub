"""Application settings — loaded from the repo-root .env via pydantic-settings.

The root .env is shared with the Java services (JWT_SECRET, EUREKA_URL, ...). Secrets
without a default (jwt_secret) fail fast at startup if missing.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        # ai-service runs from its own dir; the shared secrets live in the repo-root .env.
        # A local ai-service/.env (if present) overrides the root one.
        env_file=("../.env", ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # --- LLM (provider-agnostic; default Gemini 2.5 Flash) ---
    llm_provider: str = "gemini"
    gemini_api_key: str = "FILL_IN"
    openai_api_key: str = "FILL_IN"

    # --- RAG (Day 4): Gemini embeddings over pgvector on ai_db ---
    # Pinned to gemini-embedding-001 (stable GA) at 768 dims — text-embedding-004 was removed
    # for new keys (same story as gemini-3.5-flash). Pin one GA model (no -preview) so the
    # Day-7 eval runs on a fixed embedder. Changing it means re-seeding + a matching Vector dim.
    embedding_model: str = "models/gemini-embedding-001"
    embedding_dim: int = 768
    rag_top_k: int = 4
    # cosine similarity floor — below → don't answer, escalate. Calibrated live on
    # gemini-embedding-001@768: in-corpus hits score 0.73–0.83, off-topic 0.56–0.59, so 0.68
    # sits cleanly in the gap (raise if a new corpus narrows it).
    rag_min_score: float = 0.68

    # --- Database (ai_db · postgres-ai on host port 5440) ---
    ai_db_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5440/ai_db"

    # --- Downstream services are reached through the gateway (never a hardcoded host) ---
    gateway_url: str = "http://localhost:3000"
    http_timeout_seconds: float = 10.0

    # --- Redis (Day 6 · rate-limit rate_limit:ai:{userId}; fail-open like BookingRateLimiter) ---
    redis_url: str = "redis://localhost:6379/0"
    ai_rate_limit_per_minute: int = 20

    # --- Cost / loop caps (Day 6 · §11.3) — every LLM/tool call costs money, so cap everything.
    #     Tests set these tiny to force a stop; over-limit → graceful turn / escalate. ---
    graph_recursion_limit: int = 15  # passed to graph.ainvoke/astream config
    max_react_steps: int = 3  # ReAct iterations per turn (was the MAX_REACT_STEPS constant)
    max_tool_calls_per_turn: int = 6  # total tool executions across all ReAct iterations
    max_turns_per_session: int = 40
    token_budget_per_session: int = 200_000
    llm_timeout_seconds: float = 25.0  # per LLM call (asyncio.wait_for)
    tool_timeout_seconds: float = 12.0  # per tool call — hard ceiling over httpx's own 10s

    # --- Auth: shared HS256 secret with the rest of the platform (no default → fail fast) ---
    jwt_secret: str

    # --- Assistant sessions + PII retention (Day 6 · §11.6) ---
    #  session TTL = the live checkpointer thread lifetime = the transcript retention window;
    #  an expired session → GET /{id} 404/410 (the FE A→B fallback of Day 5). The periodic
    #  sweeper evicts expired sessions AND purges their checkpointer thread state.
    session_ttl_minutes: int = 1440
    transcript_retention_minutes: int = 1440  # kept == session_ttl_minutes (documented, PII policy)
    session_sweep_interval_seconds: int = 300

    # --- Service discovery ---
    eureka_url: str = "http://localhost:8761/eureka/"
    service_name: str = "ai-service"
    service_port: int = 3010

    # --- Observability ---
    otel_enabled: bool = True
    zipkin_url: str = "http://localhost:9411/api/v2/spans"


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
