package com.badmintonhub.court.scheduler;

import com.badmintonhub.court.service.SlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Nightly slot auto-generation (resilience.md "Slot Auto-Generation"): at midnight, ensure every
 * active court has 30-min AVAILABLE slots (05:00–22:00) for the next 30 days. Idempotent per (court, date).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotGenerationScheduler {

    private final SlotService slotService;

    @Scheduled(cron = "0 0 0 * * *")
    public void generateUpcomingSlots() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusDays(30);
        int created = slotService.generateForAllActiveCourts(from, to);
        log.info("Slot auto-generation: created {} new slots for {}..{}", created, from, to);
    }
}
