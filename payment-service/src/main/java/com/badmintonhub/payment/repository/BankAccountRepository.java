package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    /** The account shown on the payment screen (single-club model: typically one active row). */
    Optional<BankAccount> findFirstByIsActiveTrue();

    /** Idempotency guard for the seeder. */
    boolean existsByAccountNumber(String accountNumber);
}
