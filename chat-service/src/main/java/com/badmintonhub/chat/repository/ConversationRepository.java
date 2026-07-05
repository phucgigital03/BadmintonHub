package com.badmintonhub.chat.repository;

import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Simple derived finders. Atomic state transitions (claim/close/transfer/release), keyset pagination and
 * bulk read-marking use {@code MongoTemplate} directly in the service impl (Mongo has no
 * {@code SELECT … FOR UPDATE}, so those need conditional {@code findAndModify}).
 */
public interface ConversationRepository extends MongoRepository<Conversation, UUID> {

    /** Active thread of a customer (status ∈ {OPEN, ASSIGNED}) — for the ensure-open idempotent read. */
    Optional<Conversation> findFirstByCustomerIdAndStatusIn(UUID customerId, Collection<ConversationStatus> statuses);

    /** A staff member's private inbox (all statuses). Sort carried by {@code pageable}. */
    Page<Conversation> findByAssignedStaffId(UUID assignedStaffId, Pageable pageable);

    /** The unassigned queue: OPEN + no owner yet. */
    Page<Conversation> findByStatusAndAssignedStaffIdIsNull(ConversationStatus status, Pageable pageable);

    /** A customer's own threads. */
    Page<Conversation> findByCustomerId(UUID customerId, Pageable pageable);
}
