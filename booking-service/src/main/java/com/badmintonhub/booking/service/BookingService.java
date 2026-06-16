package com.badmintonhub.booking.service;

import com.badmintonhub.booking.dto.request.CreateBookingRequest;
import com.badmintonhub.booking.dto.response.BookingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.UUID;

public interface BookingService {

    /** Create a PENDING booking order, locking + snapshotting every selected slot atomically. */
    BookingResponse create(CreateBookingRequest req, UUID userId);

    /** Fetch one order — owner or STAFF/ADMIN only. */
    BookingResponse getById(UUID id, UUID actorId, Collection<String> actorRoles);

    /**
     * Claim a PENDING order for payment (payment-service handshake): validates it is still payable
     * (PENDING + owner/STAFF), re-anchors the hold window to {@code now + holdMinutes}, and returns the
     * authoritative order (its {@code totalPrice} is the amount to charge). 409 if the order is no longer PENDING.
     */
    BookingResponse beginPayment(UUID id, UUID actorId, Collection<String> actorRoles);

    /** List orders — caller's own, or all when STAFF/ADMIN. */
    Page<BookingResponse> list(UUID actorId, Collection<String> actorRoles, Pageable pageable);

    /** Cancel an order (owner or STAFF/ADMIN); computes the refund and releases the slots. */
    BookingResponse cancel(UUID id, UUID actorId, Collection<String> actorRoles, String reason);
}
