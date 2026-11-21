# Implementation Plan: Tech Support Chat Assistant

**Branch**: `001-tech-support-chat` | **Date**: 2026-11-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from [spec.md](spec.md), with the PRD in [prd.md](../../prd.md)
and principles in [constitution.md](../../.specify/memory/constitution.md).

## Summary

Deliver an MVP chat assistant that answers Caramel AI technical support questions in a browser.
A React + TypeScript single-page app sends each user message to a Spring Boot backend. The backend
calls `gemma-4-26b-a4b-it` through Google AI Studio using Spring AI, keeps a short in-memory
conversation window per chat, and streams the reply back over Server-Sent Events (SSE). The system
prompt sets the support persona, scope limits, and escalation behavior. No authentication,
persistence, or account lookups are included.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript 5.x (frontend)

**Primary Dependencies**: Spring Boot with Spring AI 2.0.x (Google GenAI starter) on the backend;
React 19 with Vite on the frontend. Exact versions are pinned in the build files at scaffold time.

**Storage**: None for chat content. Conversation memory is held in process memory with a bounded
window and idle expiry. No database.

**Testing**: JUnit 5 with Spring Boot test slices and MockMvc or WebTestClient for the backend;
Vitest with React Testing Library for the frontend; manual scenario runs against the real model
for SC-001 to SC-004.

**Target Platform**: Backend on Linux or macOS (local dev and a single server); frontend in current
evergreen desktop and mobile browsers.

**Project Type**: Web application (SPA frontend plus JSON/SSE backend API).

**Performance Goals**: First reply words visible within 2 s in 95% of attempts (SC-002). Backend
overhead, excluding provider latency, under 100 ms p95.

**Constraints**: API key only in server environment variables (constitution II). User message
limited to 2,000 characters. Conversation window limited to the last 20 messages. No chat content
persisted or logged. Single backend instance is sufficient for MVP.

**Scale/Scope**: MVP. Tens of concurrent chat sessions on one instance. Seven supported topic
areas, one assistant persona, one model.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How the plan complies |
|---|---|---|
| I. PRD Is the Source of Truth for Scope | PASS | Plan covers only PRD in-scope items. Auth, accounts, persistence, attachments, and ticketing are excluded. |
| II. Secrets Stay Server-Side | PASS | `GOOGLE_API_KEY` is read from the environment by the backend only. The frontend never receives it. `.mcp.json` token is noted for removal in Phase 0 (R6). |
| III. Layered Separation | PASS | Backend: `ChatController` (HTTP/SSE) → `ChatService` (orchestration, memory) → `ModelConfig` (Spring AI wiring). Frontend: components → `chatApi` client. |
| IV. Grounded, In-Scope Assistant Behavior | PASS | System prompt in `prompts/system-prompt.txt` defines scope, refusal, escalation, and no-account-data rules. Verified by manual scenarios (SC-001, SC-004). |
| V. Streaming, Resilience, Safe Observability | PASS | SSE streaming; 2,000-char validation; CORS allow-list from config; friendly error mapping; logs carry request ID and latency, never message text. |
| Test requirements (Workflow section) | PASS | Backend validation, service, and error mapping get unit and slice tests. Frontend streaming render and error states get component tests. |

