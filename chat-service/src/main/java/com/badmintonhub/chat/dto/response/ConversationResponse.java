package com.badmintonhub.chat.dto.response;

import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID customerId,
        String customerName,
        UUID assignedStaffId,
        ConversationStatus status,
        String lastMessagePreview,
        Instant lastMessageAt,
        int customerUnread,
        int staffUnread,
        Instant createdAt
) {
    public static ConversationResponse from(Conversation c) {
        return new ConversationResponse(
                c.getId(), c.getCustomerId(), c.getCustomerName(), c.getAssignedStaffId(),
                c.getStatus(), c.getLastMessagePreview(), c.getLastMessageAt(),
                c.getCustomerUnread(), c.getStaffUnread(), c.getCreatedAt());
    }
}
