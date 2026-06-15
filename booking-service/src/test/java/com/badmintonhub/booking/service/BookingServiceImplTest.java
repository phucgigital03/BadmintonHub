package com.badmintonhub.booking.service;

import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.entity.enums.CustomerType;
import com.badmintonhub.booking.messaging.OutboxWriter;
import com.badmintonhub.booking.repository.BookingItemRepository;
import com.badmintonhub.booking.repository.BookingRepository;
import com.badmintonhub.booking.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cancel → refund-required emission rule (FIX #1): only a paid (CONFIRMED) order
 * cancelled with a non-zero tier asks payment-service for a refund; an unpaid (PENDING) cancel does not.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    // Only the collaborators cancel() actually touches are mocked. slotLockService / courtServiceClient
    // (used only by create()) are left null — @InjectMocks passes null for unmocked constructor params.
    @Mock BookingRepository bookingRepository;
    @Mock BookingItemRepository bookingItemRepository;
    @Mock OutboxWriter outboxWriter;

    @InjectMocks BookingServiceImpl service;

    private Booking booking(BookingStatus status, LocalDateTime earliestStart) {
        Booking b = new Booking();
        b.setId(UUID.randomUUID());
        b.setUserId(UUID.randomUUID());
        b.setStatus(status);
        b.setTotalPrice(new BigDecimal("100000"));
        b.setEarliestStartTime(earliestStart);
        b.setCustomerType(CustomerType.WALK_IN);
        b.setBookingDate(LocalDate.now());
        return b;
    }

    @Test
    void cancel_confirmedBookingMoreThan24h_emitsRefundRequiredFullAmount() {
        Booking b = booking(BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(48)); // >24h → 100%
        when(bookingRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));
        when(bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(b.getId())).thenReturn(List.of());

        service.cancel(b.getId(), b.getUserId(), List.of(), "user changed mind");

        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getRefundAmount()).isEqualByComparingTo("100000");
        verify(outboxWriter).writeSlotReleased(eq(b.getId()), any());
        verify(outboxWriter).writeRefundRequired(eq(b.getId()), eq(new BigDecimal("100000.00")), eq("BOOKING_CANCELLED_BY_USER"));
    }

    @Test
    void cancel_pendingBooking_doesNotEmitRefundRequired() {
        Booking b = booking(BookingStatus.PENDING, LocalDateTime.now().plusHours(48));
        when(bookingRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));
        when(bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(b.getId())).thenReturn(List.of());

        service.cancel(b.getId(), b.getUserId(), List.of(), "user changed mind");

        assertThat(b.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.getRefundAmount()).isEqualByComparingTo("0"); // unpaid → nothing to refund
        verify(outboxWriter).writeSlotReleased(eq(b.getId()), any());
        verify(outboxWriter, never()).writeRefundRequired(any(), any(), any());
    }
}
