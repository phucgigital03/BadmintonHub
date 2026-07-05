package com.badmintonhub.chat.service.impl;

import com.badmintonhub.chat.dto.request.SendMessageRequest;
import com.badmintonhub.chat.dto.response.ConversationResponse;
import com.badmintonhub.chat.dto.response.MessageResponse;
import com.badmintonhub.chat.dto.response.Persisted;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.MessageType;
import com.badmintonhub.chat.entity.SenderRole;
import com.badmintonhub.chat.realtime.ChatRealtimePublisher;
import com.badmintonhub.chat.repository.ConversationRepository;
import com.badmintonhub.chat.repository.MessageRepository;
import com.badmintonhub.chat.service.ChatRateLimiter;
import com.badmintonhub.chat.service.ChatService;
import com.badmintonhub.common.exception.ApiException;
import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.ForbiddenException;
import com.badmintonhub.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * See {@link ChatService}. No {@code @Transactional} — Mongo standalone has no multi-doc transaction, so
 * writes are ordered "source of truth first" (message before conversation denormalization, §G.5) and every
 * status transition is an atomic conditional {@code findAndModify} (P0-1), the Mongo counterpart of the
 * booking/payment {@code SELECT … FOR UPDATE} row lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final List<ConversationStatus> ACTIVE = List.of(ConversationStatus.OPEN, ConversationStatus.ASSIGNED);
    private static final int MAX_PAGE = 100;
    private static final int DEFAULT_PAGE = 30;
    private static final int PREVIEW_LEN = 120;

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final MongoTemplate mongoTemplate;
    private final ChatRateLimiter rateLimiter;
    private final ChatRealtimePublisher publisher;

    // ---------------------------------------------------------------- open

    @Override
    public Persisted<ConversationResponse> ensureOpen(UUID customerId, String displayName) {
        var existing = conversationRepo.findFirstByCustomerIdAndStatusIn(customerId, ACTIVE);
        if (existing.isPresent()) {
            return Persisted.existing(ConversationResponse.from(existing.get()));
        }
        Conversation fresh = Conversation.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .customerName(cleanName(displayName, "Khách"))
                .assignedStaffId(null)
                .status(ConversationStatus.OPEN)
                .customerUnread(0)
                .staffUnread(0)
                .build();
        try {
            Conversation saved = conversationRepo.insert(fresh);
            publisher.queueChanged(saved); // new unassigned thread → staff queue (best-effort)
            return Persisted.created(ConversationResponse.from(saved));
        } catch (DuplicateKeyException race) {
            // Two "Hỗ trợ" clicks raced; the partial-unique {customerId} guard fired → return the winner (P0-5).
            return Persisted.existing(ConversationResponse.from(
                    conversationRepo.findFirstByCustomerIdAndStatusIn(customerId, ACTIVE)
                            .orElseThrow(() -> conflict("CONVERSATION_RACE", "Không mở được hội thoại, thử lại"))));
        }
    }

    // ---------------------------------------------------------------- list

    @Override
    public Page<ConversationResponse> listConversations(UUID userId, Collection<String> roles,
                                                        String queue, String scope, Pageable pageable) {
        if ("unassigned".equals(queue)) {
            requireStaffOrAdmin(roles);
            return conversationRepo.findByStatusAndAssignedStaffIdIsNull(ConversationStatus.OPEN, pageable)
                    .map(ConversationResponse::from);
        }
        if ("all".equals(scope)) {
            if (!isAdmin(roles)) {
                throw forbidden();
            }
            return conversationRepo.findAll(pageable).map(ConversationResponse::from);
        }
        if (isStaff(roles) || isAdmin(roles)) {
            return conversationRepo.findByAssignedStaffId(userId, pageable).map(ConversationResponse::from);
        }
        return conversationRepo.findByCustomerId(userId, pageable).map(ConversationResponse::from);
    }

    // ---------------------------------------------------------------- history (keyset)

    @Override
    public List<MessageResponse> getMessages(UUID conversationId, UUID userId, Collection<String> roles,
                                             String before, int limit) {
        Conversation c = load(conversationId);
        requireParticipantOrAdmin(c, userId, roles);

        int lim = Math.min(limit <= 0 ? DEFAULT_PAGE : limit, MAX_PAGE);
        var criteria = where("conversationId").is(conversationId);
        if (before != null && !before.isBlank()) {
            criteria = criteria.and("_id").lt(parseCursor(before));
        }
        Query q = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "_id")).limit(lim);
        return mongoTemplate.find(q, Message.class).stream().map(MessageResponse::from).toList();
    }

    // ---------------------------------------------------------------- send

    @Override
    public Persisted<MessageResponse> sendMessage(UUID conversationId, UUID senderId, Collection<String> roles,
                                                  SendMessageRequest req) {
        Conversation c = load(conversationId);

        boolean isCustomer = senderId.equals(c.getCustomerId());
        boolean isAssignedStaff = senderId.equals(c.getAssignedStaffId());
        if (!isCustomer && !isAssignedStaff && !isAdmin(roles)) {
            throw forbidden();
        }
        if (req.type() != null && req.type() == MessageType.IMAGE) {
            throw new ApiException("USE_IMAGE_ENDPOINT", "Ảnh gửi qua /images (chưa hỗ trợ ở bản này)", HttpStatus.BAD_REQUEST);
        }

        rateLimiter.check(senderId);

        String content = req.content() == null ? "" : req.content().trim();
        if (content.isEmpty()) {
            throw new ApiException("EMPTY_MESSAGE", "Nội dung không được để trống", HttpStatus.BAD_REQUEST);
        }

        // Reopen a CLOSED thread when the customer writes back (P0-3). Atomic CLOSED→ASSIGNED keeping the
        // old staff; if the customer meanwhile opened another active thread, the partial-unique fires → 409
        // and the FE should route the message to that active thread instead.
        if (c.getStatus() == ConversationStatus.CLOSED) {
            if (!isCustomer) {
                throw conflict("CONVERSATION_CLOSED", "Hội thoại đã đóng");
            }
            c = reopen(conversationId);
        }

        SenderRole senderRole = isCustomer ? SenderRole.CUSTOMER : SenderRole.STAFF;
        String senderName = cleanName(req.senderName(),
                isCustomer ? c.getCustomerName() : "Nhân viên hỗ trợ");

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .senderRole(senderRole)
                .senderName(senderName)
                .type(MessageType.TEXT)
                .content(content)
                .clientMsgId(req.clientMsgId())
                .createdAt(Instant.now())
                .build();

        Message saved;
        try {
            saved = messageRepo.insert(message);   // source of truth persisted FIRST (§G.5)
        } catch (DuplicateKeyException retry) {
            // Same clientMsgId replayed → return the original, no duplicate (P0-5). Idempotent 200.
            return Persisted.existing(messageRepo
                    .findFirstByConversationIdAndSenderIdAndClientMsgId(conversationId, senderId, req.clientMsgId())
                    .map(MessageResponse::from)
                    .orElseThrow(() -> conflict("MESSAGE_RACE", "Không gửi được tin, thử lại")));
        }

        // Then update the denormalized conversation cache (recipient unread + last-message preview).
        String recipientUnread = isCustomer ? "staffUnread" : "customerUnread";
        mongoTemplate.updateFirst(
                new Query(where("_id").is(conversationId)),
                new Update()
                        .inc(recipientUnread, 1)
                        .set("lastMessagePreview", preview(content))
                        .set("lastMessageAt", saved.getCreatedAt())
                        .currentDate("updatedAt"),
                Conversation.class);

        // Mirror the write onto the in-memory copy so the WS payload reflects it (no extra read), then push.
        c.setLastMessagePreview(preview(content));
        c.setLastMessageAt(saved.getCreatedAt());
        if (isCustomer) {
            c.setStaffUnread(c.getStaffUnread() + 1);
        } else {
            c.setCustomerUnread(c.getCustomerUnread() + 1);
        }
        publisher.messageSent(c, MessageResponse.from(saved), isCustomer); // best-effort (P1-6)

        return Persisted.created(MessageResponse.from(saved));
    }

    // ---------------------------------------------------------------- read

    @Override
    public ConversationResponse markRead(UUID conversationId, UUID userId, Collection<String> roles) {
        Conversation c = load(conversationId);
        requireParticipantOrAdmin(c, userId, roles);

        boolean isCustomer = userId.equals(c.getCustomerId());
        SenderRole otherParty = isCustomer ? SenderRole.STAFF : SenderRole.CUSTOMER;
        mongoTemplate.updateMulti(
                new Query(where("conversationId").is(conversationId)
                        .and("senderRole").is(otherParty).and("readAt").is(null)),
                new Update().set("readAt", Instant.now()),
                Message.class);

        String myUnread = isCustomer ? "customerUnread" : "staffUnread";
        mongoTemplate.updateFirst(
                new Query(where("_id").is(conversationId)),
                new Update().set(myUnread, 0).currentDate("updatedAt"),
                Conversation.class);

        Conversation updated = load(conversationId);
        // Tell the other party (whose messages were just read) — best-effort read receipt.
        UUID readReceiptTarget = isCustomer ? updated.getAssignedStaffId() : updated.getCustomerId();
        publisher.read(readReceiptTarget, conversationId, userId);
        return ConversationResponse.from(updated);
    }

    @Override
    public UUID markDelivered(UUID conversationId, String messageId, UUID byUserId) {
        if (messageId == null || !ObjectId.isValid(messageId)) {
            return null;
        }
        ObjectId oid = new ObjectId(messageId);
        Message msg = messageRepo.findById(oid).orElse(null);
        // Ignore unknown messages, cross-thread ids, or a sender ACKing its own message.
        if (msg == null || !conversationId.equals(msg.getConversationId()) || byUserId.equals(msg.getSenderId())) {
            return null;
        }
        mongoTemplate.updateFirst(
                new Query(where("_id").is(oid).and("deliveredAt").is(null)),
                new Update().set("deliveredAt", Instant.now()),
                Message.class);
        return msg.getSenderId();
    }

    // ---------------------------------------------------------------- lifecycle (atomic — P0-1)

    @Override
    public ConversationResponse claim(UUID conversationId, UUID staffId, Collection<String> roles) {
        // Pure atomic transition (NO find-check-save): the filter carries the expected state, so two staff
        // racing to claim the same OPEN thread → exactly one modifiedCount==1, the other gets null → 409.
        Conversation claimed = mongoTemplate.findAndModify(
                new Query(where("_id").is(conversationId)
                        .and("status").is(ConversationStatus.OPEN)
                        .and("assignedStaffId").is(null)),
                new Update().set("assignedStaffId", staffId)
                        .set("status", ConversationStatus.ASSIGNED)
                        .currentDate("updatedAt"),
                returnNew(), Conversation.class);
        if (claimed == null) {
            if (!conversationRepo.existsById(conversationId)) {
                throw notFound();
            }
            throw conflict("ALREADY_CLAIMED", "Hội thoại đã được nhận hoặc đã đóng");
        }
        publisher.queueChanged(claimed); // left the queue → into my inbox (best-effort)
        return ConversationResponse.from(claimed);
    }

    @Override
    public ConversationResponse close(UUID conversationId, UUID userId, Collection<String> roles) {
        Conversation c = load(conversationId);
        if (c.getStatus() == ConversationStatus.CLOSED) {
            throw conflict("ALREADY_CLOSED", "Hội thoại đã đóng");
        }
        // ASSIGNED threads: only the owning staff or ADMIN may close (§E.1).
        if (c.getStatus() == ConversationStatus.ASSIGNED && !isAdmin(roles) && !userId.equals(c.getAssignedStaffId())) {
            throw forbidden();
        }
        Conversation closed = mongoTemplate.findAndModify(
                new Query(where("_id").is(conversationId).and("status").is(c.getStatus())),
                new Update().set("status", ConversationStatus.CLOSED).currentDate("updatedAt"),
                returnNew(), Conversation.class);
        if (closed == null) {
            throw conflict("CONVERSATION_CHANGED", "Trạng thái hội thoại vừa thay đổi, thử lại");
        }
        publisher.queueChanged(closed);
        return ConversationResponse.from(closed);
    }

    @Override
    public ConversationResponse transfer(UUID conversationId, UUID userId, Collection<String> roles, UUID toStaffId) {
        Conversation c = load(conversationId);
        if (c.getStatus() != ConversationStatus.ASSIGNED) {
            throw conflict("NOT_ASSIGNED", "Chỉ chuyển được hội thoại đã nhận");
        }
        if (!isAdmin(roles) && !userId.equals(c.getAssignedStaffId())) {
            throw forbidden();
        }
        Conversation transferred = mongoTemplate.findAndModify(
                new Query(where("_id").is(conversationId)
                        .and("status").is(ConversationStatus.ASSIGNED)
                        .and("assignedStaffId").is(c.getAssignedStaffId())),
                new Update().set("assignedStaffId", toStaffId).currentDate("updatedAt"),
                returnNew(), Conversation.class);
        if (transferred == null) {
            throw conflict("CONVERSATION_CHANGED", "Trạng thái hội thoại vừa thay đổi, thử lại");
        }
        publisher.queueChanged(transferred); // new assignee → their inbox (best-effort)
        return ConversationResponse.from(transferred);
    }

    @Override
    public ConversationResponse release(UUID conversationId, UUID userId, Collection<String> roles) {
        Conversation c = load(conversationId);
        if (c.getStatus() != ConversationStatus.ASSIGNED) {
            throw conflict("NOT_ASSIGNED", "Chỉ nhả được hội thoại đã nhận");
        }
        if (!isAdmin(roles) && !userId.equals(c.getAssignedStaffId())) {
            throw forbidden();
        }
        Conversation released = mongoTemplate.findAndModify(
                new Query(where("_id").is(conversationId)
                        .and("status").is(ConversationStatus.ASSIGNED)
                        .and("assignedStaffId").is(c.getAssignedStaffId())),
                new Update().set("status", ConversationStatus.OPEN).set("assignedStaffId", null).currentDate("updatedAt"),
                returnNew(), Conversation.class);
        if (released == null) {
            throw conflict("CONVERSATION_CHANGED", "Trạng thái hội thoại vừa thay đổi, thử lại");
        }
        publisher.queueChanged(released); // back to the unassigned queue (best-effort)
        return ConversationResponse.from(released);
    }

    // ---------------------------------------------------------------- helpers

    private Conversation reopen(UUID conversationId) {
        try {
            Conversation reopened = mongoTemplate.findAndModify(
                    new Query(where("_id").is(conversationId).and("status").is(ConversationStatus.CLOSED)),
                    new Update().set("status", ConversationStatus.ASSIGNED).currentDate("updatedAt"),
                    returnNew(), Conversation.class);
            if (reopened == null) {
                throw conflict("CONVERSATION_CHANGED", "Trạng thái hội thoại vừa thay đổi, thử lại");
            }
            return reopened;
        } catch (DuplicateKeyException collision) {
            // The customer already has another active (OPEN/ASSIGNED) thread — partial-unique blocks reopen.
            throw conflict("ACTIVE_THREAD_EXISTS", "Bạn đang có một hội thoại khác đang mở");
        }
    }

    private Conversation load(UUID id) {
        return conversationRepo.findById(id).orElseThrow(ChatServiceImpl::notFound);
    }

    private void requireParticipantOrAdmin(Conversation c, UUID userId, Collection<String> roles) {
        boolean participant = userId.equals(c.getCustomerId()) || userId.equals(c.getAssignedStaffId());
        if (!participant && !isAdmin(roles)) {
            throw forbidden();
        }
    }

    private void requireStaffOrAdmin(Collection<String> roles) {
        if (!isStaff(roles) && !isAdmin(roles)) {
            throw forbidden();
        }
    }

    private static boolean isStaff(Collection<String> roles) {
        return roles != null && roles.contains("ROLE_STAFF");
    }

    private static boolean isAdmin(Collection<String> roles) {
        return roles != null && roles.contains("ROLE_ADMIN");
    }

    private static ObjectId parseCursor(String before) {
        if (!ObjectId.isValid(before)) {
            throw new ApiException("INVALID_CURSOR", "Con trỏ phân trang không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        return new ObjectId(before);
    }

    private static String cleanName(String candidate, String fallback) {
        return (candidate != null && !candidate.isBlank()) ? candidate.trim() : fallback;
    }

    private static String preview(String content) {
        return content.length() <= PREVIEW_LEN ? content : content.substring(0, PREVIEW_LEN);
    }

    private static FindAndModifyOptions returnNew() {
        return new FindAndModifyOptions().returnNew(true);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("CONVERSATION_NOT_FOUND", "Không tìm thấy hội thoại");
    }

    private static ForbiddenException forbidden() {
        return new ForbiddenException("FORBIDDEN", "Bạn không có quyền truy cập hội thoại này");
    }

    private static ConflictException conflict(String code, String message) {
        return new ConflictException(code, message);
    }
}
