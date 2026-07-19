"""Tiny REPL to drive the concierge against live Gemini + a running gateway (manual e2e).

    AI_BEARER=<a user JWT>  uv run python -m app.assistant.cli

Requires GEMINI_API_KEY in .env and the gateway/court/booking/payment services up.
Day 3: when a proposal pauses at the human_review interrupt, the REPL asks
"Xác nhận giữ chỗ? (y/n/text)" — y resumes into guardrail → hold → payment (a REAL
PENDING hold + Bank-QR info), n declines, any other text is treated as an edit.
Money stays human-gated: nothing is written until you answer y. Ctrl-C / "quit" to exit.
"""

from __future__ import annotations

import asyncio
import os
import uuid

from app.assistant.graph import (
    build_graph,
    has_pending_interrupt,
    resume_confirm,
    run_turn,
)
from app.assistant.llm import get_chat_model
from app.assistant.models import ConfirmDecision


def _print_turn(state: dict) -> None:
    turn = state.get("turn")
    print(f"Trợ lý: {turn.content if turn else '(không có phản hồi)'}")
    if turn and turn.cards:
        for card in turn.cards:
            print(f"   • {card}")
    print()


async def _confirm_loop(graph, session_id: str, bearer: str) -> None:
    """While the graph is paused at human_review, ask the human at this terminal."""
    while await has_pending_interrupt(graph, session_id):
        answer = input("Xác nhận giữ chỗ? (y/n/text sửa): ").strip()
        if answer.lower() == "y":
            decision = ConfirmDecision(confirmed=True)
        elif answer.lower() == "n":
            decision = ConfirmDecision(confirmed=False)
        else:
            decision = ConfirmDecision(confirmed=False, edits=answer or None)
        state = await resume_confirm(
            graph, session_id=session_id, bearer=bearer, decision=decision
        )
        _print_turn(state)
        if state.get("stage") == "payment" and state.get("payment"):
            booking = state.get("hold") or {}
            pay = state.get("payment") or {}
            print("=== HOLD THẬT ===")
            print(f"  bookingId : {booking.get('id')}  (status {booking.get('status')})")
            print(f"  totalPrice: {booking.get('total_price')}  (authoritative, server-tính)")
            print(f"  orderCode : {pay.get('order_code')}  ·  amount: {pay.get('amount')}")
            print(f"  bank      : {pay.get('bank_name')} {pay.get('account_number')}")
            print(f"  QR        : {pay.get('qr_image_url')}")
            print(f"  expiresAt : {pay.get('expires_at')}\n")
        if not decision.confirmed and decision.edits is None:
            break  # declined outright — back to the chat prompt


async def _main() -> None:
    bearer = os.environ.get("AI_BEARER")
    if not bearer:
        raise SystemExit("Set AI_BEARER=<user JWT> first.")
    graph = build_graph(get_chat_model())
    session_id = str(uuid.uuid4())
    print("Trợ lý đặt sân (Day 3 — có giữ chỗ thật sau khi bạn xác nhận). Gõ 'quit' để thoát.\n")
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
        _print_turn(state)
        await _confirm_loop(graph, session_id, bearer)


if __name__ == "__main__":
    asyncio.run(_main())
