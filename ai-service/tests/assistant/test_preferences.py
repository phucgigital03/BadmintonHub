"""L2 personalization (Day 4): derive from history · fill gaps without override · learn after booking."""

import uuid
from datetime import date, datetime, time
from decimal import Decimal

import httpx
import respx

from app.assistant import preferences as prefs_mod
from app.assistant.graph import build_graph, resume_confirm, run_turn
from app.assistant.models import BookingIntent, ConfirmDecision
from app.assistant.nodes import AssistantNodes
from app.tools import schemas
from tests.assistant._fakes import FakeModel, FakePreferenceStore
from tests.assistant.test_interrupt_flow import _mock_reads
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h

CLUB_A = uuid.UUID("aaaaaaaa-0000-0000-0000-000000000001")
CLUB_B = uuid.UUID("aaaaaaaa-0000-0000-0000-000000000002")
USER = "11111111-1111-1111-1111-111111111111"


def _bk(club, hour, price):
    return schemas.BookingResponse(
        id=uuid.uuid4(),
        club_id=club,
        status="CONFIRMED",
        earliest_start_time=datetime(2026, 8, 1, hour, 0),
        total_price=Decimal(price),
    )


def test_derive_from_bookings_infers_club_time_budget():
    snap = prefs_mod.derive_from_bookings(
        [_bk(CLUB_A, 18, "120000"), _bk(CLUB_A, 18, "160000"), _bk(CLUB_B, 7, "80000")]
    )
    assert snap.preferred_club_id == CLUB_A  # most frequent
    assert snap.preferred_time_window == "18:00-20:00"  # most common start hour → 2h block
    assert snap.budget_max == 120000  # median of 120k/160k/80k
    assert snap.preferred_sport is None  # NOT inferable from booking history (§5)


async def test_memory_load_fills_gaps_from_preferences():
    snap = prefs_mod.PreferenceSnapshot(
        preferred_club_id=uuid.UUID(h.CLUB_ID),
        preferred_sport="PICKLEBALL",
        preferred_time_window="18:00-20:00",
        budget_max=200000,
    )
    nodes = AssistantNodes(None, prefs_store=FakePreferenceStore({USER: snap}))
    intent = BookingIntent(date=date(2026, 8, 1), club_id=uuid.UUID(h.CLUB_ID))
    state = {"intent": intent, "user_id": USER, "default_contact": {"name": "x", "phone": "y"}}

    update = await nodes.memory_load(state)  # club+contact preset → no HTTP

    filled = update["intent"]
    assert filled.sport == "PICKLEBALL"
    assert filled.time_from == time(18, 0) and filled.time_to == time(20, 0)
    assert filled.budget_max == 200000
    assert update["personalization_note"]


async def test_memory_load_does_not_override_stated_criteria():
    snap = prefs_mod.PreferenceSnapshot(preferred_sport="PICKLEBALL", budget_max=200000)
    nodes = AssistantNodes(None, prefs_store=FakePreferenceStore({USER: snap}))
    intent = BookingIntent(date=date(2026, 8, 1), club_id=uuid.UUID(h.CLUB_ID), sport="BADMINTON")
    state = {"intent": intent, "user_id": USER, "default_contact": {"name": "x", "phone": "y"}}

    update = await nodes.memory_load(state)

    assert update["intent"].sport == "BADMINTON"  # explicit choice preserved


@respx.mock
async def test_memory_load_derives_and_upserts_when_no_stored_prefs():
    respx.get(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(200, json=h.booking_page())
    )
    store = FakePreferenceStore()
    nodes = AssistantNodes(None, prefs_store=store)
    intent = BookingIntent(date=date(2026, 8, 1), club_id=uuid.UUID(h.CLUB_ID))
    state = {"intent": intent, "user_id": USER, "default_contact": {"name": "x"}}

    await nodes.memory_load(state)

    assert USER in store._data  # derived from history, then upserted


@respx.mock
async def test_preferences_learned_after_successful_booking():
    _mock_reads()  # GET bookings (PENDING) + GET slots (stable grid)
    respx.post(f"{h.BASE}/api/bookings").mock(return_value=httpx.Response(201, json=h.booking()))
    respx.post(f"{h.BASE}/api/payments/initiate").mock(
        return_value=httpx.Response(200, json=h.payment())
    )
    store = FakePreferenceStore()
    graph = build_graph(
        FakeModel(BookingIntent(sport="PICKLEBALL", date=date(2026, 8, 1))), prefs_store=store
    )

    # explicit budget so the derived (history) budget doesn't shrink the proposal below it
    await run_turn(
        graph, session_id="p1", bearer=TEST_BEARER, text="đặt pickleball 18-20h dưới 500k", user_id=USER
    )
    await resume_confirm(
        graph,
        session_id="p1",
        bearer=TEST_BEARER,
        decision=ConfirmDecision(confirmed=True),
        claims={"sub": USER, "email_verified": True},
    )

    # sport is captured from the intent at booking time (history alone can't give it)
    assert USER in store._data
    assert store._data[USER].preferred_sport == "PICKLEBALL"
