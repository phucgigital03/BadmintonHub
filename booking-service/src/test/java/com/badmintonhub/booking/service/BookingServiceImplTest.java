package com.badmintonhub.booking.service;

import com.badmintonhub.booking.client.CourtServiceClient;
import com.badmintonhub.booking.client.dto.ClubGridView;
import com.badmintonhub.booking.client.dto.CourtSlotsView;
import com.badmintonhub.booking.client.dto.SlotView;
import com.badmintonhub.booking.dto.request.CreateBookingRequest;
import com.badmintonhub.booking.dto.response.BookingResponse;
import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.entity.enums.CustomerType;
import com.badmintonhub.booking.messaging.OutboxWriter;
import com.badmintonhub.booking.repository.BookingItemRepository;
import com.badmintonhub.booking.repository.BookingRepository;
import com.badmintonhub.booking.service.impl.BookingServiceImpl;
import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the money-/inventory-safety rules in {@link BookingServiceImpl}:
 * <ul>
 *   <li>cancel → refund-required emission (FIX #1): only a paid (CONFIRMED) order cancelled with a
 *       non-zero tier asks payment-service for a refund.</li>
 *   <li>create() Tier-1 guards: per-user rate limit (429), past-time slot rejection, and the happy
 *       path persisting header+items+outbox (Feign now runs outside the transaction).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock BookingRepository bookingRepository;
    @Mock BookingItemRepository bookingItemRepository;
    @Mock RedisSlotLockService slotLockService;
    @Mock BookingRateLimiter rateLimiter;
    @Mock CourtServiceClient courtServiceClient;
    @Mock OutboxWriter outboxWriter;
    @Mock PlatformTransactionManager txManager;

    @InjectMocks BookingServiceImpl service;

    @BeforeEach
    void injectValueFields() {
        // @Value fields aren't populated without a Spring context — set the window + ceiling explicitly.
        ReflectionTestUtils.setField(service, "holdMinutes", 10L);
        ReflectionTestUtils.setField(service, "maxHoldMinutes", 30L);
    }

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

    private CreateBookingRequest request(UUID clubId, LocalDate date, UUID courtId, UUID slotId) {
        return new CreateBookingRequest(clubId, date, "Nguyen Van A", "0901234567", null,
                List.of(new CreateBookingRequest.Item(courtId, slotId)));
    }

    /** One-court, one-slot grid (slot at 05:00–05:30) with the given status/price. */
    private ClubGridView grid(UUID courtId, UUID slotId, String status, BigDecimal price) {
        SlotView slot = new SlotView(slotId, LocalDate.now(), LocalTime.of(5, 0), LocalTime.of(5, 30), status, price);
        CourtSlotsView court = new CourtSlotsView(courtId, "Sân 1", "PICKLEBALL", "INDOOR", List.of(slot));
        return new ClubGridView(LocalDate.now(), "WEEKDAY", List.of(court));
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

    @Test
    void create_overRateLimit_throwsTooManyRequests() {
        UUID userId = UUID.randomUUID();
        CreateBookingRequest req = request(UUID.randomUUID(), LocalDate.now().plusDays(1),
                UUID.randomUUID(), UUID.randomUUID());
        doThrow(new TooManyRequestsException("RATE_LIMITED", "slow down")).when(rateLimiter).check(userId);

        assertThatThrownBy(() -> service.create(req, userId))
                .isInstanceOf(TooManyRequestsException.class);

        // Throttled before any I/O — no Feign, no write.
        verifyNoInteractions(courtServiceClient);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_pastTimeSlot_throwsConflict() {
        UUID userId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1); // any slot time is already past
        CreateBookingRequest req = request(clubId, yesterday, courtId, slotId);

        when(bookingItemRepository.existsBySlotIdIn(any())).thenReturn(false);
        when(courtServiceClient.getGrid(eq(clubId), eq(yesterday), isNull()))
                .thenReturn(grid(courtId, slotId, "AVAILABLE", new BigDecimal("40000")));

        assertThatThrownBy(() -> service.create(req, userId))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("SLOT_IN_PAST");

        // Rejected during validation — locks never acquired, nothing persisted.
        verify(slotLockService, never()).acquireAll(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void create_happyPath_persistsAndWritesOutbox() {
        UUID userId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1); // future → past-time guard passes
        CreateBookingRequest req = request(clubId, tomorrow, courtId, slotId);

        when(bookingItemRepository.existsBySlotIdIn(any())).thenReturn(false);
        when(courtServiceClient.getGrid(eq(clubId), eq(tomorrow), isNull()))
                .thenReturn(grid(courtId, slotId, "AVAILABLE", new BigDecimal("40000")));
        when(slotLockService.acquireAll(any())).thenReturn(List.of("lock:slot:" + slotId));

        BookingResponse resp = service.create(req, userId);

        assertThat(resp).isNotNull();
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(saved.getValue().getTotalPrice()).isEqualByComparingTo("40000");
        verify(bookingItemRepository).saveAllAndFlush(any());
        verify(outboxWriter).writeSlotHeld(any(), any(), any());
        verify(slotLockService).releaseAll(any()); // released in finally
    }

    @Test
    void beginPayment_holdExhausted_throwsConflict() {
        Booking b = booking(BookingStatus.PENDING, LocalDateTime.now().plusHours(48));
        // Held longer than the 30-min ceiling already → re-anchor must be refused.
        ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now().minusMinutes(40));
        LocalDateTime holdBefore = LocalDateTime.now().plusMinutes(1);
        b.setHoldExpiresAt(holdBefore);
        when(bookingRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.beginPayment(b.getId(), b.getUserId(), List.of()))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("BOOKING_HOLD_EXHAUSTED");

        assertThat(b.getHoldExpiresAt()).isEqualTo(holdBefore); // hold NOT extended
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void beginPayment_withinCeiling_reAnchorsCapped() {
        Booking b = booking(BookingStatus.PENDING, LocalDateTime.now().plusHours(48));
        ReflectionTestUtils.setField(b, "createdAt", LocalDateTime.now().minusMinutes(2)); // fresh order
        when(bookingRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));
        when(bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(b.getId())).thenReturn(List.of());

        service.beginPayment(b.getId(), b.getUserId(), List.of());

        assertThat(b.getHoldExpiresAt()).isAfter(LocalDateTime.now());                  // re-anchored forward
        assertThat(b.getHoldExpiresAt()).isBeforeOrEqualTo(b.getCreatedAt().plusMinutes(30)); // never past ceiling
        verify(bookingRepository).save(b);
    }
}
