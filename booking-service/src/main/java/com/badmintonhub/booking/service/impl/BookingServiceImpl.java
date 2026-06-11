package com.badmintonhub.booking.service.impl;

import com.badmintonhub.booking.client.CourtServiceClient;
import com.badmintonhub.booking.client.dto.ClubGridView;
import com.badmintonhub.booking.client.dto.CourtSlotsView;
import com.badmintonhub.booking.client.dto.SlotView;
import com.badmintonhub.booking.dto.request.CreateBookingRequest;
import com.badmintonhub.booking.dto.response.BookingItemResponse;
import com.badmintonhub.booking.dto.response.BookingResponse;
import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.BookingItem;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.entity.enums.CustomerType;
import com.badmintonhub.booking.messaging.OutboxWriter;
import com.badmintonhub.booking.repository.BookingItemRepository;
import com.badmintonhub.booking.repository.BookingRepository;
import com.badmintonhub.booking.service.BookingService;
import com.badmintonhub.booking.service.RedisSlotLockService;
import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.ForbiddenException;
import com.badmintonhub.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Duration FULL_REFUND_THRESHOLD = Duration.ofHours(24);
    private static final Duration HALF_REFUND_THRESHOLD = Duration.ofHours(2);
    private static final BigDecimal HALF = new BigDecimal("0.50");

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final RedisSlotLockService slotLockService;
    private final CourtServiceClient courtServiceClient;
    private final OutboxWriter outboxWriter;

    /** PENDING hold window (= payment window). Non-final so it stays out of the Lombok constructor. */
    @Value("${app.booking.hold-minutes:10}")
    private long holdMinutes;

    @Override
    @Transactional
    public BookingResponse create(CreateBookingRequest req, UUID userId) {
        // Reject duplicate slots within the same request up front (deterministic, before any I/O).
        Set<UUID> slotIds = new LinkedHashSet<>(req.items().stream().map(CreateBookingRequest.Item::slotId).toList());
        if (slotIds.size() != req.items().size()) {
            throw new ConflictException("DUPLICATE_SLOT", "Có ô bị chọn trùng trong đơn");
        }

        // 1. Lock every selected slot (all-or-nothing; releases in finally). Fail-open if Redis is down.
        List<String> heldLocks = slotLockService.acquireAll(slotIds);
        try {
            // 2. Fast pre-check against active holds (the UNIQUE backstop below is authoritative).
            if (bookingItemRepository.existsBySlotIdIn(slotIds)) {
                throw new ConflictException("SLOT_TAKEN", "Một hoặc nhiều ô đã được đặt");
            }

            // 3. One Feign round-trip: the club's day grid → authoritative price/time/status per slot.
            Map<UUID, SlotSnapshot> grid = fetchGridSnapshots(req.clubId(), req.date());

            // 4. Validate every selected slot + build the header and line items (snapshots frozen here).
            Booking booking = new Booking();
            booking.setUserId(userId);
            booking.setClubId(req.clubId());
            booking.setCustomerName(req.customerName());
            booking.setCustomerPhone(req.customerPhone());
            booking.setNote(req.note());
            booking.setCustomerType(CustomerType.WALK_IN);
            booking.setBookingDate(req.date());
            booking.setStatus(BookingStatus.PENDING);

            BigDecimal total = BigDecimal.ZERO;
            LocalTime earliestStart = null;
            List<BookingItem> items = new java.util.ArrayList<>();

            for (CreateBookingRequest.Item reqItem : req.items()) {
                SlotSnapshot snap = grid.get(reqItem.slotId());
                if (snap == null) {
                    throw new ResourceNotFoundException("SLOT_NOT_FOUND",
                            "Ô " + reqItem.slotId() + " không tồn tại cho CLB/ngày này");
                }
                if (!reqItem.courtId().equals(snap.courtId())) {
                    throw new ConflictException("COURT_MISMATCH",
                            "Ô " + reqItem.slotId() + " không thuộc sân đã chọn");
                }
                if (!snap.available()) {
                    throw new ConflictException("SLOT_NOT_AVAILABLE",
                            "Ô " + snap.courtName() + " " + snap.startTime() + " không còn trống");
                }
                if (snap.price() == null) {
                    throw new ConflictException("PRICE_UNAVAILABLE",
                            "Chưa có bảng giá cho ô " + snap.courtName() + " " + snap.startTime());
                }

                BookingItem item = new BookingItem();
                item.setBooking(booking);
                item.setCourtId(snap.courtId());
                item.setSlotId(reqItem.slotId());
                item.setCourtName(snap.courtName());
                item.setStartTime(snap.startTime());
                item.setEndTime(snap.endTime());
                item.setPrice(snap.price());
                items.add(item);

                total = total.add(snap.price());
                if (earliestStart == null || snap.startTime().isBefore(earliestStart)) {
                    earliestStart = snap.startTime();
                }
            }

            booking.setTotalPrice(total);
            booking.setEarliestStartTime(LocalDateTime.of(req.date(), earliestStart));
            booking.setHoldExpiresAt(LocalDateTime.now().plusMinutes(holdMinutes));

            bookingRepository.save(booking);
            try {
                // saveAllAndFlush forces the INSERT now so the UNIQUE(slot_id) violation surfaces inside
                // this try (caught → 409) rather than at commit (→ 500).
                bookingItemRepository.saveAllAndFlush(items);
            } catch (DataIntegrityViolationException e) {
                throw new ConflictException("SLOT_TAKEN", "Một hoặc nhiều ô vừa được người khác đặt");
            }

            // Outbox (same tx): tell court-service to flip these slots RESERVED → grid shows them taken.
            List<UUID> slotIdList = items.stream().map(BookingItem::getSlotId).toList();
            outboxWriter.writeSlotHeld(booking.getId(), slotIdList, booking.getHoldExpiresAt());

            log.info("Booking {} created: user={} club={} items={} total={} holdExpiresAt={}",
                    booking.getId(), userId, req.clubId(), items.size(), total, booking.getHoldExpiresAt());
            return toResponse(booking, items);
        } finally {
            slotLockService.releaseAll(heldLocks);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getById(UUID id, UUID actorId, Collection<String> actorRoles) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt"));
        requireOwnerOrPrivileged(booking, actorId, actorRoles);
        return toResponse(booking, bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> list(UUID actorId, Collection<String> actorRoles, Pageable pageable) {
        Page<Booking> page = isPrivileged(actorRoles)
                ? bookingRepository.findAll(pageable)
                : bookingRepository.findByUserId(actorId, pageable);
        return page.map(b -> toResponse(b, bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(b.getId())));
    }

    @Override
    @Transactional
    public BookingResponse cancel(UUID id, UUID actorId, Collection<String> actorRoles, String reason) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt"));
        requireOwnerOrPrivileged(booking, actorId, actorRoles);

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ConflictException("INVALID_STATE",
                    "Đơn ở trạng thái " + booking.getStatus() + " không thể huỷ");
        }

        boolean wasConfirmed = booking.getStatus() == BookingStatus.CONFIRMED;
        // Capture slot ids before deleting the items — needed for the release event.
        List<UUID> slotIds = bookingItemRepository.findByBooking_IdOrderByStartTimeAsc(id)
                .stream().map(BookingItem::getSlotId).toList();

        BigDecimal refund = computeRefund(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setRefundAmount(refund);
        booking.setCancelReason(reason);
        booking.setCancelledBy(actorId);
        booking.setCancelledAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // Release the held slots so they become re-bookable; the header keeps the financial snapshot.
        bookingItemRepository.deleteByBooking_Id(id);

        // Outbox (same tx): tell court-service to flip these slots back to AVAILABLE.
        outboxWriter.writeSlotReleased(id, slotIds);

        log.info("Booking {} cancelled by {} (wasPaid={}), refund={}", id, actorId, wasConfirmed, refund);
        return toResponse(booking, List.of());
    }

    /**
     * Refund policy anchored on {@code earliest_start_time}: &gt;24h → 100%, 2–24h → 50%, &lt;2h → 0%,
     * of the <b>paid</b> amount. A PENDING (unpaid) order has nothing to refund.
     */
    private BigDecimal computeRefund(Booking booking) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return BigDecimal.ZERO; // unpaid → nothing to refund
        }
        Duration until = Duration.between(LocalDateTime.now(), booking.getEarliestStartTime());
        BigDecimal pct;
        if (until.compareTo(FULL_REFUND_THRESHOLD) > 0) {
            pct = BigDecimal.ONE;
        } else if (until.compareTo(HALF_REFUND_THRESHOLD) > 0) {
            pct = HALF;
        } else {
            pct = BigDecimal.ZERO;
        }
        return booking.getTotalPrice().multiply(pct).setScale(2, RoundingMode.HALF_UP);
    }

    /** Pull the club's day grid and index it by slotId with the owning court's id/name. */
    private Map<UUID, SlotSnapshot> fetchGridSnapshots(UUID clubId, java.time.LocalDate date) {
        ClubGridView grid;
        try {
            grid = courtServiceClient.getGrid(clubId, date, null);
        } catch (Exception e) {
            // court-service unreachable → cannot snapshot prices; fail closed (never guess a price).
            log.warn("court-service grid call failed for club={} date={}: {}", clubId, date, e.getMessage());
            throw new ConflictException("COURT_SERVICE_UNAVAILABLE",
                    "Không lấy được lịch sân, vui lòng thử lại");
        }
        Map<UUID, SlotSnapshot> byId = new HashMap<>();
        for (CourtSlotsView court : grid.courts()) {
            for (SlotView slot : court.slots()) {
                byId.put(slot.id(), new SlotSnapshot(
                        court.id(), court.courtNumber(),
                        slot.startTime(), slot.endTime(), slot.isAvailable(), slot.price()));
            }
        }
        return byId;
    }

    private void requireOwnerOrPrivileged(Booking booking, UUID actorId, Collection<String> roles) {
        if (!isPrivileged(roles) && !booking.getUserId().equals(actorId)) {
            throw new ForbiddenException("FORBIDDEN", "Bạn không có quyền với đơn đặt này");
        }
    }

    private boolean isPrivileged(Collection<String> roles) {
        return roles.contains("ROLE_STAFF") || roles.contains("ROLE_ADMIN");
    }

    private BookingResponse toResponse(Booking b, List<BookingItem> items) {
        List<BookingItemResponse> itemResponses = items.stream()
                .map(i -> new BookingItemResponse(
                        i.getId(), i.getCourtId(), i.getSlotId(), i.getCourtName(),
                        i.getStartTime(), i.getEndTime(), i.getPrice()))
                .collect(Collectors.toList());
        return new BookingResponse(
                b.getId(), b.getUserId(), b.getClubId(), b.getCustomerName(), b.getCustomerPhone(), b.getNote(),
                b.getCustomerType(), b.getBookingDate(), b.getTotalPrice(), b.getRefundAmount(), b.getStatus(),
                b.getEarliestStartTime(), b.getHoldExpiresAt(), b.getCancelReason(), b.getCreatedAt(), itemResponses);
    }

    /** Per-slot snapshot indexed from the grid (court identity + frozen time/price). */
    private record SlotSnapshot(
            UUID courtId, String courtName,
            LocalTime startTime, LocalTime endTime, boolean available, BigDecimal price) {}
}
