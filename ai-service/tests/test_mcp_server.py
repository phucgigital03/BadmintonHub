import pytest

from app.tools.mcp_server import BOOKING_TOOLS, mcp

EXPECTED = {
    "search_clubs",
    "get_day_grid",
    "get_pricing",
    "get_user_bookings",
    "create_booking_hold",
    "initiate_payment",
    "cancel_booking",
}


def test_seven_tools_registered():
    assert len(BOOKING_TOOLS) == 7


@pytest.mark.asyncio
async def test_mcp_exposes_all_booking_tools():
    tools = await mcp.list_tools()
    assert {t.name for t in tools} == EXPECTED
