package com.badmintonhub.chat.dto.request;

import com.badmintonhub.chat.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Send a text message. {@code clientMsgId} is the client-generated idempotency key (retries reuse it).
 * {@code type} is optional (defaults to TEXT); IMAGE messages use the dedicated image endpoint (PHASE 3).
 * {@code content} is trimmed and capped at 2000 chars; blank/whitespace-only is rejected (§G.3).
 *
 * <p>{@code senderName} is an optional display-name snapshot supplied by the sender's own client — the
 * JWT carries no name claim, and names are cosmetic (authz is always by UUID), so client-provided is
 * acceptable. When absent the service falls back to the conversation's snapshot / a role default.</p>
 */
public record SendMessageRequest(
        @NotBlank String clientMsgId,
        MessageType type,
        @NotBlank @Size(max = 2000, message = "Tin nhắn tối đa 2000 ký tự") String content,
        String senderName
) {
}
