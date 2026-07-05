package com.badmintonhub.chat.dto.request;

/**
 * Optional body for opening a support thread. {@code displayName} is the customer's own display name,
 * snapshot as {@code customerName} (JWT has no name claim; cosmetic, authz is by UUID). May be null —
 * the whole body is optional and the service falls back to a placeholder.
 */
public record OpenConversationRequest(String displayName) {
}
