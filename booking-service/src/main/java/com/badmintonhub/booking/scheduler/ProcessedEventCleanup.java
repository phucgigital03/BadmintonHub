package com.badmintonhub.booking.scheduler;

import com.badmintonhub.booking.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Purges idempotency-guard rows older than 7 days (resilience.md / database.md). The {@code processed_events}
 * table is populated by the Kafka consumer wired in Day 8; this keeps it bounded once that lands.
 */
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
        log.debug("Purged processed_events older than {}", cutoff);
    }
}
