package com.badmintonhub.payment.repository;

import com.badmintonhub.payment.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    /** Dedupe guard for import — a {@code bank_ref} already stored is skipped. */
    boolean existsByBankRef(String bankRef);

    /**
     * Candidate matches for the AI lookup: a transaction whose memo contains the {@code order_code} the
     * payer wrote AND whose amount equals the payment amount (VND, exact). Newest first so the agent can
     * pick the closest. Empty list = no bank evidence yet (the policy gate {@code G} stays false).
     */
    @Query("select t from BankTransaction t " +
            "where t.amount = :amount and t.memo like %:orderCode% " +
            "order by t.transferredAt desc")
    List<BankTransaction> findMatches(@Param("amount") BigDecimal amount, @Param("orderCode") String orderCode);
}
