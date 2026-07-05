package com.badmintonhub.chat.controller;

import com.badmintonhub.chat.realtime.ChatParticipantResolver;
import com.badmintonhub.chat.realtime.ChatRealtimePublisher;
import com.badmintonhub.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * STOMP {@code @MessageMapping} handlers for the two low-weight signals (UC-CHAT-02 · §E.2). The heavyweight
 * path — sending a message — goes through REST (persist + authz), not here (§D.3). Participant authz for these
 * {@code /app/conv/{id}/*} frames is already enforced by {@code StompAuthChannelInterceptor}; these handlers
 * just react.
 */
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final ChatRealtimePublisher publisher;
    private final ChatParticipantResolver participantResolver;

    /** Recipient's client ACKs receipt of a message (delivered mark). */
    public record DeliveredAck(String messageId) {
    }

    @MessageMapping("/conv/{id}/delivered")
    public void delivered(@DestinationVariable("id") UUID conversationId,
                          @Payload DeliveredAck ack,
                          Principal principal) {
        if (principal == null || ack == null) {
            return;
        }
        UUID byUser = UUID.fromString(principal.getName());
        UUID sender = chatService.markDelivered(conversationId, ack.messageId(), byUser);
        if (sender != null) {
            publisher.delivery(sender, conversationId, ack.messageId());
        }
    }

    @MessageMapping("/conv/{id}/typing")
    public void typing(@DestinationVariable("id") UUID conversationId,
                       Principal principal,
                       MessageHeaders headers) {
        if (principal == null) {
            return;
        }
        UUID fromUser = UUID.fromString(principal.getName());
        Map<String, Object> sessionAttrs = SimpMessageHeaderAccessor.getSessionAttributes(headers);
        ChatParticipantResolver.Participants participants = participantResolver.resolve(sessionAttrs, conversationId);
        if (participants != null) {
            UUID other = participants.other(fromUser);
            if (other != null) {
                publisher.typing(other, conversationId, fromUser); // ephemeral, not persisted
            }
        }
    }
}
