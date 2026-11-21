package com.caramel.support.service;

import com.caramel.support.config.ChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryTest {

    private static final String ID = "3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c";

    /** A clock whose instant the test moves forward explicitly. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static ChatProperties props(int window, Duration idle) {
        return new ChatProperties("gemma-4-26b-a4b-it", 0.3, 1024, 2000, window, idle,
                "http://localhost:5173");
    }

    @Test
    void windowKeepsOnlyTheLastMessagesAndDropsOldestFirst() {
        ConversationMemory memory = new ConversationMemory(
                props(20, Duration.ofMinutes(30)), new MutableClock(Instant.parse("2026-11-14T10:00:00Z")));

        List<Message> batch = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            batch.add(new UserMessage("m" + i));
        }
        memory.append(ID, batch);

        List<Message> history = memory.history(ID);
        assertThat(history).hasSize(20);
        assertThat(history.get(0).getText()).isEqualTo("m5");
        assertThat(history.get(19).getText()).isEqualTo("m24");
    }

    @Test
    void idleConversationIsEvictedAfterTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));
        ConversationMemory memory = new ConversationMemory(props(20, Duration.ofMinutes(30)), clock);
        memory.append(ID, List.of(new UserMessage("hello")));

        clock.advance(Duration.ofMinutes(31));

        assertThat(memory.history(ID)).isEmpty();
    }

    @Test
    void activeConversationIsNotEvictedBeforeTimeout() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));
        ConversationMemory memory = new ConversationMemory(props(20, Duration.ofMinutes(30)), clock);
        memory.append(ID, List.of(new UserMessage("hello")));

        clock.advance(Duration.ofMinutes(20));
        memory.history(ID); // touch resets the idle timer
        clock.advance(Duration.ofMinutes(20));

        assertThat(memory.history(ID)).hasSize(1);
    }

    @Test
    void unknownConversationStartsEmpty() {
        ConversationMemory memory = new ConversationMemory(
                props(20, Duration.ofMinutes(30)), new MutableClock(Instant.parse("2026-11-14T10:00:00Z")));

        assertThat(memory.history("00000000-0000-4000-8000-000000000000")).isEmpty();
    }
}
