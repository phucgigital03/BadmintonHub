package com.badmintonhub.chat.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live WebSocket sessions and proactively closes each one when its access token expires (§G.2).
 *
 * <p>The access token TTL (15 min) is shorter than a chat socket's lifetime; verifying at CONNECT is a
 * single point in time. On CONNECT the interceptor schedules {@link #scheduleClose} at the token's {@code exp};
 * this closes the socket so it can't outlive its token. The interceptor also rejects any frame past {@code exp}
 * as a backstop, and the FE silent-refreshes + reconnects. Sessions are registered/unregistered by a
 * {@code WebSocketHandlerDecorator} in {@code WebSocketConfig}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionTracker {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final TaskScheduler taskScheduler;

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    /** Schedule a proactive close at {@code expiresAt} (§G.2). No-op on null inputs. */
    public void scheduleClose(String sessionId, Instant expiresAt) {
        if (sessionId == null || expiresAt == null) {
            return;
        }
        taskScheduler.schedule(() -> closeExpired(sessionId), expiresAt);
    }

    private void closeExpired(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Token expired"));
            } catch (IOException e) {
                log.debug("Failed to close expired session {}: {}", sessionId, e.getMessage());
            }
        }
    }
}
