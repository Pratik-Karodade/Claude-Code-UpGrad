package com.caramel.support.service;

import com.caramel.support.config.ChatProperties;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded, in-memory conversation windows keyed by client-generated conversation ID
 * (research R3). Each window keeps the last {@code memoryWindow} messages. A window
 * idle longer than {@code memoryIdleTimeout} is cleared. Nothing is persisted.
 */
@Component
public class ConversationMemory {

    private final ChatMemory window;
    private final Map<String, Instant> lastAccess = new ConcurrentHashMap<>();
    private final Duration idleTimeout;
    private final Clock clock;

    @Autowired
    public ConversationMemory(ChatProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ConversationMemory(ChatProperties properties, Clock clock) {
        this.clock = clock;
        this.idleTimeout = properties.memoryIdleTimeout();
        this.window = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(properties.memoryWindow())
                .build();
    }

    /** Returns the stored messages for the conversation, or an empty list if none. */
    public List<Message> history(String conversationId) {
        sweepIdle();
        lastAccess.put(conversationId, clock.instant());
        return window.get(conversationId);
    }

    /** Appends messages to the conversation window, dropping the oldest beyond the limit. */
    public void append(String conversationId, List<Message> messages) {
        sweepIdle();
        window.add(conversationId, messages);
        lastAccess.put(conversationId, clock.instant());
    }

    private void sweepIdle() {
        Instant cutoff = clock.instant().minus(idleTimeout);
        lastAccess.forEach((conversationId, last) -> {
            if (last.isBefore(cutoff)) {
                window.clear(conversationId);
                lastAccess.remove(conversationId, last);
            }
        });
    }
}
