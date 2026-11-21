package com.caramel.support.service;

/**
 * Events streamed to the client over SSE (contracts/chat-api.md).
 * Each event has a fixed SSE event name and a JSON payload.
 */
public sealed interface ChatEvent permits ChatEvent.Token, ChatEvent.Done, ChatEvent.Error {

    String eventName();

    /** Incremental piece of the assistant reply. */
    record Token(String text) implements ChatEvent {
        @Override
        public String eventName() {
            return "token";
        }
    }

    /** Reply complete. {@code escalation} is true when the reply referred the user to human support. */
    record Done(String requestId, boolean escalation, long latencyMs) implements ChatEvent {
        @Override
        public String eventName() {
            return "done";
        }
    }

    /** Reply failed. {@code message} is always user-facing text. */
    record Error(String requestId, ChatErrorCode code, String message) implements ChatEvent {
        @Override
        public String eventName() {
            return "error";
        }
    }
}
