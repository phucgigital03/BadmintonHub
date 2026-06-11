package com.badmintonhub.booking.repository;

import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    /** Unpaid bookings whose hold window has elapsed — to auto-cancel + release their slots. */
    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, LocalDateTime cutoff);
}
