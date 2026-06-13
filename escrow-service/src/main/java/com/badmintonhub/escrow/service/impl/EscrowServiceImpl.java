package com.badmintonhub.escrow.service.impl;

import com.badmintonhub.escrow.entity.EscrowAccount;
import com.badmintonhub.escrow.entity.EscrowTransaction;
import com.badmintonhub.escrow.entity.enums.EscrowAccountStatus;
import com.badmintonhub.escrow.entity.enums.EscrowTransactionStatus;
import com.badmintonhub.escrow.entity.enums.EscrowTransactionType;
import com.badmintonhub.escrow.messaging.EscrowOutboxWriter;
import com.badmintonhub.escrow.repository.EscrowAccountRepository;
import com.badmintonhub.escrow.repository.EscrowTransactionRepository;
import com.badmintonhub.escrow.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Escrow ledger logic. Runs inside the consumer transaction (see {@code EscrowEventHandler}). The
 * account is the source of truth for status; every money movement also appends an immutable
 * {@link EscrowTransaction} row for audit. Deposits/reimbursements are recorded COMPLETED (in-system
 * accounting); settlements/refunds are PENDING until STAFF executes the manual bank transfer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowServiceImpl implements EscrowService {

    private final EscrowAccountRepository accountRepository;
    private final EscrowTransactionRepository transactionRepository;
    private final EscrowOutboxWriter outboxWriter;

    @Override
    @Transactional
    public void recordHostDeposit(UUID matchId, UUID hostId, BigDecimal amount, UUID paymentId) {
        if (accountRepository.findByMatchId(matchId).isPresent()) {
            log.warn("Escrow account already exists for matchId={} — skipping duplicate host deposit", matchId);
            return;
        }
        EscrowAccount account = new EscrowAccount();
        account.setMatchId(matchId);
        account.setAmount(amount);
        account.setReleasedAmount(BigDecimal.ZERO);
        account.setStatus(EscrowAccountStatus.HOLDING);
        accountRepository.save(account);

        record(account, EscrowTransactionType.HOST_DEPOSIT, hostId, null, amount, paymentId,
                EscrowTransactionStatus.COMPLETED);
        log.info("Escrow HOLDING opened for matchId={} amount={} (host={})", matchId, amount, hostId);
    }

    @Override
    @Transactional
    public void recordPlayerReimbursement(UUID matchId, UUID playerId, BigDecimal amount, UUID paymentId) {
        EscrowAccount account = requireAccount(matchId);
        if (isTerminal(account)) {
            log.warn("Escrow for matchId={} already {} — ignoring late player reimbursement", matchId, account.getStatus());
            return;
        }
        UUID hostId = host(account);
        record(account, EscrowTransactionType.PLAYER_REIMBURSEMENT, playerId, hostId, amount, paymentId,
                EscrowTransactionStatus.COMPLETED);

        account.setReleasedAmount(account.getReleasedAmount().add(amount));
        account.setStatus(EscrowAccountStatus.PARTIALLY_RELEASED);
        accountRepository.save(account);

        outboxWriter.writeHostReimbursed(matchId, hostId, amount);
        log.info("Reimbursed host of matchId={} amount={} (player={}), releasedTotal={}",
                matchId, amount, playerId, account.getReleasedAmount());
    }

    @Override
    @Transactional
    public void settle(UUID matchId, UUID courtOwnerId) {
        EscrowAccount account = requireAccount(matchId);
        if (isTerminal(account)) {
            log.warn("Escrow for matchId={} already {} — ignoring match.completed", matchId, account.getStatus());
            return;
        }
        if (courtOwnerId != null) {
            account.setCourtOwnerId(courtOwnerId);
        }
        // PENDING — STAFF executes the bank transfer to the court owner, then marks it COMPLETED.
        record(account, EscrowTransactionType.COURT_OWNER_SETTLEMENT, null, courtOwnerId, account.getAmount(), null,
                EscrowTransactionStatus.PENDING);
        account.setStatus(EscrowAccountStatus.SETTLED);
        account.setSettledAt(LocalDateTime.now());
        accountRepository.save(account);
        log.info("Escrow SETTLED for matchId={} amount={} (owner={})", matchId, account.getAmount(), courtOwnerId);
    }

    @Override
    @Transactional
    public void refund(UUID matchId) {
        EscrowAccount account = requireAccount(matchId);
        if (isTerminal(account)) {
            log.warn("Escrow for matchId={} already {} — ignoring match.cancelled", matchId, account.getStatus());
            return;
        }
        BigDecimal totalRefund = BigDecimal.ZERO;

        // Each player who paid in gets their share back (PENDING — STAFF transfers).
        List<EscrowTransaction> playerReimbursements =
                transactionRepository.findByEscrow_IdAndType(account.getId(), EscrowTransactionType.PLAYER_REIMBURSEMENT);
        for (EscrowTransaction r : playerReimbursements) {
            record(account, EscrowTransactionType.PLAYER_REFUND, null, r.getFromPartyId(), r.getAmount(), null,
                    EscrowTransactionStatus.PENDING);
            totalRefund = totalRefund.add(r.getAmount());
        }

        // Host gets back the un-reimbursed remainder of the deposit.
        BigDecimal hostRemainder = account.getAmount().subtract(account.getReleasedAmount());
        if (hostRemainder.compareTo(BigDecimal.ZERO) > 0) {
            record(account, EscrowTransactionType.HOST_REFUND, null, host(account), hostRemainder, null,
                    EscrowTransactionStatus.PENDING);
            totalRefund = totalRefund.add(hostRemainder);
        }

        account.setStatus(EscrowAccountStatus.REFUNDED);
        account.setSettledAt(LocalDateTime.now());
        accountRepository.save(account);

        outboxWriter.writeRefundQueued(matchId, totalRefund);
        log.info("Escrow REFUNDED for matchId={} totalQueued={} ({} player refunds + host remainder)",
                matchId, totalRefund, playerReimbursements.size());
    }

    private EscrowAccount requireAccount(UUID matchId) {
        return accountRepository.findByMatchId(matchId).orElseThrow(() -> new IllegalStateException(
                "No escrow account for matchId=" + matchId + ", awaiting payment.host.confirmed"));
    }

    /** The Host is the from_party of the account's HOST_DEPOSIT row. */
    private UUID host(EscrowAccount account) {
        return transactionRepository
                .findFirstByEscrow_IdAndType(account.getId(), EscrowTransactionType.HOST_DEPOSIT)
                .map(EscrowTransaction::getFromPartyId)
                .orElse(null);
    }

    private boolean isTerminal(EscrowAccount account) {
        return account.getStatus() == EscrowAccountStatus.SETTLED
                || account.getStatus() == EscrowAccountStatus.REFUNDED;
    }

    private void record(EscrowAccount account, EscrowTransactionType type, UUID fromParty, UUID toParty,
                        BigDecimal amount, UUID referencePaymentId, EscrowTransactionStatus status) {
        EscrowTransaction tx = new EscrowTransaction();
        tx.setEscrow(account);
        tx.setType(type);
        tx.setFromPartyId(fromParty);
        tx.setToPartyId(toParty);
        tx.setAmount(amount);
        tx.setReferencePaymentId(referencePaymentId);
        tx.setStatus(status);
        if (status == EscrowTransactionStatus.COMPLETED) {
            tx.setCompletedAt(LocalDateTime.now());
        }
        transactionRepository.save(tx);
    }
}
