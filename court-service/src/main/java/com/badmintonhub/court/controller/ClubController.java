package com.badmintonhub.court.controller;

import com.badmintonhub.court.dto.request.CreateClubRequest;
import com.badmintonhub.court.dto.request.CreatePricingRuleRequest;
import com.badmintonhub.court.dto.response.ClubGridResponse;
import com.badmintonhub.court.dto.response.ClubResponse;
import com.badmintonhub.court.dto.response.CourtResponse;
import com.badmintonhub.court.dto.response.PricingRuleResponse;
import com.badmintonhub.court.entity.enums.Sport;
import com.badmintonhub.court.service.ClubService;
import com.badmintonhub.court.service.CourtService;
import com.badmintonhub.court.service.PricingService;
import com.badmintonhub.court.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Venue/club endpoints. GET browse (search, detail, courts, pricing, slot grid) is public; mutations
 * require STAFF/ADMIN. The visual day-booking grid lives at {@code GET /api/clubs/{id}/slots}.
 */
@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;
    private final CourtService courtService;
    private final PricingService pricingService;
    private final SlotService slotService;

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ClubResponse> create(@Valid @RequestBody CreateClubRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clubService.create(req, UUID.fromString(auth.getName())));
    }

    @GetMapping
    public ResponseEntity<Page<ClubResponse>> search(
            @RequestParam(required = false) String district,
            @RequestParam(required = false) Sport sport,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radius,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(clubService.search(district, sport, lat, lng, radius, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(clubService.getById(id));
    }

    @GetMapping("/{id}/courts")
    public ResponseEntity<List<CourtResponse>> courts(@PathVariable UUID id) {
        return ResponseEntity.ok(courtService.listCourts(id));
    }

    @PostMapping("/{id}/pricing")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<PricingRuleResponse> createPricing(@PathVariable UUID id,
                                                             @Valid @RequestBody CreatePricingRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingService.createRule(id, req));
    }

    @GetMapping("/{id}/pricing")
    public ResponseEntity<List<PricingRuleResponse>> pricing(@PathVariable UUID id, @RequestParam Sport sport) {
        return ResponseEntity.ok(pricingService.listRules(id, sport));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<ClubGridResponse> grid(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Sport sport) {
        return ResponseEntity.ok(slotService.getGrid(id, date, sport));
    }

    @PostMapping("/{id}/generate-slots")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<Map<String, Object>> generateSlots(@PathVariable UUID id) {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusDays(30);
        int created = slotService.generateForClub(id, from, to);
        return ResponseEntity.ok(Map.of("created", created, "from", from.toString(), "to", to.toString()));
    }
}
