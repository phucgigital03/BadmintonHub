package com.badmintonhub.chat.dto.response;

import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.MessageType;
import com.badmintonhub.chat.entity.SenderRole;

import java.time.Instant;
import java.util.UUID;

/** {@code id} is the ObjectId hex — clients pass it back as the {@code before=} keyset cursor (§F.4). */
public record MessageResponse(
        String id,
        UUID conversationId,
        UUID senderId,
        SenderRole senderRole,
        String senderName,
        MessageType type,
        String content,
        String imageUrl,
        String clientMsgId,
        Instant deliveredAt,
        Instant readAt,
        Instant createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId().toHexString(), m.getConversationId(), m.getSenderId(), m.getSenderRole(),
                m.getSenderName(), m.getType(), m.getContent(), m.getImageUrl(), m.getClientMsgId(),
                m.getDeliveredAt(), m.getReadAt(), m.getCreatedAt());
    }
}
