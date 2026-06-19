package com.badmintonhub.payment.service;

import com.badmintonhub.payment.dto.request.InitiatePaymentRequest;
import com.badmintonhub.payment.dto.request.RefundRequest;
import com.badmintonhub.payment.dto.response.PaymentProofResponse;
import com.badmintonhub.payment.dto.response.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Bank-QR payment lifecycle: initiate → submit proof → STAFF confirm/reject → manual refund. */
public interface PaymentService {

    /** Create a PENDING payment with a countdown deadline, attaching the active bank account. */
    PaymentResponse initiate(InitiatePaymentRequest req, UUID userId);

    /** Attach a transfer-screenshot proof → PROOF_SUBMITTED. Owner or STAFF/ADMIN. */
    PaymentResponse submitProof(UUID id, MultipartFile file, UUID actorId, Collection<String> actorRoles);

    /** STAFF confirms the transfer → CONFIRMED + emits the confirmation event. */
    PaymentResponse confirm(UUID id, UUID staffId);

    /** STAFF rejects the proof → EXPIRED + emits the expiry event (slot released). */
    PaymentResponse reject(UUID id, String reason, UUID staffId);

    /** STAFF records a manual bank-transfer refund → REFUNDED. */
    PaymentResponse refund(UUID id, RefundRequest req, UUID staffId);

    /** A single payment — owner or STAFF/ADMIN. */
    PaymentResponse getById(UUID id, UUID actorId, Collection<String> actorRoles);

    /** The transfer-screenshot proofs for a payment, newest first — owner or STAFF/ADMIN. */
    List<PaymentProofResponse> listProofs(UUID id, UUID actorId, Collection<String> actorRoles);

    /** The caller's own payment history, newest first. */
    Page<PaymentResponse> listMine(UUID userId, Pageable pageable);

    /** Confirmed payments awaiting a manual refund (booking cancelled after confirm). STAFF/ADMIN. */
    Page<PaymentResponse> listRefundRequired(Pageable pageable);

    /** Payments awaiting STAFF review (proof uploaded → PROOF_SUBMITTED), oldest first. STAFF/ADMIN. */
    Page<PaymentResponse> listPendingReview(Pageable pageable);
}
