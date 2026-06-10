package com.badmintonhub.court.controller;

import com.badmintonhub.court.dto.request.CreateCourtRequest;
import com.badmintonhub.court.dto.response.CourtResponse;
import com.badmintonhub.court.dto.response.SlotResponse;
import com.badmintonhub.court.service.CourtService;
import com.badmintonhub.court.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Court (Sân) endpoints. Adding courts and blocking slots is STAFF/ADMIN-only; the single-slot
 * lookup is public (used by booking/matchmaking-service via Feign).
 */
@RestController
@RequestMapping("/api/courts")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;
    private final SlotService slotService;

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<CourtResponse> addCourt(@RequestParam UUID clubId,
                                                  @Valid @RequestBody CreateCourtRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courtService.addCourt(clubId, req));
    }

    @GetMapping("/{courtId}/slots/{slotId}")
    public ResponseEntity<SlotResponse> getSlot(@PathVariable UUID courtId, @PathVariable UUID slotId) {
        return ResponseEntity.ok(slotService.getSlot(courtId, slotId));
    }

    @PatchMapping("/slots/{slotId}/block")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<Void> block(@PathVariable UUID slotId, Authentication auth) {
        courtService.blockSlot(slotId, UUID.fromString(auth.getName()));
        return ResponseEntity.noContent().build();
    }
}
