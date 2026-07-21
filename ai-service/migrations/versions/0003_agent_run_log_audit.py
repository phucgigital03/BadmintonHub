"""agent_run_log audit columns (Day 6 hardening)

Revision ID: 0003
Revises: 0002
Create Date: 2026-07-21

Adds the structured run-snapshot columns (§11.1) so every agent turn is auditable/reproducible:
intent, proposal (customerPhone masked at write), decision (outcome label), latency_ms. All
nullable/additive — safe on an existing table. Existing columns (input_text/output_summary/
tool_calls) are kept and PII-masked at write time.
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("agent_run_log", sa.Column("intent", postgresql.JSONB()))
    op.add_column("agent_run_log", sa.Column("proposal", postgresql.JSONB()))
    op.add_column("agent_run_log", sa.Column("decision", sa.String(length=20)))
    op.add_column("agent_run_log", sa.Column("latency_ms", sa.Integer()))


def downgrade() -> None:
    op.drop_column("agent_run_log", "latency_ms")
    op.drop_column("agent_run_log", "decision")
    op.drop_column("agent_run_log", "proposal")
    op.drop_column("agent_run_log", "intent")
