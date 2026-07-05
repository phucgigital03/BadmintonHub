package com.badmintonhub.chat.realtime;

import com.badmintonhub.chat.dto.response.ConversationResponse;
import com.badmintonhub.chat.dto.response.MessageResponse;
import com.badmintonhub.chat.dto.response.RealtimeSignals.DeliveryReceipt;
import com.badmintonhub.chat.dto.response.RealtimeSignals.ReadReceipt;
import com.badmintonhub.chat.dto.response.RealtimeSignals.TypingSignal;
import com.badmintonhub.chat.entity.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pushes real-time events over STOMP — <b>best-effort (P1-6)</b>. The message is already persisted in Mongo
 * (the source of truth) before any push, so every send here is wrapped in try/catch: if the broker
 * (RabbitMQ relay) is down the REST call still returns 201 and the recipient catches up via history-sync
 * (§G.5/§C). A broker outage must never turn a stored message into a 500.
 *
 * <p>Destinations: per-user {@code /user/queue/{messages,delivery,read,typing,inbox}} (via
 * {@code convertAndSendToUser}, multi-device by principal — §G.6) and the shared {@code /topic/staff.queue}
 * broadcast for the unassigned queue.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimePublisher {

    private static final String STAFF_QUEUE = "/topic/staff.queue";

    private final SimpMessagingTemplate messagingTemplate;

    /** Route a newly-persisted message to the recipient (and refresh the staff queue/inbox). */
    public void messageSent(Conversation conv, MessageResponse dto, boolean fromCustomer) {
        UUID recipient = fromCustomer ? conv.getAssignedStaffId() : conv.getCustomerId();
        if (recipient != null) {
            toUser(recipient, "/queue/messages", dto);
        }
        if (fromCustomer && conv.getAssignedStaffId() == null) {
            // Unassigned thread — no direct recipient yet; refresh the shared queue preview.
            broadcast(STAFF_QUEUE, ConversationResponse.from(conv));
        } else if (conv.getAssignedStaffId() != null) {
            // Bump the owning staff member's inbox list.
            toUser(conv.getAssignedStaffId(), "/queue/inbox", ConversationResponse.from(conv));
        }
    }

    public void delivery(UUID toUser, UUID conversationId, String messageId) {
        toUser(toUser, "/queue/delivery", new DeliveryReceipt(conversationId, messageId));
    }

    public void read(UUID toUser, UUID conversationId, UUID readerId) {
        toUser(toUser, "/queue/read", new ReadReceipt(conversationId, readerId));
    }

    public void typing(UUID toUser, UUID conversationId, UUID fromUserId) {
        toUser(toUser, "/queue/typing", new TypingSignal(conversationId, fromUserId));
    }

    /** Broadcast a queue/inbox change after claim/transfer/release/close or a new thread. */
    public void queueChanged(Conversation conv) {
        broadcast(STAFF_QUEUE, ConversationResponse.from(conv));
        if (conv.getAssignedStaffId() != null) {
            toUser(conv.getAssignedStaffId(), "/queue/inbox", ConversationResponse.from(conv));
        }
    }

    private void toUser(UUID user, String destination, Object payload) {
        if (user == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(user.toString(), destination, payload);
        } catch (Exception e) {
            log.warn("WS push to user {} {} failed (best-effort): {}", user, destination, e.toString());
        }
    }

    private void broadcast(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("WS broadcast {} failed (best-effort): {}", destination, e.toString());
        }
    }
}
