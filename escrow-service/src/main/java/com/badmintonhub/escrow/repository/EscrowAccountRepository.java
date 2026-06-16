package com.badmintonhub.escrow.repository;

import com.badmintonhub.escrow.entity.EscrowAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {

    /** One account per match — the lookup key for every consumed match/payment event. */
    Optional<EscrowAccount> findByMatchId(UUID matchId);
}
