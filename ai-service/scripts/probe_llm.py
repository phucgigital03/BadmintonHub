"""Diagnostic: show EXACTLY what the `perceive` node sends to the LLM, then call it.

Reuses the same building blocks the node uses (`prompts.perceive_system`,
`prompts.wrap_user_text`, `llm.get_chat_model`, `models.BookingIntent`) so what it prints IS the
real input. Unlike the node, it does NOT wrap the call in the 25s `asyncio.wait_for`, so the real
Gemini result / error / latency is visible instead of a bare, message-less `TimeoutError`.

Run from ai-service/ (picks up the same root .env as the service):
    uv run python scripts/probe_llm.py "how are you?"
    uv run python scripts/probe_llm.py "tối thứ 6 pickleball 18-20h dưới 200k"

Reading the result:
    OK in <3s              -> transient/cold-start blip (raise LLM_TIMEOUT_SECONDS)
    ResourceExhausted/429  -> free-tier rate limit (wait, slow down, or enable billing)
    DeadlineExceeded / SSL / connection / DNS -> network to Google slow/blocked from this machine
    PermissionDenied / "API key not valid"    -> GEMINI_API_KEY / quota problem
"""

from __future__ import annotations

import asyncio
import sys
import time
from datetime import date
from pathlib import Path

# make the `app` package importable whether this is run as a file or a module
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from langchain_core.messages import HumanMessage, SystemMessage  # noqa: E402

from app.assistant import prompts  # noqa: E402
from app.assistant.llm import get_chat_model  # noqa: E402
from app.assistant.models import BookingIntent  # noqa: E402


async def main(text: str) -> None:
    today = date.today()
    system = prompts.perceive_system(today)
    user = prompts.wrap_user_text(text)

    print("===== SYSTEM PROMPT =====")
    print(system)
    print("===== USER MESSAGE (as sent) =====")
    print(user)
    print("==================================")

    model = get_chat_model().with_structured_output(BookingIntent)
    t0 = time.perf_counter()
    try:
        result = await model.ainvoke(
            [SystemMessage(content=system), HumanMessage(content=user)]
        )
        print(f"OK in {time.perf_counter() - t0:.1f}s -> {result!r}")
    except Exception as exc:  # noqa: BLE001 — we WANT the real error surfaced here
        print(f"FAILED in {time.perf_counter() - t0:.1f}s -> {type(exc).__name__}: {exc}")


if __name__ == "__main__":
    asyncio.run(main(sys.argv[1] if len(sys.argv) > 1 else "how are you?"))
