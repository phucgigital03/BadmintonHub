package com.badmintonhub.escrow.messaging;

import com.badmintonhub.escrow.entity.OutboxEvent;
import com.badmintonhub.escrow.entity.enums.OutboxStatus;
import com.badmintonhub.escrow.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Publishes the transactional Outbox to Kafka. Polls PENDING rows oldest-first, blocks on the send
 * acknowledgement, and only marks a row SENT once the broker confirmed it — a failed send leaves the row
 * PENDING for the next cycle (at-least-once delivery). A daily job purges old SENT rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMsgKey(), event.getPayload())
                        .get(5, TimeUnit.SECONDS); // block so we only mark SENT on a confirmed publish
                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
            } catch (Exception e) {
                // Broker unavailable / send failed → leave PENDING, retry next cycle. Don't fail the batch.
                log.warn("Outbox publish failed for event {} (topic {}), will retry: {}",
                        event.getId(), event.getTopic(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *") // 2am daily
    @Transactional
    public void purgeSent() {
        outboxRepository.deleteByStatusAndSentAtBefore(OutboxStatus.SENT, LocalDateTime.now().minusDays(7));
    }
}
