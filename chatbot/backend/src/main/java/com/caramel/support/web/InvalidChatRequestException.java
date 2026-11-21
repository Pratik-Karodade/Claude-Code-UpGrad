package com.caramel.support.web;

/**
 * Raised when a request passes bean validation but breaks a configured limit,
 * such as a message longer than {@code caramel.chat.max-message-chars}.
 */
public class InvalidChatRequestException extends RuntimeException {

    public InvalidChatRequestException(String userMessage) {
        super(userMessage);
    }
}
