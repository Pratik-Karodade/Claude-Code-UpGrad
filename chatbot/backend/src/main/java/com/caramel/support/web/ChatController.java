package com.caramel.support.web;

import com.caramel.support.config.ChatProperties;
import com.caramel.support.service.ChatEvent;
import com.caramel.support.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * HTTP and SSE transport only. Business logic lives in {@link ChatService}.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final ChatProperties properties;

    public ChatController(ChatService chatService, ChatProperties properties) {
        this.chatService = chatService;
        this.properties = properties;
    }

    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> chat(@Valid @RequestBody ChatRequest request) {
        if (request.message().length() > properties.maxMessageChars()) {
            throw new InvalidChatRequestException(
                    "Please enter a message of up to " + properties.maxMessageChars() + " characters.");
        }
        return chatService.stream(request.conversationId(), request.message())
                .map(ChatController::toSse);
    }

    static ServerSentEvent<Object> toSse(ChatEvent event) {
        return ServerSentEvent.builder((Object) event)
                .event(event.eventName())
                .build();
    }
}
