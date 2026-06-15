package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.client.BookingServiceClient;
import com.badmintonhub.payment.entity.BankAccount;
import com.badmintonhub.payment.entity.Payment;
import com.badmintonhub.payment.entity.enums.PaymentStatus;
import com.badmintonhub.payment.entity.enums.PaymentType;
import com.badmintonhub.payment.service.CloudinaryService;
import com.badmintonhub.test.AbstractKafkaIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the real-DB backstop a unit test (mocked repo) cannot: the partial unique index
 * {@code uk_payments_active_booking} (created by {@code PaymentIndexInitializer}) lets a booking have at
 * most ONE active (PENDING / PROOF_SUBMITTED) payment, while still allowing many EXPIRED ones. This is
 * what turns a double-initiate that slips past the service-level query into a 409 instead of two payments
 * (= the user transferring twice). Runs against real Postgres + Redis + Kafka (Testcontainers).
 */
class PaymentActiveBookingIndexIT extends AbstractKafkaIntegrationTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // application.yml has no default for jwt.secret (fail-fast in prod) — supply one for the test context.
        r.add("jwt.secret", () -> "test-jwt-secret-at-least-32-bytes-long-0123456789");
        // No Eureka in tests — don't let the discovery client block / spam on startup.
        r.add("eureka.client.enabled", () -> "false");
        r.add("eureka.client.register-with-eureka", () -> "false");
        r.add("eureka.client.fetch-registry", () -> "false");
    }

    @MockBean BookingServiceClient bookingServiceClient; // avoid resolving lb://booking-service via Eureka
    @MockBean CloudinaryService cloudinaryService;

    @Autowired PaymentRepository paymentRepository;
    @Autowired BankAccountRepository bankAccountRepository;

    private BankAccount bank() {
        BankAccount b = new BankAccount();
        b.setBankName("VCB");
        b.setAccountNumber("ACC-" + UUID.randomUUID());
        b.setAccountName("CLB An Binh");
        return bankAccountRepository.save(b);
    }

    private Payment payment(UUID bookingId, PaymentStatus status, BankAccount bank) {
        Payment p = new Payment();
        p.setUserId(UUID.randomUUID());
        p.setBookingId(bookingId);
        p.setBankAccount(bank);
        p.setAmount(new BigDecimal("100000"));
        p.setPaymentType(PaymentType.BOOKING);
        p.setStatus(status);
        p.setExpiresAt(LocalDateTime.now().plusMinutes(15)); // future → the expiry scheduler won't touch it
        return p;
    }

    @Test
    void secondActivePaymentForSameBooking_violatesPartialUniqueIndex() {
        BankAccount bank = bank();
        UUID bookingId = UUID.randomUUID();
        paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.PENDING, bank));

        assertThatThrownBy(() ->
                paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.PENDING, bank)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void manyExpiredPaymentsForSameBooking_areAllowedThenOneActive() {
        BankAccount bank = bank();
        UUID bookingId = UUID.randomUUID();
        // A booking legitimately accumulates EXPIRED payments (rejected / timed out) over its life…
        paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.EXPIRED, bank));
        paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.EXPIRED, bank));
        // …and a single fresh active one is still allowed (the partial index only scopes active statuses).
        Payment active = paymentRepository.saveAndFlush(payment(bookingId, PaymentStatus.PENDING, bank));

        assertThat(paymentRepository.findById(active.getId())).isPresent();
    }
}
