"""READ tools — forward JWT, correct method/path/query, correct parsing."""

from datetime import date

import httpx
import pytest
import respx

from app.tools import booking_tools
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h


@respx.mock
async def test_search_clubs_forwards_jwt_and_parses():
    route = respx.get(f"{h.BASE}/api/clubs").mock(return_value=httpx.Response(200, json=h.club_page()))
    page = await booking_tools.search_clubs(sport="PICKLEBALL")

    assert page.total_elements == 1
    assert page.content[0].name == "An Bình Pickleball"
    req = route.calls.last.request
    assert req.headers["Authorization"] == f"Bearer {TEST_BEARER}"
    assert req.url.params["sport"] == "PICKLEBALL"


@respx.mock
async def test_get_day_grid_filters_available_and_maps_ids():
    respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=h.grid_mixed())
    )
    available = await booking_tools.get_day_grid(h.CLUB_ID, date(2026, 8, 1), sport="PICKLEBALL")

    # only the single AVAILABLE cell survives (RESERVED/BLOCKED/EVENT dropped)
    assert len(available) == 1
    slot = available[0]
    assert str(slot.slot_id) == h.SLOT_ID
    assert str(slot.court_id) == h.COURT_ID  # court_id came from the court, slot_id from the slot
    assert slot.court_number == "Sân 1"


@respx.mock
async def test_get_day_grid_sends_required_date_and_optional_sport():
    route = respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/slots").mock(
        return_value=httpx.Response(200, json=h.grid_mixed())
    )
    await booking_tools.get_day_grid(h.CLUB_ID, date(2026, 8, 1))
    params = route.calls.last.request.url.params
    assert params["date"] == "2026-08-01"
    assert "sport" not in params  # omitted when not given


@respx.mock
async def test_get_pricing_requires_sport_query_and_parses_bare_list():
    route = respx.get(f"{h.BASE}/api/clubs/{h.CLUB_ID}/pricing").mock(
        return_value=httpx.Response(200, json=h.pricing_list())
    )
    rules = await booking_tools.get_pricing(h.CLUB_ID, "PICKLEBALL")

    assert isinstance(rules, list) and len(rules) == 1
    assert str(rules[0].price_per_hour) == "120000"
    assert route.calls.last.request.url.params["sport"] == "PICKLEBALL"


@respx.mock
async def test_get_user_bookings_parses_page():
    route = respx.get(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(200, json=h.booking_page())
    )
    page = await booking_tools.get_user_bookings()

    assert page.total_elements == 1
    assert page.content[0].status == "PENDING"
    assert route.calls.last.request.headers["Authorization"] == f"Bearer {TEST_BEARER}"


@respx.mock
async def test_tool_raises_on_missing_bearer():
    from app.security.context import current_bearer

    respx.get(f"{h.BASE}/api/clubs").mock(return_value=httpx.Response(200, json=h.club_page()))
    tok = current_bearer.set(None)
    try:
        with pytest.raises(RuntimeError):
            await booking_tools.search_clubs()
    finally:
        current_bearer.reset(tok)
