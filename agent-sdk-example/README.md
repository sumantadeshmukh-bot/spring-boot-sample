# agent-sdk-example

A standalone **Claude Agent SDK** example — deliberately separate from the Spring Boot app (this is Node.js, a different tech stack entirely) because the Agent SDK is its own certification domain, distinct from both "Claude Code" (the CLI you're likely reading this in) and "Claude API" (the raw Messages API tool-use protocol hand-rolled in `../src/main/java/com/example/sample/ai/AnthropicLlmClient.java`).

## What this proves, not just describes

This was actually run, twice, in the session that wrote it — not written-and-assumed-correct the way `AnthropicLlmClient` had to be (no API key was available to test that one live). Real findings from those runs:

1. **The SDK inherits this machine's existing Claude Code authentication** — no `ANTHROPIC_API_KEY` was set, and it still worked (`"apiKeySource":"none"` in the raw output, using whatever login/session Claude Code itself was already using). This is the core architectural difference from `AnthropicLlmClient`: that class talks to the raw Anthropic API and needs its own key; the Agent SDK spawns the Claude Code CLI's own bundled binary and rides on its auth, its settings, its permission system — everything.
2. **Real cost, transparently reported**: a trivial "reply PONG" call cost $0.148; a call using a custom tool cost $0.037 (before it errored — see below). Every `result` message includes `total_cost_usd`.
3. **A real bug, found by running it**: the first attempt set `maxTurns: 2`, which was too low — the model needed one turn to discover the tool via `ToolSearch`, one to call it, and one more to produce its final text reply, and ran out of budget with `"Reached maximum number of turns (2)"`. Fixed to `maxTurns: 4` in `item-agent.mjs`. `maxTurns` (and `maxBudgetUsd`, not used here) are the SDK's built-in cost/runaway-loop controls — exactly the kind of guardrail `docs/agentic-concepts/agentic-application-layer.md` implements by hand for the Java app's mocked loop.
4. **The custom tool went through `ToolSearch` first** — the exact same deferred-tool-loading mechanism visible in an interactive Claude Code session's own tool list. This isn't a coincidence: the Agent SDK *is* the Claude Code engine, driven programmatically instead of interactively.

## What it does

Defines one custom in-process tool (`list_items`, via `tool()` + `createSdkMcpServer()`) that calls the Spring Boot app's real `GET /api/items` endpoint, then asks the model to use it and report back the first item's name.

## Running it

```bash
# 1. Start the Spring Boot app first (from the repo root)
cd .. && java -jar target/spring-boot-sample-0.0.1-SNAPSHOT.jar

# 2. In this directory
npm install
npm start
```

Costs real money each run (see above) — small, but not zero. Requires either an authenticated Claude Code install on this machine, or set `ANTHROPIC_API_KEY` in the environment.
