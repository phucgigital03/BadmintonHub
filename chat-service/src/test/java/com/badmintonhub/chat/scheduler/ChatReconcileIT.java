package com.badmintonhub.chat.scheduler;

import com.badmintonhub.chat.AbstractChatIntegrationTest;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.MessageType;
import com.badmintonhub.chat.entity.SenderRole;
import com.badmintonhub.chat.repository.ConversationRepository;
import com.badmintonhub.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §G.10 item 7 — the unread-reconcile job heals denormalized counters that drifted (the "INSERT message" and
 * "bump conversation counter" writes aren't atomic on Mongo standalone, §G.5). Recomputes both counters from
 * the messages (source of truth) for active threads.
 */
class ChatReconcileIT extends AbstractChatIntegrationTest {

    @Autowired UnreadReconcileScheduler scheduler;
    @Autowired ConversationRepository conversationRepo;
    @Autowired MessageRepository messageRepo;
    @Autowired MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), Conversation.class);
        mongoTemplate.remove(new Query(), Message.class);
    }

    private Conversation assigned(UUID customer, UUID staff, int staffUnread, int customerUnread) {
        return Conversation.builder()
                .id(UUID.randomUUID()).customerId(customer).customerName("Nam").assignedStaffId(staff)
                .status(ConversationStatus.ASSIGNED).staffUnread(staffUnread).customerUnread(customerUnread).build();
    }

    private Message unread(UUID convId, UUID sender, SenderRole role, String clientMsgId) {
        return Message.builder()
                .conversationId(convId).senderId(sender).senderRole(role).senderName("x")
                .type(MessageType.TEXT).content("hi").clientMsgId(clientMsgId)
                .createdAt(Instant.now()).build(); // readAt = null → counts as unread
    }

    @Test
    void reconcile_driftedCounters_healedFromMessages() {
        UUID customer = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Conversation c = conversationRepo.insert(assigned(customer, staff, 0, 0)); // deliberately wrong (both 0)
        messageRepo.insert(unread(c.getId(), customer, SenderRole.CUSTOMER, "c1"));
        messageRepo.insert(unread(c.getId(), customer, SenderRole.CUSTOMER, "c2")); // → staffUnread should be 2
        messageRepo.insert(unread(c.getId(), staff, SenderRole.STAFF, "s1"));       // → customerUnread should be 1

        scheduler.reconcileUnread();

        Conversation healed = conversationRepo.findById(c.getId()).orElseThrow();
        assertThat(healed.getStaffUnread()).isEqualTo(2);
        assertThat(healed.getCustomerUnread()).isEqualTo(1);
    }

    @Test
    void reconcile_consistentCounters_leftUnchanged() {
        UUID customer = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Conversation c = conversationRepo.insert(assigned(customer, staff, 1, 0)); // already correct
        messageRepo.insert(unread(c.getId(), customer, SenderRole.CUSTOMER, "c1")); // 1 unread customer msg

        scheduler.reconcileUnread();

        Conversation after = conversationRepo.findById(c.getId()).orElseThrow();
        assertThat(after.getStaffUnread()).isEqualTo(1);
        assertThat(after.getCustomerUnread()).isZero();
    }
}
