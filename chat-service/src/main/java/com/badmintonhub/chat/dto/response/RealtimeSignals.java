package com.badmintonhub.chat.dto.response;

import java.util.UUID;

/** Lightweight WebSocket signal payloads (delivery/read/typing) pushed over {@code /user/queue/*}. */
public final class RealtimeSignals {

    private RealtimeSignals() {
    }

    /** Pushed to a message's original sender when the recipient's client ACKs receipt. */
    public record DeliveryReceipt(UUID conversationId, String messageId) {
    }

    /** Pushed to the other party when a participant opens/reads the thread. */
    public record ReadReceipt(UUID conversationId, UUID readerId) {
    }

    /** Pushed to the other party while a participant is typing (ephemeral). */
    public record TypingSignal(UUID conversationId, UUID fromUserId) {
    }
}
