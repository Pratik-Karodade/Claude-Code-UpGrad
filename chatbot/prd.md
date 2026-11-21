# PRD: Caramel AI Tech Support Chat Assistant

## 1. Objective

Build a customer service chat assistant for **Tech Support on LLM Inferencing**. The assistant serves customers of **Caramel AI**, an LLM inferencing company similar to OpenRouter. It answers technical questions about using the platform, such as API usage, model availability, rate limits, latency, errors, and billing.

## 2. Tech Stack

| Layer | Choice |
|---|---|
| Backend | Java |
| Framework | Spring AI |
| Frontend | React + TypeScript |
| LLM | `gemma-4-26b-a4b-it` |
| Service provider | Google, via AI Studio |

## 3. Target Users

- Developers and teams integrating Caramel AI's inference API.
- Existing customers troubleshooting production issues.
- Prospects evaluating the platform.

## 4. Scope

### In scope (MVP)

- Chat UI with message history for the current session.
- Streaming responses from the LLM.
- System prompt that sets the assistant's persona as a Caramel AI tech support agent.
- Topics covered: API keys and auth, model routing and selection, rate limits and quotas, error codes (4xx/5xx, timeouts), latency and throughput, streaming and request formats, pricing and billing basics.
- Graceful handling of out-of-scope questions (politely redirect).
- Escalation message pointing to human support when the assistant cannot resolve an issue.

### Out of scope (MVP)

- User authentication and accounts.
- Access to real customer data or live account lookups.
- Persistent chat history across sessions.
- Voice or file attachments.
- Ticketing system integration.

## 5. Functional Requirements

1. **Chat endpoint**: the backend exposes a REST endpoint (for example `POST /api/chat`) that accepts a user message and conversation context, and streams back the reply (SSE).
2. **Conversation memory**: use Spring AI chat memory to keep multi-turn context within a session.
3. **System prompt**: defines tone (concise, professional, friendly), scope, and refusal and escalation behavior.
4. **Model config**: model, API key, and parameters (temperature, max tokens) are set through Spring configuration and environment variables. The API key is never committed.
5. **Frontend**: React + TypeScript chat interface with message list, input box, send action, streaming render, loading and error states, and a "new chat" button.
6. **Error handling**: the UI shows friendly messages for network, rate-limit, and provider errors.

## 6. Non-Functional Requirements

- **Latency**: first token in under ~2s under normal conditions.
- **Security**: API key held server-side only. Basic input validation and message length limits. CORS restricted to the frontend origin.
- **Observability**: log request IDs, latency, and provider errors, without logging sensitive content.
- **Maintainability**: clear separation of controller, service, and config layers. The frontend is split into components and an API client.

## 7. Architecture (High Level)

```
React + TS UI  --(HTTP/SSE)-->  Spring Boot + Spring AI  -->  Google AI Studio (gemma-4-26b-a4b-it)
```

## 8. Success Metrics

- Assistant answers on-topic technical questions accurately in manual test scenarios.
- Multi-turn conversations keep context correctly.
- Out-of-scope questions are redirected rather than answered.
- End-to-end chat works locally with a single configuration step (API key).

## 9. Open Questions

- Should the assistant be grounded on Caramel AI documentation (RAG) in a later phase?
- What is the human escalation channel (email, ticket link, live chat)?
- Is chat history persistence or analytics needed after MVP?
