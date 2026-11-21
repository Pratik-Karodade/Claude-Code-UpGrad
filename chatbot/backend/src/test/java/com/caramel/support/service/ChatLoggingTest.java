package com.caramel.support.service;

import com.caramel.support.config.ChatProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Constitution V: logs carry request ID, latency, and error codes, and never message text.
 */
@ExtendWith(OutputCaptureExtension.class)
class ChatLoggingTest {

    private static final String USER_SECRET = "USER_SECRET_QUESTION_9931";
    private static final String REPLY_SECRET = "REPLY_SECRET_ANSWER_4417";

    @Test
    void successfulTurnLogsMetadataButNotContent(CapturedOutput output) throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(new ChatResponse(
                        List.of(new Generation(new AssistantMessage(REPLY_SECRET))))));
        ChatService service = new ChatService(
                chatModel,
                ChatOptions.builder().model("gemma-4-26b-a4b-it").build(),
                new ConversationMemory(new ChatProperties("gemma-4-26b-a4b-it", 0.3, 1024, 2000, 20,
                        Duration.ofMinutes(30), "http://localhost:5173")),
                new ErrorClassifier(),
                new ClassPathResource("prompts/system-prompt.txt"));

        service.stream("3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c", USER_SECRET).collectList().block();

        assertThat(output.getAll())
                .contains("requestId=req_")
                .contains("latencyMs=")
                .doesNotContain(USER_SECRET)
                .doesNotContain(REPLY_SECRET);
    }

    @Test
    void errorTurnLogsErrorCodeButNotProviderText(CapturedOutput output) throws Exception {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("HTTP 429 " + USER_SECRET)));
        ChatService service = new ChatService(
                chatModel,
                ChatOptions.builder().model("gemma-4-26b-a4b-it").build(),
                new ConversationMemory(new ChatProperties("gemma-4-26b-a4b-it", 0.3, 1024, 2000, 20,
                        Duration.ofMinutes(30), "http://localhost:5173")),
                new ErrorClassifier(),
                new ClassPathResource("prompts/system-prompt.txt"));

        service.stream("3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c", "hello").collectList().block();

        assertThat(output.getAll())
                .contains("errorCode=RATE_LIMITED")
                .doesNotContain(USER_SECRET);
    }
}
