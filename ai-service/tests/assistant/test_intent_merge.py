"""Loop-back MERGE: a follow-up edit changes only what it mentions, keeps the rest (statefulness)."""

from datetime import date, time
from uuid import UUID

from app.assistant.models import BookingIntent

CLUB = UUID("aaaaaaaa-0000-0000-0000-000000000001")


def _full() -> BookingIntent:
    return BookingIntent(
        sport="PICKLEBALL",
        date=date(2026, 8, 1),
        time_from=time(18, 0),
        time_to=time(20, 0),
        budget_max=200_000,
        club_id=CLUB,
    )


def test_edit_time_keeps_everything_else():
    merged = _full().merge(BookingIntent(time_from=time(19, 0)))
    assert merged.time_from == time(19, 0)
    assert merged.time_to == time(20, 0)  # untouched
    assert merged.sport == "PICKLEBALL"
    assert merged.budget_max == 200_000
    assert merged.club_id == CLUB


def test_none_fields_never_wipe_prior():
    merged = _full().merge(BookingIntent())  # nothing mentioned
    assert merged.sport == "PICKLEBALL"
    assert merged.date == date(2026, 8, 1)
    assert merged.budget_max == 200_000


def test_new_value_overrides():
    merged = _full().merge(BookingIntent(sport="BADMINTON", budget_max=300_000))
    assert merged.sport == "BADMINTON"
    assert merged.budget_max == 300_000
    assert merged.time_from == time(18, 0)  # kept
