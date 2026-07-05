package com.badmintonhub.chat.controller;

import com.badmintonhub.chat.AbstractChatIntegrationTest;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.test.JwtTestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatRestIT extends AbstractChatIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MongoTemplate mongoTemplate;

    @Value("${jwt.secret}") String jwtSecret;

    @BeforeEach
    void clean() {
        // Singleton container is reused across tests — clear documents (keep indexes from the initializer).
        mongoTemplate.remove(new Query(), Conversation.class);
        mongoTemplate.remove(new Query(), Message.class);
    }

    private String customerAuth(String userId) {
        return JwtTestTokens.bearer(jwtSecret, userId, "ROLE_USER");
    }

    private String staffAuth(String userId) {
        return JwtTestTokens.bearer(jwtSecret, userId, "ROLE_STAFF");
    }

    private String openConversation(String auth) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/chat/conversations").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nam\"}"))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private ResultActions send(String auth, String convId, String clientMsgId, String content) throws Exception {
        String body = "{\"clientMsgId\":\"" + clientMsgId + "\",\"content\":\"" + content + "\"}";
        return mockMvc.perform(post("/api/chat/conversations/" + convId + "/messages")
                .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void open_calledTwice_returnsSameThread_201then200() throws Exception {
        String auth = customerAuth(UUID.randomUUID().toString());

        MvcResult first = mockMvc.perform(post("/api/chat/conversations").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nam\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedStaffId").doesNotExist())
                .andReturn();
        String id = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/chat/conversations").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void send_sameClientMsgId_deduped_201then200_sameMessage() throws Exception {
        String customer = UUID.randomUUID().toString();
        String auth = customerAuth(customer);
        String convId = openConversation(auth);

        MvcResult first = send(auth, convId, "m1", "xin chao")
                .andExpect(status().isCreated())
                .andReturn();
        String msgId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        send(auth, convId, "m1", "xin chao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(msgId));
    }

    @Test
    void getMessages_keysetPagination_walksNewestToOldest() throws Exception {
        String customer = UUID.randomUUID().toString();
        String auth = customerAuth(customer);
        String convId = openConversation(auth);
        send(auth, convId, "m1", "one").andExpect(status().isCreated());
        send(auth, convId, "m2", "two").andExpect(status().isCreated());
        send(auth, convId, "m3", "three").andExpect(status().isCreated());

        MvcResult page1 = mockMvc.perform(get("/api/chat/conversations/" + convId + "/messages")
                        .param("limit", "2").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("three"))   // newest first
                .andExpect(jsonPath("$[1].content").value("two"))
                .andReturn();
        JsonNode arr = objectMapper.readTree(page1.getResponse().getContentAsString());
        String cursor = arr.get(1).get("id").asText();               // oldest shown

        mockMvc.perform(get("/api/chat/conversations/" + convId + "/messages")
                        .param("before", cursor).param("limit", "2").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].content").value("one"));
    }

    @Test
    void send_blankContent_returns400() throws Exception {
        String auth = customerAuth(UUID.randomUUID().toString());
        String convId = openConversation(auth);
        send(auth, convId, "m1", "   ").andExpect(status().isBadRequest());
    }

    @Test
    void getMessages_otherUsersThread_returns403() throws Exception {
        String owner = customerAuth(UUID.randomUUID().toString());
        String convId = openConversation(owner);
        String intruder = customerAuth(UUID.randomUUID().toString());

        mockMvc.perform(get("/api/chat/conversations/" + convId + "/messages").header("Authorization", intruder))
                .andExpect(status().isForbidden());
    }

    @Test
    void claim_secondStaff_returns409() throws Exception {
        String convId = openConversation(customerAuth(UUID.randomUUID().toString()));
        String staffA = staffAuth(UUID.randomUUID().toString());
        String staffB = staffAuth(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/chat/conversations/" + convId + "/claim").header("Authorization", staffA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc.perform(post("/api/chat/conversations/" + convId + "/claim").header("Authorization", staffB))
                .andExpect(status().isConflict());
    }

    @Test
    void unassignedQueue_requiresStaff_userForbidden() throws Exception {
        mockMvc.perform(get("/api/chat/conversations").param("queue", "unassigned")
                        .header("Authorization", customerAuth(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
    }
}
