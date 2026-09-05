# Claude Agent SDK

The fourth pillar (alongside Claude Code, Claude API, and MCP — see `certification-alignment.md`) is the one this workspace hadn't touched until this pass: the **Claude Agent SDK** (`@anthropic-ai/claude-agent-sdk` on npm, real package, verified — not to be confused with the plain Anthropic API SDK). A standalone, runnable example lives in `../../agent-sdk-example/`; this doc explains the concept and what running it actually revealed.

## What it is, precisely

Reading the SDK's own type definitions (installed and inspected directly, not guessed from docs) settles the one question that actually matters: **the Agent SDK is not a thin wrapper around the raw Messages API** — it bundles and spawns the **Claude Code CLI's own binary** as a subprocess, and talks to it over stdio using the same JSON message protocol Claude Code uses internally (`--output-format stream-json`, etc., visible directly in the SDK's own source when something goes wrong). This was confirmed empirically, not just read: a live `query()` call in this environment succeeded with `"apiKeySource":"none"` — no `ANTHROPIC_API_KEY` was ever set, because it inherited whatever authentication Claude Code itself was already using on this machine.

This is the core distinction worth internalizing for an architecture conversation:

| | Claude API (raw) | Claude Agent SDK |
|---|---|---|
| What you're calling | Anthropic's Messages API directly (HTTP/JSON) | The Claude Code CLI binary, as a subprocess |
| Auth | Your own API key, every time (`AnthropicLlmClient` in this repo requires `ANTHROPIC_API_KEY`) | Whatever Claude Code is already authenticated with, inherited automatically |
| What you get "for free" | Nothing beyond the model call itself — you build the tool loop, guardrails, tracing yourself (see `agentic-application-layer.md`) | The entire Claude Code engine: tool-calling loop, permission system, hooks, skills, subagents, `CLAUDE.md` loading, settings precedence — all of it, programmatically |
| Cost visibility | You compute it yourself from token counts | Every `result` message includes `total_cost_usd` directly |

## What the example proves, concretely

`agent-sdk-example/item-agent.mjs` defines one custom tool (`list_items`) via `tool()` + `createSdkMcpServer()`, wired to call this repo's own live `GET /api/items` endpoint — an agent, built in Node.js, calling into the Java app as its data source. Running it twice surfaced two real, non-obvious findings (full detail in the example's own README):

1. **The custom tool call went through `ToolSearch` first** — the identical deferred-tool-loading mechanism observable in any interactive Claude Code session's own tool list. Not an analogy: the *same mechanism*, because it's the same engine.
2. **A real `maxTurns` exhaustion bug**, caught by actually running it: `maxTurns: 2` wasn't enough to cover tool-discovery + tool-call + final-reply, and the SDK reported exactly why (`"Reached maximum number of turns (2)"`). Fixed to `4`.

`maxTurns` and `maxBudgetUsd` (available but unused here) are the SDK's built-in versions of exactly the guardrails hand-built in `agentic-application-layer.md`'s `RateLimiter` — a good comparison point: when the SDK's engine is doing the orchestrating, cost/runaway-loop control is a configuration option; when you hand-roll the loop yourself (as `AiOrchestrationService` does), you own building that control yourself.

## Where this fits for architecture decisions

Choosing between the raw API and the Agent SDK isn't about which is "better" — it's about what you're building:

- **Raw API tool-use** (`AnthropicLlmClient`) fits when the model's decision is one small, well-defined step inside a larger non-agentic system (this repo's `/api/ai/ask` endpoint is exactly this: one tool call, one summary, done) — you want minimal footprint and full control over exactly what happens.
- **Agent SDK** fits when you want an actual multi-step, tool-using *agent* — something that reads files, runs commands, iterates, and makes several sequential decisions — without reimplementing that control loop, permission model, and context-management machinery yourself. It's the right tool when the thing you're building is closer to "a custom Claude Code" than "one AI-assisted API endpoint."

## Cost note

Every run costs real money — small ($0.03–$0.15 in the two calls made while building this), but not zero, and it accrues per invocation regardless of whether the call "succeeds" in the way you intended (the `maxTurns`-exhausted run still cost $0.037). Budget for this differently than a mocked or mocked-then-occasionally-real setup like `agentic-application-layer.md`'s `LlmClient` — an SDK-based agent has no free/mock mode by default.
