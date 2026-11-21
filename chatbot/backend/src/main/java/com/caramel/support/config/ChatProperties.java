package com.caramel.support.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed configuration for the support chat, bound from {@code caramel.chat.*}.
 * The Google API key is NOT part of this class; it is bound by Spring AI from
 * {@code spring.ai.google.genai.api-key} and must never be logged or returned.
 */
@Validated
@ConfigurationProperties(prefix = "caramel.chat")
public record ChatProperties(
        @NotBlank @DefaultValue("gemma-4-26b-a4b-it") String model,
        @DecimalMin("0.0") @DecimalMax("2.0") @DefaultValue("0.3") double temperature,
        @Positive @DefaultValue("1024") int maxTokens,
        @Positive @DefaultValue("2000") int maxMessageChars,
        @Min(1) @Max(50) @DefaultValue("20") int memoryWindow,
        @DefaultValue("30m") Duration memoryIdleTimeout,
        @NotBlank @DefaultValue("http://localhost:5173") String corsAllowedOrigin
) {
}
