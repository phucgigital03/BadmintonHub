package com.badmintonhub.booking.repository;

import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    /** Unpaid bookings whose hold window has elapsed — to auto-cancel + release their slots. */
    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, LocalDateTime cutoff);

    /**
     * Row-locked load (SELECT … FOR UPDATE) used by every status transition (cancel / begin-payment /
     * the payment.* Kafka handlers / hold-expiry). Serialises the read-check-write so a concurrent
     * cancel and payment-confirm can't lose-update each other — without this, money can be received
     * for a booking that ends CANCELLED with no refund flag.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);
}
