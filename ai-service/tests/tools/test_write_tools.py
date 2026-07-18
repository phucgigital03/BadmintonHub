"""WRITE tools — correct body shape, no-amount on initiate, 403 not swallowed."""

import json
from datetime import date

import httpx
import pytest
import respx

from app.tools import booking_tools, schemas
from app.tools.http_client import ToolError
from tests.conftest import TEST_BEARER
from tests.tools import _helpers as h


@respx.mock
async def test_create_booking_hold_sends_items_and_no_sport():
    route = respx.post(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(201, json=h.booking(status="PENDING"))
    )
    items = [schemas.BookingItemInput(court_id=h.COURT_ID, slot_id=h.SLOT_ID)]
    booking = await booking_tools.create_booking_hold(
        h.CLUB_ID, date(2026, 8, 1), items, "Nguyễn Văn A", "0901234567"
    )

    assert booking.status == "PENDING"
    body = json.loads(route.calls.last.request.content)
    assert body["clubId"] == h.CLUB_ID
    assert body["date"] == "2026-08-01"
    assert body["items"] == [{"courtId": h.COURT_ID, "slotId": h.SLOT_ID}]
    assert "sport" not in body  # CreateBookingRequest has no sport field
    assert route.calls.last.request.headers["Authorization"] == f"Bearer {TEST_BEARER}"


@respx.mock
async def test_create_booking_hold_403_email_not_verified_raises_toolerror():
    respx.post(f"{h.BASE}/api/bookings").mock(
        return_value=httpx.Response(
            403,
            json={"code": "ACCESS_DENIED", "message": "Email chưa xác minh", "timestamp": "2026-07-18T10:00:00"},
        )
    )
    with pytest.raises(ToolError) as exc:
        await booking_tools.create_booking_hold(
            h.CLUB_ID, date(2026, 8, 1),
            [schemas.BookingItemInput(court_id=h.COURT_ID, slot_id=h.SLOT_ID)],
            "A", "0901234567",
        )
    assert exc.value.status_code == 403
    assert exc.value.code == "ACCESS_DENIED"


@respx.mock
async def test_initiate_payment_omits_amount():
    route = respx.post(f"{h.BASE}/api/payments/initiate").mock(
        return_value=httpx.Response(201, json=h.payment())
    )
    payment = await booking_tools.initiate_payment(h.BOOKING_ID)

    assert payment.order_code == "#184"
    assert str(payment.amount) == "60000"
    body = json.loads(route.calls.last.request.content)
    assert body == {"paymentType": "BOOKING", "bookingId": h.BOOKING_ID}
    assert "amount" not in body  # derived server-side, never sent


@respx.mock
async def test_cancel_booking_returns_cancelled():
    route = respx.post(f"{h.BASE}/api/bookings/{h.BOOKING_ID}/cancel").mock(
        return_value=httpx.Response(200, json=h.booking(status="CANCELLED"))
    )
    booking = await booking_tools.cancel_booking(h.BOOKING_ID, reason="Đổi lịch")

    assert booking.status == "CANCELLED"
    assert booking.items == []
    body = json.loads(route.calls.last.request.content)
    assert body == {"reason": "Đổi lịch"}


@respx.mock
async def test_initiate_payment_409_not_payable_raises():
    respx.post(f"{h.BASE}/api/payments/initiate").mock(
        return_value=httpx.Response(
            409, json={"code": "BOOKING_NOT_PAYABLE", "message": "Đơn không thể thanh toán", "timestamp": "x"}
        )
    )
    with pytest.raises(ToolError) as exc:
        await booking_tools.initiate_payment(h.BOOKING_ID)
    assert exc.value.status_code == 409
    assert exc.value.code == "BOOKING_NOT_PAYABLE"
