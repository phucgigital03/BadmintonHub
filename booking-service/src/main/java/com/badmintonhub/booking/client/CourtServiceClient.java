package com.badmintonhub.booking.client;

import com.badmintonhub.booking.client.dto.ClubGridView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Calls court-service via Eureka ({@code lb://court-service}) — no hardcoded URL (Never-Violate #3).
 * The day grid is a single call that returns every selected slot's price/time/status, so booking-service
 * snapshots prices from one round-trip instead of N per-slot lookups.
 */
@FeignClient(name = "court-service")
public interface CourtServiceClient {

    @GetMapping("/api/clubs/{clubId}/slots")
    ClubGridView getGrid(@PathVariable("clubId") UUID clubId,
                         @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam(value = "sport", required = false) String sport);
}
