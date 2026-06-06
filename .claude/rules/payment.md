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
  → INSERT manual_refunds
  → payments.status = REFUNDED, payments.refund_amount = amount
  → Kafka: payment.refund.processed → user notified

No automated refund. STAFF physically executes the bank transfer, then records it.
```

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
