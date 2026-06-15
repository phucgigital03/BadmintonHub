package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Row-locked load (SELECT … FOR UPDATE) for the STAFF state transitions (confirm / reject / refund).
     * Serialises the read-check-write so two concurrent refund calls can't both insert a ManualRefund
     * (= two real bank transfers); the loser blocks, re-reads REFUNDED/CONFIRMED, and the status guard 409s.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    /** A user's own payment history, newest first. */
    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Stale unpaid payments for the expiry scheduler (only PENDING — PROOF_SUBMITTED awaits STAFF). */
    List<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime cutoff);

    /** Confirmed payments flagged for a manual refund (orphaned by a cancelled booking), newest first. */
    Page<Payment> findByRefundRequiredTrueOrderByCreatedAtDesc(Pageable pageable);

    /** Payments awaiting STAFF review by status (e.g. PROOF_SUBMITTED), oldest first (FIFO work queue). */
    Page<Payment> findByStatusOrderByCreatedAtAsc(PaymentStatus status, Pageable pageable);

    /**
     * SLA monitor: payments stuck in a status past a cutoff on their last change ({@code updated_at}) —
     * e.g. PROOF_SUBMITTED a payer transferred for but STAFF hasn't reviewed (money sitting in limbo).
     */
    long countByStatusAndUpdatedAtBefore(PaymentStatus status, LocalDateTime cutoff);

    /**
     * The current active (PENDING / PROOF_SUBMITTED) payment for a booking, if any. Used by
     * {@code initiate} to stay idempotent — a second initiate for the same booking returns this one
     * instead of creating a duplicate.
     */
    Optional<Payment> findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(
            UUID bookingId, Collection<PaymentStatus> statuses);
}
