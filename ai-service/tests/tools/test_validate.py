"""Tool-input validation (§11.2) — reject garbage/injection BEFORE the gateway is touched."""

from datetime import date

import httpx
import pytest
import respx

from app.tools import booking_tools, validate
from app.tools.http_client import ToolError
from tests.tools import _helpers as h

CLUB = h.CLUB_ID


def test_sport_normalizes_and_enum_checks():
    assert validate.sport("pickleball", required=False) == "PICKLEBALL"
    assert validate.sport(None, required=False) is None
    with pytest.raises(ToolError) as ei:
        validate.sport("golf", required=True)
    assert ei.value.code == "INVALID_TOOL_INPUT"
    with pytest.raises(ToolError):
        validate.sport(None, required=True)


def test_phone_and_name_and_note():
    assert validate.phone("0901234567") == "0901234567"
    assert validate.phone("+84 901 234 567") == "+84901234567"
    for bad in ("", "abc", "12", "090123456789012"):
        with pytest.raises(ToolError):
            validate.phone(bad)
    assert validate.name("  Nguyễn A  ") == "Nguyễn A"
    with pytest.raises(ToolError):
        validate.name("")
    with pytest.raises(ToolError):
        validate.name("x" * 101)
    with pytest.raises(ToolError):
        validate.note("y" * 501)


def test_items_and_coordinate_bounds():
    with pytest.raises(ToolError):
        validate.items([])
    with pytest.raises(ToolError):
        validate.items([{} for _ in range(21)])  # cap 20
    with pytest.raises(ToolError):
        validate.coordinate(200.0, 0.0, None)
    with pytest.raises(ToolError):
        validate.coordinate(0.0, 0.0, -5.0)


# --- the tool short-circuits before any HTTP call (respx would see 0 requests) ---------


@respx.mock
async def test_get_pricing_bad_sport_no_http():
    route = respx.get(f"{h.BASE}/api/clubs/{CLUB}/pricing").mock(
        return_value=httpx.Response(200, json=h.pricing_list())
    )
    with pytest.raises(ToolError) as ei:
        await booking_tools.get_pricing(CLUB, "chess")
    assert ei.value.code == "INVALID_TOOL_INPUT"
    assert not route.called  # never reached the gateway


@respx.mock
async def test_create_hold_bad_phone_no_http():
    route = respx.post(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(201, json=h.booking())
    )
    items = [{"courtId": h.COURT_ID, "slotId": h.SLOT_ID}]
    with pytest.raises(ToolError) as ei:
        await booking_tools.create_booking_hold(
            CLUB, date(2026, 8, 1), items, "Khách", "not-a-phone"
        )
    assert ei.value.code == "INVALID_TOOL_INPUT"
    assert not route.called
