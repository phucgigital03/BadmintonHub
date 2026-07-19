"""L2 long-term structured memory — a user's learned booking preferences (§6).

Facts with a clear key (userId) → SQL exact, NOT vector (§6.1 rule 2). We infer club / usual
time-window / typical budget from booking history, and capture the sport at the moment WE book
(BookingResponse has no sport field — §5). The store is a Protocol so tests inject an in-memory
fake and stay offline.
"""

from __future__ import annotations

import statistics
import uuid
from collections import Counter
from datetime import time
from decimal import Decimal
from typing import Protocol

from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.tools.schemas import BookingResponse

_WINDOW_HOURS = 2  # a usual window is rendered as a 2-hour block starting at the usual hour


class PreferenceSnapshot(BaseModel):
    preferred_club_id: uuid.UUID | None = None
    preferred_sport: str | None = None
    preferred_time_window: str | None = None  # "HH:MM-HH:MM"
    budget_max: int | None = None  # VND

    def is_empty(self) -> bool:
        return not any(
            (
                self.preferred_club_id,
                self.preferred_sport,
                self.preferred_time_window,
                self.budget_max,
            )
        )

    def window_times(self) -> tuple[time, time] | None:
        if not self.preferred_time_window or "-" not in self.preferred_time_window:
            return None
        try:
            start, end = self.preferred_time_window.split("-", 1)
            return time.fromisoformat(start.strip()), time.fromisoformat(end.strip())
        except ValueError:
            return None


def derive_from_bookings(bookings: list[BookingResponse]) -> PreferenceSnapshot:
    """Pure, testable inference from history — club (most frequent), usual window (most common
    start hour → 2h block), typical budget (median total). Sport is NOT inferable here."""
    club_ids = [b.club_id for b in bookings if b.club_id is not None]
    start_hours = [
        b.earliest_start_time.hour for b in bookings if b.earliest_start_time is not None
    ]
    totals = [float(b.total_price) for b in bookings if b.total_price is not None]

    snapshot = PreferenceSnapshot()
    if club_ids:
        snapshot.preferred_club_id = Counter(club_ids).most_common(1)[0][0]
    if start_hours:
        hour = Counter(start_hours).most_common(1)[0][0]
        end = min(hour + _WINDOW_HOURS, 23)
        snapshot.preferred_time_window = f"{hour:02d}:00-{end:02d}:00"
    if totals:
        snapshot.budget_max = int(round(statistics.median(totals)))
    return snapshot


def snapshot_from_booking(sport: str | None, booking: BookingResponse) -> PreferenceSnapshot:
    """Preferences captured right after WE created a hold — sport comes from the intent (known)."""
    snapshot = PreferenceSnapshot(preferred_sport=sport, preferred_club_id=booking.club_id)
    if booking.earliest_start_time is not None:
        hour = booking.earliest_start_time.hour
        end = min(hour + _WINDOW_HOURS, 23)
        snapshot.preferred_time_window = f"{hour:02d}:00-{end:02d}:00"
    if booking.total_price is not None:
        snapshot.budget_max = int(round(float(booking.total_price)))
    return snapshot


def personalization_note(snapshot: PreferenceSnapshot) -> str:
    """One VN line surfaced to the user + injected into the system prompt."""
    parts: list[str] = []
    if snapshot.preferred_sport:
        parts.append(f"môn {snapshot.preferred_sport}")
    if snapshot.preferred_time_window:
        parts.append(f"khung {snapshot.preferred_time_window}")
    if snapshot.budget_max:
        parts.append(f"ngân sách ~{snapshot.budget_max:,}đ")
    return "Gợi ý theo lịch sử của bạn: " + " · ".join(parts) if parts else ""


class PreferenceStore(Protocol):
    async def load(self, user_id: str) -> PreferenceSnapshot | None: ...

    async def upsert(self, user_id: str, snapshot: PreferenceSnapshot) -> None: ...


class SqlPreferenceStore:
    """Backed by the user_preferences table (ai_db) — exact lookup by user_id (UNIQUE)."""

    def __init__(self, sessionmaker: async_sessionmaker):
        self._sessionmaker = sessionmaker

    @staticmethod
    def _to_snapshot(row) -> PreferenceSnapshot:
        return PreferenceSnapshot(
            preferred_club_id=row.preferred_club_id,
            preferred_sport=row.preferred_sport,
            preferred_time_window=row.preferred_time_window,
            budget_max=int(row.budget_max) if row.budget_max is not None else None,
        )

    async def load(self, user_id: str) -> PreferenceSnapshot | None:
        from app.models.user_preference import UserPreference

        async with self._sessionmaker() as session:
            row = (
                await session.execute(
                    select(UserPreference).where(UserPreference.user_id == uuid.UUID(user_id))
                )
            ).scalar_one_or_none()
        return self._to_snapshot(row) if row is not None else None

    async def upsert(self, user_id: str, snapshot: PreferenceSnapshot) -> None:
        from app.models.user_preference import UserPreference

        async with self._sessionmaker() as session:
            row = (
                await session.execute(
                    select(UserPreference).where(UserPreference.user_id == uuid.UUID(user_id))
                )
            ).scalar_one_or_none()
            budget = Decimal(snapshot.budget_max) if snapshot.budget_max is not None else None
            if row is None:
                session.add(
                    UserPreference(
                        user_id=uuid.UUID(user_id),
                        preferred_club_id=snapshot.preferred_club_id,
                        preferred_sport=snapshot.preferred_sport,
                        preferred_time_window=snapshot.preferred_time_window,
                        budget_max=budget,
                    )
                )
            else:
                if snapshot.preferred_club_id is not None:
                    row.preferred_club_id = snapshot.preferred_club_id
                if snapshot.preferred_sport is not None:
                    row.preferred_sport = snapshot.preferred_sport
                if snapshot.preferred_time_window is not None:
                    row.preferred_time_window = snapshot.preferred_time_window
                if budget is not None:
                    row.budget_max = budget
            await session.commit()


def get_default_preference_store() -> PreferenceStore:
    from app.db import get_sessionmaker

    return SqlPreferenceStore(get_sessionmaker())
