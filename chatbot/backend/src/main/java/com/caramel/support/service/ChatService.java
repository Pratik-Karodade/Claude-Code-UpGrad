package com.caramel.support.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates one chat turn: builds the prompt from the system persona and the
 * conversation window, streams model output as {@link ChatEvent.Token} events,
 * strips the escalation marker, and stores the completed turn in memory.
 *
 * <p>Logs carry request ID, conversation ID, latency, and error code only. Message
 * text, reply text, and the API key are never logged.
 */
@Service
public class ChatService {

    static final String ESCALATION_MARKER = "[[ESCALATE]]";

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChatModel chatModel;
    private final ChatOptions options;
    private final ConversationMemory memory;
    private final ErrorClassifier errorClassifier;
    private final String systemPrompt;

    public ChatService(
            ChatModel chatModel,
            ChatOptions caramelChatOptions,
            ConversationMemory memory,
            ErrorClassifier errorClassifier,
            @Value("classpath:prompts/system-prompt.txt") Resource systemPromptResource) throws IOException {
        this.chatModel = chatModel;
        this.options = caramelChatOptions;
        this.memory = memory;
        this.errorClassifier = errorClassifier;
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Streams one reply. The returned flux always ends with exactly one terminal
     * event: {@link ChatEvent.Done} on success or {@link ChatEvent.Error} on failure.
     */
    public Flux<ChatEvent> stream(String conversationId, String userMessage) {
        return Flux.defer(() -> {
            String requestId = newRequestId();
            long startNanos = System.nanoTime();
            EscalationStripper stripper = new EscalationStripper();
            StringBuilder reply = new StringBuilder();

            Prompt prompt = buildPrompt(conversationId, userMessage);

            Flux<ChatEvent> tokens = chatModel.stream(prompt)
                    .map(ChatService::textOf)
                    .filter(text -> !text.isEmpty())
                    .concatMap(chunk -> Flux.fromIterable(emit(stripper.push(chunk), reply)));

            Flux<ChatEvent> tail = Flux.defer(() -> {
                String rest = stripper.finish();
                return rest.isEmpty() ? Flux.empty() : Flux.fromIterable(emit(rest, reply));
            });

            Flux<ChatEvent> done = Flux.defer(() -> {
                long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
                memory.append(conversationId, List.of(
                        new UserMessage(userMessage),
                        new AssistantMessage(reply.toString())));
                log.info("requestId={} conversationId={} status=done latencyMs={} escalation={}",
                        requestId, conversationId, latencyMs, stripper.escalated());
                return Flux.just(new ChatEvent.Done(requestId, stripper.escalated(), latencyMs));
            });

            return Flux.concat(tokens, tail, done)
                    .onErrorResume(error -> {
                        ChatErrorCode code = errorClassifier.classify(error);
                        log.warn("requestId={} conversationId={} status=error errorCode={} exception={}",
                                requestId, conversationId, code, error.getClass().getSimpleName());
                        return Flux.just(new ChatEvent.Error(requestId, code, code.userMessage()));
                    });
        });
    }

    private Prompt buildPrompt(String conversationId, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(memory.history(conversationId));
        messages.add(new UserMessage(userMessage));
        return new Prompt(messages, options);
    }

    /** Appends non-empty text to the reply buffer and returns it as a token event list. */
    private static List<ChatEvent> emit(String text, StringBuilder reply) {
        if (text.isEmpty()) {
            return List.of();
        }
        reply.append(text);
        return List.of(new ChatEvent.Token(text));
    }

    private static String textOf(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    static String newRequestId() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("req_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Removes {@value #ESCALATION_MARKER} from streamed text and records whether it
     * appeared. A trailing partial prefix of the marker is held back until the next
     * chunk, so a marker split across chunks is still removed.
     */
    static final class EscalationStripper {

        private final StringBuilder pending = new StringBuilder();
        private boolean escalated;

        String push(String chunk) {
            pending.append(chunk);
            return drain(false);
        }

        String finish() {
            return drain(true);
        }

        boolean escalated() {
            return escalated;
        }

        private String drain(boolean flushAll) {
            String text = pending.toString();
            if (text.contains(ESCALATION_MARKER)) {
                escalated = true;
                text = text.replace(ESCALATION_MARKER, "");
            }
            int hold = flushAll ? 0 : partialMarkerSuffixLength(text);
            String emit = text.substring(0, text.length() - hold);
            pending.setLength(0);
            pending.append(text, text.length() - hold, text.length());
            return emit;
        }

        private static int partialMarkerSuffixLength(String text) {
            int max = Math.min(ESCALATION_MARKER.length() - 1, text.length());
            for (int len = max; len > 0; len--) {
                if (text.endsWith(ESCALATION_MARKER.substring(0, len))) {
                    return len;
                }
            }
            return 0;
        }
    }
}
