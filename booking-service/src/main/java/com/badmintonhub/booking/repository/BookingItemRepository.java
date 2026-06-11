package com.badmintonhub.booking.repository;

import com.badmintonhub.booking.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {

    List<BookingItem> findByBooking_IdOrderByStartTimeAsc(UUID bookingId);

    /** Fast pre-check before the insert; the {@code uk_booking_items_slot} UNIQUE is the real backstop. */
    boolean existsBySlotIdIn(Collection<UUID> slotIds);

    /** Releases a cancelled order's slots so they can be re-booked (keeps the header for audit). */
    void deleteByBooking_Id(UUID bookingId);
}
