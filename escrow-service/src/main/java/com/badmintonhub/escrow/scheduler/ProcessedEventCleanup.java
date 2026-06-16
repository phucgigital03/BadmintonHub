package com.badmintonhub.escrow.scheduler;

import com.badmintonhub.escrow.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Keeps the consumer idempotency-guard table bounded (resilience.md / database.md). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessedEventCleanup {

    private final ProcessedEventRepository processedEventRepository;

    @Scheduled(cron = "0 0 3 * * *") // 3am daily
    @Transactional
    public void purgeOldProcessedEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        processedEventRepository.deleteByProcessedAtBefore(cutoff);
        log.debug("Purged escrow processed_events older than {}", cutoff);
    }
}
