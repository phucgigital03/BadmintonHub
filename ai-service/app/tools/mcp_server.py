"""FastMCP server exposing the 7 booking tools.

Defined ONCE here: the internal LangGraph agent consumes these via langchain-mcp-adapters
(Day 2), and external agents (Claude Desktop, a calendar agent) can reuse the exact same
tool set without duplicating code. WRITE tools are exposed but gated by human-in-the-loop
in the agent graph — the MCP server itself adds no money logic.
"""

from __future__ import annotations

from fastmcp import FastMCP

from app.tools import booking_tools

mcp = FastMCP("badmintonhub-booking-tools")

BOOKING_TOOLS = [
    booking_tools.search_clubs,
    booking_tools.get_day_grid,
    booking_tools.get_pricing,
    booking_tools.get_user_bookings,
    booking_tools.create_booking_hold,
    booking_tools.initiate_payment,
    booking_tools.cancel_booking,
]

for _fn in BOOKING_TOOLS:
    mcp.add_tool(_fn)


if __name__ == "__main__":
    mcp.run()
