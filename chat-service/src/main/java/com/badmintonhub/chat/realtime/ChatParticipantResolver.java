package com.badmintonhub.chat.realtime;

import com.badmintonhub.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a conversation's participants (customer + assigned staff) for per-frame authz and typing/delivery
 * routing — with a short-TTL cache kept in the STOMP session attributes (P1-7 · §G.1).
 *
 * <p>SEND {@code /app/conv/{id}/*} frames (typing on every keystroke, delivered on every message) are a hot
 * path, so a naive Mongo read per frame would hammer the DB. Instead the participant set is cached per
 * (session, conversation) for {@link #TTL_MS}. The authoritative content path (REST {@code POST /messages})
 * always re-checks in the service, so a stale cache after a transfer/release can at most let an ex-assignee
 * emit an ephemeral typing/delivered ping for ≤TTL — a bounded, acceptable window (the UC allows a short-TTL
 * cache as an alternative to explicit invalidation).</p>
 */
@Component
@RequiredArgsConstructor
public class ChatParticipantResolver {

    /** Session-attribute key under which the per-conversation cache map is stored. */
    public static final String CACHE_KEY = "chat.participantCache";
    private static final long TTL_MS = 30_000L;

    private final ConversationRepository conversationRepo;

    public record Participants(UUID customerId, UUID assignedStaffId) {
        public boolean includes(UUID userId) {
            return userId != null && (userId.equals(customerId) || userId.equals(assignedStaffId));
        }

        /** The other party relative to {@code userId}, or {@code null} if unknown/unassigned. */
        public UUID other(UUID userId) {
            if (userId == null) {
                return null;
            }
            if (userId.equals(customerId)) {
                return assignedStaffId;
            }
            if (userId.equals(assignedStaffId)) {
                return customerId;
            }
            return null;
        }
    }

    private record Timed(Participants participants, long at) {}

    /**
     * @return the participants of {@code conversationId}, or {@code null} if the conversation does not exist.
     */
    @SuppressWarnings("unchecked")
    public Participants resolve(Map<String, Object> sessionAttributes, UUID conversationId) {
        Map<UUID, Timed> cache = null;
        if (sessionAttributes != null) {
            cache = (Map<UUID, Timed>) sessionAttributes.computeIfAbsent(
                    CACHE_KEY, k -> new ConcurrentHashMap<UUID, Timed>());
            Timed cached = cache.get(conversationId);
            if (cached != null && (System.currentTimeMillis() - cached.at()) < TTL_MS) {
                return cached.participants();
            }
        }
        Participants fresh = conversationRepo.findById(conversationId)
                .map(c -> new Participants(c.getCustomerId(), c.getAssignedStaffId()))
                .orElse(null);
        if (cache != null && fresh != null) {
            cache.put(conversationId, new Timed(fresh, System.currentTimeMillis()));
        }
        return fresh;
    }
}
