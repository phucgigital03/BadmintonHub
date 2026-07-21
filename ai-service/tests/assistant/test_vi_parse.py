"""Deterministic Vietnamese parsing (the Day 2 DoD 'parse intent VN' tests) — pure, offline."""

from datetime import date, time, timedelta

import pytest

from app.assistant.vi_parse import (
    parse_budget,
    parse_sport,
    parse_time_window,
    resolve_relative_date,
)

TODAY = date(2026, 7, 20)  # a Monday


def test_hom_nay_and_mai_and_mot():
    assert resolve_relative_date("đặt hôm nay", TODAY) == TODAY
    assert resolve_relative_date("chơi ngày mai", TODAY) == TODAY + timedelta(days=1)
    assert resolve_relative_date("mai nhé", TODAY) == TODAY + timedelta(days=1)
    assert resolve_relative_date("để mốt", TODAY) == TODAY + timedelta(days=2)


def test_weekday_next_upcoming():
    d = resolve_relative_date("tối thứ 6", TODAY)
    assert d is not None and d.weekday() == 4 and TODAY <= d < TODAY + timedelta(days=7)

    cn = resolve_relative_date("chủ nhật", TODAY)
    assert cn is not None and cn.weekday() == 6


def test_weekday_next_week_bump():
    d = resolve_relative_date("thứ 6 tới", TODAY)
    assert d is not None and d.weekday() == 4
    assert TODAY + timedelta(days=7) <= d < TODAY + timedelta(days=14)


def test_cuoi_tuan_is_saturday():
    d = resolve_relative_date("cuối tuần này", TODAY)
    assert d is not None and d.weekday() == 5


def test_explicit_ddmm():
    assert resolve_relative_date("ngày 15/08", TODAY) == date(2026, 8, 15)


def test_no_date_phrase_returns_none():
    assert resolve_relative_date("đặt sân pickleball", TODAY) is None


def test_khuyen_mai_is_not_tomorrow():
    # "khuyến mãi" strips to "khuyen mai" — must NOT be read as "ngày mai" (Day-7 fix)
    assert resolve_relative_date("có khuyến mãi gì không?", TODAY) is None
    assert resolve_relative_date("tối mai có ưu đãi không?", TODAY) == TODAY + timedelta(days=1)


@pytest.mark.parametrize(
    "text,expected",
    [
        ("18-20h", (time(18, 0), time(20, 0))),
        ("18h-20h", (time(18, 0), time(20, 0))),
        ("18h30-20h", (time(18, 30), time(20, 0))),
        ("18:00-20:00", (time(18, 0), time(20, 0))),
        ("từ 19h đến 21h", (time(19, 0), time(21, 0))),
        ("7h tối", (time(19, 0), None)),
        ("2h chiều", (time(14, 0), None)),
        ("8h sáng", (time(8, 0), None)),
        # spelled-out "giờ" marker binds the hour to its period word (Day-7 parser fix)
        ("6 giờ tối", (time(18, 0), None)),
        ("8 giờ sáng", (time(8, 0), None)),
        ("tối mai 6 giờ", (time(18, 0), None)),
        ("18 giờ đến 20 giờ", (time(18, 0), time(20, 0))),
        # a leading weekday digit must NOT be swallowed as the hour (Day-7 parser fix)
        ("tối thứ 6 18-20h", (time(18, 0), time(20, 0))),
        ("không có giờ", (None, None)),
    ],
)
def test_parse_time_window(text, expected):
    assert parse_time_window(text) == expected


@pytest.mark.parametrize(
    "text,expected",
    [
        ("dưới 200k", 200_000),
        ("200k", 200_000),
        ("khoảng 200 nghìn", 200_000),
        ("200 ngàn", 200_000),
        ("tối đa 200.000", 200_000),
        ("budget 150k", 150_000),
        ("1 triệu", 1_000_000),
        ("1tr", 1_000_000),
        ("sân đẹp thoáng", None),
    ],
)
def test_parse_budget(text, expected):
    assert parse_budget(text) == expected


def test_full_sentence_extracts_window_and_budget_not_the_clock_numbers():
    text = "đặt pickleball 18-20h dưới 200k quận 3"
    assert parse_time_window(text) == (time(18, 0), time(20, 0))
    assert parse_budget(text) == 200_000  # 18/20 (clock) are not mistaken for a budget


# --- sport (deterministic keywords — works even when the LLM is down) --------------

def test_parse_sport_pickleball():
    assert parse_sport("đặt sân pickleball ngày mai 18h") == "PICKLEBALL"


def test_parse_sport_cau_long_accented():
    assert parse_sport("đặt sân cầu lông tối thứ 6") == "BADMINTON"


def test_parse_sport_absent_returns_none():
    assert parse_sport("đặt sân ngày mai 18h-20h") is None
