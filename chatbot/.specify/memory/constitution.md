<!--
Sync Impact Report
- Version change: (unfilled template) → 1.0.0
- Modified principles: none renamed (all principles newly defined)
- Added sections: Core Principles I–V, Technology & Security Constraints,
  Development Workflow & Quality Gates, Governance
- Removed sections: none
- Follow-up TODOs: none deferred. Open product questions from prd.md §9 (RAG grounding,
  human escalation channel, persistence/analytics) are intentionally not governed here.
-->
# Caramel AI Tech Support Chat Assistant Constitution

## Core Principles

### I. PRD Is the Source of Truth for Scope
[prd.md](../../prd.md) defines MVP scope. Work outside its "In scope" list MUST NOT be built.
Items listed as out of scope (authentication and accounts, live account lookups, persistent
history across sessions, voice or file attachments, ticketing integration) MUST NOT be
implemented until the PRD is amended first.
Rationale: a small, well-bounded MVP is the fastest path to a working end-to-end chat.

### II. Secrets Stay Server-Side
The Google AI Studio API key MUST be held only by the backend and supplied through environment
variables and Spring configuration. It MUST NOT be committed, bundled into the frontend, logged,
or returned in any response. Any other credential (including tokens in `.mcp.json`) MUST be
read from the environment and MUST NOT be copied into other files.
Rationale: a leaked key is directly billable and cannot be recalled once pushed.

### III. Layered Separation
The backend MUST separate controller, service, and config layers; controllers handle HTTP/SSE
only, and model, prompt, and memory logic live in the service and config layers. The frontend
MUST be split into UI components and a dedicated API client; components MUST NOT call `fetch`
directly. Model name, temperature, max tokens, and CORS origin MUST be configurable
without code changes.
Rationale: clear boundaries keep each part testable and replaceable.

### IV. Grounded, In-Scope Assistant Behavior
The system prompt MUST define a concise, professional, friendly Caramel AI tech support
persona covering: API keys and auth, model routing and selection, rate limits and quotas, error
codes, latency and throughput, streaming and request formats, and pricing and billing basics.
Out-of-scope questions MUST be politely redirected, not answered. When the assistant cannot
resolve an issue, it MUST point the user to human support. The assistant MUST NOT claim access
to real customer data or live account state, and MUST NOT invent account-specific facts.
Rationale: customers act on support answers; fabricated specifics cause production harm.

### V. Streaming, Resilience, and Safe Observability
Chat responses MUST stream over SSE, and conversation memory MUST use Spring AI chat memory
scoped to a session. Input MUST be validated with a message length limit, and CORS MUST be
restricted to the frontend origin. The UI MUST show loading states and friendly messages for
network, rate-limit, and provider errors. The backend MUST log request ID, latency, and
provider errors, and MUST NOT log message content or other sensitive data.
Rationale: perceived latency (first token under ~2s) and user privacy are core to a support chat.

## Technology & Security Constraints

- Backend: Java with Spring Boot and Spring AI.
- Frontend: React with TypeScript.
- LLM: `gemma-4-26b-a4b-it` via Google AI Studio. Changing model or provider requires a PRD
  amendment.
- Local setup MUST work end-to-end with a single configuration step (supplying the API key).
- Dependencies SHOULD be kept minimal; each added dependency MUST have a clear purpose.

## Development Workflow & Quality Gates

- Features follow the Spec Kit flow: specify → plan → tasks → implement.
- Manual test scenarios MUST verify: on-topic answers are accurate, multi-turn context is
  kept, and out-of-scope questions are redirected.
- Backend logic (validation, service, error mapping) MUST have automated tests; frontend streaming
  render and error states SHOULD have component tests.
- Build, lint, and test commands MUST be recorded in CLAUDE.md once the code is scaffolded.
- Changes MUST NOT commit secrets; reviewers MUST check this on every change.

## Governance

This constitution supersedes other practices for this project. Amendments MUST be made through
`/speckit-constitution` with a documented rationale and be committed with the dependent artifacts
they affect. Versioning follows semantic versioning: MAJOR for incompatible principle removals
or redefinitions, MINOR for new or materially expanded principles or sections, PATCH for
clarifications and wording fixes. Every spec, plan, and review MUST verify compliance with these
principles, and any deviation MUST be justified in writing in the plan. Use
[CLAUDE.md](../../CLAUDE.md) for runtime development guidance.

**Version**: 1.0.0 | **Ratified**: 2026-11-14 | **Last Amended**: 2026-11-14
