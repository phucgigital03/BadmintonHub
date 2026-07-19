"""Deterministic Vietnamese parsing for the time- and money-critical fields.

The LLM extracts fuzzy intent (sport, district, phrasing), but relative dates, time windows,
and budgets are parsed by CODE here — we do NOT trust the LLM on numbers/dates. These helpers
are pure and offline-unit-tested (the Day 2 DoD "parse intent VN" tests).
"""

from __future__ import annotations

import re
import unicodedata
from datetime import date as date_
from datetime import time, timedelta

# --- normalization ---------------------------------------------------------------


def _strip_accents(text: str) -> str:
    # đ/Đ has no NFD decomposition — fold it explicitly so "đến"→"den", "tối đa"→"toi da".
    text = text.replace("đ", "d").replace("Đ", "D")
    nfkd = unicodedata.normalize("NFD", text)
    return "".join(c for c in nfkd if unicodedata.category(c) != "Mn").lower()


# --- relative date ---------------------------------------------------------------

# VN weekday word (accent-stripped) → Python weekday (Mon=0 .. Sun=6)
_WEEKDAYS: dict[str, int] = {
    "thu 2": 0, "t2": 0, "thu hai": 0,
    "thu 3": 1, "t3": 1, "thu ba": 1,
    "thu 4": 2, "t4": 2, "thu tu": 2,
    "thu 5": 3, "t5": 3, "thu nam": 3,
    "thu 6": 4, "t6": 4, "thu sau": 4,
    "thu 7": 5, "t7": 5, "thu bay": 5,
    "chu nhat": 6, "cn": 6,
}


def resolve_relative_date(text: str, today: date_) -> date_ | None:
    """Resolve a Vietnamese relative-date phrase to an absolute date.

    Handles: hôm nay · (ngày) mai · mốt/ngày kia · thứ 2..7 / CN (next upcoming, "+tới/tuần sau"
    bumps a week) · cuối tuần (upcoming Saturday) · explicit dd/mm. Returns None if nothing matches.
    """
    t = _strip_accents(text)

    if re.search(r"\bhom nay\b", t):
        return today
    if re.search(r"\bngay kia\b|\bmot\b", t):
        return today + timedelta(days=2)
    if re.search(r"\b(ngay )?mai\b", t):
        return today + timedelta(days=1)

    # explicit dd/mm (optionally /yyyy)
    m = re.search(r"\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b", t)
    if m:
        day, month = int(m.group(1)), int(m.group(2))
        year = int(m.group(3)) if m.group(3) else today.year
        if year < 100:
            year += 2000
        try:
            cand = date_(year, month, day)
            # a bare dd/mm already in the past this year rolls to next year
            if not m.group(3) and cand < today:
                cand = date_(year + 1, month, day)
            return cand
        except ValueError:
            return None

    # "next week" bump must read the ACCENTED text: "tới" (next) vs "tối" (evening) both strip
    # to "toi", so only the accented form distinguishes them.
    bump_week = bool(re.search(r"\btới\b|\btuần sau\b|\btuần tới\b|\bsau\b", text.lower()))

    if re.search(r"\bcuoi tuan\b", t):
        return _next_weekday(today, 5, bump_week)  # Saturday

    # longest weekday key first so "thu 7" wins over "t7" etc.
    for key in sorted(_WEEKDAYS, key=len, reverse=True):
        if re.search(rf"\b{re.escape(key)}\b", t):
            return _next_weekday(today, _WEEKDAYS[key], bump_week)

    return None


def _next_weekday(today: date_, weekday: int, bump_week: bool) -> date_:
    """Nearest upcoming date whose weekday == `weekday` (today counts). `bump_week` adds 7."""
    delta = (weekday - today.weekday()) % 7
    result = today + timedelta(days=delta)
    if bump_week:
        result += timedelta(days=7)
    return result


# --- time window -----------------------------------------------------------------

# period words that push an afternoon/evening clock reading past noon
_PM_PERIODS = {"chieu", "toi", "dem"}
_ALL_PERIODS = _PM_PERIODS | {"sang", "trua"}


