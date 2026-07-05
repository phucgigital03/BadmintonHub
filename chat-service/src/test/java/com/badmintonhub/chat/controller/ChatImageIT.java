package com.badmintonhub.chat.controller;

import com.badmintonhub.chat.AbstractChatIntegrationTest;
import com.badmintonhub.chat.entity.Conversation;
import com.badmintonhub.chat.entity.Message;
import com.badmintonhub.test.JwtTestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-CHAT-04 image upload (Cloudinary runs in degrade mode in tests → local-fallback:// URL). */
class ChatImageIT extends AbstractChatIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MongoTemplate mongoTemplate;

    @Value("${jwt.secret}") String jwtSecret;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), Conversation.class);
        mongoTemplate.remove(new Query(), Message.class);
    }

    private String customerAuth(String userId) {
        return JwtTestTokens.bearer(jwtSecret, userId, "ROLE_USER");
    }

    private String openConversation(String auth) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/chat/conversations").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Nam\"}"))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private MockMultipartFile png(String clientMsgId) {
        return new MockMultipartFile("file", "chuyen-khoan.png", "image/png", ("PNG" + clientMsgId).getBytes());
    }

    @Test
    void sendImage_validPng_createsImageMessage() throws Exception {
        String auth = customerAuth(UUID.randomUUID().toString());
        String convId = openConversation(auth);

        mockMvc.perform(multipart("/api/chat/conversations/" + convId + "/images")
                        .file(png("i1")).param("clientMsgId", "i1").param("caption", "bill")
                        .header("Authorization", auth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("local-fallback://chat/")))
                .andExpect(jsonPath("$.content").value("bill"));
    }

    @Test
    void sendImage_unsupportedMime_returns415() throws Exception {
        String auth = customerAuth(UUID.randomUUID().toString());
        String convId = openConversation(auth);
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "PDF".getBytes());

        mockMvc.perform(multipart("/api/chat/conversations/" + convId + "/images")
                        .file(pdf).param("clientMsgId", "i1").header("Authorization", auth))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void sendImage_over5MB_returns413() throws Exception {
        // §G.4 size cap — MockMvc bypasses the servlet multipart limit, so the bytes reach validateImage.
        String auth = customerAuth(UUID.randomUUID().toString());
        String convId = openConversation(auth);
        MockMultipartFile oversize =
                new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/chat/conversations/" + convId + "/images")
                        .file(oversize).param("clientMsgId", "big").header("Authorization", auth))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void sendImage_sameClientMsgId_deduped_201then200() throws Exception {
        String auth = customerAuth(UUID.randomUUID().toString());
        String convId = openConversation(auth);

        MvcResult first = mockMvc.perform(multipart("/api/chat/conversations/" + convId + "/images")
                        .file(png("dup")).param("clientMsgId", "dup").header("Authorization", auth))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(multipart("/api/chat/conversations/" + convId + "/images")
                        .file(png("dup")).param("clientMsgId", "dup").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }
}
