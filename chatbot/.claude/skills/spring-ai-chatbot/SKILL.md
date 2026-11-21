---
name: spring-ai-chatbot
description: Build Java chatbots with Spring AI (v2.0.x) on Spring Boot. Use when implementing AI chat endpoints, calling or streaming LLM completions with ChatModel or StreamingChatModel, building Prompts from SystemMessage, UserMessage and AssistantMessage, configuring LLM providers (OpenAI, Anthropic, Ollama, Amazon Bedrock, Mistral AI, Google GenAI) and API keys, overriding ChatOptions per request, or handling SSE and Flux streaming responses in Java.
---

# Spring AI Chatbot (v2.0.x)

Source: Spring AI Chat Model API reference, https://docs.spring.io/spring-ai/reference/api/chatmodel.html

Spring AI exposes one portable chat abstraction over many providers. Write business logic against `ChatModel` and `StreamingChatModel` (or `ChatClient`), and pick the provider with a Spring Boot starter plus configuration.

Verify exact starter artifact IDs and property names for the chosen provider on its reference page before writing them. They are the part most likely to differ between versions.

## Core API

### Interfaces

```java
public interface ChatModel extends Model<Prompt, ChatResponse>, StreamingChatModel {
    default String call(String message) {...}   // convenience
    ChatResponse call(Prompt prompt);           // the real entry point
}

public interface StreamingChatModel extends StreamingModel<Prompt, ChatResponse> {
    default Flux<String> stream(String message) {...}
    Flux<ChatResponse> stream(Prompt prompt);
}
```

`ChatModel` already extends `StreamingChatModel`, so one injected `ChatModel` bean supports both blocking and streaming calls.

### Synchronous completion

```java
@Service
public class ChatService {

    private final ChatModel chatModel;

    public ChatService(ChatModel chatModel) {      // constructor injection
        this.chatModel = chatModel;
    }

    public String ask(String question) {
        return chatModel.call(question);           // call(String) -> String
    }

    public String askWithPrompt(Prompt prompt) {
        ChatResponse response = chatModel.call(prompt);
        Generation generation = response.getResult();          // first Generation
        return generation.getOutput().getText();               // AssistantMessage text
    }
}
```

Response structure:
- `ChatResponse.getResults()` returns `List<Generation>`. `getResult()` returns the first.
- `Generation.getOutput()` returns an `AssistantMessage`. Read the text with `getText()`.
- `Generation.getMetadata()` returns `ChatGenerationMetadata`, which has the finish reason.
- `ChatResponse.getMetadata()` returns `ChatResponseMetadata`, which has usage and model info.

### Streaming

```java
public Flux<String> streamText(String question) {
    return chatModel.stream(question);                         // Flux<String>
}

public Flux<ChatResponse> streamResponses(Prompt prompt) {
    return chatModel.stream(prompt);                           // Flux<ChatResponse>
}
```

Expose it as Server-Sent Events:

```java
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatModel chatModel;

    public ChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return chatModel.stream(request.message());
    }

    public record ChatRequest(String message) {}
}
```

Streaming guidelines:
- Return the `Flux` and let Spring subscribe. Never call `.block()` on a request thread when streaming.
- Add `.onErrorResume(...)` to emit a friendly error event instead of a broken stream.
- Each `ChatResponse` chunk holds a partial `Generation`. To rebuild the full text, collect the chunk text yourself, for example with `.collectList()` or `.reduce`.
- Spring MVC supports `Flux` return types, so Spring WebFlux is not required for SSE. Check that the reactor dependency comes in transitively with the starter.

### Prompts and message roles

A `Prompt` wraps a `List<Message>` plus optional `ChatOptions`. Each `Message` has a `MessageType` that gives its role.

```java
Prompt prompt = new Prompt(List.of(
    new SystemMessage("You are a support assistant for Caramel AI. Be concise."),
    new UserMessage("What is a context window?"),
    new AssistantMessage("It is the maximum number of tokens a model can consider."),
    new UserMessage("And how does it relate to max tokens?")
));
ChatResponse response = chatModel.call(prompt);
```

| Class | Role | Use |
|---|---|---|
| `SystemMessage` | system | Persona, rules, escalation behavior. Put it first. |
| `UserMessage` | user | End-user input. For models without distinct roles it is the default type. |
| `AssistantMessage` | assistant | Prior model replies, to keep multi-turn context. |

`Message` extends `Content` (`getText()`, `getMetadata()`) and adds `getMessageType()`. Multimodal messages also implement `MediaContent` (`getMedia()`).

Chat models are stateless. For multi-turn chat you must resend the earlier turns in every `Prompt`, or use Spring AI chat memory with `ChatClient`.

## Configuration and options

### Start-up defaults (application.yml)

Defaults are set when the `ChatModel` bean is created:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.3
```

Property prefixes differ per provider (`spring.ai.anthropic.*`, `spring.ai.ollama.*`, `spring.ai.mistralai.*`, `spring.ai.bedrock.*`, and so on). Check the provider page.

### Runtime overrides with ChatOptions

`ChatOptions` carries the portable options: `model`, `frequencyPenalty`, `maxTokens`, `presencePenalty`, `stopSequences`, `temperature`, `topK`, `topP`. Providers add their own (OpenAI has `logitBias`, `seed`, `user`).

```java
Prompt prompt = new Prompt(
    "Summarize this ticket.",
    ChatOptions.builder()
        .model("gpt-4o")
        .temperature(0.1)
        .maxTokens(300)
        .build());
