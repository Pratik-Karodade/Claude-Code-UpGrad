package com.caramel.support.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the per-request model options used for every chat call.
 * The ChatModel bean itself is created by the Spring AI Google GenAI starter from
 * {@code spring.ai.google.genai.*} properties.
 */
@Configuration
public class ModelConfig {

    /**
     * Options applied to every Prompt. Prompt-level options fully override the
     * ChatModel defaults, so all three values are set here explicitly.
     */
    @Bean
    public ChatOptions caramelChatOptions(ChatModel chatModel, ChatProperties properties) {
        // mutate() keeps the provider's options class. The Google model casts call options to
        // GoogleGenAiChatOptions, so building a generic ChatOptions here fails at call time.
        return chatModel.getDefaultOptions().mutate()
                .model(properties.model())
                .temperature(properties.temperature())
                .maxTokens(properties.maxTokens())
                .build();
    }

    /**
     * Fails startup when GOOGLE_API_KEY is not set. Without this check the
     * unresolved placeholder would reach the provider and surface as a confusing
     * authentication error at chat time.
     */
    @Bean
    public InitializingBean googleApiKeyCheck(Environment environment) {
        return () -> {
            // getProperty leaves an unset ${GOOGLE_API_KEY} placeholder as literal text, which we detect below.
            String apiKey = environment.getProperty("spring.ai.google.genai.api-key");
            if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
                throw new IllegalStateException(
                        "GOOGLE_API_KEY is not set. Export it in the environment before starting the backend.");
            }
        };
    }
}
