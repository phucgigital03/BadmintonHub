package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** A user's own payment history, newest first. */
    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Stale unpaid payments for the expiry scheduler (only PENDING — PROOF_SUBMITTED awaits STAFF). */
    List<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime cutoff);

    /** Confirmed payments flagged for a manual refund (orphaned by a cancelled booking), newest first. */
    Page<Payment> findByRefundRequiredTrueOrderByCreatedAtDesc(Pageable pageable);

    /** Payments awaiting STAFF review by status (e.g. PROOF_SUBMITTED), oldest first (FIFO work queue). */
    Page<Payment> findByStatusOrderByCreatedAtAsc(PaymentStatus status, Pageable pageable);

    /**
     * The current active (PENDING / PROOF_SUBMITTED) payment for a booking, if any. Used by
     * {@code initiate} to stay idempotent — a second initiate for the same booking returns this one
     * instead of creating a duplicate.
     */
    Optional<Payment> findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(
            UUID bookingId, Collection<PaymentStatus> statuses);
}
