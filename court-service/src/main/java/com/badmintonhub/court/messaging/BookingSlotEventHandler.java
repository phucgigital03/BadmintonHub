package com.badmintonhub.court.messaging;

import com.badmintonhub.court.entity.ProcessedEvent;
import com.badmintonhub.court.messaging.event.SlotHeldEvent;
import com.badmintonhub.court.messaging.event.SlotReleasedEvent;
import com.badmintonhub.court.repository.ProcessedEventRepository;
import com.badmintonhub.court.service.SlotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void handleHeld(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        SlotHeldEvent event = parse(payload, SlotHeldEvent.class);
        slotService.holdSlots(event.bookingId(), event.slotIds());
        recordProcessed(eventId);
    }

    @Transactional
    public void handleReleased(String eventId, String payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        SlotReleasedEvent event = parse(payload, SlotReleasedEvent.class);
        slotService.releaseSlots(event.bookingId(), event.slotIds());
        recordProcessed(eventId);
    }

    private boolean alreadyProcessed(String eventId) {
        if (eventId == null) {
            // Outbox always sets msgKey = event UUID, so a null key means a misconfigured producer —
            // idempotency can't dedupe this message. Warn loudly rather than silently risk reprocessing.
            log.warn("Kafka event arrived with a NULL key — idempotency guard disabled for this message");
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
