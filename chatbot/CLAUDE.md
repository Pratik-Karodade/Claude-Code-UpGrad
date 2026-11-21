# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This directory currently holds only [prd.md](prd.md) and [.mcp.json](.mcp.json). There is no source code, build config, or tests yet. Update this file with real build, lint, and test commands once the backend and frontend are scaffolded.

## What is being built

A customer service chat assistant for "Tech Support on LLM Inferencing" at Caramel AI, a company like OpenRouter. [prd.md](prd.md) is the source of truth for scope. The MVP has no auth, no persistent history, and no live account lookups.

## Planned architecture

```
React + TypeScript UI --(HTTP/SSE)--> Java Spring Boot + Spring AI --> Google AI Studio (gemma-4-26b-a4b-it)
```

- Backend: Java with Spring AI. It exposes a streaming chat endpoint (e.g. `POST /api/chat`, SSE), uses Spring AI chat memory for multi-turn context, and sets the support persona and escalation behavior in a system prompt.
- Frontend: React + TypeScript chat UI that renders streamed responses.
- The Google AI Studio API key stays server-side, set through environment variables and Spring config. It must not be committed.

## Notes

- [.mcp.json](.mcp.json) configures a GitHub MCP server and currently contains a hardcoded bearer token. Don't copy it elsewhere. Prefer moving it to an environment variable and keeping the file out of git.
