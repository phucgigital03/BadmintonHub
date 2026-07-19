"""Seed the RAG corpus into kb_chunks (idempotent · admin-curated · Day 4).

Reads every ``ai-service/knowledge/*.md`` (one file = one `source`), splits into ~500-char
chunks along paragraph/heading boundaries, embeds with the configured embedder, and upserts
(delete-by-source then insert) so re-running never duplicates. Run live:

    uv run python -m app.knowledge.seed
"""

from __future__ import annotations

import asyncio
import uuid
from pathlib import Path

import structlog
from sqlalchemy import delete

from app.assistant.embeddings import Embedder, get_default_embedder
from app.db import get_sessionmaker

log = structlog.get_logger(__name__)

CORPUS_DIR = Path(__file__).resolve().parents[2] / "knowledge"
MAX_CHARS = 500


def chunk_markdown(text: str) -> list[str]:
    """Greedy paragraph merge up to ~MAX_CHARS, keeping headings with their paragraph."""
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
    chunks: list[str] = []
    buffer = ""
    for para in paragraphs:
        candidate = f"{buffer}\n\n{para}".strip() if buffer else para
        if len(candidate) <= MAX_CHARS:
            buffer = candidate
        else:
            if buffer:
                chunks.append(buffer)
            buffer = para
    if buffer:
        chunks.append(buffer)
    return chunks


def load_corpus() -> dict[str, list[str]]:
    """{source_filename: [chunk, ...]} for every markdown file in the corpus dir."""
    corpus: dict[str, list[str]] = {}
    for path in sorted(CORPUS_DIR.glob("*.md")):
        corpus[path.name] = chunk_markdown(path.read_text(encoding="utf-8"))
    return corpus


async def seed(embedder: Embedder | None = None) -> int:
    from app.models.kb_chunk import KbChunk

    embedder = embedder or get_default_embedder()
    corpus = load_corpus()
    sessionmaker = get_sessionmaker()
    total = 0
    async with sessionmaker() as session:
        for source, chunks in corpus.items():
            await session.execute(delete(KbChunk).where(KbChunk.source == source))
            vectors = await embedder.embed(chunks)
            for content, vector in zip(chunks, vectors, strict=True):
                session.add(
                    KbChunk(id=uuid.uuid4(), source=source, content=content, embedding=vector)
                )
                total += 1
            log.info("kb.seeded_source", source=source, chunks=len(chunks))
        await session.commit()
    log.info("kb.seed_done", sources=len(corpus), chunks=total)
    return total


if __name__ == "__main__":
    asyncio.run(seed())
