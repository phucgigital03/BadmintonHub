---
description: Payment model for BadmintonHub — Bank QR + manual STAFF confirmation. No VNPay, no payment gateway. Apply whenever writing payment, booking, match, enrollment, or event ticket code.
alwaysApply: true
---

# Payment Model — Bank QR + Manual STAFF Confirm

**There is NO VNPay, NO third-party payment gateway.** Any suggestion to integrate VNPay or similar APIs is wrong for this project.

## Flow

```
POST /api/payments/initiate
  → INSERT payments: status=PENDING, expires_at=NOW()+10min, order_code=SERIAL (#184)
  → Redis: SET payment:countdown:{paymentId} {expiresAt} TTL 10min
  → Returns: { orderId, orderCode, bankName, accountNumber, accountName, qrImageUrl, amount, expiresAt }

Frontend shows QR screen:
  • Bank account info + QR image (from bank_accounts.qr_image_url)
  • ⚠️  Transfer {amount} VND — note: #{orderCode}
  • ⏱  Countdown timer
  • Upload zone for proof screenshot

User transfers → uploads screenshot:
POST /api/payments/{id}/proof  (multipart/form-data)
  → Cloudinary upload → INSERT payment_proofs
  → payments.status = PROOF_SUBMITTED
  → Kafka: payment.proof.submitted → notification-service → STAFF push/email

STAFF reviews in /admin/payments:
POST /api/payments/{id}/confirm  (STAFF/ADMIN only)
  → payments.status = CONFIRMED
  → Kafka: payment.host.confirmed OR payment.player.confirmed
POST /api/payments/{id}/reject  (STAFF/ADMIN only)
  → payments.status = EXPIRED → slot released, user notified

Scheduler (every 1 min):
  → PENDING payments past expires_at → EXPIRED → slot released
```

## Refund Flow (Manual Bank Transfer)

```
STAFF processes refund:
POST /api/payments/{id}/refund  (STAFF/ADMIN only)
  Body: { amount, toBankName, toAccountNumber, toAccountName, refundNote }
  → row-locked load (SELECT … FOR UPDATE) so two concurrent refunds can't both transfer
  → reject if amount > payments.amount  → 409 REFUND_EXCEEDS_PAID  (never refund more than was paid)
  → INSERT manual_refunds
  → payments.status = REFUNDED, payments.refund_amount = amount, refund_required = false
  → Kafka: payment.refund.processed → user notified

No automated refund. STAFF physically executes the bank transfer, then records it.
```

**Refund-required queue.** A payment is flagged `refund_required = true` (surfaced by
`GET /api/payments/refund-required`) when booking-service reports the money is owed back:
- `booking.payment.orphaned` — a `payment.player.confirmed` landed on an already-CANCELLED booking.
- `booking.refund.required` — a CONFIRMED (paid) booking was cancelled within the refund window; the
  event carries the policy-computed amount → stored in `payments.refund_required_amount` as the suggested
  transfer (STAFF doesn't recompute the tier). Refund is allowed from CONFIRMED, or from PROOF_SUBMITTED
  when the flag is set (user transferred for a booking that was already cancelled).

## payment_type Values

| Value | When |
|---|---|
| `BOOKING` | Court booking payment |
| `MATCH_HOST` | Host pays full court_price upfront (Prepay model) |
| `MATCH_PLAYER` | Player pays price_per_person to join match |
| `COACH_ENROLLMENT` | Student pays to enroll with coach |
| `EVENT_TICKET` | User buys event ticket |

## payments.status State Machine

```
PENDING → PROOF_SUBMITTED → CONFIRMED
                          ↘ EXPIRED   (scheduler timeout OR STAFF reject)
CONFIRMED → REFUNDED      (manual refund by STAFF)
```

## Prepay + Escrow Model (Match)

- Host pays full `court_price` → Escrow holds it (status=HOLDING)
- Each Player pays `price_per_person` → Escrow reimburses Host proportionally
- Court Owner receives `court_price` ONLY when `match.status = COMPLETED` (STAFF settles manually)
- On cancel: STAFF manually transfers refunds, records each in `manual_refunds`
