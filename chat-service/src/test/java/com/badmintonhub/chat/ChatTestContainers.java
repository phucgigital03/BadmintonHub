package com.badmintonhub.chat;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers singletons (MongoDB + Redis) for every chat {@code *IT}, started once per JVM.
 * Both the MockMvc base ({@link AbstractChatIntegrationTest}) and the real-server WebSocket IT reference
 * this holder so the module spins up exactly one Mongo and one Redis. Also forces the in-memory simple
 * broker ({@code app.broker.relay=false}) so {@code mvn verify} needs no RabbitMQ.
 */
public final class ChatTestContainers {

    public static final MongoDBContainer MONGO;
    public static final GenericContainer<?> REDIS;

    static {
        MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        MONGO.start();
        REDIS.start();
    }

    private ChatTestContainers() {
    }

    public static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("chat_db"));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // application.yml has no default for jwt.secret (fail-fast in prod) — supply one for the test context.
        registry.add("jwt.secret", () -> "test-jwt-secret-at-least-32-bytes-long-0123456789");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
        // In-memory simple broker for tests (no RabbitMQ relay).
        registry.add("app.broker.relay", () -> "false");
    }
}
