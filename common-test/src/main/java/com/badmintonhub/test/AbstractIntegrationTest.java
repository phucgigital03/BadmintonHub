package com.badmintonhub.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for {@code *IT} integration tests. Extend this and write {@code @Test} methods —
 * a real PostgreSQL and a real Redis (Testcontainers) are already up and wired into the Spring
 * context, so there is no per-test Testcontainers setup.
 *
 * <p>Containers use the <b>singleton</b> pattern: started once in a static block and reused across
 * every test class in the module (Testcontainers' Ryuk reaper stops them when the JVM exits). This
 * is much faster than {@code @Container} which restarts containers per class.</p>
 *
 * <p>Services that also need Kafka extend {@link AbstractKafkaIntegrationTest} instead.</p>
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void infraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
