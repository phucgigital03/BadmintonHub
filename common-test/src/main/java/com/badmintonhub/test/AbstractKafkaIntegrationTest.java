package com.badmintonhub.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests that also need a real Kafka broker (booking, matchmaking,
 * payment, escrow, notification…). Adds a singleton {@link KafkaContainer} on top of the
 * PostgreSQL + Redis from {@link AbstractIntegrationTest}.
 *
 * <p>Kept separate so test classes that don't touch Kafka don't pay its ~30s startup cost.</p>
 */
public abstract class AbstractKafkaIntegrationTest extends AbstractIntegrationTest {

    protected static final KafkaContainer KAFKA;

    static {
        KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
