package com.badmintonhub.court.messaging;

import com.badmintonhub.court.repository.ProcessedEventRepository;
import com.badmintonhub.court.service.SlotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for the merged booking.slot.changed consumer (NEW-B): action routing + payload-id idempotency. */
@ExtendWith(MockitoExtension.class)
class BookingSlotEventHandlerTest {

    @Mock SlotService slotService;
    @Mock ProcessedEventRepository processedEventRepository;

    private BookingSlotEventHandler handler() {
        return new BookingSlotEventHandler(new ObjectMapper(), slotService, processedEventRepository);
    }

    private String payload(String eventId, String action, UUID bookingId, UUID slotId) {
        return "{\"eventId\":\"" + eventId + "\",\"action\":\"" + action + "\",\"bookingId\":\""
                + bookingId + "\",\"slotId\":\"" + slotId + "\",\"holdExpiresAt\":null}";
    }

    @Test
    void handle_heldAction_holdsTheSlot() {
        UUID b = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        when(processedEventRepository.existsById("e1")).thenReturn(false);

        handler().handle(payload("e1", "HELD", b, s));

        verify(slotService).holdSlots(eq(b), eq(List.of(s)));
        verify(slotService, never()).releaseSlots(any(), any());
        verify(processedEventRepository).save(any());
    }

    @Test
    void handle_releasedAction_releasesTheSlot() {
        UUID b = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        when(processedEventRepository.existsById("e2")).thenReturn(false);

        handler().handle(payload("e2", "RELEASED", b, s));

        verify(slotService).releaseSlots(eq(b), eq(List.of(s)));
        verify(slotService, never()).holdSlots(any(), any());
        verify(processedEventRepository).save(any());
    }

    @Test
    void handle_alreadyProcessed_isNoOp() {
        UUID b = UUID.randomUUID();
        UUID s = UUID.randomUUID();
        when(processedEventRepository.existsById("e3")).thenReturn(true);

        handler().handle(payload("e3", "HELD", b, s));

        verifyNoInteractions(slotService);
        verify(processedEventRepository, never()).save(any());
    }
}
