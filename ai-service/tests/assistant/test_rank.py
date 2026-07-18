"""CODE ranker: exact window + budget → proposal; adjacency; UC-CS-04 alternatives."""

from datetime import date, time
from decimal import Decimal
from uuid import UUID

from app.assistant.models import BookingIntent
from app.assistant.ranker import rank
from app.tools.schemas import AvailableSlot

CLUB = UUID("aaaaaaaa-0000-0000-0000-000000000001")
COURT_A = UUID("cccccccc-0000-0000-0000-00000000000a")
COURT_B = UUID("cccccccc-0000-0000-0000-00000000000b")

_HALF = {
    time(16, 0): time(16, 30), time(16, 30): time(17, 0),
    time(17, 0): time(17, 30), time(17, 30): time(18, 0),
    time(18, 0): time(18, 30), time(18, 30): time(19, 0),
    time(19, 0): time(19, 30), time(19, 30): time(20, 0),
    time(20, 0): time(20, 30), time(20, 30): time(21, 0),
    time(21, 0): time(21, 30), time(21, 30): time(22, 0),
}


def _slot(court_id, court_number, start, price):
    return AvailableSlot(
        court_id=court_id,
        court_number=court_number,
        slot_id=UUID(int=hash((court_id, start)) & (2**128 - 1)),
        start_time=start,
        end_time=_HALF[start],
        price=Decimal(price),
    )


def _cells(court_id, court_number, starts, price):
    return [_slot(court_id, court_number, s, price) for s in starts]


def _intent(**kw) -> BookingIntent:
    base = dict(
        sport="PICKLEBALL", date=date(2026, 8, 1), club_id=CLUB,
        time_from=time(18, 0), time_to=time(20, 0),
    )
    base.update(kw)
    return BookingIntent(**base)


def test_exact_window_within_budget_proposes():
    slots = _cells(COURT_A, "Sân 1", [time(18, 0), time(18, 30), time(19, 0), time(19, 30)], "40000")
    result = rank(slots, _intent(budget_max=200_000))

    assert result.kind == "proposal"
    assert result.proposal is not None
    assert result.proposal.total_price == Decimal("160000")
    assert len(result.proposal.items) == 4  # four 30-min cells joined
    assert result.candidates[0].within_budget is True
    assert result.candidates[0].start_time == time(18, 0)
    assert result.candidates[0].end_time == time(20, 0)


def test_gap_in_window_no_exact_fit():
    # 19:00–19:30 missing (reserved) → cannot tile 18:00–20:00, but a full 4-cell block exists 20:00–22:00
    slots = (
        _cells(COURT_A, "Sân 1", [time(18, 0), time(18, 30), time(19, 30)], "40000")
        + _cells(COURT_A, "Sân 1", [time(20, 0), time(20, 30), time(21, 0), time(21, 30)], "40000")
    )
    result = rank(slots, _intent(budget_max=200_000))
    assert result.kind == "alternatives"
    assert all("proposal" != c.rationale for c in result.candidates)


def test_over_budget_becomes_alternative():
    slots = _cells(COURT_A, "Sân 1", [time(18, 0), time(18, 30), time(19, 0), time(19, 30)], "60000")
    result = rank(slots, _intent(budget_max=200_000))  # 4×60000 = 240000 > 200k

    assert result.kind == "alternatives"
    over = result.candidates[0]
    assert over.within_budget is False
    assert "vượt ngân sách" in over.rationale


def test_uc_cs_04_alternative_when_window_sold_out():
    # requested 18–20h unavailable anywhere; other same-length blocks exist on both courts
    slots = (
        _cells(COURT_A, "Sân 1", [time(20, 0), time(20, 30), time(21, 0), time(21, 30)], "40000")
        + _cells(COURT_B, "Sân 2", [time(16, 0), time(16, 30), time(17, 0), time(17, 30)], "40000")
    )
    result = rank(slots, _intent(budget_max=200_000))

    assert result.kind == "alternatives"
    assert len(result.candidates) >= 1
    assert all(c.rationale for c in result.candidates)  # each alternative has a reason


def test_empty_grid_is_empty():
    result = rank([], _intent())
    assert result.kind == "empty"
    assert result.proposal is None
