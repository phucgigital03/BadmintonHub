"""L1 session transcript — one row per turn (user / assistant / tool)."""

from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, String, Text, func
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class AssistantMessage(Base):
    __tablename__ = "assistant_messages"

    id: Mapped[uuid.UUID] = mapped_column(
        PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    session_id: Mapped[uuid.UUID] = mapped_column(PGUUID(as_uuid=True), index=True, nullable=False)
    # ref users.id · cross-service UUID
    user_id: Mapped[uuid.UUID] = mapped_column(PGUUID(as_uuid=True), index=True, nullable=False)
    # role ∈ {user, assistant, system, tool}
    role: Mapped[str] = mapped_column(String(20), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
