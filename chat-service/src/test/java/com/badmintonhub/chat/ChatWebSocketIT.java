package com.badmintonhub.chat;

import com.badmintonhub.chat.dto.request.SendMessageRequest;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.ConversationStatus;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.chat.entity.MessageType;
import com.badmintonhub.chat.repository.ConversationRepository;
import com.badmintonhub.chat.service.ChatService;
import com.badmintonhub.test.JwtTestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-server STOMP integration: connects an actual WebSocket client to {@code ws://localhost:{port}/ws} and
 * exercises CONNECT auth + the REST→WS push (§G.1/§G.6). Uses the in-memory simple broker (single instance,
 * {@code app.broker.relay=false}) — cross-instance relay isn't testable in one JVM. Negative subscribe/spoof
 * cases are covered deterministically in {@code StompAuthChannelInterceptorTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        ChatTestContainers.props(registry);
    }

    @LocalServerPort int port;
    @Autowired ChatService chatService;
    @Autowired ConversationRepository conversationRepo;
    @Autowired MongoTemplate mongoTemplate;
    @Value("${jwt.secret}") String jwtSecret;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), Conversation.class);
        mongoTemplate.remove(new Query(), Message.class);
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @Test
    void connect_withoutToken_isRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connect_withValidToken_succeeds() throws Exception {
        StompSession session = connect(JwtTestTokens.token(jwtSecret, UUID.randomUUID().toString(), "ROLE_USER"));
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void customerReceivesStaffMessageOverUserQueue() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Conversation conv = Conversation.builder()
                .id(UUID.randomUUID()).customerId(customer).customerName("Nam")
                .assignedStaffId(staff).status(ConversationStatus.ASSIGNED)
                .customerUnread(0).staffUnread(0).build();
        conversationRepo.insert(conv);

        StompSession session = connect(JwtTestTokens.token(jwtSecret, customer.toString(), "ROLE_USER"));
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(300); // let the SUBSCRIBE register on the broker

        // Staff sends via the service → best-effort push to the customer's /user/queue/messages.
        chatService.sendMessage(conv.getId(), staff, List.of("ROLE_STAFF"),
                new SendMessageRequest("m1", MessageType.TEXT, "xin chao khach", "Nhân viên"));

        Map<String, Object> pushed = received.poll(5, TimeUnit.SECONDS);
        assertThat(pushed).isNotNull();
        assertThat(pushed.get("content")).isEqualTo("xin chao khach");
        assertThat(pushed.get("senderRole")).isEqualTo("STAFF");
        session.disconnect();
    }
}
