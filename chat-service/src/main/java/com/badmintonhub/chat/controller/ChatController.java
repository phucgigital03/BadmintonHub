package com.badmintonhub.chat.controller;

import com.badmintonhub.chat.dto.request.OpenConversationRequest;
import com.badmintonhub.chat.dto.request.SendMessageRequest;
import com.badmintonhub.chat.dto.request.TransferRequest;
import com.badmintonhub.chat.dto.response.ConversationResponse;
import com.badmintonhub.chat.dto.response.MessageResponse;
import com.badmintonhub.chat.dto.response.Persisted;
import com.badmintonhub.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Support-chat REST API (UC_Chatting §E.1). Coarse role gates live in {@code @PreAuthorize}; fine
 * participant/ownership checks are in the service (ownership is not a path variable). Identity is the
 * verified token only: {@code auth.getName()} = userId, authorities = roles. No WebSocket here (PHASE 3).
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** UC-CHAT-01 — open (find-or-create) the caller's single support thread. */
    @PostMapping("/conversations")
    @PreAuthorize("hasAnyRole('USER','COACH')")
    public ResponseEntity<ConversationResponse> open(@RequestBody(required = false) OpenConversationRequest req,
                                                     Authentication auth) {
        String displayName = (req != null) ? req.displayName() : null;
        Persisted<ConversationResponse> result = chatService.ensureOpen(userId(auth), displayName);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.value());
    }

    /** UC-CHAT-03 — inbox / unassigned queue (STAFF) / scope=all (ADMIN) / own threads (customer). */
    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ConversationResponse>> list(
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String scope,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(chatService.listConversations(userId(auth), roles(auth), queue, scope, pageable));
    }

    /** UC-CHAT-02/03 — keyset history (newest→oldest), {@code before} = ObjectId hex cursor (§F.4). */
    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MessageResponse>> messages(@PathVariable UUID id,
                                                          @RequestParam(required = false) String before,
                                                          @RequestParam(defaultValue = "30") int limit,
                                                          Authentication auth) {
        return ResponseEntity.ok(chatService.getMessages(id, userId(auth), roles(auth), before, limit));
    }

    /** UC-CHAT-02 — send a text message (idempotent by clientMsgId). 201 new / 200 duplicate. */
    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> send(@PathVariable UUID id,
                                                @Valid @RequestBody SendMessageRequest req,
                                                Authentication auth) {
        Persisted<MessageResponse> result = chatService.sendMessage(id, userId(auth), roles(auth), req);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.value());
    }

    /** UC-CHAT-04 — send an image attachment (multipart). 201 new / 200 duplicate. */
    @PostMapping(value = "/conversations/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> sendImage(@PathVariable UUID id,
                                                     @RequestParam("file") MultipartFile file,
                                                     @RequestParam("clientMsgId") String clientMsgId,
                                                     @RequestParam(value = "caption", required = false) String caption,
                                                     @RequestParam(value = "senderName", required = false) String senderName,
                                                     Authentication auth) {
        Persisted<MessageResponse> result =
                chatService.sendImage(id, userId(auth), roles(auth), file, clientMsgId, caption, senderName);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.value());
    }

    /** UC-CHAT-02 — mark the other party's messages read + reset my unread. */
    @PatchMapping("/conversations/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationResponse> read(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(chatService.markRead(id, userId(auth), roles(auth)));
    }

    /** UC-CHAT-03 — claim an unassigned thread into my inbox (atomic). */
    @PostMapping("/conversations/{id}/claim")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ConversationResponse> claim(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(chatService.claim(id, userId(auth), roles(auth)));
    }

    /** UC-CHAT-03 / §G.7 — hand an assigned thread to another staff. */
    @PostMapping("/conversations/{id}/transfer")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ConversationResponse> transfer(@PathVariable UUID id,
                                                        @Valid @RequestBody TransferRequest req,
                                                        Authentication auth) {
        return ResponseEntity.ok(chatService.transfer(id, userId(auth), roles(auth), req.toStaffId()));
    }

    /** UC-CHAT-03 / §G.7 — release an assigned thread back to the queue. */
    @PostMapping("/conversations/{id}/release")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ConversationResponse> release(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(chatService.release(id, userId(auth), roles(auth)));
    }

    /** UC-CHAT-03 — close a thread. */
    @PostMapping("/conversations/{id}/close")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ConversationResponse> close(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(chatService.close(id, userId(auth), roles(auth)));
    }

    private static UUID userId(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    private static Collection<String> roles(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
