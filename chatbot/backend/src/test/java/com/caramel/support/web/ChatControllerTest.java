package com.caramel.support.web;

import com.caramel.support.config.ChatProperties;
import com.caramel.support.config.CorsConfig;
import com.caramel.support.service.ChatEvent;
import com.caramel.support.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({ApiErrorHandler.class, CorsConfig.class, ChatControllerTest.Config.class})
class ChatControllerTest {

    private static final String UUID_V4 = "3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c";

    @TestConfiguration
    @EnableConfigurationProperties(ChatProperties.class)
    static class Config {
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChatService chatService;

    private static String body(String conversationId, String message) {
        return "{\"conversationId\":\"" + conversationId + "\",\"message\":\"" + message + "\"}";
    }

    @Test
    void validRequestStreamsEventStream() throws Exception {
        when(chatService.stream(anyString(), anyString()))
                .thenReturn(Flux.just(new ChatEvent.Token("Hi"),
                        new ChatEvent.Done("req_1", false, 12)));

        MvcResult result = mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body(UUID_V4, "How do I rotate my key?")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    void emptyMessageIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID_V4, "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void messageOverLimitIsRejectedWith400() throws Exception {
        String tooLong = "a".repeat(2001);
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID_V4, tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Please enter a message of up to 2000 characters."));
    }

    @Test
    void nonUuidConversationIdIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-a-uuid", "hello")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
