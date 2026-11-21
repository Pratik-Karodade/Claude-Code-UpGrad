package com.caramel.support.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Inbound chat request. The message is stripped before validation, so the
 * length rule applies to the trimmed text (data-model.md: "Trimmed length").
 * The maximum length is checked in the controller against
 * {@code caramel.chat.max-message-chars}, because annotation values must be constants.
 */
public record ChatRequest(
        @NotBlank(message = "Invalid conversation ID.")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "Invalid conversation ID.")
        String conversationId,

        @NotBlank(message = "Please enter a message before sending.")
        String message) {

    public ChatRequest {
        if (message != null) {
            message = message.strip();
        }
    }
}
