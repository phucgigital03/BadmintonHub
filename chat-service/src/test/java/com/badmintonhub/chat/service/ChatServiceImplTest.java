package com.badmintonhub.chat.service;

import com.badmintonhub.chat.dto.request.SendMessageRequest;
import com.badmintonhub.chat.dto.response.ConversationResponse;
import com.badmintonhub.chat.dto.response.MessageResponse;
import com.badmintonhub.chat.dto.response.Persisted;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.MessageType;
import com.badmintonhub.chat.entity.SenderRole;
import com.badmintonhub.chat.repository.ConversationRepository;
import com.badmintonhub.chat.repository.MessageRepository;
import com.badmintonhub.chat.service.impl.ChatServiceImpl;
import com.badmintonhub.common.exception.ApiException;
import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.ForbiddenException;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock ConversationRepository conversationRepo;
    @Mock MessageRepository messageRepo;
    @Mock MongoTemplate mongoTemplate;
    @Mock ChatRateLimiter rateLimiter;

    @InjectMocks ChatServiceImpl service;

    private static final List<String> USER = List.of("ROLE_USER");
    private static final List<String> STAFF = List.of("ROLE_STAFF");

    private Conversation conv(UUID id, UUID customerId, UUID staffId, ConversationStatus status) {
        return Conversation.builder()
                .id(id).customerId(customerId).customerName("Nam").assignedStaffId(staffId)
                .status(status).customerUnread(0).staffUnread(0).build();
    }

    private SendMessageRequest send(String clientMsgId, String content) {
        return new SendMessageRequest(clientMsgId, MessageType.TEXT, content, null);
    }

    @Test
    void ensureOpen_existingActiveThread_returnsExistingNotCreated() {
        UUID customer = UUID.randomUUID();
        when(conversationRepo.findFirstByCustomerIdAndStatusIn(eq(customer), any()))
                .thenReturn(Optional.of(conv(UUID.randomUUID(), customer, null, ConversationStatus.OPEN)));

        Persisted<ConversationResponse> result = service.ensureOpen(customer, "Nam");

        assertThat(result.created()).isFalse();
        verify(conversationRepo, never()).insert(any(Conversation.class));
    }

    @Test
    void ensureOpen_duplicateKeyRace_reReadsAndReturnsExisting() {
        UUID customer = UUID.randomUUID();
        Conversation winner = conv(UUID.randomUUID(), customer, null, ConversationStatus.OPEN);
        when(conversationRepo.findFirstByCustomerIdAndStatusIn(eq(customer), any()))
                .thenReturn(Optional.empty())      // first check: none yet
                .thenReturn(Optional.of(winner));  // re-read after the race
        when(conversationRepo.insert(any(Conversation.class))).thenThrow(new DuplicateKeyException("dup"));

        Persisted<ConversationResponse> result = service.ensureOpen(customer, "Nam");

        assertThat(result.created()).isFalse();
        assertThat(result.value().id()).isEqualTo(winner.getId());
    }

    @Test
    void sendMessage_duplicateClientMsgId_returnsExistingMessage() {
        UUID convId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        Conversation c = conv(convId, customer, UUID.randomUUID(), ConversationStatus.ASSIGNED);
        Message existing = Message.builder().id(new ObjectId()).conversationId(convId).senderId(customer)
                .senderRole(SenderRole.CUSTOMER).type(MessageType.TEXT).content("hi").clientMsgId("cm1")
                .createdAt(Instant.now()).build();
        when(conversationRepo.findById(convId)).thenReturn(Optional.of(c));
        when(messageRepo.insert(any(Message.class))).thenThrow(new DuplicateKeyException("dup"));
        when(messageRepo.findFirstByConversationIdAndSenderIdAndClientMsgId(convId, customer, "cm1"))
                .thenReturn(Optional.of(existing));

        Persisted<MessageResponse> result = service.sendMessage(convId, customer, USER, send("cm1", "hi"));

        assertThat(result.created()).isFalse();
        assertThat(result.value().id()).isEqualTo(existing.getId().toHexString());
        verify(rateLimiter).check(customer);
    }

    @Test
    void sendMessage_nonParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        Conversation c = conv(convId, UUID.randomUUID(), UUID.randomUUID(), ConversationStatus.ASSIGNED);
        when(conversationRepo.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.sendMessage(convId, UUID.randomUUID(), USER, send("cm1", "hi")))
                .isInstanceOf(ForbiddenException.class);
        verify(rateLimiter, never()).check(any());
    }

    @Test
    void sendMessage_closedThreadByStaff_throwsConflict() {
        UUID convId = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Conversation c = conv(convId, UUID.randomUUID(), staff, ConversationStatus.CLOSED);
        when(conversationRepo.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.sendMessage(convId, staff, STAFF, send("cm1", "hi")))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("CONVERSATION_CLOSED");
    }

    @Test
    void sendMessage_blankContent_throwsBadRequest() {
        UUID convId = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        Conversation c = conv(convId, customer, UUID.randomUUID(), ConversationStatus.ASSIGNED);
        when(conversationRepo.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.sendMessage(convId, customer, USER, send("cm1", "   ")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void claim_alreadyClaimedOrClosed_throwsConflict() {
        UUID convId = UUID.randomUUID();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Conversation.class))).thenReturn(null);
        when(conversationRepo.existsById(convId)).thenReturn(true);

        assertThatThrownBy(() -> service.claim(convId, UUID.randomUUID(), STAFF))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ALREADY_CLAIMED");
    }

    @Test
    void getMessages_nonParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        Conversation c = conv(convId, UUID.randomUUID(), UUID.randomUUID(), ConversationStatus.ASSIGNED);
        when(conversationRepo.findById(convId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.getMessages(convId, UUID.randomUUID(), USER, null, 30))
                .isInstanceOf(ForbiddenException.class);
    }
}
