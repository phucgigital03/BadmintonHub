"""RAG knowledge retrieval (§6 L4, UC-CS-08 · tool #8 search_knowledge).

`KnowledgeService` = embed the query, then cosine top-k over kb_chunks (pgvector). The store
is a Protocol so tests use an in-memory `FakeKnowledgeStore` and never touch a DB. RAG must
ALWAYS cite its source and NEVER fabricate — a below-threshold best hit means "not in the
corpus", handled by the caller (offer escalate), not by inventing an answer.
"""

from __future__ import annotations

from typing import Protocol

from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.assistant.embeddings import Embedder
from app.config import get_settings


class KnowledgeHit(BaseModel):
    content: str
    source: str
    score: float  # cosine similarity in [0, 1] — higher is more relevant


class KnowledgeStore(Protocol):
    async def search(self, query_vector: list[float], top_k: int) -> list[KnowledgeHit]: ...


class PgvectorKnowledgeStore:
    """Cosine top-k over kb_chunks.embedding (pgvector `<=>` distance → similarity = 1 - d)."""

    def __init__(self, sessionmaker: async_sessionmaker):
        self._sessionmaker = sessionmaker

    async def search(self, query_vector: list[float], top_k: int) -> list[KnowledgeHit]:
        from app.models.kb_chunk import KbChunk

        distance = KbChunk.embedding.cosine_distance(query_vector).label("distance")
        stmt = (
            select(KbChunk.source, KbChunk.content, distance)
            .order_by(distance)
            .limit(top_k)
        )
        async with self._sessionmaker() as session:
            rows = (await session.execute(stmt)).all()
        return [
            KnowledgeHit(source=r.source, content=r.content, score=1.0 - float(r.distance))
            for r in rows
        ]


class KnowledgeService:
    def __init__(self, embedder: Embedder, store: KnowledgeStore, top_k: int | None = None):
        self._embedder = embedder
        self._store = store
        self._top_k = top_k or get_settings().rag_top_k

    async def search_knowledge(self, query: str) -> list[KnowledgeHit]:
        vector = await self._embedder.embed_query(query)
        return await self._store.search(vector, self._top_k)


def get_default_knowledge_service() -> KnowledgeService:
    """Real service backed by Gemini embeddings + pgvector on ai_db (MCP tool #8, seeding)."""
    from app.assistant.embeddings import get_default_embedder
    from app.db import get_sessionmaker

    return KnowledgeService(get_default_embedder(), PgvectorKnowledgeStore(get_sessionmaker()))
