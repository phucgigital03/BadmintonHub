"""Pure unit tests for the deterministic money guardrails (§0.3 rule 5, §7) — no LLM, no HTTP."""

import uuid
from datetime import date, time
from decimal import Decimal

from app.assistant import guardrail as checks
from app.assistant.models import BookingIntent, ProposedBooking
from app.tools.schemas import AvailableSlot, BookingItemInput, BookingResponse

CLUB = uuid.UUID("aaaaaaaa-0000-0000-0000-000000000001")
COURT = uuid.UUID("cccccccc-0000-0000-0000-000000000001")
S1 = uuid.UUID("dddddddd-0000-0000-0000-000000000001")
S2 = uuid.UUID("dddddddd-0000-0000-0000-000000000002")


def proposal(price="160000", name="Nguyễn Văn A", phone="0901234567", slot_ids=(S1, S2)):
    return ProposedBooking(
        club_id=CLUB,
        date=date(2026, 8, 1),
        items=[BookingItemInput(court_id=COURT, slot_id=s) for s in slot_ids],
        total_price=Decimal(price),
        customer_name=name,
        customer_phone=phone,
    )


def cell(slot_id, start="18:00:00"):
    return AvailableSlot(
        court_id=COURT, court_number="Sân 1", slot_id=slot_id,
        start_time=time.fromisoformat(start), end_time=time(20, 0), price=Decimal("40000"),
    )


def booking_with(slot_id, status="PENDING"):
    return BookingResponse.model_validate({
        "id": str(uuid.uuid4()), "clubId": str(CLUB), "status": status,
        "items": [{"id": str(uuid.uuid4()), "courtId": str(COURT), "slotId": str(slot_id)}],
    })


# --- email verified ---------------------------------------------------------------

def test_email_verified_true_passes():
    assert checks.check_email_verified({"email_verified": True}) is None


def test_email_unverified_blocks():
    failure = checks.check_email_verified({"email_verified": False})
    assert failure is not None and failure.reason == "email_unverified"


# --- contact ----------------------------------------------------------------------

def test_contact_present_passes():
    assert checks.check_contact(proposal()) is None


def test_contact_missing_phone_blocks():
    failure = checks.check_contact(proposal(phone=None))
    assert failure is not None and failure.reason == "missing_contact"


# --- budget -----------------------------------------------------------------------

def test_budget_within_passes():
    intent = BookingIntent(budget_max=200_000)
    assert checks.check_budget(proposal("160000"), intent) is None


def test_budget_none_passes():
    assert checks.check_budget(proposal("160000"), BookingIntent()) is None


def test_budget_exceeded_blocks():
    intent = BookingIntent(budget_max=100_000)
    failure = checks.check_budget(proposal("160000"), intent)
    assert failure is not None and failure.reason == "over_budget"
    assert "vượt ngân sách" in failure.message


# --- grid re-check ----------------------------------------------------------------

def test_all_slots_still_available_passes():
    assert checks.check_slots_still_available(proposal(), [cell(S1), cell(S2)]) is None


def test_lost_slot_blocks():
    failure = checks.check_slots_still_available(proposal(), [cell(S1)])  # S2 gone
    assert failure is not None and failure.reason == "slot_lost"


# --- idempotent confirm (reuse own PENDING hold) ----------------------------------

def test_pending_overlap_is_reused():
    existing = booking_with(S1, status="PENDING")
    assert checks.find_reusable_pending(proposal(), [existing]) is existing


def test_confirmed_overlap_not_reused():
    assert checks.find_reusable_pending(proposal(), [booking_with(S1, status="CONFIRMED")]) is None


def test_pending_disjoint_not_reused():
    other = booking_with(uuid.uuid4(), status="PENDING")
    assert checks.find_reusable_pending(proposal(), [other]) is None
