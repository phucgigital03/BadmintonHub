package com.badmintonhub.chat.realtime;

import com.badmintonhub.chat.dto.response.MessageResponse;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.MessageType;
import com.badmintonhub.chat.entity.SenderRole;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Proves the WS push is <b>best-effort (P1-6)</b>: when the broker (RabbitMQ relay) is down and the
 * messaging template throws, the publisher swallows it — the message is already persisted, so a broker
 * outage must never bubble up and turn a stored message into a 500. The recipient catches up via
 * history-sync (§G.5/§C).
 */
class ChatRealtimePublisherTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ChatRealtimePublisher publisher = new ChatRealtimePublisher(messagingTemplate);

    private Conversation conv(UUID customerId, UUID staffId, ConversationStatus status) {
        return Conversation.builder()
                .id(UUID.randomUUID()).customerId(customerId).customerName("Nam").assignedStaffId(staffId)
                .status(status).customerUnread(0).staffUnread(0).build();
    }

    private MessageResponse dto() {
        return MessageResponse.from(Message.builder()
                .id(new ObjectId()).conversationId(UUID.randomUUID()).senderId(UUID.randomUUID())
                .senderRole(SenderRole.CUSTOMER).senderName("Nam").type(MessageType.TEXT).content("hi")
                .clientMsgId("cm1").createdAt(Instant.now()).build());
    }

    @Test
    void messageSent_brokerThrowsOnUserSend_doesNotPropagate() {
        // convertAndSendToUser blows up (relay unreachable) — push must be swallowed.
        doThrow(new RuntimeException("relay down")).when(messagingTemplate)
                .convertAndSendToUser(anyString(), anyString(), any());

        Conversation c = conv(UUID.randomUUID(), UUID.randomUUID(), ConversationStatus.ASSIGNED);
        assertThatCode(() -> publisher.messageSent(c, dto(), true)).doesNotThrowAnyException();
    }

    @Test
    void queueChanged_brokerThrowsOnBroadcast_doesNotPropagate() {
        // Broadcast to /topic/staff.queue blows up — must be swallowed (best-effort queue refresh).
        doThrow(new RuntimeException("relay down")).when(messagingTemplate)
                .convertAndSend(anyString(), any(Object.class));

        Conversation c = conv(UUID.randomUUID(), null, ConversationStatus.OPEN);
        assertThatCode(() -> publisher.queueChanged(c)).doesNotThrowAnyException();
    }
}
