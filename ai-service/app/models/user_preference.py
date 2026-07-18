"""L2 long-term structured memory — a user's learned booking preferences (§6)."""

from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import DateTime, Numeric, String, func
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class UserPreference(Base):
    __tablename__ = "user_preferences"

    id: Mapped[uuid.UUID] = mapped_column(
        PGUUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    # ref users.id · cross-service UUID
    user_id: Mapped[uuid.UUID] = mapped_column(
        PGUUID(as_uuid=True), unique=True, index=True, nullable=False
    )
    preferred_sport: Mapped[str | None] = mapped_column(String(50))
    preferred_club_id: Mapped[uuid.UUID | None] = mapped_column(PGUUID(as_uuid=True))
    preferred_time_window: Mapped[str | None] = mapped_column(String(50))
    budget_max: Mapped[Decimal | None] = mapped_column(Numeric(12, 2))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )
