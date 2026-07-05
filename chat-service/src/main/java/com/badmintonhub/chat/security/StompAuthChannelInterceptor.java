package com.badmintonhub.chat.security;

import com.badmintonhub.chat.realtime.ChatParticipantResolver;
import com.badmintonhub.chat.realtime.WebSocketSessionTracker;
import com.badmintonhub.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The WebSocket security boundary (§G.1/§G.2). The gateway leaves {@code /ws/**} public (a browser can't put
 * a token on the WS handshake), so authentication happens here at the STOMP <b>CONNECT</b> frame, and
 * authorization is enforced <b>per frame</b>.
 *
 * <ul>
 *   <li><b>CONNECT</b> — verify the {@code Authorization: Bearer} native header with {@link JwtUtil}, set the
 *       principal (name = userId, authorities = roles), stash {@code exp}, and schedule a proactive close at exp.</li>
 *   <li><b>SUBSCRIBE</b> {@code /topic/staff.queue} — STAFF/ADMIN only ({@code /user/**} is safe by principal).</li>
 *   <li><b>SEND</b> {@code /app/conv/{id}/**} — principal must be a participant of the conversation
 *       (anti-spoof for {@code delivered}/{@code typing}); resolved via the short-TTL cache (P1-7).</li>
 *   <li><b>Any frame past {@code exp}</b> — rejected (backstop for the scheduled close, §G.2).</li>
 * </ul>
 *
 * <p>A thrown exception aborts the frame and makes Spring send a STOMP ERROR frame (CONNECT → refused).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    static final String EXP_ATTR = "chat.tokenExpEpochMs";
    private static final String BEARER = "Bearer ";
    private static final String APP_CONV_PREFIX = "/app/conv/";
    private static final String STAFF_QUEUE = "/topic/staff.queue";

    private final JwtUtil jwtUtil;
    private final ChatParticipantResolver participantResolver;
    private final WebSocketSessionTracker sessionTracker;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeats / non-STOMP frames
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> {
                enforceNotExpired(accessor);
                authorizeSubscribe(accessor);
            }
            case SEND -> {
                enforceNotExpired(accessor);
                authorizeSend(accessor);
            }
            default -> { /* DISCONNECT / UNSUBSCRIBE / ACK — nothing to check */ }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessagingException("CONNECT without a bearer token");
        }
        try {
            Claims claims = jwtUtil.parseAndValidate(header.substring(BEARER.length()));
            String userId = jwtUtil.getUserId(claims);
            List<SimpleGrantedAuthority> authorities = jwtUtil.getRoles(claims).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, authorities));

            Date exp = claims.getExpiration();
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (exp != null && attrs != null) {
                attrs.put(EXP_ATTR, exp.getTime());
                sessionTracker.scheduleClose(accessor.getSessionId(), exp.toInstant());
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessagingException("Invalid or expired token on CONNECT");
        }
    }

    private void enforceNotExpired(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null && attrs.get(EXP_ATTR) instanceof Long expMs && System.currentTimeMillis() >= expMs) {
            throw new MessagingException("Token expired mid-session");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith(STAFF_QUEUE) && !hasStaffRole(accessor)) {
            throw new MessagingException("Only STAFF/ADMIN may subscribe to the staff queue");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        UUID conversationId = extractConversationId(accessor.getDestination());
        if (conversationId == null) {
            return; // not a conversation-scoped app destination
        }
        UUID userId = currentUserId(accessor);
        if (userId == null) {
            throw new MessagingException("Unauthenticated SEND");
        }
        ChatParticipantResolver.Participants participants =
                participantResolver.resolve(accessor.getSessionAttributes(), conversationId);
        if (participants == null || !participants.includes(userId)) {
            throw new MessagingException("Not a participant of this conversation");
        }
    }

    // ---- helpers ----

    private boolean hasStaffRole(StompHeaderAccessor accessor) {
        Collection<String> roles = authorities(accessor);
        return roles.contains("ROLE_STAFF") || roles.contains("ROLE_ADMIN");
    }

    private Collection<String> authorities(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication auth) {
            return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        }
        return List.of();
    }

    private UUID currentUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            return null;
        }
        try {
            return UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code /app/conv/{id}/delivered|typing} → the {id} UUID, or null if not that shape. */
    private UUID extractConversationId(String destination) {
        if (destination == null || !destination.startsWith(APP_CONV_PREFIX)) {
            return null;
        }
        String rest = destination.substring(APP_CONV_PREFIX.length());
        int slash = rest.indexOf('/');
        String idPart = (slash >= 0) ? rest.substring(0, slash) : rest;
        try {
            return UUID.fromString(idPart);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
