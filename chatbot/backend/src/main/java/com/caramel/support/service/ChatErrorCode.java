package com.caramel.support.service;

/**
 * User-facing error codes from contracts/chat-api.md. Each code carries the
 * friendly message shown to the customer. Raw provider text is never exposed.
 */
public enum ChatErrorCode {

    VALIDATION_ERROR("Please enter a message of up to 2,000 characters."),
    RATE_LIMITED("We're receiving a lot of requests right now. Please try again in a moment."),
    PROVIDER_UNAVAILABLE("The assistant is temporarily unavailable. Please try again shortly."),
    INTERNAL("Something went wrong. Please try again.");

    private final String userMessage;

    ChatErrorCode(String userMessage) {
        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }
}
