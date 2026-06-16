package com.badmintonhub.payment.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the partial unique index that lets a booking have at most one <em>active</em>
 * (PENDING / PROOF_SUBMITTED) payment at a time — the hard backstop for double-initiate that the
 * service-level query check can miss under a genuine concurrent race.
 *
 * <p>It must be partial: a booking may legitimately accumulate several EXPIRED payments (rejected /
 * timed out) over its life, so a plain unique constraint on {@code booking_id} would be wrong. Hibernate
 * can't express a partial / filtered unique index from annotations and the project uses {@code ddl-auto=update}
 * with no Flyway, so it is created here idempotently with {@code IF NOT EXISTS}.
 *
 * <p>{@code booking_id IS NOT NULL} scopes it to court bookings — match / event payments (null booking_id)
 * are untouched (and NULLs are distinct in a unique index anyway).
 */
@Slf4j
@Order(0) // run before DataSeeder — pure DDL, no dependency, but keep schema setup first
@Component
@RequiredArgsConstructor
public class PaymentIndexInitializer implements CommandLineRunner {

    private static final String CREATE_ACTIVE_BOOKING_INDEX = """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_active_booking
            ON payments (booking_id)
            WHERE booking_id IS NOT NULL AND status IN ('PENDING', 'PROOF_SUBMITTED')
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute(CREATE_ACTIVE_BOOKING_INDEX);
        log.info("Ensured partial unique index uk_payments_active_booking (one active payment per booking)");
    }
}
