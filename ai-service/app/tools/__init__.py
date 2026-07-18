"""Booking tools = thin wrappers around the real court/booking/payment endpoints.

Each tool forwards the user's JWT (act-as-user) and contains NO money logic. READ tools
(search_clubs / get_day_grid / get_pricing / get_user_bookings) never change state; WRITE
tools (create_booking_hold / initiate_payment / cancel_booking) only run after the agent's
human-in-the-loop confirmation (Day 3). Registered once on a FastMCP server (mcp_server.py).
"""
