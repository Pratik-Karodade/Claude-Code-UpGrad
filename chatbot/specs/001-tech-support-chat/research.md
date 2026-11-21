# Research: Tech Support Chat Assistant

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-14

Each entry lists the decision, the rationale, and the alternatives considered.

## R1: Gemma model access and system instructions via Spring AI

- **Decision**: Use the Spring AI Google GenAI integration with model id `gemma-4-26b-a4b-it`,
  passing the system prompt as a system message. Before building the persona on top of it, run a
  one-off smoke test that confirms (a) the model responds through the integration, and (b) the
  system message changes behavior. If (b) fails, prepend the system prompt to the first user turn
  inside `ChatService` and keep everything else unchanged.
- **Rationale**: The PRD fixes both the model and the provider. Gemma models served through AI
  Studio may handle system-level instructions differently from other models, so this is verified
  rather than assumed.
- **Alternatives considered**: Switching to a Gemini model (rejected: PRD fixes the model; changing
  it requires a PRD amendment under constitution I).

## R2: Streaming transport in Spring MVC

- **Decision**: `POST /api/chat` returns `Flux<ServerSentEvent<...>>`, backed by Spring AI's
  streaming chat call.
- **Rationale**: Reactive return types integrate directly with the streaming model API and let
  the controller stay thin (constitution III).
- **Alternatives considered**: `SseEmitter` with manual threading (more code, more error paths);
  WebSocket (bidirectional channel not needed; violates the SSE mandate in constitution V).

## R3: Conversation memory

- **Decision**: Spring AI `MessageWindowChatMemory` with a window of 20 messages, one instance per
  `conversationId`, held in a concurrent map. Entries expire after 30 minutes of inactivity. The
  frontend generates the `conversationId` (UUID v4) when a chat starts and creates a new one for
  "new chat".
- **Rationale**: Satisfies FR-004 and FR-005 and the PRD's session-scoped memory with no database.
  The window bounds token cost and memory use.
- **Alternatives considered**: Server-generated session cookie (adds session handling not needed
  for MVP); a database-backed store (violates the no-persistence scope); sending full history from
  the client each turn (simpler, but the server would then trust client-supplied assistant turns).

## R4: Provider error classification

- **Decision**: `ErrorClassifier` maps provider failures to three user-facing codes:
  `RATE_LIMITED` (HTTP 429 or quota errors), `PROVIDER_UNAVAILABLE` (5xx, connection errors,
  timeouts), and `INTERNAL` (anything else). Raw provider messages are logged with the request ID
  but never sent to the client.
- **Rationale**: Matches FR-010 and SC-005; keeps provider details out of the UI.
- **Alternatives considered**: Forwarding provider messages (leaks internals, violates SC-005);
  a single generic error (loses the distinction the spec requires for rate limits).

## R5: Consuming a streamed POST in the browser

- **Decision**: The frontend `chatClient.ts` uses `fetch` with `POST` and reads
  `response.body` through a `ReadableStream` reader, parsing `event:`/`data:` frames itself.
- **Rationale**: The browser `EventSource` API supports only GET and cannot send the JSON body.
- **Alternatives considered**: `EventSource` with the message in the query string (length limits,
  and message text would appear in URLs and logs); a third-party SSE client library (an extra
  dependency for a small parser; constitution III asks for minimal dependencies).

## R6: Hardcoded token in `.mcp.json`

- **Decision**: Not part of this feature build. Record it as a follow-up: rotate the token, move it
  to an environment variable, and keep `.mcp.json` out of git. The GitHub MCP server currently
  fails with HTTP 401 (credential expired), which is consistent with that token being stale.
- **Rationale**: Constitution II and CLAUDE.md both call for this. It is a repository hygiene task,
  not a chat-assistant requirement.
- **Alternatives considered**: Editing `.mcp.json` as part of this plan (out of scope for the
  feature; touches developer tooling rather than the product).

## Resolved unknowns

No `NEEDS CLARIFICATION` items remain from the Technical Context. R1 contains one verification
step that must pass before the persona is tuned.
