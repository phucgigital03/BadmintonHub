package com.badmintonhub.test;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Foundation smoke test: proves Docker + Testcontainers + the failsafe wiring actually work, without
 * needing a Spring Boot app (common-test has none). Runs on {@code mvn verify} (suffix {@code *IT}).
 */
class ContainersSmokeIT {

    @Test
    void testcontainers_startPostgresAndRedis_bothRunning() {
        try (PostgreSQLContainer<?> postgres =
                     new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));
             GenericContainer<?> redis =
                     new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {

            postgres.start();
            redis.start();

            assertThat(postgres.isRunning()).isTrue();
            assertThat(redis.isRunning()).isTrue();
        }
    }
}
