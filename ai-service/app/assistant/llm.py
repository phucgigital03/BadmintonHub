"""Provider-agnostic chat model factory (§8.1).

Default: Gemini 3.6 Flash, temperature=0 (stable, reproducible reasoning/tool-planning).
Pinned to one generation on purpose — reproducibility matters for a money-facing concierge,
and the Day-7 red-team eval must run against a FIXED model (a moving `-latest` alias could
shift under us between eval and production). When Google deprecates it (as it did the spec's
original gemini-2.5-flash for new keys), bump this one constant and re-run the eval.
Provider is switchable via `LLM_PROVIDER` in .env. The model is *injectable* everywhere so
tests pass a fake model and never touch the network.
"""

from __future__ import annotations

from typing import Any

from app.config import get_settings

GEMINI_MODEL = "gemini-3.6-flash"


def model_label(settings=None) -> str:
    """The active model id — snapshotted to agent_run_log so a run is reproducible."""
    s = settings or get_settings()
    provider = s.llm_provider.lower()
    if provider == "gemini":
        return GEMINI_MODEL
    if provider == "openai":
        return "gpt-4o-mini"
    if provider == "ollama":
        return s.ollama_model
    return provider


def get_chat_model() -> Any:
    """Build the configured chat model. Imported lazily so tests that inject a fake model
    (and CI without an API key) never import the provider SDK."""
    settings = get_settings()
    provider = settings.llm_provider.lower()

    if provider == "gemini":
        from langchain_google_genai import ChatGoogleGenerativeAI

        return ChatGoogleGenerativeAI(
            model=GEMINI_MODEL,
            temperature=0,
            google_api_key=settings.gemini_api_key,
            # 🔴 MUST stay explicit. Gemini 3+ defaults thinking_level to "high" when unset
            # (langchain_google_genai/chat_models.py — "For Gemini 3+, this defaults to 'high'"),
            # i.e. maximum reasoning depth on EVERY call. Our two call sites don't need it:
            # `perceive` extracts fields from one sentence and `agent` plans READ tool calls.
            # Left at the default it burns thinking tokens against the free-tier quota and adds
            # latency under the hard 25s asyncio.wait_for cap — which is exactly how a turn dies
            # as a bare ReadTimeout with an empty message. Tune via GEMINI_THINKING_LEVEL.
            thinking_level=settings.gemini_thinking_level,
            # Surface the REAL failure instead of a bare, message-less TimeoutError: give the
            # client its own request timeout and cap silent retries so a 429/network/key error
            # propagates fast (and gets logged by perceive.llm_parse_failed) rather than being
            # retried until the outer asyncio.wait_for cap fires.
            timeout=settings.llm_timeout_seconds,
            max_retries=2,
        )
    if provider == "openai":
        from langchain_openai import ChatOpenAI  # optional, only if installed

        return ChatOpenAI(model="gpt-4o-mini", temperature=0, api_key=settings.openai_api_key)
    if provider == "ollama":
        # Local self-host (Ollama). qwen2.5:3b has native tool-calling for the ReAct agent
        # node, and Ollama's structured-output support backs with_structured_output in perceive.
        # Both node call-sites already fall back deterministically, so a small local model
        # degrades slot-filling quality gracefully — it never breaks the booking flow.
        from langchain_ollama import ChatOllama

        return ChatOllama(
            model=settings.ollama_model,
            base_url=settings.ollama_base_url,
            temperature=0,
            num_ctx=settings.ollama_num_ctx,
            keep_alive="30m",  # keep the model resident so turns after the first stay fast
        )

    raise ValueError(f"Unsupported LLM_PROVIDER: {settings.llm_provider}")
