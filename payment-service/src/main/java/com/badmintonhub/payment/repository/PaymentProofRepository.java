package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.PaymentProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, UUID> {

    /** All proofs of a payment, newest upload first. */
    List<PaymentProof> findByPayment_IdOrderByUploadedAtDesc(UUID paymentId);
}
