package com.badmintonhub.chat.config;

import com.badmintonhub.chat.realtime.WebSocketSessionTracker;
import com.badmintonhub.chat.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * STOMP-over-WebSocket setup (UC_Chatting §C/§D/§E.2/§G.1).
 *
 * <ul>
 *   <li>Handshake endpoint {@code /ws} (public at the gateway; auth is at the CONNECT frame). The handshake
 *       Origin allow-list is {@code FRONTEND_URL} (comma-separated patterns), defaulting to {@code *}.
 *       Browsers send an {@code Origin} header on EVERY WebSocket handshake — even a same-origin one — and
 *       behind the ALB that origin is a DNS name that changes on every {@code terraform apply}, so pinning
 *       it to one host would mean editing values + resyncing on every cluster rebuild. Safe to leave open
 *       because the real gate is the CONNECT-frame Bearer token (§G.1), not Origin: a cross-origin page
 *       cannot read our localStorage, so it cannot forge a valid CONNECT. Set {@code FRONTEND_URL} to the
 *       real domain once there is one (Day 8) — no code change needed.</li>
 *   <li>Broker: RabbitMQ STOMP relay for cross-instance fan-out (§D.11) when {@code app.broker.relay=true};
 *       otherwise an in-memory simple broker (local/test — no RabbitMQ needed). Client destinations are
 *       identical either way; only ~2 config lines differ. Broker prefixes are {@code /topic}+{@code /queue}
 *       ({@code /user/**} resolves to {@code /queue/...}).</li>
 *   <li>Every inbound frame passes through {@link StompAuthChannelInterceptor} (CONNECT auth + per-frame authz).</li>
 *   <li>Message/buffer size caps (anti-huge-frame) + a session-tracking decorator so tokens can be expired
 *       mid-session (§G.2).</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthInterceptor;
    private final WebSocketSessionTracker sessionTracker;

    /** Comma-separated Origin patterns allowed on the {@code /ws} handshake. See the class javadoc. */
    @Value("${app.frontend-url:*}")
    private String frontendUrl;

    @Value("${app.broker.relay:true}")
    private boolean relayEnabled;

    @Value("${app.broker.host:localhost}")
    private String brokerHost;

    @Value("${app.broker.stomp-port:61613}")
    private int brokerPort;

    @Value("${app.broker.user:badminton}")
    private String brokerUser;

    @Value("${app.broker.pass:badminton}")
    private String brokerPass;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Patterns (not setAllowedOrigins): only the *Patterns variant accepts "*".
        registry.addEndpoint("/ws").setAllowedOriginPatterns(frontendUrl.split(","));
        // Native WebSocket only (@stomp/stompjs on the FE) — no SockJS fallback.
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (relayEnabled) {
            registry.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(brokerHost)
                    .setRelayPort(brokerPort)
                    .setSystemLogin(brokerUser)
                    .setSystemPasscode(brokerPass)
                    .setClientLogin(brokerUser)
                    .setClientPasscode(brokerPass)
                    // Share the user↔session registry across instances so convertAndSendToUser is cross-instance.
                    .setUserRegistryBroadcast("/topic/simp-user-registry")
                    .setUserDestinationBroadcast("/topic/unresolved-user-destination");
        } else {
            registry.enableSimpleBroker("/topic", "/queue");
        }
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024);       // 64 KB per inbound message
        registration.setSendBufferSizeLimit(512 * 1024);   // 512 KB outbound buffer per session
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessionTracker.register(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessionTracker.unregister(session.getId());
                super.afterConnectionClosed(session, status);
            }
        });
    }
}
