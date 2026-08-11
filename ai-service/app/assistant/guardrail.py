"""Deterministic money guardrails (§0.3 rule 5, §7) — pure CODE, never the LLM.

Every check here is a plain function over typed data so it can be unit-tested in isolation.
The graph's guardrail node calls these before any WRITE; a failure never reaches
create_booking_hold. The final authority is still the DB (`booking_items.slot_id` UNIQUE)
and the target services' own RBAC/EMAIL_VERIFIED — this layer exists so the agent refuses
early and explains itself, not to replace server enforcement.
"""

from __future__ import annotations

from dataclasses import dataclass

from langsmith import traceable

from app.assistant.models import BookingIntent, ProposedBooking
from app.tools.schemas import AvailableSlot, BookingResponse


@dataclass(frozen=True)
class GuardrailFailure:
    reason: str  # not_confirmed | email_unverified | missing_contact | over_budget | slot_lost
    message: str  # Vietnamese, shown to the user as-is


@traceable(name="guardrail.check_email_verified", run_type="chain")
def check_email_verified(claims: dict) -> GuardrailFailure | None:
    if bool(claims.get("email_verified", False)):
        return None
    return GuardrailFailure(
        "email_unverified",
        "Tài khoản của bạn chưa xác thực email nên chưa thể giữ chỗ. "
        "Vui lòng kiểm tra hộp thư và bấm link xác thực, hoặc chọn 'Gặp nhân viên hỗ trợ'.",
    )


@traceable(name="guardrail.check_contact", run_type="chain")
def check_contact(proposal: ProposedBooking) -> GuardrailFailure | None:
    if proposal.customer_name and proposal.customer_phone:
        return None
    return GuardrailFailure(
        "missing_contact",
        "Mình cần tên và số điện thoại liên hệ để giữ chỗ. "
        "Bạn vui lòng cung cấp giúp mình nhé.",
    )


@traceable(name="guardrail.check_budget", run_type="chain")
def check_budget(proposal: ProposedBooking, intent: BookingIntent) -> GuardrailFailure | None:
    if intent.budget_max is None or proposal.total_price <= intent.budget_max:
        return None
    return GuardrailFailure(
        "over_budget",
        f"Đề xuất này {int(proposal.total_price):,}đ — vượt ngân sách "
        f"{intent.budget_max:,}đ bạn đưa ra, nên mình không giữ chỗ. "
        "Bạn muốn nới ngân sách hay đổi khung giờ?",
    )


@traceable(name="guardrail.check_slots_still_available", run_type="chain")
def check_slots_still_available(
    proposal: ProposedBooking, fresh_grid: list[AvailableSlot]
) -> GuardrailFailure | None:
    """Re-check against a grid fetched *now* — every proposed cell must still be AVAILABLE."""
    available_ids = {s.slot_id for s in fresh_grid}
    lost = [i for i in proposal.items if i.slot_id not in available_ids]
    if not lost:
        return None
    return GuardrailFailure(
        "slot_lost",
        "Một số ô trong đề xuất vừa có người khác giữ mất — để mình tìm phương án khác nhé.",
    )


@traceable(name="guardrail.find_reusable_pending", run_type="chain")
def find_reusable_pending(
    proposal: ProposedBooking, bookings: list[BookingResponse]
) -> BookingResponse | None:
    """Idempotency (§7): a PENDING booking of this user already holding any proposed slot.

    Confirm pressed twice (or hold created but payment failed last time) must not create a
    second hold — reuse the existing one and go straight to payment (initiate is itself
    idempotent server-side).
    """
    proposed_ids = {i.slot_id for i in proposal.items}
    for b in bookings:
        if b.status != "PENDING":
            continue
        if any(item.slot_id in proposed_ids for item in b.items):
            return b
    return None
