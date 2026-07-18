"""The 7 booking tools (§5). Signatures match the MCP contract; each forwards the user JWT.

READ:  search_clubs, get_day_grid, get_pricing, get_user_bookings
WRITE: create_booking_hold, initiate_payment, cancel_booking  (agent runs only after human confirm)
"""

from __future__ import annotations

from datetime import date as date_
from uuid import UUID

from app.tools import schemas
from app.tools.http_client import request

# --- READ --------------------------------------------------------------------------


async def search_clubs(
    district: str | None = None,
    sport: str | None = None,
    lat: float | None = None,
    lng: float | None = None,
    radius: float | None = None,
) -> schemas.Page[schemas.ClubResponse]:
    """Find clubs (venues). Single-club today, so filters are near-degenerate. READ-only."""
    params = {
        k: v
        for k, v in {
            "district": district,
            "sport": sport,
            "lat": lat,
            "lng": lng,
            "radius": radius,
        }.items()
        if v is not None
    }
    resp = await request("GET", "/api/clubs", params=params)
    return schemas.Page[schemas.ClubResponse].model_validate(resp.json())


async def get_day_grid(
    club_id: UUID, date: date_, sport: str | None = None
) -> list[schemas.AvailableSlot]:
    """Real-time slot grid for a club/day → only the AVAILABLE 30-min cells. READ-only."""
    params: dict[str, str] = {"date": date.isoformat()}
    if sport:
        params["sport"] = sport
    resp = await request("GET", f"/api/clubs/{club_id}/slots", params=params)
    grid = schemas.ClubGridResponse.model_validate(resp.json())
    available: list[schemas.AvailableSlot] = []
    for court in grid.courts:
        for slot in court.slots:
            if slot.status == "AVAILABLE":
                available.append(
                    schemas.AvailableSlot(
                        court_id=court.id,
                        court_number=court.court_number,
                        slot_id=slot.id,
                        start_time=slot.start_time,
                        end_time=slot.end_time,
                        price=slot.price,
                    )
                )
    return available


async def get_pricing(club_id: UUID, sport: str) -> list[schemas.PricingRuleResponse]:
    """Pricing rules for a club + sport (sport is required upstream). READ-only."""
    resp = await request("GET", f"/api/clubs/{club_id}/pricing", params={"sport": sport})
    return [schemas.PricingRuleResponse.model_validate(x) for x in resp.json()]


async def get_user_bookings() -> schemas.Page[schemas.BookingResponse]:
    """The current user's bookings (server filters owner-or-STAFF). READ-only."""
    resp = await request("GET", "/api/bookings")
    return schemas.Page[schemas.BookingResponse].model_validate(resp.json())


# --- WRITE (human-in-the-loop only) ------------------------------------------------


async def create_booking_hold(
    club_id: UUID,
    date: date_,
    items: list[schemas.BookingItemInput],
    name: str,
    phone: str,
    note: str | None = None,
) -> schemas.BookingResponse:
    """Create a PENDING booking hold (10 min). Requires EMAIL_VERIFIED. WRITE — confirm first."""
    normalized = [schemas.BookingItemInput.model_validate(i) for i in items]
    body: dict = {
        "clubId": str(club_id),
        "date": date.isoformat(),
        "customerName": name,
        "customerPhone": phone,
        "items": [
            {"courtId": str(i.court_id), "slotId": str(i.slot_id)} for i in normalized
        ],
    }
    if note:
        body["note"] = note
    resp = await request("POST", "/api/bookings", json=body)
    return schemas.BookingResponse.model_validate(resp.json())


async def initiate_payment(booking_id: UUID) -> schemas.PaymentResponse:
    """Start Bank-QR payment for a booking. amount is derived server-side (never sent). WRITE."""
    body = {"paymentType": "BOOKING", "bookingId": str(booking_id)}
    resp = await request("POST", "/api/payments/initiate", json=body)
    return schemas.PaymentResponse.model_validate(resp.json())


async def cancel_booking(
    booking_id: UUID, reason: str | None = None
) -> schemas.BookingResponse:
    """Cancel a booking. WRITE — confirm first."""
    body = {"reason": reason} if reason else None
    resp = await request("POST", f"/api/bookings/{booking_id}/cancel", json=body)
    return schemas.BookingResponse.model_validate(resp.json())
