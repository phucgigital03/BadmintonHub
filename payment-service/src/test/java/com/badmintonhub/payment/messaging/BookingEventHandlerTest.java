package com.badmintonhub.payment.messaging;

import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.repository.PaymentRepository;
import com.badmintonhub.payment.repository.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for the refund-required compensation consumer (FIX #1 — paid booking cancelled). */
@ExtendWith(MockitoExtension.class)
class BookingEventHandlerTest {

    @Mock PaymentRepository paymentRepository;
    @Mock ProcessedEventRepository processedEventRepository;

    private BookingEventHandler handler() {
        return new BookingEventHandler(new ObjectMapper(), paymentRepository, processedEventRepository);
    }

    @Test
    void handleRefundRequired_flagsConfirmedPaymentWithSuggestedAmount() {
        UUID bookingId = UUID.randomUUID();
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setStatus(PaymentStatus.CONFIRMED);
        String payload = "{\"eventId\":\"e1\",\"bookingId\":\"" + bookingId
                + "\",\"refundAmount\":50000,\"reason\":\"BOOKING_CANCELLED_BY_USER\"}";

        when(processedEventRepository.existsById("e1")).thenReturn(false);
        when(paymentRepository.findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(eq(bookingId), any()))
                .thenReturn(Optional.of(p));
        when(paymentRepository.findByIdForUpdate(p.getId())).thenReturn(Optional.of(p));

        handler().handleRefundRequired("e1", payload);

        assertThat(p.isRefundRequired()).isTrue();
        assertThat(p.getRefundRequiredReason()).isEqualTo("BOOKING_CANCELLED_BY_USER");
        assertThat(p.getRefundRequiredAmount()).isEqualByComparingTo("50000");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRMED); // stays CONFIRMED — STAFF refunds manually
        verify(paymentRepository).save(p);
        verify(processedEventRepository).save(any());
    }

    @Test
    void handleRefundRequired_alreadyProcessed_isNoOp() {
        when(processedEventRepository.existsById("e1")).thenReturn(true);

        handler().handleRefundRequired("e1", "{\"eventId\":\"e1\"}");

        verifyNoInteractions(paymentRepository);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void handleRefundRequired_noLivePayment_recordsProcessedAndSkips() {
        UUID bookingId = UUID.randomUUID();
        String payload = "{\"eventId\":\"e2\",\"bookingId\":\"" + bookingId
                + "\",\"refundAmount\":50000,\"reason\":\"BOOKING_CANCELLED_BY_USER\"}";
        when(processedEventRepository.existsById("e2")).thenReturn(false);
        when(paymentRepository.findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(eq(bookingId), any()))
                .thenReturn(Optional.empty());

        handler().handleRefundRequired("e2", payload);

        verify(paymentRepository, never()).save(any());
        verify(processedEventRepository).save(any()); // idempotency row still written
    }
}
