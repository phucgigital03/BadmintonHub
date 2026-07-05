package com.badmintonhub.chat;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for chat MockMvc {@code *IT} tests. Real MongoDB + Redis (shared {@link ChatTestContainers}); the
 * shared {@code common-test} base wires a PostgreSQL datasource (which chat excludes) and ships no Mongo,
 * so chat needs its own. {@code ChatIndexInitializer} runs on context startup, so the unique / partial-unique
 * indexes exist for these tests. WebSocket ITs that need a real server port use {@link ChatTestContainers}
 * directly with {@code RANDOM_PORT} instead of extending this.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractChatIntegrationTest {

    @DynamicPropertySource
    static void infraProperties(DynamicPropertyRegistry registry) {
        ChatTestContainers.props(registry);
    }
}
