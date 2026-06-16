package com.badmintonhub.escrow.messaging;

import com.badmintonhub.escrow.entity.ProcessedEvent;
import com.badmintonhub.escrow.messaging.event.MatchCancelledEvent;
import com.badmintonhub.escrow.messaging.event.MatchCompletedEvent;
import com.badmintonhub.escrow.messaging.event.PaymentConfirmedEvent;
import com.badmintonhub.escrow.repository.ProcessedEventRepository;
import com.badmintonhub.escrow.service.EscrowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional handling of escrow events: the ledger change + the idempotency-guard row commit together
 * (one {@code @Transactional}). The listener acks only after this returns, so a crash before the ack just
 * replays — and the {@code escrow_processed_events} check makes the replay a no-op. A bad payload or a
 * missing-account (out-of-order delivery) throws → the listener's error handler retries → DLT, so no
 * money movement is ever silently dropped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowEventHandler {

    private final ObjectMapper objectMapper;
    private final EscrowService escrowService;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void handleHostPaymentConfirmed(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        PaymentConfirmedEvent event = parse(payload, PaymentConfirmedEvent.class);
        escrowService.recordHostDeposit(event.matchId(), event.userId(), event.amount(), event.paymentId());
        recordProcessed(eventId);
    }

    @Transactional
    public void handlePlayerPaymentConfirmed(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        PaymentConfirmedEvent event = parse(payload, PaymentConfirmedEvent.class);
        escrowService.recordPlayerReimbursement(event.matchId(), event.userId(), event.amount(), event.paymentId());
        recordProcessed(eventId);
    }

    @Transactional
    public void handleMatchCompleted(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        MatchCompletedEvent event = parse(payload, MatchCompletedEvent.class);
        escrowService.settle(event.matchId(), event.courtOwnerId());
        recordProcessed(eventId);
    }

    @Transactional
    public void handleMatchCancelled(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        MatchCancelledEvent event = parse(payload, MatchCancelledEvent.class);
        escrowService.refund(event.matchId());
        recordProcessed(eventId);
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId != null && processedEventRepository.existsById(eventId)) {
            log.debug("Event {} already processed — skipping", eventId);
            return true;
        }
        return false;
    }

    private void recordProcessed(String eventId) {
        if (eventId != null) {
            processedEventRepository.save(new ProcessedEvent(eventId));
        }
    }

    private <T> T parse(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception e) {
            // Unparseable payload → throw so the error handler retries then routes to the DLT.
            throw new IllegalStateException("Cannot parse " + type.getSimpleName() + " payload: " + e.getMessage(), e);
        }
    }
}
