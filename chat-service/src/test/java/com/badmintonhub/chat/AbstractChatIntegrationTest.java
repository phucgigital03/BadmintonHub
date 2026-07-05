package com.badmintonhub.chat;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for chat {@code *IT} tests. The shared {@code common-test} {@code AbstractIntegrationTest} wires a
 * PostgreSQL datasource (which chat-service excludes) and ships no Mongo container, so chat needs its own
 * base: real MongoDB + Redis (Testcontainers singletons), plus {@code jwt.secret} and disabled Eureka —
 * the property pattern is copied from payment-service's ITs. {@code ChatIndexInitializer} runs on context
 * startup, so the unique / partial-unique indexes exist for these tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractChatIntegrationTest {

    protected static final MongoDBContainer MONGO;
    protected static final GenericContainer<?> REDIS;

    static {
        MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        MONGO.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void infraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("chat_db"));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // application.yml has no default for jwt.secret (fail-fast in prod) — supply one for the test context.
        registry.add("jwt.secret", () -> "test-jwt-secret-at-least-32-bytes-long-0123456789");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
    }
}
