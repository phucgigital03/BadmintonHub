"""Provider-agnostic chat model factory (§8.1).

Default: Gemini 2.5 Flash, temperature=0 (stable, reproducible reasoning/tool-planning).
Provider is switchable via `LLM_PROVIDER` in .env. The model is *injectable* everywhere so
tests pass a fake model and never touch the network.
"""

from __future__ import annotations

from typing import Any

from app.config import get_settings

GEMINI_MODEL = "gemini-2.5-flash"


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
