package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** A user's own payment history, newest first. */
    Page<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Stale unpaid payments for the expiry scheduler (only PENDING — PROOF_SUBMITTED awaits STAFF). */
    List<Payment> findByStatusAndExpiresAtBefore(PaymentStatus status, LocalDateTime cutoff);
}
