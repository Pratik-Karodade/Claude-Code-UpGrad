# Data Model: Tech Support Chat Assistant

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-11-14

No database is used. All entities below are in-memory (backend) or in component state (frontend).

## Backend

### ConversationWindow

Holds the recent messages for one chat so the model can answer follow-ups (FR-004).

| Field | Type | Rules |
|---|---|---|
| `conversationId` | UUID string | Supplied by the client. Must be a valid UUID v4; otherwise the request is rejected. |
| `messages` | ordered list of `ChatMessage` | Maximum 20 entries. Oldest entries are dropped first. |
| `lastAccessedAt` | instant | Updated on every request. Entries idle for 30 minutes are evicted. |

**Lifecycle**: created on first request with a new `conversationId`; evicted by idle timeout; no
explicit delete endpoint (new chat = new `conversationId`, so the old window simply expires).

### ChatMessage

| Field | Type | Rules |
|---|---|---|
| `role` | enum: `USER`, `ASSISTANT` | `SYSTEM` is never stored in the window; the persona is added per call. |
| `content` | string | User content: 1 to 2,000 characters after trimming. Assistant content: model output. |
| `createdAt` | instant | Set when the message is added to the window. |

### ChatRequest (inbound)

| Field | Type | Rules |
|---|---|---|
| `conversationId` | UUID string | Required. |
| `message` | string | Required. Trimmed length 1 to 2,000 characters (FR-009). |

Validation failures return HTTP 400 before any model call. See [contracts/chat-api.md](contracts/chat-api.md).

### ChatEvent (outbound, SSE)

| Event | Payload fields | Meaning |
|---|---|---|
| `token` | `text` | Incremental piece of the assistant reply. |
| `done` | `requestId`, `escalation` (boolean), `latencyMs` | Reply complete. `escalation` is true when the reply contains a human-support referral. |
| `error` | `requestId`, `code`, `message` | Reply failed. `code` is one of the codes in the contract. |

### ChatProperties (configuration, `caramel.chat.*`)

| Key | Type | Default | Notes |
|---|---|---|---|
| `model` | string | `gemma-4-26b-a4b-it` | Fixed by PRD; configurable for testing. |
| `temperature` | double | `0.3` | Lower for factual support answers. |
| `max-tokens` | int | `1024` | Per reply. |
| `max-message-chars` | int | `2000` | Validation limit. |
| `memory-window` | int | `20` | Messages kept per conversation. |
| `memory-idle-timeout` | duration | `30m` | Eviction threshold. |
| `cors-allowed-origin` | string | `http://localhost:5173` | Frontend origin. |
| `google.api-key` | string | (none) | Bound from `${GOOGLE_API_KEY}`. Never has a default. Never logged. |

### Log record (observability)

Fields logged per request: `requestId`, `conversationId`, `status`, `latencyMs`, `errorCode`
(if any), `providerStatus` (if any). **Never logged**: `message`, reply `content`, or the API key.

## Frontend

### Message (UI state)

| Field | Type | Rules |
|---|---|---|
| `id` | string | Client-generated. |
| `role` | `'user' \| 'assistant'` | |
| `content` | string | Grows as tokens arrive. |
| `status` | `'streaming' \| 'complete' \| 'failed'` | Drives the streaming indicator and retry affordance (FR-002, FR-010). |

### ChatSession (UI state)

| Field | Type | Rules |
|---|---|---|
| `conversationId` | string | New UUID on mount and on "new chat". |
| `messages` | `Message[]` | Cleared on "new chat" (FR-005). Not persisted to storage (FR-011). |
| `isStreaming` | boolean | While true, the send action is disabled (edge case: send while streaming). |
| `error` | `{ code, message } \| null` | Shown in `ErrorBanner` with a retry action. |

## Relationships

- One `ChatSession` ↔ one `ConversationWindow` (linked by `conversationId`).
- One `ConversationWindow` contains up to 20 `ChatMessage` records.
- Each `ChatRequest` produces one `token` sequence and exactly one terminal event (`done` or `error`).
