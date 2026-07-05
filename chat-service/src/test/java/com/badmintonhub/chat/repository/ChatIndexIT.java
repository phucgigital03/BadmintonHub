package com.badmintonhub.chat.repository;

import com.badmintonhub.chat.AbstractChatIntegrationTest;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.MessageType;
import com.badmintonhub.chat.entity.SenderRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the DB-level guards created by {@link com.badmintonhub.chat.config.ChatIndexInitializer} actually
 * exist (P0-2/P0-3) — the service pre-checks would pass these silently if the indexes were missing.
 */
class ChatIndexIT extends AbstractChatIntegrationTest {

    @Autowired ConversationRepository conversationRepo;
    @Autowired MessageRepository messageRepo;
    @Autowired MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), Conversation.class);
        mongoTemplate.remove(new Query(), Message.class);
    }

    private Conversation conversation(UUID customerId, ConversationStatus status) {
        return Conversation.builder()
                .id(UUID.randomUUID()).customerId(customerId).customerName("Nam")
                .status(status).customerUnread(0).staffUnread(0).build();
    }

    @Test
    void twoActiveThreadsSameCustomer_secondRejectedByPartialUnique() {
        UUID customer = UUID.randomUUID();
        conversationRepo.insert(conversation(customer, ConversationStatus.OPEN));

        assertThatThrownBy(() -> conversationRepo.insert(conversation(customer, ConversationStatus.ASSIGNED)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void closedPlusActiveSameCustomer_allowed() {
        UUID customer = UUID.randomUUID();
        conversationRepo.insert(conversation(customer, ConversationStatus.CLOSED));

        // The partial filter excludes CLOSED, so opening a fresh active thread must be allowed.
        assertThatCode(() -> conversationRepo.insert(conversation(customer, ConversationStatus.OPEN)))
                .doesNotThrowAnyException();
        assertThat(conversationRepo.findFirstByCustomerIdAndStatusIn(customer,
                List.of(ConversationStatus.OPEN, ConversationStatus.ASSIGNED))).isPresent();
    }

    @Test
    void duplicateClientMsgId_rejectedByUniqueIndex() {
        UUID conversationId = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        messageRepo.insert(message(conversationId, sender, "cm1"));

        assertThatThrownBy(() -> messageRepo.insert(message(conversationId, sender, "cm1")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private Message message(UUID conversationId, UUID sender, String clientMsgId) {
        return Message.builder()
                .conversationId(conversationId).senderId(sender).senderRole(SenderRole.CUSTOMER)
                .senderName("Nam").type(MessageType.TEXT).content("hi").clientMsgId(clientMsgId)
                .createdAt(Instant.now()).build();
    }
}
