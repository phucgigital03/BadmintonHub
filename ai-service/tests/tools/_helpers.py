"""Shared constants + sample JSON payloads matching the real service DTOs."""

BASE = "http://gateway.test"

CLUB_ID = "aaaaaaaa-0000-0000-0000-000000000001"
COURT_ID = "cccccccc-0000-0000-0000-000000000001"
SLOT_ID = "dddddddd-0000-0000-0000-000000000001"
BOOKING_ID = "bbbbbbbb-0000-0000-0000-000000000001"
PAYMENT_ID = "eeeeeeee-0000-0000-0000-000000000001"


def club_page():
    return {
        "content": [
            {
                "id": CLUB_ID,
                "name": "An Bình Pickleball",
                "address": "123 Đường ABC",
                "district": "Quận 3",
                "latitude": "10.77",
                "longitude": "106.68",
                "images": [],
                "rating": "4.8",
                "totalReviews": 12,
                "isActive": True,
            }
        ],
        "totalElements": 1,
        "totalPages": 1,
        "number": 0,
        "size": 20,
        "first": True,
        "last": True,
    }


def grid_mixed():
    """One court, four cells: AVAILABLE, RESERVED, BLOCKED, EVENT (only 1 is bookable)."""
    return {
        "date": "2026-08-01",
        "dayType": "WEEKEND",
        "courts": [
            {
                "id": COURT_ID,
                "courtNumber": "Sân 1",
                "sport": "PICKLEBALL",
                "type": "OUTDOOR",
                "slots": [
                    {"id": SLOT_ID, "date": "2026-08-01", "startTime": "18:00:00", "endTime": "18:30:00", "status": "AVAILABLE", "price": "60000"},
                    {"id": "dddddddd-0000-0000-0000-000000000002", "date": "2026-08-01", "startTime": "18:30:00", "endTime": "19:00:00", "status": "RESERVED", "price": "60000", "bookingId": BOOKING_ID},
                    {"id": "dddddddd-0000-0000-0000-000000000003", "date": "2026-08-01", "startTime": "19:00:00", "endTime": "19:30:00", "status": "BLOCKED", "price": None},
                    {"id": "dddddddd-0000-0000-0000-000000000004", "date": "2026-08-01", "startTime": "19:30:00", "endTime": "20:00:00", "status": "EVENT", "price": "60000"},
                ],
            }
        ],
    }


def pricing_list():
    return [
        {"id": "ffffffff-0000-0000-0000-000000000001", "sport": "PICKLEBALL", "dayType": "WEEKEND", "startTime": "17:00:00", "endTime": "22:00:00", "customerType": "WALK_IN", "pricePerHour": "120000"},
    ]


def booking_page():
    return {
        "content": [booking(status="PENDING")],
        "totalElements": 1,
        "totalPages": 1,
        "number": 0,
        "size": 20,
        "first": True,
        "last": True,
    }


def booking(status="PENDING"):
    return {
        "id": BOOKING_ID,
        "userId": "11111111-1111-1111-1111-111111111111",
        "clubId": CLUB_ID,
        "customerName": "Nguyễn Văn A",
        "customerPhone": "0901234567",
        "note": None,
        "customerType": "WALK_IN",
        "bookingDate": "2026-08-01",
        "totalPrice": "60000",
        "refundAmount": None,
        "status": status,
        "earliestStartTime": "2026-08-01T18:00:00",
        "holdExpiresAt": "2026-08-01T17:10:00" if status == "PENDING" else None,
        "cancelReason": None,
        "createdAt": "2026-07-18T10:00:00",
        "items": [] if status == "CANCELLED" else [
            {"id": "99999999-0000-0000-0000-000000000001", "courtId": COURT_ID, "slotId": SLOT_ID, "courtName": "Sân 1", "startTime": "18:00:00", "endTime": "18:30:00", "price": "60000"}
        ],
    }


def payment():
    return {
        "id": PAYMENT_ID,
        "orderCode": "#184",
        "paymentType": "BOOKING",
        "status": "PENDING",
        "amount": "60000",
        "refundAmount": None,
        "bookingId": BOOKING_ID,
        "matchId": None,
        "enrollmentId": None,
        "userId": "11111111-1111-1111-1111-111111111111",
        "expiresAt": "2026-08-01T17:10:00",
        "bankName": "Vietcombank",
        "accountNumber": "0011001234567",
        "accountName": "NGUYEN VAN OWNER",
        "qrImageUrl": "https://img.vietqr.io/...",
        "createdAt": "2026-07-18T10:00:00",
        "refundRequired": False,
        "refundRequiredAmount": None,
        "refundRequiredReason": None,
    }
