package com.badmintonhub.chat.security;

import com.badmintonhub.chat.realtime.ChatParticipantResolver;
import com.badmintonhub.chat.realtime.WebSocketSessionTracker;
import com.badmintonhub.security.JwtUtil;
import com.badmintonhub.test.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "test-jwt-secret-at-least-32-bytes-long-0123456789";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);
    private final ChatParticipantResolver participantResolver = mock(ChatParticipantResolver.class);
    private final WebSocketSessionTracker sessionTracker = mock(WebSocketSessionTracker.class);
    private final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(jwtUtil, participantResolver, sessionTracker);
    private final MessageChannel channel = mock(MessageChannel.class);

    private StompHeaderAccessor connectAccessor(String bearerToken) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("s1");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        if (bearerToken != null) {
            accessor.addNativeHeader("Authorization", "Bearer " + bearerToken);
        }
        return accessor;
    }

    private Message<byte[]> frame(StompCommand cmd, Principal user, String destination, Map<String, Object> attrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(cmd);
        accessor.setSessionId("s1");
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(attrs);
        if (user != null) {
            accessor.setUser(user);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal principal(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId.toString(), null,
                List.of(new SimpleGrantedAuthority(role)));
    }

    private Map<String, Object> attrsWithExp(long expEpochMs) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(StompAuthChannelInterceptor.EXP_ATTR, expEpochMs);
        return attrs;
    }

    private long future() {
        return System.currentTimeMillis() + 60_000;
    }

    @Test
    void connect_validToken_setsPrincipalAndStashesExp() {
        UUID userId = UUID.randomUUID();
        StompHeaderAccessor accessor = connectAccessor(JwtTestTokens.token(SECRET, userId.toString(), "ROLE_USER"));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(userId.toString());
        assertThat(accessor.getSessionAttributes()).containsKey(StompAuthChannelInterceptor.EXP_ATTR);
    }

    @Test
    void connect_missingToken_throws() {
        StompHeaderAccessor accessor = connectAccessor(null);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }

    @Test
    void connect_invalidToken_throws() {
        StompHeaderAccessor accessor = connectAccessor("not-a-real-jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_staffQueue_byUser_rejected() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE,
                principal(UUID.randomUUID(), "ROLE_USER"), "/topic/staff.queue", attrsWithExp(future()));

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribe_staffQueue_byStaff_allowed() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE,
                principal(UUID.randomUUID(), "ROLE_STAFF"), "/topic/staff.queue", attrsWithExp(future()));

        assertThatCode(() -> interceptor.preSend(message, channel)).doesNotThrowAnyException();
    }

    @Test
    void send_deliveredByNonParticipant_rejected() {
        UUID intruder = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(participantResolver.resolve(org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.eq(conversationId)))
                .thenReturn(new ChatParticipantResolver.Participants(UUID.randomUUID(), UUID.randomUUID()));

        Message<byte[]> message = frame(StompCommand.SEND, principal(intruder, "ROLE_USER"),
                "/app/conv/" + conversationId + "/delivered", attrsWithExp(future()));

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }

    @Test
    void send_deliveredByParticipant_allowed() {
        UUID customer = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(participantResolver.resolve(org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.eq(conversationId)))
                .thenReturn(new ChatParticipantResolver.Participants(customer, UUID.randomUUID()));

        Message<byte[]> message = frame(StompCommand.SEND, principal(customer, "ROLE_USER"),
                "/app/conv/" + conversationId + "/delivered", attrsWithExp(future()));

        assertThatCode(() -> interceptor.preSend(message, channel)).doesNotThrowAnyException();
    }

    @Test
    void frameAfterTokenExpiry_rejected() {
        Message<byte[]> message = frame(StompCommand.SUBSCRIBE,
                principal(UUID.randomUUID(), "ROLE_STAFF"), "/topic/staff.queue",
                attrsWithExp(System.currentTimeMillis() - 1_000)); // already expired

        assertThatThrownBy(() -> interceptor.preSend(message, channel)).isInstanceOf(MessagingException.class);
    }
}
