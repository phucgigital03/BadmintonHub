"""Audit snapshot per agent run — model + prompt version + tool calls (§11, §15)."""

from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, String, Text, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class AgentRunLog(Base):
    __tablename__ = "agent_run_log"

    id: Mapped[uuid.UUID] = mapped_column(
        PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    session_id: Mapped[uuid.UUID] = mapped_column(PGUUID(as_uuid=True), index=True, nullable=False)
    # ref users.id · cross-service UUID
    user_id: Mapped[uuid.UUID] = mapped_column(PGUUID(as_uuid=True), index=True, nullable=False)
    model: Mapped[str] = mapped_column(String(100), nullable=False)
    prompt_version: Mapped[str | None] = mapped_column(String(50))
    input_text: Mapped[str | None] = mapped_column(Text)
    output_summary: Mapped[str | None] = mapped_column(Text)
    tool_calls: Mapped[list | None] = mapped_column(JSONB)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
