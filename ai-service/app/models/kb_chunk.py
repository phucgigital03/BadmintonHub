"""L4 knowledge corpus — curated, chunked, embedded knowledge for RAG (§6, UC-CS-08).

Only semi-static admin-curated knowledge (cancellation policy, club facilities, payment
guide, promotions) lives here — NEVER live booking/pricing data (§6.1: that stays a live
tool-query so it can never go stale). 768-dim to match Gemini text-embedding-004.
"""

from __future__ import annotations

import uuid
from datetime import datetime

from pgvector.sqlalchemy import Vector
from sqlalchemy import DateTime, Text, func
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base

EMBEDDING_DIM = 768  # Gemini models/text-embedding-004


class KbChunk(Base):
    __tablename__ = "kb_chunks"

    id: Mapped[uuid.UUID] = mapped_column(
        PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    # e.g. "cancellation_policy.md" — the curated file the chunk came from (for citation)
    source: Mapped[str] = mapped_column(Text, nullable=False, index=True)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    embedding: Mapped[list[float]] = mapped_column(Vector(EMBEDDING_DIM), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
