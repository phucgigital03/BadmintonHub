"""Deterministic rank + propose (CODE, never the LLM).

Input: the AVAILABLE 30-min cells from the live grid + the parsed BookingIntent.
Output: a RankResult — either a concrete ProposedBooking (exact window, within budget) or, when
nothing fits, UC-CS-04 alternatives (nearest time / other court / cheapest over-budget). The LLM
is never the authority on price or slot; this module is.
"""

from __future__ import annotations

from datetime import time
from decimal import Decimal

from pydantic import BaseModel

from app.assistant.models import BookingIntent, CourtOption, ProposedBooking
from app.tools.schemas import AvailableSlot, BookingItemInput

CELL_MINUTES = 30


class RankResult(BaseModel):
    kind: str  # "proposal" | "alternatives" | "empty"
    proposal: ProposedBooking | None = None
    candidates: list[CourtOption] = []
    message: str = ""


def _mins(t: time) -> int:
    return t.hour * 60 + t.minute


def _target_duration(intent: BookingIntent) -> int | None:
    if intent.time_from and intent.time_to:
        return _mins(intent.time_to) - _mins(intent.time_from)
    return intent.duration_minutes


def _by_court(slots: list[AvailableSlot]) -> dict:
    courts: dict = {}
    for s in slots:
        courts.setdefault(s.court_id, []).append(s)
    for cells in courts.values():
        cells.sort(key=lambda c: _mins(c.start_time))
    return courts


def _contiguous_runs(cells: list[AvailableSlot]) -> list[list[AvailableSlot]]:
    """Split time-sorted cells into maximal runs where each cell abuts the next (end == start)."""
    runs: list[list[AvailableSlot]] = []
    current: list[AvailableSlot] = []
    for c in cells:
        if current and current[-1].end_time == c.start_time:
            current.append(c)
        else:
            if current:
                runs.append(current)
            current = [c]
    if current:
        runs.append(current)
    return runs


def _block_option(
    intent: BookingIntent, court_number_by_id: dict, block: list[AvailableSlot], rationale: str
) -> CourtOption:
    total = sum((c.price or Decimal(0) for c in block), Decimal(0))
    within = intent.budget_max is None or total <= intent.budget_max
    return CourtOption(
        club_id=intent.club_id,  # set by caller-provided intent
        court_id=block[0].court_id,
        court_number=court_number_by_id[block[0].court_id],
        sport=intent.sport,
        slot_ids=[c.slot_id for c in block],
        start_time=block[0].start_time,
        end_time=block[-1].end_time,
        total_price=total,
        within_budget=within,
        rationale=rationale,
    )


def _blocks_of_length(cells: list[AvailableSlot], n_cells: int) -> list[list[AvailableSlot]]:
    """Every contiguous block of exactly n_cells adjacent cells within this court."""
    out: list[list[AvailableSlot]] = []
    for run in _contiguous_runs(cells):
        for i in range(0, len(run) - n_cells + 1):
            out.append(run[i : i + n_cells])
    return out


def rank(slots: list[AvailableSlot], intent: BookingIntent) -> RankResult:
    duration = _target_duration(intent)
    if not slots or not duration or duration <= 0:
        return RankResult(kind="empty", message="Không có ô trống phù hợp.")

    n_cells = duration // CELL_MINUTES
    if n_cells <= 0:
        return RankResult(kind="empty", message="Khung giờ quá ngắn.")

    courts = _by_court(slots)
    court_number_by_id = {cid: cells[0].court_number for cid, cells in courts.items()}

    # 1) exact-window blocks (start == time_from) when a window was given
    exact: list[CourtOption] = []
    if intent.time_from and intent.time_to:
        for cells in courts.values():
            for block in _blocks_of_length(cells, n_cells):
                if (
                    block[0].start_time == intent.time_from
                    and block[-1].end_time == intent.time_to
                ):
                    exact.append(
                        _block_option(intent, court_number_by_id, block, "Đúng khung giờ yêu cầu")
                    )

    in_budget = [o for o in exact if o.within_budget]
    if in_budget:
        best = _rank_options(in_budget)[0]
        return RankResult(
            kind="proposal",
            proposal=_to_proposal(intent, best),
            candidates=[best],
            message=(
                f"Đề xuất {best.court_number} · {best.start_time.strftime('%H:%M')}–"
                f"{best.end_time.strftime('%H:%M')} · {int(best.total_price):,}đ"
            ),
        )

    # 2) UC-CS-04 alternatives — nothing fits the exact window within budget
    alts = _alternatives(courts, court_number_by_id, intent, n_cells, exact)
    if alts:
        return RankResult(
            kind="alternatives",
            candidates=alts,
            message="Khung giờ mong muốn không còn phù hợp — gợi ý phương án thay thế:",
        )
    return RankResult(kind="empty", message="Rất tiếc, không còn ô trống phù hợp trong ngày.")


def _alternatives(
    courts: dict, court_number_by_id: dict, intent: BookingIntent, n_cells: int, exact: list
) -> list[CourtOption]:
    """UC-CS-04: exact-window-but-over-budget (cheapest) + nearest-time same-length blocks."""
    out: list[CourtOption] = []

    # (a) exact window but over budget → cheapest, labelled
    if exact:
        cheapest = min(exact, key=lambda o: o.total_price)
        cheapest.rationale = "Đúng giờ nhưng vượt ngân sách"
        out.append(cheapest)

    # (b) same-length blocks at other times, ranked by closeness to the requested start
    anchor = _mins(intent.time_from) if intent.time_from else 0
    others: list[CourtOption] = []
    for cells in courts.values():
        for block in _blocks_of_length(cells, n_cells):
            if intent.time_from and intent.time_to and (
                block[0].start_time == intent.time_from and block[-1].end_time == intent.time_to
            ):
                continue  # already covered by (a)
            opt = _block_option(intent, court_number_by_id, block, "Đổi giờ/đổi sân gần yêu cầu")
            others.append(opt)
    others.sort(
        key=lambda o: (abs(_mins(o.start_time) - anchor), not o.within_budget, o.total_price)
    )
    out.extend(others[:3])
    return out[:4]


def _rank_options(options: list[CourtOption]) -> list[CourtOption]:
    """Best first: within budget, then cheapest, then lowest court number."""
    return sorted(
        options,
        key=lambda o: (not o.within_budget, o.total_price, _court_key(o.court_number)),
    )


def _court_key(court_number: str) -> tuple:
    digits = "".join(ch for ch in court_number if ch.isdigit())
    return (int(digits) if digits else 9999, court_number)


def _to_proposal(intent: BookingIntent, opt: CourtOption) -> ProposedBooking:
    return ProposedBooking(
        club_id=intent.club_id,
        date=intent.date,
        items=[
            BookingItemInput(court_id=opt.court_id, slot_id=sid) for sid in opt.slot_ids
        ],
        total_price=opt.total_price,
        hold_minutes=10,
        summary=(
            f"{opt.court_number} · {intent.date.isoformat()} · "
            f"{opt.start_time.strftime('%H:%M')}–{opt.end_time.strftime('%H:%M')} · "
            f"{int(opt.total_price):,}đ (giữ chỗ 10 phút)"
        ),
    )
