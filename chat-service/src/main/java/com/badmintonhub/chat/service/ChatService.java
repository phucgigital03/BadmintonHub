package com.badmintonhub.chat.service;

import com.badmintonhub.chat.dto.request.SendMessageRequest;
import com.badmintonhub.chat.dto.response.ConversationResponse;
import com.badmintonhub.chat.dto.response.MessageResponse;
import com.badmintonhub.chat.dto.response.Persisted;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Chat REST use-cases (UC_Chatting §E.1). Authorization (participant / role / ADMIN-scope) is enforced
 * here, not in the controller, because ownership is not a path variable (mirrors booking-service). All
 * conversation state transitions are atomic conditional updates (P0-1) and both create-paths are
 * idempotent under a duplicate-key race (P0-5). No WebSocket push in PHASE 2.
 */
public interface ChatService {

    /** Find-or-create the caller's single open thread (idempotent, unassigned). */
    Persisted<ConversationResponse> ensureOpen(UUID customerId, String displayName);

    /** Staff inbox / unassigned queue / ADMIN scope-all / customer's own threads. */
    Page<ConversationResponse> listConversations(UUID userId, Collection<String> roles,
                                                 String queue, String scope, Pageable pageable);

    /** Keyset history (newest → oldest), cursor = {@code before} ObjectId hex (§F.4). */
    List<MessageResponse> getMessages(UUID conversationId, UUID userId, Collection<String> roles,
                                      String before, int limit);

    /** Send a text message (dedupe by clientMsgId, reopen on CLOSED, message-first persist). */
    Persisted<MessageResponse> sendMessage(UUID conversationId, UUID senderId, Collection<String> roles,
                                           SendMessageRequest req);

    /** Mark the other party's messages read + reset my unread. */
    ConversationResponse markRead(UUID conversationId, UUID userId, Collection<String> roles);

    /**
     * Mark a single message delivered (recipient's WS ACK). Idempotent (set once).
     *
     * @return the original sender's id to notify with a delivery receipt, or {@code null} if there is
     *         nothing to do (message not found / not in this thread / ACK'd by its own sender).
     */
    UUID markDelivered(UUID conversationId, String messageId, UUID byUserId);

    /** OPEN(unassigned) → ASSIGNED to me — atomic (P0-1). */
    ConversationResponse claim(UUID conversationId, UUID staffId, Collection<String> roles);

    /** → CLOSED (from OPEN/ASSIGNED) — atomic. */
    ConversationResponse close(UUID conversationId, UUID userId, Collection<String> roles);

    /** Hand an ASSIGNED thread to another staff — atomic (§G.7). */
    ConversationResponse transfer(UUID conversationId, UUID userId, Collection<String> roles, UUID toStaffId);

    /** ASSIGNED → OPEN, clear assignee (back to queue) — atomic (§G.7). */
    ConversationResponse release(UUID conversationId, UUID userId, Collection<String> roles);
}
