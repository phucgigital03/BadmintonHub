package com.badmintonhub.escrow.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Kafka consumer wiring for the escrow ledger. Manual-ack so a message is only committed after the
 * escrow mutation + idempotency row commit. On failure: retry 2s → 4s → 8s (3 attempts) then route to
 * {@code {topic}.DLT} for manual replay (Never-Violate #7). {@code ConsumerFactory}/{@code KafkaTemplate}
 * are Spring Boot-autoconfigured from {@code spring.kafka} (String key/value).
 *
 * <p>🔴 <b>Known gap — "manual replay" above has no one watching.</b> Routing to {@code .DLT} is only
 * half the mechanism: booking-service, payment-service and court-service each run a
 * {@code *DeadLetterMonitor} that consumes those DLT topics, logs ERROR with the payload, and bumps a
 * Micrometer counter an alert watches. Escrow has none. A message that dies here is therefore
 * <b>completely silent</b> — the ledger mutation never applied, so money held in escrow is not
 * reimbursed to the Host, not settled to the court owner, or not refunded, and nobody is told. Not
 * urgent yet only because escrow is asleep until Day 11 (nothing produces {@code match.*} today), but
 * it must be closed before escrow runs for real. See CLAUDE.md → Việc tiếp theo #4.</p>
 *
 * <p>When that monitor is written, register its counter in the <b>constructor</b>, not lazily inside
 * the handler. The alert is {@code increase(<metric>[5m]) > 0}; a series first created on the first
 * dead letter appears already at 1, leaving no step for {@code increase()} to see, so the very first
 * message is missed — exactly when money is stuck. Measured on a live cluster 2026-08-13, and it is
 * why both booking-service and payment-service had to be patched (both needed {@code .tag("topic", …)},
 * which is what pushed their {@code register()} call inside the handler).</p>
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> manualAckListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
        return factory;
    }

    private DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0); // 2s, 4s, 8s
        backOff.setMaxAttempts(3);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
