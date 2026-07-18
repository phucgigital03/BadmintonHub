"""Pydantic v2 models mirroring the real service DTOs.

The Java services serialize record fields as camelCase JSON (totalPrice, courtNumber, ...).
We keep pythonic snake_case fields and parse via a camelCase alias generator, so
`SlotResponse.start_time` reads the JSON key `startTime`, etc. `extra="ignore"` means new
upstream fields never break parsing.
"""

from __future__ import annotations

from datetime import date as date_
from datetime import datetime, time
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )


class Page[T](CamelModel):
    """Spring Data Page<T> JSON shape."""

    content: list[T] = []
    total_elements: int = 0
    total_pages: int = 0
    number: int = 0
    size: int = 0
    first: bool = True
    last: bool = True


# --- court-service ---------------------------------------------------------------


class ClubResponse(CamelModel):
    id: UUID
    name: str
    address: str | None = None
    district: str | None = None
    latitude: Decimal | None = None
    longitude: Decimal | None = None
    images: list[str] = []
    rating: Decimal | None = None
    total_reviews: int = 0
    is_active: bool = True


class SlotResponse(CamelModel):
    id: UUID  # NOTE: the grid slot id JSON key is "id" (a booking item uses "slotId")
    date: date_
    start_time: time
    end_time: time
    status: str  # AVAILABLE | RESERVED | BLOCKED | EVENT
    price: Decimal | None = None
    event_id: UUID | None = None
    booking_id: UUID | None = None
    match_id: UUID | None = None
    enrollment_id: UUID | None = None


class CourtSlotsResponse(CamelModel):
    id: UUID
    court_number: str
    sport: str
    type: str
    slots: list[SlotResponse] = []


class ClubGridResponse(CamelModel):
    date: date_
    day_type: str
    courts: list[CourtSlotsResponse] = []


class PricingRuleResponse(CamelModel):
    id: UUID
    sport: str
    day_type: str
    start_time: time
    end_time: time
    customer_type: str
    price_per_hour: Decimal


class AvailableSlot(CamelModel):
    """Tool-side normalized AVAILABLE cell — carries both ids create_booking_hold needs."""

    court_id: UUID
    court_number: str
    slot_id: UUID
    start_time: time
    end_time: time
    price: Decimal | None = None


# --- booking-service -------------------------------------------------------------


class BookingItemInput(CamelModel):
    court_id: UUID
    slot_id: UUID


class BookingItemResponse(CamelModel):
    id: UUID
    court_id: UUID
    slot_id: UUID
    court_name: str | None = None
    start_time: time | None = None
    end_time: time | None = None
    price: Decimal | None = None


class BookingResponse(CamelModel):
    id: UUID
    user_id: UUID | None = None
    club_id: UUID | None = None
    customer_name: str | None = None
    customer_phone: str | None = None
    note: str | None = None
    customer_type: str | None = None
    booking_date: date_ | None = None
    total_price: Decimal | None = None
    refund_amount: Decimal | None = None
    status: str  # PENDING | CONFIRMED | COMPLETED | CANCELLED
    earliest_start_time: datetime | None = None
    hold_expires_at: datetime | None = None
    cancel_reason: str | None = None
    created_at: datetime | None = None
    items: list[BookingItemResponse] = []


# --- payment-service -------------------------------------------------------------


class PaymentResponse(CamelModel):
    id: UUID
    order_code: str | None = None
    payment_type: str | None = None
    status: str  # PENDING | PROOF_SUBMITTED | CONFIRMED | EXPIRED | REFUNDED
    amount: Decimal | None = None
    refund_amount: Decimal | None = None
    booking_id: UUID | None = None
    match_id: UUID | None = None
    enrollment_id: UUID | None = None
    user_id: UUID | None = None
    expires_at: datetime | None = None
    bank_name: str | None = None
    account_number: str | None = None
    account_name: str | None = None
    qr_image_url: str | None = None
    created_at: datetime | None = None
    refund_required: bool = False
    refund_required_amount: Decimal | None = None
    refund_required_reason: str | None = None
