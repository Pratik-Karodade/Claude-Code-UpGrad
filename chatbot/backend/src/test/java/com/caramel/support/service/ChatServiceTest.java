package com.caramel.support.service;

import com.caramel.support.config.ChatProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final String CONVERSATION = "3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ConversationMemory memory = new ConversationMemory(
            new ChatProperties("gemma-4-26b-a4b-it", 0.3, 1024, 2000, 20,
                    Duration.ofMinutes(30), "http://localhost:5173"));
    private final ChatService service = newService();

    private ChatService newService() {
        try {
            return new ChatService(
                    chatModel,
                    ChatOptions.builder().model("gemma-4-26b-a4b-it").temperature(0.3).maxTokens(1024).build(),
                    memory,
                    new ErrorClassifier(),
                    new ClassPathResource("prompts/system-prompt.txt"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void streamsTokensInOrderThenDone() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chunk("Hel"), chunk("lo "), chunk("world")));

        List<ChatEvent> events = service.stream(CONVERSATION, "hi").collectList().block();

        assertThat(events).hasSize(4);
        assertThat(events.get(0)).isEqualTo(new ChatEvent.Token("Hel"));
        assertThat(events.get(1)).isEqualTo(new ChatEvent.Token("lo "));
        assertThat(events.get(2)).isEqualTo(new ChatEvent.Token("world"));
        ChatEvent.Done done = (ChatEvent.Done) events.get(3);
        assertThat(done.requestId()).startsWith("req_");
        assertThat(done.escalation()).isFalse();
        assertThat(done.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void providerErrorYieldsSingleFriendlyErrorEvent() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("HTTP 429 secret-raw-detail")));

        List<ChatEvent> events = service.stream(CONVERSATION, "hi").collectList().block();

        assertThat(events).hasSize(1);
        ChatEvent.Error error = (ChatEvent.Error) events.get(0);
        assertThat(error.code()).isEqualTo(ChatErrorCode.RATE_LIMITED);
        assertThat(error.message()).isEqualTo(ChatErrorCode.RATE_LIMITED.userMessage());
        assertThat(error.message()).doesNotContain("secret-raw-detail");
    }

    @Test
    void secondTurnIncludesPriorUserAndAssistantMessages() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chunk("answer one")))
                .thenReturn(Flux.just(chunk("answer two")));

        service.stream(CONVERSATION, "question one").collectList().block();
        service.stream(CONVERSATION, "question two").collectList().block();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(captor.capture());
        List<Message> second = captor.getAllValues().get(1).getInstructions();

        assertThat(second).hasSize(4);
        assertThat(second.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(second.get(1).getText()).isEqualTo("question one");
        assertThat(second.get(2).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(second.get(2).getText()).isEqualTo("answer one");
        assertThat(second.get(3).getText()).isEqualTo("question two");
    }

    @Test
    void failedTurnIsNotStoredInMemory() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("HTTP 503")))
                .thenReturn(Flux.just(chunk("fine")));

        service.stream(CONVERSATION, "first").collectList().block();
        service.stream(CONVERSATION, "second").collectList().block();

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(captor.capture());
        List<Message> second = captor.getAllValues().get(1).getInstructions();

        // Only the system prompt and the new user message: the failed turn left no trace.
        assertThat(second).hasSize(2);
        assertThat(second.get(1).getText()).isEqualTo("second");
    }

    @Test
    void escalationMarkerIsRemovedAndFlagSet() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chunk("Please contact Caramel AI support.\n[[ESCALATE]]")));

        List<ChatEvent> events = service.stream(CONVERSATION, "help").collectList().block();

        String streamed = events.stream()
                .filter(e -> e instanceof ChatEvent.Token)
                .map(e -> ((ChatEvent.Token) e).text())
                .reduce("", String::concat);
        assertThat(streamed).doesNotContain("ESCALATE").doesNotContain("[[");
        assertThat(((ChatEvent.Done) events.get(events.size() - 1)).escalation()).isTrue();
    }

    @Test
    void escalationMarkerSplitAcrossChunksIsRemoved() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chunk("Let me escalate. [[ESC"), chunk("ALATE]]")));

        List<ChatEvent> events = service.stream(CONVERSATION, "help").collectList().block();

        String streamed = events.stream()
                .filter(e -> e instanceof ChatEvent.Token)
                .map(e -> ((ChatEvent.Token) e).text())
                .reduce("", String::concat);
        assertThat(streamed).isEqualTo("Let me escalate. ");
        assertThat(((ChatEvent.Done) events.get(events.size() - 1)).escalation()).isTrue();
    }

    @Test
    void stripperHoldsBackPartialMarkerUntilResolved() {
        ChatService.EscalationStripper stripper = new ChatService.EscalationStripper();

        assertThat(stripper.push("abc [[")).isEqualTo("abc ");
        assertThat(stripper.push("ESCALATE]] done")).isEqualTo(" done");
        assertThat(stripper.finish()).isEmpty();
        assertThat(stripper.escalated()).isTrue();
    }

    @Test
    void stripperFlushesHeldPrefixThatIsNotAMarker() {
        ChatService.EscalationStripper stripper = new ChatService.EscalationStripper();

        assertThat(stripper.push("price is [[")).isEqualTo("price is ");
        assertThat(stripper.finish()).isEqualTo("[[");
        assertThat(stripper.escalated()).isFalse();
    }
}
