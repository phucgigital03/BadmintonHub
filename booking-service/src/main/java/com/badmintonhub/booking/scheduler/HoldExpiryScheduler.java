package com.badmintonhub.booking.scheduler;

import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.BookingItem;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.messaging.OutboxWriter;
import com.badmintonhub.booking.repository.BookingItemRepository;
import com.badmintonhub.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Auto-cancels PENDING bookings whose hold window has elapsed (no payment in time) and releases their
 * slots via an Outbox {@code booking.slot.released} event. This is the timeout half of the slot-hold Saga
 * — it guarantees a held slot never stays "stuck red". (When payment-service lands in Day 8, a paid
 * booking becomes CONFIRMED and is no longer picked up here.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final OutboxWriter outboxWriter;

    @Scheduled(fixedDelay = 60_000) // every 60s
    @Transactional
    public void releaseExpiredHolds() {
        List<Booking> candidates = bookingRepository
                .findByStatusAndHoldExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now());
        if (candidates.isEmpty()) {
            return;
        }
        int released = 0;
        for (Booking candidate : candidates) {
            // Re-load row-locked + re-check under the lock: a payment.confirmed may have flipped this
            // booking to CONFIRMED (and cleared the hold) between the query and now — never cancel that.
            Booking booking = bookingRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (booking == null || booking.getStatus() != BookingStatus.PENDING
                    || booking.getHoldExpiresAt() == null
                    || booking.getHoldExpiresAt().isAfter(LocalDateTime.now())) {
                continue; // confirmed / hold paused or re-anchored — leave it alone
            }
            List<UUID> slotIds = bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(booking.getId())
                    .stream().map(BookingItem::getSlotId).toList();

            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelReason("PAYMENT_TIMEOUT");
            booking.setCancelledAt(LocalDateTime.now());
            booking.setRefundAmount(BigDecimal.ZERO); // unpaid hold — nothing to refund
            bookingRepository.save(booking);

            bookingItemRepository.deleteByBooking_Id(booking.getId());
            outboxWriter.writeSlotReleased(booking.getId(), slotIds);
            released++;
            log.info("Booking {} hold expired (deadline {}) → CANCELLED + {} slot(s) released",
                    booking.getId(), booking.getHoldExpiresAt(), slotIds.size());
        }
        log.info("HoldExpiryScheduler released {} expired hold(s)", released);
    }
}
