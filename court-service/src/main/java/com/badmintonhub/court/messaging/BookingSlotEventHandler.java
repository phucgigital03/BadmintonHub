package com.badmintonhub.court.messaging;

import com.badmintonhub.court.entity.ProcessedEvent;
import com.badmintonhub.court.messaging.event.SlotChangedEvent;
import com.badmintonhub.court.repository.ProcessedEventRepository;
import com.badmintonhub.court.service.SlotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Transactional handling of booking-slot events: the slot mutation + the idempotency-guard row commit
 * together (one {@code @Transactional}). The listener acks only after this returns, so a crash before
 * the ack just replays — and the {@code processed_events} check makes the replay a no-op. A bad payload
 * throws → the listener's error handler retries → DLT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingSlotEventHandler {

    private final ObjectMapper objectMapper;
    private final SlotService slotService;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * One slot's hold change. Idempotency keys on the payload's {@code eventId} (the Kafka message key is
     * the slotId, for ordering — not unique per event). Routes by action; reuses the existing batch slot
     * methods with a single-element list.
     */
    @Transactional
    public void handle(String payload) {
        SlotChangedEvent event = parse(payload, SlotChangedEvent.class);
        if (alreadyProcessed(event.eventId())) {
            return;
        }
        switch (event.action()) {
            case HELD -> slotService.holdSlots(event.bookingId(), List.of(event.slotId()));
            case RELEASED -> slotService.releaseSlots(event.bookingId(), List.of(event.slotId()));
        }
        recordProcessed(event.eventId());
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId == null) {
            // The Outbox always sets a payload eventId, so null means a malformed event — warn rather than
            // silently risk reprocessing.
            log.warn("booking.slot.changed event arrived with a NULL eventId — idempotency guard disabled");
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
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
