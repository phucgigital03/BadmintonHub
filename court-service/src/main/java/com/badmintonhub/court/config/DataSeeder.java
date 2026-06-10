package com.badmintonhub.court.config;

import com.badmintonhub.court.dto.request.CreateClubRequest;
import com.badmintonhub.court.dto.request.CreateCourtRequest;
import com.badmintonhub.court.dto.request.CreatePricingRuleRequest;
import com.badmintonhub.court.dto.response.ClubResponse;
import com.badmintonhub.court.entity.enums.CourtType;
import com.badmintonhub.court.entity.enums.CustomerType;
import com.badmintonhub.court.entity.enums.DayType;
import com.badmintonhub.court.entity.enums.Sport;
import com.badmintonhub.court.repository.ClubRepository;
import com.badmintonhub.court.service.ClubService;
import com.badmintonhub.court.service.CourtService;
import com.badmintonhub.court.service.PricingService;
import com.badmintonhub.court.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Idempotent demo seed mirroring the frontend's {@code mockClub} (An Bình), so the FE CourtsPage can
 * point at the real API end-to-end. Skips entirely once the demo club exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String CLUB_NAME = "An Bình Pickleball";

    private final ClubRepository clubRepository;
    private final ClubService clubService;
    private final CourtService courtService;
    private final PricingService pricingService;
    private final SlotService slotService;

    @Override
    public void run(String... args) {
        if (clubRepository.existsByName(CLUB_NAME)) {
            log.info("Demo club '{}' already present — skipping seed", CLUB_NAME);
            return;
        }

        ClubResponse club = clubService.create(new CreateClubRequest(
                CLUB_NAME,
                "12/15 Kha Vạn Cân, Kp.Bình Đường 2, P.Dĩ An, Tp.HCM",
                "Dĩ An",
                new BigDecimal("10.894600"),
                new BigDecimal("106.765400"),
                List.of()), null);
        UUID clubId = club.id();

        // Cosmetic: match the mock rating so the FE looks identical when switched to the real API.
        clubRepository.findById(clubId).ifPresent(c -> {
            c.setRating(new BigDecimal("4.80"));
            c.setTotalReviews(0);
            clubRepository.save(c);
        });

        // Courts — same layout as mockClub: Pickleball Sân 1–3, Badminton Sân 4–5.
        courtService.addCourt(clubId, new CreateCourtRequest("Sân 1", Sport.PICKLEBALL, CourtType.SYNTHETIC));
        courtService.addCourt(clubId, new CreateCourtRequest("Sân 2", Sport.PICKLEBALL, CourtType.SYNTHETIC));
        courtService.addCourt(clubId, new CreateCourtRequest("Sân 3", Sport.PICKLEBALL, CourtType.SYNTHETIC));
        courtService.addCourt(clubId, new CreateCourtRequest("Sân 4", Sport.BADMINTON, CourtType.WOOD));
        courtService.addCourt(clubId, new CreateCourtRequest("Sân 5", Sport.BADMINTON, CourtType.WOOD));

        seedPricing(clubId);

        // Include today so the grid demos immediately; idempotent per (court, date).
        int created = slotService.generateForClub(clubId, LocalDate.now(), LocalDate.now().plusDays(30));
        log.info("Seeded demo club {} (5 courts + pricing rules + {} slots)", clubId, created);
    }

    /** Three contiguous windows tiling 05:00–22:00 for each sport × day-type × customer-type. */
    private void seedPricing(UUID clubId) {
        for (Sport sport : List.of(Sport.PICKLEBALL, Sport.BADMINTON)) {
            for (DayType dayType : DayType.values()) {
                for (CustomerType customerType : CustomerType.values()) {
                    BigDecimal base = basePrice(sport, dayType, customerType);
                    addRule(clubId, sport, dayType, customerType, LocalTime.of(5, 0), LocalTime.of(10, 0), base);
                    addRule(clubId, sport, dayType, customerType, LocalTime.of(10, 0), LocalTime.of(17, 0),
                            base.add(BigDecimal.valueOf(20_000)));
                    addRule(clubId, sport, dayType, customerType, LocalTime.of(17, 0), LocalTime.of(22, 0),
                            base.add(BigDecimal.valueOf(40_000)));
                }
            }
        }
    }

    private BigDecimal basePrice(Sport sport, DayType dayType, CustomerType customerType) {
        long base = (sport == Sport.BADMINTON) ? 100_000 : 60_000; // Badminton pricier per mock
        if (dayType == DayType.WEEKEND) base += 20_000;
        if (customerType == CustomerType.FIXED) base -= 20_000;    // subscription discount
        return BigDecimal.valueOf(base);
    }

    private void addRule(UUID clubId, Sport sport, DayType dayType, CustomerType customerType,
                         LocalTime start, LocalTime end, BigDecimal pricePerHour) {
        pricingService.createRule(clubId,
                new CreatePricingRuleRequest(sport, dayType, start, end, customerType, pricePerHour));
    }
}
