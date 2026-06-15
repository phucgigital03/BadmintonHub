package com.badmintonhub.payment.service;

import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.payment.dto.request.InitiatePaymentRequest;
import com.badmintonhub.payment.dto.request.RefundRequest;
import com.badmintonhub.payment.dto.response.PaymentResponse;
import com.badmintonhub.payment.entity.BankAccount;
import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.entity.enums.PaymentType;
import com.badmintonhub.payment.messaging.PaymentOutboxWriter;
import com.badmintonhub.payment.repository.BankAccountRepository;
import com.badmintonhub.payment.repository.ManualRefundRepository;
import com.badmintonhub.payment.repository.PaymentProofRepository;
import com.badmintonhub.payment.repository.PaymentRepository;
import com.badmintonhub.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the money-safe guards on the STAFF refund path (FIX #3 — cap + state guard). */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentProofRepository paymentProofRepository;
    @Mock ManualRefundRepository manualRefundRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock CloudinaryService cloudinaryService;
    @Mock PaymentCountdownService countdownService;
    @Mock PaymentOutboxWriter outboxWriter;
    @Mock com.badmintonhub.payment.client.BookingServiceClient bookingServiceClient;

    @InjectMocks PaymentServiceImpl service;

    private Payment confirmedPayment(BigDecimal amount) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setUserId(UUID.randomUUID());
        p.setStatus(PaymentStatus.CONFIRMED);
        p.setAmount(amount);
        p.setBankAccount(new BankAccount());
        return p;
    }

    private RefundRequest refundReq(BigDecimal amount) {
        return new RefundRequest(amount, "VCB", "123", "CLB An Binh", "test");
    }

    @Test
    void refund_amountExceedsPaid_throwsConflictAndDoesNotTransfer() {
        Payment p = confirmedPayment(new BigDecimal("100000"));
        when(paymentRepository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.refund(p.getId(), refundReq(new BigDecimal("150000")), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCode()).isEqualTo("REFUND_EXCEEDS_PAID"));

        verify(manualRefundRepository, never()).save(any());
        verify(outboxWriter, never()).writeRefundProcessed(any());
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRMED); // unchanged
    }

    @Test
    void refund_amountWithinPaid_marksRefundedAndRecordsTransfer() {
        Payment p = confirmedPayment(new BigDecimal("100000"));
        when(paymentRepository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        service.refund(p.getId(), refundReq(new BigDecimal("100000")), UUID.randomUUID());

        verify(manualRefundRepository).save(any());
        verify(outboxWriter).writeRefundProcessed(p);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(p.getRefundAmount()).isEqualByComparingTo("100000");
        assertThat(p.isRefundRequired()).isFalse();
    }

    @Test
    void refund_pendingPayment_throwsInvalidState() {
        Payment p = confirmedPayment(new BigDecimal("100000"));
        p.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.refund(p.getId(), refundReq(new BigDecimal("50000")), UUID.randomUUID()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCode()).isEqualTo("INVALID_STATE"));
        verify(manualRefundRepository, never()).save(any());
    }

    // ---- NEW-A: submitProof re-checks status under the row lock (no resurrection of a closed payment) ----

    @Test
    void submitProof_paymentRejectedUnderLockAfterUpload_throwsAndDoesNotResurrect() {
        UUID id = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        // Pre-check sees it still open (PENDING)…
        Payment open = new Payment();
        open.setId(id);
        open.setUserId(actor);
        open.setStatus(PaymentStatus.PENDING);
        open.setBankAccount(new BankAccount());
        // …but by the time we take the lock, STAFF has rejected it (EXPIRED).
        Payment rejected = new Payment();
        rejected.setId(id);
        rejected.setUserId(actor);
        rejected.setStatus(PaymentStatus.EXPIRED);
        rejected.setBankAccount(new BankAccount());

        when(paymentRepository.findById(id)).thenReturn(Optional.of(open));
        when(cloudinaryService.uploadProof(any())).thenReturn("http://img/proof.jpg");
        when(paymentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.submitProof(id, null, actor, List.of()))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCode()).isEqualTo("INVALID_STATE"));

        verify(paymentProofRepository, never()).save(any()); // proof not attached to a closed payment
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.EXPIRED); // not resurrected to PROOF_SUBMITTED
    }

    // ---- NEW-C: initiate is idempotent when the booking already has a CONFIRMED payment ----

    @Test
    void initiate_bookingAlreadyConfirmed_returnsExistingWithoutCreatingNew() {
        UUID user = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment confirmed = new Payment();
        confirmed.setId(UUID.randomUUID());
        confirmed.setUserId(user);
        confirmed.setStatus(PaymentStatus.CONFIRMED);
        confirmed.setBookingId(bookingId);
        confirmed.setAmount(new BigDecimal("100000"));
        confirmed.setBankAccount(new BankAccount());
        when(paymentRepository.findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(eq(bookingId), any()))
                .thenReturn(Optional.of(confirmed));

        InitiatePaymentRequest req =
                new InitiatePaymentRequest(PaymentType.BOOKING, new BigDecimal("100000"), bookingId, null, null);
        PaymentResponse resp = service.initiate(req, user);

        assertThat(resp.status()).isEqualTo(PaymentStatus.CONFIRMED);
        verify(bookingServiceClient, never()).beginPayment(any()); // no second handshake
        verify(paymentRepository, never()).saveAndFlush(any());    // no duplicate payment created
    }
}