**Result**: No violations. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-tech-support-chat/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── chat-api.md      # Phase 1 output: HTTP/SSE contract
├── checklists/
│   └── requirements.md  # From /speckit-specify
└── tasks.md             # Created later by /speckit-tasks, not by this command
```

### Source Code (repository root)

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/caramel/support/
    │   │   ├── SupportChatApplication.java
    │   │   ├── web/
    │   │   │   ├── ChatController.java          # POST /api/chat (SSE), request validation
    │   │   │   ├── ChatRequest.java
    │   │   │   └── ApiErrorHandler.java         # Maps exceptions to friendly error events/JSON
    │   │   ├── service/
    │   │   │   ├── ChatService.java             # Builds prompt, calls model, streams tokens
    │   │   │   ├── ConversationMemory.java      # Bounded per-conversation window, idle expiry
    │   │   │   └── ErrorClassifier.java         # Provider error → RATE_LIMITED / UNAVAILABLE
    │   │   └── config/
    │   │       ├── ModelConfig.java             # ChatModel wiring, model/temperature/max tokens
    │   │       ├── ChatProperties.java          # caramel.chat.* typed config
    │   │       └── CorsConfig.java              # Frontend origin allow-list
    │   └── resources/
    │       ├── application.yml                  # Non-secret defaults
    │       └── prompts/system-prompt.txt        # Persona, scope, escalation
    └── test/java/com/caramel/support/
        ├── web/ChatControllerTest.java
        ├── service/ChatServiceTest.java
        ├── service/ConversationMemoryTest.java
        └── service/ErrorClassifierTest.java

frontend/
├── package.json
├── vite.config.ts
└── src/
    ├── api/
    │   └── chatClient.ts                        # Only module that calls fetch / reads SSE
    ├── components/
    │   ├── ChatWindow.tsx                       # Layout, new-chat button
    │   ├── MessageList.tsx
    │   ├── MessageBubble.tsx
    │   ├── MessageInput.tsx                     # Send action, length check
    │   └── ErrorBanner.tsx                      # Friendly error text + retry
    ├── hooks/
    │   └── useChatSession.ts                    # State: messages, streaming, error
    ├── types/chat.ts
    └── App.tsx
    tests/
    ├── MessageList.test.tsx
    ├── MessageInput.test.tsx
    ├── ErrorBanner.test.tsx
    └── useChatSession.test.ts
```

**Structure Decision**: Web application with two top-level folders (`backend/`, `frontend/`).
Backend follows the controller, service, config split required by constitution III. Frontend
separates components, one API client, and one state hook. There are no shared packages; the
request and event shapes are documented once in [contracts/chat-api.md](contracts/chat-api.md)
and mirrored by hand in `types/chat.ts`.

## Phase 0: Research

Unknowns and decisions are recorded in [research.md](research.md). Summary:

- **R1**: Spring AI Google GenAI support for `gemma-4-26b-a4b-it` and whether a system instruction
  is honored. Fallback: prepend the system prompt to the first user turn.
- **R2**: SSE in Spring MVC: `Flux<ServerSentEvent>` versus `SseEmitter`. Decision: `Flux`.
- **R3**: Conversation memory: Spring AI `MessageWindowChatMemory` keyed by a client-generated
  conversation ID, with idle expiry.
- **R4**: Mapping Google provider errors (429, 5xx, timeouts) to user-facing error codes.
- **R5**: Frontend SSE consumption: `fetch` with a streamed body reader, because `EventSource`
  cannot send a POST body.
- **R6**: Removing the hardcoded token from `.mcp.json` (flagged in CLAUDE.md; not part of the
  feature build).

## Phase 1: Design & Contracts

- **Data model**: [data-model.md](data-model.md) (conversation window, chat message, request, event,
  error, configuration).
- **Contract**: [contracts/chat-api.md](contracts/chat-api.md) (`POST /api/chat` request and SSE
  events, error codes, CORS).
- **Validation guide**: [quickstart.md](quickstart.md) (local run and scenario checks mapped to
  spec user stories and success criteria).

## Post-Design Constitution Re-check

| Principle | Status | Note |
|---|---|---|
| I. Scope | PASS | Contract and model contain no auth, account, or persistence endpoints. |
| II. Secrets | PASS | Contract has no key field; configuration uses `${GOOGLE_API_KEY}` placeholder only. |
| III. Layering | PASS | Contract is transport-only; service types are not exposed to the controller response. |
| IV. Behavior | PASS | Refusal and escalation are prompt-level; the contract includes an `escalation` flag on the final event so the UI can show the support referral consistently. |
| V. Streaming/Observability | PASS | `done` and `error` events carry `requestId`; log fields defined in data-model.md exclude content. |

**Result**: All gates pass. No unresolved NEEDS CLARIFICATION remains.
