"""pgvector + kb_chunks (Day 4 RAG)

Revision ID: 0002
Revises: 0001
Create Date: 2026-07-19

Enables the `vector` extension (requires the pgvector/pgvector:pg15 image — the plain
postgres:15-alpine image does NOT ship it) and creates the kb_chunks corpus table.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from pgvector.sqlalchemy import Vector
from sqlalchemy.dialects import postgresql

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

EMBEDDING_DIM = 768  # Gemini models/text-embedding-004


def upgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS vector")
    op.create_table(
        "kb_chunks",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("source", sa.Text(), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("embedding", Vector(EMBEDDING_DIM), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_kb_chunks_source", "kb_chunks", ["source"])


def downgrade() -> None:
    op.drop_table("kb_chunks")
    # leave the extension in place — dropping it could break other objects
