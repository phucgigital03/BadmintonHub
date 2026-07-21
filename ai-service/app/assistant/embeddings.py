"""Text embeddings for RAG — provider-agnostic + injectable (Day 4, §6.1 knowledge only).

`Embedder` is a Protocol so the real Gemini embedder and an offline `FakeEmbedder` (tests)
are interchangeable — the same discipline as the injectable chat model (llm.py). Only the
curated knowledge corpus is ever embedded; live booking/pricing data is NEVER embedded.
"""

from __future__ import annotations

from typing import Protocol

from app.config import get_settings


class Embedder(Protocol):
    async def embed(self, texts: list[str]) -> list[list[float]]: ...

    async def embed_query(self, text: str) -> list[float]: ...


class GeminiEmbedder:
    """Gemini embeddings (gemini-embedding-001, 768-dim). Lazily builds the client so importing
    this module never needs an API key (tests inject a fake and stay offline)."""

    def __init__(
        self, model: str | None = None, api_key: str | None = None, dim: int | None = None
    ):
        settings = get_settings()
        self._model = model or settings.embedding_model
        self._api_key = api_key or settings.gemini_api_key
        self._dim = dim or settings.embedding_dim
        self._client = None

    def _get_client(self):
        if self._client is None:
            from langchain_google_genai import GoogleGenerativeAIEmbeddings

            self._client = GoogleGenerativeAIEmbeddings(
                model=self._model,
                google_api_key=self._api_key,
                output_dimensionality=self._dim,  # gemini-embedding-001 native dim is 3072
            )
        return self._client

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return await self._get_client().aembed_documents(texts)

    async def embed_query(self, text: str) -> list[float]:
        return await self._get_client().aembed_query(text)


def get_default_embedder() -> Embedder:
    return GeminiEmbedder()
