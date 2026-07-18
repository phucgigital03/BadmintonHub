"""initial ai-service foundation tables

Revision ID: 0001
Revises:
Create Date: 2026-07-18

Creates the 3 foundation tables (user_preferences, assistant_messages, agent_run_log).
LangGraph checkpointer tables (Day 2) and kb_chunks/pgvector (Day 4) are NOT here.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "user_preferences",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("preferred_sport", sa.String(length=50)),
        sa.Column("preferred_club_id", postgresql.UUID(as_uuid=True)),
        sa.Column("preferred_time_window", sa.String(length=50)),
        sa.Column("budget_max", sa.Numeric(precision=12, scale=2)),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_user_preferences_user_id", "user_preferences", ["user_id"], unique=True)

    op.create_table(
        "assistant_messages",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("session_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("role", sa.String(length=20), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_assistant_messages_session_id", "assistant_messages", ["session_id"])
    op.create_index("ix_assistant_messages_user_id", "assistant_messages", ["user_id"])

    op.create_table(
        "agent_run_log",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("session_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("model", sa.String(length=100), nullable=False),
        sa.Column("prompt_version", sa.String(length=50)),
        sa.Column("input_text", sa.Text()),
        sa.Column("output_summary", sa.Text()),
        sa.Column("tool_calls", postgresql.JSONB()),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_agent_run_log_session_id", "agent_run_log", ["session_id"])
    op.create_index("ix_agent_run_log_user_id", "agent_run_log", ["user_id"])


def downgrade() -> None:
    op.drop_table("agent_run_log")
    op.drop_table("assistant_messages")
    op.drop_table("user_preferences")
