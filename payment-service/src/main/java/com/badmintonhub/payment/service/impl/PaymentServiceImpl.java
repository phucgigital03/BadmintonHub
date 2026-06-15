package com.badmintonhub.payment.service.impl;

import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.ForbiddenException;
import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.payment.client.BookingServiceClient;
import com.badmintonhub.payment.client.dto.BookingView;
import com.badmintonhub.payment.dto.request.InitiatePaymentRequest;
import com.badmintonhub.payment.dto.request.RefundRequest;
import com.badmintonhub.payment.dto.response.PaymentResponse;
import com.badmintonhub.payment.entity.BankAccount;
import com.badmintonhub.payment.entity.ManualRefund;
import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.PaymentProof;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.entity.enums.PaymentType;
import com.badmintonhub.payment.messaging.PaymentOutboxWriter;
import com.badmintonhub.payment.repository.BankAccountRepository;
import com.badmintonhub.payment.repository.ManualRefundRepository;
import com.badmintonhub.payment.repository.PaymentProofRepository;
import com.badmintonhub.payment.repository.PaymentRepository;
import com.badmintonhub.payment.service.CloudinaryService;
import com.badmintonhub.payment.service.PaymentCountdownService;
import com.badmintonhub.payment.service.PaymentService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProofRepository paymentProofRepository;
    private final ManualRefundRepository manualRefundRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CloudinaryService cloudinaryService;
    private final PaymentCountdownService countdownService;
    private final PaymentOutboxWriter outboxWriter;
    private final BookingServiceClient bookingServiceClient;

    /** A payment still occupying its booking — a second initiate must reuse it, not create a duplicate. */
    private static final List<PaymentStatus> ACTIVE_STATUSES =
            List.of(PaymentStatus.PENDING, PaymentStatus.PROOF_SUBMITTED);

    /** Countdown / hold window. Non-final so it stays out of the Lombok constructor. */
    @Value("${app.payment.expire-minutes:10}")
    private long expireMinutes;

    @Override
    @Transactional
    public PaymentResponse initiate(InitiatePaymentRequest req, UUID userId) {
        boolean forBooking = req.paymentType() == PaymentType.BOOKING && req.bookingId() != null;

        // Idempotency (double-click / page reload / back button): if this booking already has an active
        // payment, return that one instead of opening a second. Skips the begin-payment hop so the existing
        // payment's countdown stays aligned with the booking hold set when it was first opened.
        if (forBooking) {
            Optional<PaymentResponse> existing = findActiveBookingPayment(req.bookingId(), userId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // For a court booking, booking-service is the gatekeeper: it validates the order is still payable
        // + owned by the caller, re-anchors the hold, and returns the AUTHORITATIVE amount. Fail closed —
        // never create a payment for a dead/foreign booking, and never trust the client-sent amount.
        BigDecimal amount = req.amount();
        if (forBooking) {
            amount = beginBookingPayment(req.bookingId()).totalPrice();
        }

        BankAccount bank = bankAccountRepository.findFirstByIsActiveTrue()
                .orElseThrow(() -> new ConflictException("NO_BANK_ACCOUNT",
                        "Chưa cấu hình tài khoản nhận tiền"));

        Payment p = new Payment();
        p.setUserId(userId);
        p.setBankAccount(bank);
        p.setAmount(amount);
        p.setPaymentType(req.paymentType());
        p.setBookingId(req.bookingId());
        p.setMatchId(req.matchId());
        p.setEnrollmentId(req.enrollmentId());
        p.setStatus(PaymentStatus.PENDING);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expireMinutes);
        p.setExpiresAt(expiresAt);

        // flush forces the INSERT so the bigserial order_code is assigned and read back before we respond.
        // The partial unique index (one active payment per booking) is the backstop for a genuine
        // concurrent race the query above can miss — the loser is turned into a friendly 409.
        try {
            paymentRepository.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            log.warn("initiate lost a concurrent race for booking {} — active payment already exists",
                    req.bookingId());
            throw new ConflictException("PAYMENT_ALREADY_INITIATED",
                    "Đơn này vừa được khởi tạo thanh toán, vui lòng tải lại");
        }

        countdownService.start(p.getId(), expiresAt, Duration.ofMinutes(expireMinutes));

        log.info("Payment {} (#{}) initiated: user={} type={} amount={} expiresAt={}",
                p.getId(), p.getOrderCode(), userId, req.paymentType(), amount, expiresAt);
        return toResponse(p);
    }

    /**
     * Returns the existing active payment for a booking, if any — guarding ownership (only the payer who
     * opened it, never another user, may reuse it). Empty means no active payment, so a new one is opened.
     */
    private Optional<PaymentResponse> findActiveBookingPayment(UUID bookingId, UUID userId) {
        return paymentRepository
                .findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(bookingId, ACTIVE_STATUSES)
                .map(p -> {
                    if (!p.getUserId().equals(userId)) {
                        throw new ForbiddenException("FORBIDDEN",
                                "Đơn này đang được thanh toán bởi người khác");
                    }
                    log.info("initiate: reusing active payment {} (#{}) for booking {}",
                            p.getId(), p.getOrderCode(), bookingId);
                    return toResponse(p);
                });
    }

    /**
     * Begin-payment handshake with booking-service (token forwarded): asserts the order is still PENDING +
     * owned by the caller and re-anchors its hold, returning the authoritative order (its {@code totalPrice}
     * is the amount to charge). Throws {@code BOOKING_NOT_PAYABLE} (4xx) when the order can't be paid, or
     * {@code BOOKING_SERVICE_UNAVAILABLE} when it couldn't be verified. {@code initiate} lets both propagate
     * (fail closed — no payment for a dead order); {@code submitProof} catches them to keep the user's proof
     * and decide whether to pause the hold or flag a refund (it never discards a paid user's evidence).
     */
    private BookingView beginBookingPayment(UUID bookingId) {
        try {
            return bookingServiceClient.beginPayment(bookingId);
        } catch (FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                log.warn("begin-payment rejected for booking {} (HTTP {}): {}",
                        bookingId, e.status(), e.getMessage());
                throw new ConflictException("BOOKING_NOT_PAYABLE",
                        "Đơn không thể thanh toán (đã huỷ/hết hạn hoặc không thuộc bạn)");
            }
            log.warn("begin-payment failed for booking {} (HTTP {}): {}", bookingId, e.status(), e.getMessage());
            throw new ConflictException("BOOKING_SERVICE_UNAVAILABLE",
                    "Không xác thực được đơn đặt, vui lòng thử lại");
        } catch (Exception e) {
            log.warn("begin-payment call errored for booking {}: {}", bookingId, e.getMessage());
            throw new ConflictException("BOOKING_SERVICE_UNAVAILABLE",
                    "Không xác thực được đơn đặt, vui lòng thử lại");
        }
    }

    @Override
    @Transactional
    public PaymentResponse submitProof(UUID id, MultipartFile file, UUID actorId, Collection<String> actorRoles) {
        Payment p = findOr404(id);
        requireOwnerOrPrivileged(p, actorId, actorRoles);

        if (p.getStatus() != PaymentStatus.PENDING && p.getStatus() != PaymentStatus.PROOF_SUBMITTED) {
            throw new ConflictException("INVALID_STATE",
                    "Thanh toán ở trạng thái " + p.getStatus() + " không thể nộp chứng từ");
        }

        // Money-safe: the user may have ALREADY transferred before uploading, so we NEVER discard a proof —
        // always store it first, then reconcile with booking-service.
        String url = cloudinaryService.uploadProof(file);

        PaymentProof proof = new PaymentProof();
        proof.setPayment(p);
        proof.setImageUrl(url);
        proof.setUploadedBy(actorId);
        proof.setUploadedAt(LocalDateTime.now());
        paymentProofRepository.save(proof);

        p.setStatus(PaymentStatus.PROOF_SUBMITTED);

        boolean flaggedForRefund = false;
        if (p.getPaymentType() == PaymentType.BOOKING && p.getBookingId() != null) {
            try {
                // booking still PENDING → re-anchor its hold + tell it to pause auto-cancel.
                beginBookingPayment(p.getBookingId());
                outboxWriter.writeProofSubmitted(p);
            } catch (ConflictException e) {
                if ("BOOKING_NOT_PAYABLE".equals(e.getCode())) {
                    // Booking already cancelled but the money may have moved — keep the proof and flag it for a
                    // manual refund (STAFF checks the bank: refund if it arrived, reject if the proof is bogus).
                    // We do NOT confirm a dead booking and we do NOT throw away the user's evidence.
                    p.setRefundRequired(true);
                    p.setRefundRequiredReason("BOOKING_CANCELLED");
                    flaggedForRefund = true;
                    log.warn("Proof for already-cancelled booking {} (payment {}) → kept + flagged refundRequired",
                            p.getBookingId(), p.getId());
                } else {
                    // Couldn't verify booking (service down) — keep the proof and still notify booking (it pauses
                    // only if alive; the confirm-time orphan guard backstops a dead one).
                    outboxWriter.writeProofSubmitted(p);
                    log.warn("Booking {} unverifiable on proof (payment {}): {} — proof kept",
                            p.getBookingId(), p.getId(), e.getMessage());
                }
            }
        } else {
            outboxWriter.writeProofSubmitted(p); // match/event payment — normal
        }

        paymentRepository.save(p);

        log.info("Payment {} proof submitted by {} → PROOF_SUBMITTED{}",
                id, actorId, flaggedForRefund ? " (refund-required: booking cancelled)" : "");
        return toResponse(p);
    }

    @Override
    @Transactional
    public PaymentResponse confirm(UUID id, UUID staffId) {
        Payment p = findForUpdateOr404(id);
        if (p.getStatus() != PaymentStatus.PROOF_SUBMITTED) {
            throw new ConflictException("INVALID_STATE",
                    "Chỉ xác nhận được thanh toán đã nộp chứng từ (hiện: " + p.getStatus() + ")");
        }
        p.setStatus(PaymentStatus.CONFIRMED);
        p.setConfirmedBy(staffId);
        p.setConfirmedAt(LocalDateTime.now());
        markLatestProofReviewed(p, staffId, null);
        paymentRepository.save(p);
        outboxWriter.writeConfirmed(p);

        log.info("Payment {} CONFIRMED by {} (type={}, amount={})",
                id, staffId, p.getPaymentType(), p.getAmount());
        return toResponse(p);
    }

    @Override
    @Transactional
    public PaymentResponse reject(UUID id, String reason, UUID staffId) {
        Payment p = findForUpdateOr404(id);
        if (p.getStatus() != PaymentStatus.PENDING && p.getStatus() != PaymentStatus.PROOF_SUBMITTED) {
            throw new ConflictException("INVALID_STATE",
                    "Không thể từ chối thanh toán ở trạng thái " + p.getStatus());
        }
        p.setStatus(PaymentStatus.EXPIRED);
        p.setRejectReason(reason);
        p.setRefundRequired(false); // rejecting means no money to return (bogus proof / nothing arrived)
        markLatestProofReviewed(p, staffId, reason);
        paymentRepository.save(p);
        outboxWriter.writeExpired(p);

        log.info("Payment {} REJECTED by {} (reason={}) → EXPIRED", id, staffId, reason);
        return toResponse(p);
    }

    @Override
    @Transactional
    public PaymentResponse refund(UUID id, RefundRequest req, UUID staffId) {
        Payment p = findForUpdateOr404(id);
        // Refundable when CONFIRMED, or when flagged for refund while still PROOF_SUBMITTED (user transferred
        // for a booking that had already been cancelled — money arrived but we never confirmed a dead order).
        boolean refundable = p.getStatus() == PaymentStatus.CONFIRMED
                || (p.getStatus() == PaymentStatus.PROOF_SUBMITTED && p.isRefundRequired());
        if (!refundable) {
            throw new ConflictException("INVALID_STATE",
                    "Chỉ hoàn tiền cho thanh toán đã xác nhận hoặc đơn đã huỷ cần hoàn (hiện: " + p.getStatus() + ")");
        }

        // Money-safe cap: never refund more than was actually paid (fat-finger / abuse guard). The DTO
        // already enforces amount > 0; here we bound it from above by the captured amount.
        if (req.amount().compareTo(p.getAmount()) > 0) {
            throw new ConflictException("REFUND_EXCEEDS_PAID",
                    "Số tiền hoàn (" + req.amount() + ") vượt quá số đã thanh toán (" + p.getAmount() + ")");
        }

        ManualRefund refund = new ManualRefund();
        refund.setPayment(p);
        refund.setAmount(req.amount());
        refund.setToBankName(req.toBankName());
        refund.setToAccountNumber(req.toAccountNumber());
        refund.setToAccountName(req.toAccountName());
        refund.setRefundNote(req.refundNote());
        refund.setProcessedBy(staffId);
        refund.setProcessedAt(LocalDateTime.now());
        manualRefundRepository.save(refund);

        p.setStatus(PaymentStatus.REFUNDED);
        p.setRefundAmount(req.amount());
        p.setRefundRequired(false); // refund executed — clear the STAFF action flag
        paymentRepository.save(p);
        outboxWriter.writeRefundProcessed(p);

        log.info("Payment {} REFUNDED by {} amount={}", id, staffId, req.amount());
        return toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getById(UUID id, UUID actorId, Collection<String> actorRoles) {
        Payment p = findOr404(id);
        requireOwnerOrPrivileged(p, actorId, actorRoles);
        return toResponse(p);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listMine(UUID userId, Pageable pageable) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listRefundRequired(Pageable pageable) {
        return paymentRepository.findByRefundRequiredTrueOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> listPendingReview(Pageable pageable) {
        return paymentRepository.findByStatusOrderByCreatedAtAsc(PaymentStatus.PROOF_SUBMITTED, pageable)
                .map(this::toResponse);
    }

    // ---- helpers ----

    private Payment findOr404(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Không tìm thấy thanh toán"));
    }

    /** Row-locked variant for the STAFF state transitions (confirm / reject / refund) — see repo Javadoc. */
    private Payment findForUpdateOr404(UUID id) {
        return paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Không tìm thấy thanh toán"));
    }

    /** Sets review fields on the most recent proof, if any (reject from PENDING may have none). */
    private void markLatestProofReviewed(Payment p, UUID staffId, String note) {
        paymentProofRepository.findByPayment_IdOrderByUploadedAtDesc(p.getId()).stream().findFirst()
                .ifPresent(proof -> {
                    proof.setReviewedBy(staffId);
                    proof.setReviewedAt(LocalDateTime.now());
                    proof.setReviewNote(note);
                    paymentProofRepository.save(proof);
                });
    }

    private void requireOwnerOrPrivileged(Payment p, UUID actorId, Collection<String> roles) {
        if (!isPrivileged(roles) && !p.getUserId().equals(actorId)) {
            throw new ForbiddenException("FORBIDDEN", "Bạn không có quyền với thanh toán này");
        }
    }

    private boolean isPrivileged(Collection<String> roles) {
        return roles.contains("ROLE_STAFF") || roles.contains("ROLE_ADMIN");
    }

    private PaymentResponse toResponse(Payment p) {
        BankAccount bank = p.getBankAccount();
        return new PaymentResponse(
                p.getId(),
                "#" + p.getOrderCode(),
                p.getPaymentType(),
                p.getStatus(),
                p.getAmount(),
                p.getRefundAmount(),
                p.getBookingId(),
                p.getMatchId(),
                p.getEnrollmentId(),
                p.getUserId(),
                p.getExpiresAt(),
                bank.getBankName(),
                bank.getAccountNumber(),
                bank.getAccountName(),
                bank.getQrImageUrl(),
                p.getCreatedAt(),
                p.isRefundRequired(),
                p.getRefundRequiredAmount());
    }
}
