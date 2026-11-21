# Quickstart: Tech Support Chat Assistant

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contract**: [contracts/chat-api.md](contracts/chat-api.md)

Use this guide to run the assistant locally and confirm each user story works end to end.

## Prerequisites

- JDK 21 and Maven 3.9+
- Node.js 20+ and npm
- A Google AI Studio API key with access to `gemma-4-26b-a4b-it`

## 1. Configure the API key (single configuration step)

Set the key in the shell that runs the backend. Do not write it into any file in the repo.

```bash
export GOOGLE_API_KEY="<your-key>"
```

## 2. Start the backend

```bash
cd backend
mvn spring-boot:run
```

Expected: the app starts on port 8080. No key value appears in the startup log.

## 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Expected: Vite serves the app at `http://localhost:5173`.

## 4. Smoke test the API

```bash
curl -N -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"conversationId":"3f2c9a1e-7b4d-4e8a-9c21-5d6e7f8a9b0c","message":"What does a 429 error mean?"}'
```

Expected: a stream of `token` events ending with one `done` event. The first `token` arrives
within about 2 seconds.

## 5. Validate user stories

| Story | Steps | Expected result |
|---|---|---|
| US1: technical answer | Ask one question per topic (API key, model list, 429, 500, timeout, streaming format, billing) | Relevant answer appears progressively. No request for the customer's key. |
| US2: follow-up | Ask "What does error 401 mean?", then "How do I fix it?" | Second answer refers to 401. |
| US2: new chat | Click "New chat", then ask "How do I fix it?" | Answer does not refer to 401. History is empty. |
| US3: off-topic | Ask "Give me a pasta recipe" | Polite decline with a redirect to Caramel AI technical support. |
| US3: account data | Ask "What is my balance?" | States it cannot access account data and refers to human support. |
| Edge: empty message | Send spaces only | Send is blocked or a validation message appears. |
| Edge: too long | Paste 2,001 characters | Not sent; message explains the limit. |
| Error: rate limit / outage | Use an invalid key or simulate provider failure | Friendly error banner with a retry option. No raw error text. |

## 6. Check success criteria

- **SC-001**: Run the 20-question set and have a Caramel AI reviewer score answers.
- **SC-002**: Time the first token across 20 messages; at least 19 should appear within 2 seconds.
- **SC-003**: Run 10 multi-turn conversations and check follow-up correctness.
- **SC-004**: Run the off-topic and account-specific set; none should get a fabricated answer.
- **SC-005**: Force each failure type and confirm only friendly text is shown.

## 7. Run the tests

```bash
cd backend && mvn test
cd frontend && npm test
```

Expected: all tests pass. Backend tests do not call the live model; they use a stubbed `ChatModel`.
