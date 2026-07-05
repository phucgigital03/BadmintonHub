package com.badmintonhub.chat.scheduler;

import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.SenderRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Heals denormalized unread counters. On Mongo standalone, "INSERT message" and "bump conversation
 * counter" are two non-atomic writes (§G.5) — a crash between them, or a lost update, can drift
 * {@code customerUnread}/{@code staffUnread}. This job periodically recomputes both from the messages
 * (the source of truth) for active threads and corrects any that differ.
 *
 * <p>Pilot-scoped: iterates active (OPEN/ASSIGNED) conversations and counts per thread — fine for one club.
 * Scale path: single grouped aggregation over {@code {readAt:null}} when thread count grows.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadReconcileScheduler {

    private static final long INTERVAL_MS = 300_000L; // every 5 minutes
    private static final int BATCH = 500;

    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    public void reconcileUnread() {
        List<Conversation> active = mongoTemplate.find(
                new Query(where("status").in(ConversationStatus.OPEN, ConversationStatus.ASSIGNED)).limit(BATCH),
                Conversation.class);

        int fixed = 0;
        for (Conversation c : active) {
            // staffUnread  = customer messages the staff hasn't read; customerUnread = staff messages the customer hasn't read.
            long staffUnread = countUnread(c, SenderRole.CUSTOMER);
            long customerUnread = countUnread(c, SenderRole.STAFF);
            if (c.getStaffUnread() != staffUnread || c.getCustomerUnread() != customerUnread) {
                mongoTemplate.updateFirst(
                        new Query(where("_id").is(c.getId())),
                        new Update().set("staffUnread", (int) staffUnread).set("customerUnread", (int) customerUnread),
                        Conversation.class);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("Unread reconcile: corrected counters on {} of {} active conversations", fixed, active.size());
        }
    }

    private long countUnread(Conversation c, SenderRole senderRole) {
        return mongoTemplate.count(
                new Query(where("conversationId").is(c.getId()).and("senderRole").is(senderRole).and("readAt").is(null)),
                Message.class);
    }
}
