"""READ tools exposed to the ReAct agent node (Day 2).

The canonical tool definitions live in the FastMCP server (`app/tools/mcp_server.py`); these
`@tool` objects give the LLM *schemas* for `bind_tools`, while `execute_tool_call` dispatches
to the real async `booking_tools` functions so we capture the *typed* result (`AvailableSlot[]`)
that the CODE ranker needs — and the user JWT still forwards via the contextvar. Only READ tools
here: get_day_grid, get_pricing. WRITE tools stay off the graph until Day 3.
"""

from __future__ import annotations

import json
from datetime import date as date_
from typing import Any
from uuid import UUID

from langchain_core.tools import tool

from app.tools import booking_tools, schemas
from app.tools.http_client import ToolError


@tool
async def get_day_grid(club_id: str, date: str, sport: str | None = None) -> str:
    """Lịch sân THẬT của một CLB trong một ngày — trả về các ô 30 phút còn trống (AVAILABLE).

    club_id: UUID của CLB. date: YYYY-MM-DD. sport: PICKLEBALL|BADMINTON (tuỳ chọn).
    """
    slots = await booking_tools.get_day_grid(UUID(club_id), date_.fromisoformat(date), sport)
    return _dump(slots)


@tool
async def get_pricing(club_id: str, sport: str) -> str:
    """Bảng giá theo CLB + môn (sport bắt buộc). club_id: UUID. sport: PICKLEBALL|BADMINTON."""
    rules = await booking_tools.get_pricing(UUID(club_id), sport)
    return _dump(rules)


READ_TOOLS = [get_day_grid, get_pricing]


async def execute_tool_call(
    name: str, args: dict[str, Any]
) -> tuple[list[schemas.AvailableSlot] | None, str]:
    """Run one tool call from the ReAct loop. Returns (typed slots if get_day_grid, JSON content).

    The typed slots feed the deterministic ranker; the JSON content becomes the ToolMessage the
    LLM sees. Coercion of str→UUID/date happens here (LLM emits strings).
    """
    if name == "get_day_grid":
        slots = await booking_tools.get_day_grid(
            UUID(args["club_id"]), date_.fromisoformat(args["date"]), args.get("sport")
        )
        return slots, _dump(slots)
    if name == "get_pricing":
        rules = await booking_tools.get_pricing(UUID(args["club_id"]), args["sport"])
        return None, _dump(rules)
    raise ToolError(400, "UNKNOWN_TOOL", f"Agent requested unknown tool: {name}")


def _dump(items: list[Any]) -> str:
    return json.dumps([i.model_dump(mode="json") for i in items], ensure_ascii=False)