def _to_24h(hour: int, minute: int, period: str | None) -> time | None:
    # "7h tối" → 19:00 ; "2h chiều" → 14:00 ; "7h sáng" → 07:00 ; "12h trưa" → 12:00
    if period in _PM_PERIODS and hour < 12:
        hour += 12
    if 0 <= hour <= 23 and 0 <= minute <= 59:
        return time(hour, minute)
    return None


def parse_time_window(text: str) -> tuple[time | None, time | None]:
    """Extract a start/end time window. Returns (from, to); either may be None.

    Handles: 18-20h · 18h-20h · 18h30-20h · 18:00-20:00 · từ 18h đến 20h · single "19h" /
    "7h tối" (→ from only) with sáng/chiều/tối period words.
    """
    t = _strip_accents(text)
    period = next((p for p in _ALL_PERIODS if re.search(rf"\b{p}\b", t)), None)

    # range: 18[h:30]? (-|–|den|toi) 20[h:00]?
    rng = re.search(
        r"(\d{1,2})\s*(?:h|:)?\s*(\d{2})?\s*(?:-|–|den|toi)\s*(\d{1,2})\s*(?:h|:)?\s*(\d{2})?",
        t,
    )
    if rng:
        h1, m1, h2, m2 = rng.group(1), rng.group(2), rng.group(3), rng.group(4)
        start = _to_24h(int(h1), int(m1 or 0), period)
        end = _to_24h(int(h2), int(m2 or 0), period)
        return start, end

    # single time: 18h / 18h30 / 18:00 / "7h tối"
    single = re.search(r"(\d{1,2})\s*(?:h|:)\s*(\d{2})?", t)
    if single:
        start = _to_24h(int(single.group(1)), int(single.group(2) or 0), period)
        return start, None

    return None, None


# --- budget ----------------------------------------------------------------------


def parse_budget(text: str) -> int | None:
    """Parse a VND budget: dưới 200k · 200k · 200 nghìn/ngàn · 200.000 · 1 triệu / 1tr / 1m.

    Prefers a figure attached to a budget cue (dưới/tối đa/khoảng/budget/giá) when several
    numbers appear. Returns whole VND, or None.
    """
    t = _strip_accents(text)

    # (number)(optional . , separators)(optional unit)
    pattern = re.compile(r"(\d+(?:[.,]\d+)*)\s*(trieu|tr|m|nghin|ngan|k)?", re.IGNORECASE)
    cue = re.compile(r"(duoi|toi da|khoang|toi thieu|budget|gia|tam|max)")

    best: int | None = None
    best_has_cue = False
    for m in pattern.finditer(t):
        raw, unit = m.group(1), m.group(2)
        value = _to_vnd(raw, unit)
        if value is None:
            continue
        # a plain small integer with no unit and no budget cue nearby is not a budget
        window = t[max(0, m.start() - 12): m.end()]
        has_cue = bool(cue.search(window))
        if unit is None and not has_cue:
            continue
        if best is None or (has_cue and not best_has_cue):
            best, best_has_cue = value, has_cue
    return best


def _to_vnd(raw: str, unit: str | None) -> int | None:
    digits = raw.replace(".", "").replace(",", "")
    if not digits.isdigit():
        return None
    n = int(digits)
    if unit in ("k", "nghin", "ngan"):
        return n * 1_000
    if unit in ("trieu", "tr", "m"):
        return n * 1_000_000
    return n  # already whole VND (e.g. 200000 / 200.000)


# --- sport -----------------------------------------------------------------------

# Keyword (accent-stripped) → platform sport enum. The LLM still handles fuzzy phrasing;
# this deterministic pass makes the common cases work even when the LLM is unavailable.
_SPORTS: dict[str, str] = {
    "pickleball": "PICKLEBALL",
    "pickle ball": "PICKLEBALL",
    "cau long": "BADMINTON",
    "badminton": "BADMINTON",
}


def parse_sport(text: str) -> str | None:
    stripped = _strip_accents(text)
    for keyword, sport in _SPORTS.items():
        if keyword in stripped:
            return sport
    return None
