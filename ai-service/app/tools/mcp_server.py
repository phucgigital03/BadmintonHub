"""FastMCP server exposing the 7 booking tools.

Defined ONCE here: the internal LangGraph agent consumes these via langchain-mcp-adapters
(Day 2), and external agents (Claude Desktop, a calendar agent) can reuse the exact same
tool set without duplicating code. WRITE tools are exposed but gated by human-in-the-loop
in the agent graph — the MCP server itself adds no money logic.
"""

from __future__ import annotations

import json

from fastmcp import FastMCP

from app.tools import booking_tools

mcp = FastMCP("badmintonhub-booking-tools")


async def search_knowledge(query: str) -> str:
    """Tra cứu KIẾN THỨC TĨNH đã curate (chính sách hủy/hoàn · tiện ích & địa chỉ CLB · hướng dẫn
    thanh toán chuyển khoản · khuyến mãi). KHÔNG dùng cho lịch/giá sân (dữ liệu sống — dùng
    get_day_grid/get_pricing). Trả về các đoạn tài liệu kèm nguồn; không có thì KHÔNG bịa."""
    from app.assistant.knowledge import get_default_knowledge_service

    hits = await get_default_knowledge_service().search_knowledge(query)
    return json.dumps([h.model_dump(mode="json") for h in hits], ensure_ascii=False)


BOOKING_TOOLS = [
    booking_tools.search_clubs,
    booking_tools.get_day_grid,
    booking_tools.get_pricing,
    booking_tools.get_user_bookings,
    booking_tools.create_booking_hold,
    booking_tools.initiate_payment,
    booking_tools.cancel_booking,
    search_knowledge,  # tool #8 (RAG — reads pgvector on ai_db, not a gateway endpoint)
]

for _fn in BOOKING_TOOLS:
    mcp.add_tool(_fn)


if __name__ == "__main__":
    mcp.run()
