package com.caramel.support.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Research R1 verification. Calls the real gemma-4-26b-a4b-it model through Spring AI.
 * Skipped unless GOOGLE_API_KEY is set, so default builds never hit the provider.
 * Run manually: GOOGLE_API_KEY=... mvn test -Dtest=GemmaSmokeTest
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class GemmaSmokeTest {

    @Autowired
    private ChatModel chatModel;

    @Test
    void modelRespondsWithNonEmptyText() {
        String text = chatModel.call(new Prompt("Reply with the word OK.")).getResult().getOutput().getText();
        assertThat(text).isNotBlank();
    }

    @Test
    void systemInstructionIsHonored() {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("Reply with only the word PONG and nothing else."),
                new UserMessage("ping")));
        String text = chatModel.call(prompt).getResult().getOutput().getText();
        assertThat(text).containsIgnoringCase("PONG");
    }
}
