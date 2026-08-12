package com.badmintonhub.booking.controller;

import com.badmintonhub.booking.client.CourtServiceClient;
import com.badmintonhub.booking.entity.Booking;
import com.badmintonhub.booking.entity.BookingItem;
import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.entity.enums.CustomerType;
import com.badmintonhub.booking.repository.BookingItemRepository;
import com.badmintonhub.booking.repository.BookingRepository;
import com.badmintonhub.test.AbstractKafkaIntegrationTest;
import com.badmintonhub.test.JwtTestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer end-to-end for {@code GET /api/bookings} against real Postgres/Redis/Kafka (Testcontainers)
 * — the layer that unit tests skip. Booking-service had NO {@code *IT}, so a runtime-only fault on this
 * endpoint (the exact 500 that blocked the AI widget's booking list) never showed up in {@code mvn verify}.
 *
 * <p>This exercises the full production path: the {@code findByUserId(pageable)} query, the per-row items
 * fetch (N+1), the entity→DTO mapping, and Jackson serialization of {@code Page<BookingResponse>} — the one
 * thing unique to the list endpoint vs. create/getById. It is the regression guard for that endpoint.</p>
 */
@AutoConfigureMockMvc
// Spring Boot disables every metrics exporter inside @SpringBootTest (it injects a "test" property
// source setting management.defaults.metrics.export.enabled=false) so tests never push to a real
// backend. Without opting back in, /actuator/prometheus has no handler at all and answers 500 via the
// catch-all handler — a failure that says nothing about production. This annotation is what makes the
// scrape test below exercise the thing it claims to test.
@AutoConfigureObservability
class BookingControllerIT extends AbstractKafkaIntegrationTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // application.yml has no default for jwt.secret (fail-fast in prod) — supply one for the test context.
        r.add("jwt.secret", () -> "test-jwt-secret-at-least-32-bytes-long-0123456789");
        // No Eureka in tests — don't let the discovery client block / spam on startup.
        r.add("eureka.client.enabled", () -> "false");
        r.add("eureka.client.register-with-eureka", () -> "false");
        r.add("eureka.client.fetch-registry", () -> "false");
        // application.yml ships `health,info`; in the cluster the scrape endpoint is opened by
        // MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE in the platform ConfigMap. Mirror it so the
        // scrape test below runs against the same shape production does.
        r.add("management.endpoints.web.exposure.include", () -> "health,info,prometheus");
    }

    @MockBean CourtServiceClient courtServiceClient; // avoid resolving lb://court-service via Eureka

    @Autowired MockMvc mockMvc;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingItemRepository bookingItemRepository;
    @Value("${jwt.secret}") String jwtSecret;

    @BeforeEach
    void clean() {
        bookingItemRepository.deleteAll(); // FK to bookings — delete children first
        bookingRepository.deleteAll();
    }

    /** Persist one PENDING order (header + one 30-min line item) for {@code userId}; return its id. */
    private UUID seedBooking(UUID userId) {
        Booking b = new Booking();
        b.setUserId(userId);
        b.setClubId(UUID.randomUUID());
        b.setCustomerName("Nguyen Van A");
        b.setCustomerPhone("0900000000");
        b.setCustomerType(CustomerType.WALK_IN);
        b.setBookingDate(LocalDate.now().plusDays(1));
        b.setTotalPrice(new BigDecimal("100000.00"));
        b.setStatus(BookingStatus.PENDING);
        b.setEarliestStartTime(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(18, 0)));
        b.setHoldExpiresAt(LocalDateTime.now().plusMinutes(10));
        bookingRepository.save(b);

        BookingItem item = new BookingItem();
        item.setBooking(b);
        item.setCourtId(UUID.randomUUID());
        item.setSlotId(UUID.randomUUID());
        item.setCourtName("Sân 1");
        item.setStartTime(LocalTime.of(18, 0));
        item.setEndTime(LocalTime.of(18, 30));
        item.setPrice(new BigDecimal("100000.00"));
        bookingItemRepository.save(item);
        return b.getId();
    }

    private String userToken(UUID userId) {
        return JwtTestTokens.bearer(jwtSecret, userId.toString(), "ROLE_USER");
    }

    @Test
    void list_ownBookings_returns200WithItems() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID bookingId = seedBooking(userId);

        mockMvc.perform(get("/api/bookings").header("Authorization", userToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(bookingId.toString()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].items[0].courtName").value("Sân 1"));
    }

    @Test
    void list_withExplicitPaging_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        seedBooking(userId);

        mockMvc.perform(get("/api/bookings").param("page", "0").param("size", "20")
                        .header("Authorization", userToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void list_userWithNoBookings_returns200Empty() throws Exception {
        // Seed for someone else so the table isn't empty — this user just owns nothing.
        seedBooking(UUID.randomUUID());

        mockMvc.perform(get("/api/bookings").header("Authorization", userToken(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void list_tokenWithNonUuidSubject_returns401NotServerError() throws Exception {
        // A malformed-but-authenticated token (subject isn't a UUID) must be a clean 401, not a 500 from
        // UUID.fromString falling through to the catch-all handler.
        String badToken = JwtTestTokens.bearer(jwtSecret, "not-a-uuid", "ROLE_USER");

        mockMvc.perform(get("/api/bookings").header("Authorization", badToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PRINCIPAL"));
    }

    /**
     * Prometheus scrapes this endpoint with no credentials, so it has to answer 200 to an anonymous
     * caller. Two independent things break that, and neither surfaces anywhere else: the endpoint not
     * existing (micrometer-registry-prometheus absent from the classpath) and Spring Security rejecting
     * it (the actuator matchers are literal paths, so /actuator/prometheus used to be a 403).
     *
     * <p>Asserting on the body rather than the status alone is the point: a 200 carrying no JVM metrics
     * would mean the endpoint answered but no registry is wired, which reads as "working" on a dashboard
     * that is quietly empty. Failing here costs a test run; failing in the cluster costs a running EKS.</p>
     */
    @Test
    void prometheusEndpoint_anonymous_returns200WithMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }

}
