package com.badmintonhub.payment.client;

import com.badmintonhub.payment.client.dto.BookingView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/**
 * Calls booking-service via Eureka ({@code lb://booking-service}) — no hardcoded URL (Never-Violate #3).
 * Used in the payment-initiation handshake: booking-service validates the order is still payable + owned
 * by the caller (it re-validates the forwarded user token), re-anchors the hold, and returns the
 * authoritative order. A 4xx means the order can't be paid → payment-service fails closed.
 */
@FeignClient(name = "booking-service")
public interface BookingServiceClient {

    @PostMapping("/api/bookings/{id}/begin-payment")
    BookingView beginPayment(@PathVariable("id") UUID id);
}
