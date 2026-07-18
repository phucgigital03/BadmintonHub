"""Tiny REPL to drive the concierge against live Gemini + a running gateway (manual check).

    AI_BEARER=<a user JWT>  uv run python -m app.assistant.cli

Requires GEMINI_API_KEY in .env and the gateway/court/booking services up. Read-only — it only
ever produces a proposal card; it never creates a hold. Ctrl-C / "quit" to exit.
"""

from __future__ import annotations

import asyncio
import os
import uuid

from app.assistant.graph import build_graph, run_turn
from app.assistant.llm import get_chat_model


async def _main() -> None:
    bearer = os.environ.get("AI_BEARER")
    if not bearer:
        raise SystemExit("Set AI_BEARER=<user JWT> first.")
    graph = build_graph(get_chat_model())
    session_id = str(uuid.uuid4())
    print("Trợ lý đặt sân (read-only). Gõ 'quit' để thoát.\n")
    while True:
        try:
            text = input("Bạn: ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if text.lower() in {"quit", "exit", "thoat"}:
            break
        if not text:
            continue
        state = await run_turn(graph, session_id=session_id, bearer=bearer, text=text)
        turn = state.get("turn")
        print(f"Trợ lý: {turn.content if turn else '(không có phản hồi)'}")
        if turn and turn.cards:
            for card in turn.cards:
                print(f"   • {card}")
        print()


if __name__ == "__main__":
    asyncio.run(_main())
