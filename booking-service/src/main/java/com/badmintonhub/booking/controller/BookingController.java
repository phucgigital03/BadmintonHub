package com.badmintonhub.booking.controller;

import com.badmintonhub.booking.dto.request.CancelBookingRequest;
import com.badmintonhub.booking.dto.request.CreateBookingRequest;
import com.badmintonhub.booking.dto.response.BookingResponse;
import com.badmintonhub.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.UUID;

/**
 * Booking orders. Creation requires a verified email (Never-Violate #10) via the {@code EMAIL_VERIFIED}
 * authority that {@code JwtAuthFilter} derives from the token. Read/cancel are owner-or-STAFF/ADMIN
 * (enforced in the service once the order is loaded, since ownership isn't a path variable).
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','COACH','STAFF','ADMIN') and hasAuthority('EMAIL_VERIFIED')")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest req,
                                                  Authentication auth) {
        BookingResponse created = bookingService.create(req, UUID.fromString(auth.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingResponse> getById(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(bookingService.getById(id, UUID.fromString(auth.getName()), roles(auth)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<BookingResponse>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(bookingService.list(UUID.fromString(auth.getName()), roles(auth), pageable));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookingResponse> cancel(@PathVariable UUID id,
                                                  @RequestBody(required = false) @Valid CancelBookingRequest req,
                                                  Authentication auth) {
        String reason = (req != null) ? req.reason() : null;
        return ResponseEntity.ok(bookingService.cancel(id, UUID.fromString(auth.getName()), roles(auth), reason));
    }

    private static Collection<String> roles(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