ChatResponse response = chatModel.call(prompt);
```

Precedence rules, which are easy to get wrong:

1. **Options in a `Prompt` fully override the start-up options on `ChatModel`.** Treat them as a complete set, not a patch. Anything you leave unset does not reliably inherit your configured default.
2. **Pass `null` options (or use the `Prompt` constructors without options) to use the model defaults.**
3. **`ChatClient` customizes incrementally.** Its default options plus per-request `.options(...)` customizers apply as a delta on top of the defaults. Prefer `ChatClient` when you want to change one setting per request.
4. To derive new options from existing ones, use `ChatOptions.mutate()` and change only what you need.
5. Use provider-specific option classes (such as `OpenAiChatOptions`) only when you need provider-only settings. They tie that call site to the provider.

### ChatClient for incremental customization

```java
@Configuration
class ChatConfig {
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("You are a support assistant for Caramel AI.")
            .build();
    }
}

String answer = chatClient.prompt()
    .user(question)
    .options(ChatOptions.builder().temperature(0.0))   // delta over defaults
    .call()
    .content();

Flux<String> stream = chatClient.prompt().user(question).stream().content();
```

Use `ChatModel` for low-level control over `Prompt` and `ChatResponse`. Use `ChatClient` for a fluent API, default system prompts, advisors, chat memory, and delta options. Confirm the builder's exact `.options(...)` signature for your version.

## Multi-provider portability

Business logic depends only on `ChatModel` / `StreamingChatModel`, so changing providers means changing the starter dependency and the properties. No service or controller code changes.

Supported implementations listed in the reference:

| Provider | Notes |
|---|---|
| OpenAI | streaming, multimodal, function calling |
| Anthropic | streaming, function calling. See the Anthropic migration page for the official Java SDK move. |
| Ollama | local models, streaming, multimodal, function calling |
| Amazon Bedrock (Converse) | AWS credentials and region config |
| Mistral AI | streaming, function calling |
| Google GenAI, Azure OpenAI, DeepSeek, Groq, and others | listed in the docs sidebar |

Steps to swap:
1. Replace the starter in `pom.xml` / `build.gradle` (for example `spring-ai-starter-model-openai` with `spring-ai-starter-model-anthropic`). Confirm artifact names in the provider docs.
2. Replace the `spring.ai.<provider>.*` properties and the environment variable holding the key.
3. Keep `ChatModel` injection and the `Prompt` code unchanged.

Rules for portability:
- Import `org.springframework.ai.chat.model.ChatModel` and the portable `ChatOptions`, not provider classes, in business code.
- Do not put more than one provider starter on the classpath unless you select one deliberately (for example with `@Qualifier` or `spring.ai.model.chat`). Otherwise Spring finds several `ChatModel` beans and injection is ambiguous.
- Model names, token limits, and feature support (multimodality, tool calling) differ per provider. Test after swapping.
- Use the BOM `org.springframework.ai:spring-ai-bom` to pin all Spring AI artifacts to one version (2.0.x).

## Best practices and guardrails

**Dependency injection**
- Inject `ChatModel`, `StreamingChatModel`, or `ChatClient` through the constructor. No field `@Autowired`, and do not construct provider models by hand with `new OpenAiChatModel(...)`.
- Depend on the interface. This keeps services mockable. In tests, mock `ChatModel`, and build `ChatResponse` and `Generation` fixtures directly.

**API keys and secrets**
- Never hardcode keys or commit them to `application.yml`. Reference environment variables: `api-key: ${PROVIDER_API_KEY}`.
- Keep keys server-side. Browsers call your endpoint, never the provider.
- Keep local secrets in untracked files (`.env`, `application-local.yml`) and add them to `.gitignore`.
- Do not log prompts or keys at INFO level when they may contain user data.

**Robustness**
- Set timeouts and a `maxTokens` limit.
- Handle provider errors (rate limits, auth failures) with a `@ControllerAdvice` and return a clean error to the client.
- Treat user input as untrusted. Keep instructions in `SystemMessage`, and do not concatenate user text into the system prompt.
- Cap the length of the conversation history you resend.

**Avoid deprecated or outdated patterns**
- Old starter names such as `spring-ai-openai-spring-boot-starter` are replaced by the `spring-ai-starter-model-*` naming. Check the current names.
- `getContent()` on messages and `AssistantMessage` is replaced by `getText()`.
- Old `withXxx(...)` option builder methods (`withModel`, `withTemperature`) are replaced by `model(...)`, `temperature(...)`. Use builders, not setters.
- Do not use `ChatClient` or `ChatModel` types from pre-1.0 milestone packages. If code or a snippet does not compile, check the Upgrade Notes page of the reference rather than guessing.
- Do not rely on memory of older APIs. When unsure, check the reference for the project's version.
