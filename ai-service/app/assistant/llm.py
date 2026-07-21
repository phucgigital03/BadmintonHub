"""Provider-agnostic chat model factory (§8.1).

Default: Gemini 3.5 Flash, temperature=0 (stable, reproducible reasoning/tool-planning).
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

GEMINI_MODEL = "gemini-3.5-flash"


def model_label(settings=None) -> str:
    """The active model id — snapshotted to agent_run_log so a run is reproducible."""
    s = settings or get_settings()
    provider = s.llm_provider.lower()
    if provider == "gemini":
        return GEMINI_MODEL
    if provider == "openai":
        return "gpt-4o-mini"
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
        )
    if provider == "openai":
        from langchain_openai import ChatOpenAI  # optional, only if installed

        return ChatOpenAI(model="gpt-4o-mini", temperature=0, api_key=settings.openai_api_key)

    raise ValueError(f"Unsupported LLM_PROVIDER: {settings.llm_provider}")
