package com.badmintonhub.chat.repository;

import com.badmintonhub.chat.entity.Message;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Message persistence. Keyset history reads and the bulk read-marking update use {@code MongoTemplate}
 * in the service impl; this interface only carries the dedupe re-read used after a
 * {@code DuplicateKeyException} (P0-5).
 */
public interface MessageRepository extends MongoRepository<Message, ObjectId> {

    /** Re-read the existing message after a duplicate-key race on the idempotency index. */
    Optional<Message> findFirstByConversationIdAndSenderIdAndClientMsgId(
            UUID conversationId, UUID senderId, String clientMsgId);
}
