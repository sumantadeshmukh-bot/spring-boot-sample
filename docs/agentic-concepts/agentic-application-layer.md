# The app itself becomes agentic

Everything documented elsewhere in this folder is about Claude Code's *own* tooling — skills, subagents, hooks helping build this app. This doc covers the gap a senior-architect review flagged directly: the app itself had zero AI capability. `src/main/java/com/example/sample/ai/` closes that gap with a real (if intentionally mocked-by-default) tool-calling agent loop, plus the guardrails, tracing, and evaluation harness a production version would need.

## The design: hand-rolled, not framework-wrapped

`LlmClient` is a two-method interface (`decideNextStep`, `summarize`) with two implementations:

- **`MockLlmClient`** (default, `app.ai.provider=mock`) — regex/keyword heuristics standing in for a model's reasoning. No network call, no cost, deterministic — safe for tests and CI.
- **`AnthropicLlmClient`** (`app.ai.provider=anthropic` + `ANTHROPIC_API_KEY`) — the real Anthropic Messages API tool-use protocol, built with plain Spring `RestClient` and Jackson, not Spring AI. This was a deliberate choice: Spring AI's compatibility with a bleeding-edge Spring Boot 4.1 was an unverifiable risk, and hand-rolling means the actual HTTP/JSON tool-use mechanics stay visible instead of hidden behind a framework — see `agent-sdk.md` for the contrasting case where wrapping *is* the right call.

`AiOrchestrationService` runs the loop: guard input → rate-limit → ask the model to decide **one step at a time** → **validate the decision against a whitelist before trusting it** → execute → repeat (up to a bound) → summarize the whole sequence → trace. That whitelist check is the single most important line in the class — a model (mocked or real) is never allowed to invoke anything outside the fixed tool set, regardless of what it returns.

This is a deliberate, documented exception to the repo's "no service layer" rule (see the main `CLAUDE.md`): orchestrating a multi-step decision loop is categorically different from CRUD, and hiding it inside a controller would hide the very steps worth inspecting and testing independently.

## Tool composition: why `decideNextStep` takes history, not just the query

The interface originally had a single-shot `decideTool(query, tools)` — one decision, done. That can't express **composition**: "delete the item named Widget" needs `search_items` to resolve the name to an id *before* `delete_item` can act on it — the second decision depends on the first tool's result.

`decideNextStep(userQuery, availableTools, history)` fixes this: `AiOrchestrationService` loops (bounded at `MAX_STEPS = 4`), passing the growing list of completed steps back in on each call, until the model returns `Decision.Finish` or the step limit is hit. This isn't a convenience abstraction — it's how Anthropic's real API represents multi-turn tool use: each prior step becomes an assistant `tool_use` block followed by a user `tool_result` block in the next request's message history, which is exactly what `AnthropicLlmClient.toMessagesJson()` reconstructs from the `List<ToolExecutionStep>` on every call (the API itself is stateless — the full conversation gets resent each turn).

**A real bug this surfaced immediately**: the first version of the name-extraction regex only stripped one filler word, so "delete the item named Widget" resolved to searching for "item named Widget" instead of "Widget" — found by actually running the composed flow live against the app, not by unit tests (which used cleaner test fixtures that didn't happen to trigger it). Fixed by extracting from the *last* trigger-word match ("named") instead of the first ("delete"), which needs no further filler-stripping. Live-verified afterward: create an item, ask to delete it by name, confirm the two-step search→delete sequence resolves correctly.

## Confirmation flow: a decision is not an action

`delete_item` never actually deletes anything when the model calls it. `AiOrchestrationService.execute()` instead queues the deletion in `ConfirmationStore` (in-memory, token-based, 5-minute TTL) and returns a `confirmation_required` result with a token — the loop then stops (a pending confirmation is a natural end to the turn, not something to keep deciding around). A human confirms separately via `POST /api/ai/confirm`, which is the only path that actually calls `itemRepository.deleteById()`.

This is a real, load-bearing distinction, not decoration: the model *deciding* to delete something and the deletion *happening* are two different events with a mandatory human gate between them for anything destructive. Verified live end-to-end: ask to delete by name → get a token → confirm the item is *not yet* gone → POST the token to `/confirm` → item is gone → re-using the same token 404s (single-use, enforced by `ConfirmationStore.consume()` removing it from the map on read).

## Guardrails (not optional once an LLM touches user input)

- **`PromptInjectionGuard`** — length limit + a denylist of obvious injection phrases. Explicitly documented as *not* a complete defense; the real defense is architectural (the whitelist above). This mirrors exactly the "instruction source boundary" principle governing how Claude Code itself treats tool output as data, not commands — the same idea, implemented one level down in application code.
- **`RateLimiter`** — per-client (keyed by remote address), fixed-window. A security review of this exact code caught that the *first* version was a single global counter — a trivial DoS where one caller could exhaust every other user's budget. Fixed to per-client keying; documented as still not production-grade (in-memory, no eviction, doesn't survive a restart or scale past one instance).
- **Rejections are logged too, not just successes** — an easy blind spot: if only successful runs are traced, a pattern of injection attempts or rate-limit hits from one source is invisible. `AiOrchestrationService` logs `ai_security_reject` and `ai_rate_limit_reject` distinctly from the success-path `ai_trace`.

## Claude API techniques, made concrete in `AnthropicLlmClient`

Four specific, checkable API features, not abstract advice:

- **Prompt engineering as code, not prose.** `SYSTEM_PROMPT` is structured deliberately: principles ("prefer the most specific tool") instead of an if/else decision tree, one worked example showing the composition pattern end-to-end, and an explicit constraint ("never invent an item id"). A conditional-heavy system prompt tends to dilute as a tool list grows; a short set of principles the model can apply generally holds up better — the point isn't the exact wording, it's that the prompt has a legible structure at all, which most ad-hoc system prompts don't.
- **Prompt caching.** Both the system prompt and the tools array are marked with `cache_control: {"type": "ephemeral"}` (on the system block and the *last* tool in the array — a cache breakpoint caches everything up to and including it). Both are identical across every call in this app, so this is close to free to add and directly reduces cost on repeated calls within the cache window.
- **Response prefill.** `summarize()` seeds the assistant's reply with `"Summary: "` before the model continues it — a real Anthropic API technique for nudging output format (the model can't restate or contradict what's already "said") without needing tool-use machinery for something that's just formatting.
- **Transient vs. permanent error handling.** `post()`'s retry loop distinguishes 5xx and 429 (retry with exponential backoff, up to 3 attempts) from other 4xx like 400/401 (fail immediately — retrying a malformed request or a bad key wastes time and quota, it doesn't fix anything).

Plus one separate feature, not part of the main loop: **`submitBatch`/`pollBatch`** call Anthropic's Message Batches API for bulk, non-interactive work (e.g. summarizing every item overnight) at a cost discount versus the same calls made one at a time. Exposed via `POST /api/ai/batch`, gated to 501 in mock mode — batching mocked responses would just be per-item mocking with extra steps, so it deliberately isn't given mock parity.

All four (plus batching) share the same honest caveat as the rest of `AnthropicLlmClient`: built carefully from documented API shape, never run against a live key this session — tracked as [issue #2](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/2).

## Tracing (the eval/observability half)

Every call produces an `AiTrace` — timestamp, query, tool chosen, arguments, raw result, final answer, duration — logged as structured JSON. This is the minimal shape real observability tooling (OpenTelemetry spans, LangSmith traces) builds on at scale: without per-step visibility, a wrong answer is undebuggable — was the tool choice wrong, the arguments wrong, or just the summary wrong?

## The eval harness

`MockLlmClientEvalTest` is explicitly an **eval**, not a unit test — the distinction is real: a unit test asserts one behavior; an eval runs a golden dataset (17 query→expected-tool pairs, including the composition cases — "delete the item named X" resolving to `search_items` first, "delete item 42" going straight to `delete_item`) against the decision-making component, so a change to the heuristics (or, with a real model, a prompt/model-version change) gets checked against many representative cases at once. This is the exact pattern `claude plugin eval` applies to Claude Code skills — a table of (input, expected outcome) pairs run against the thing being evaluated. Swap `MockLlmClient` for a real model and this table becomes a regression suite: if a prompt change makes "find X" stop resolving to `search_items`, this catches it before a user does.

## This code went through a real review cycle — here's what it caught

A three-agent parallel review (security / test-coverage / architecture — see `orchestration-patterns.md`) ran against this exact package after it was first written. Real findings, all fixed:

- **Security**: the global-rate-limiter DoS (above); `@Valid` was declared on the request DTO but never applied at the controller, so validation was silently dead code; a malformed `id` argument leaked a raw `NumberFormatException` message to the caller instead of a clean error; the Anthropic `RestClient` had no timeout, so a hung upstream could block a thread indefinitely.
- **Test coverage**: the whitelist-rejection path (the most important line in the service) had zero tests; `get_item` for a nonexistent id was untested end-to-end; `MockLlmClient.summarize()`'s four branches were entirely unexercised; the controller's 429/502 mappings were only reachable through scenarios the mock client can't produce.
- **Architecture**: the tool name list was hardcoded independently in three places (now derived from `ToolRegistry` in two of the three — the `execute()` switch is an acknowledged remaining seam); `ToolSpec.name` vs `ToolCall.toolName` was inconsistent naming (unified to `toolName`); each class built its own `ObjectMapper` instead of using Spring's shared bean.

**Not fixed, deliberately, and documented as acknowledged debt rather than silently ignored:** `ToolSpec`'s parameter shape is Anthropic-tool-schema-shaped, not fully provider-neutral (a second real provider with a different function-calling schema would strain it) — flagged as a "mild leak" by the review, judged not worth a bigger redesign for a 3-tool demo app, and tracked as [issue #3](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/3) rather than left as an unfindable note. This triage itself is worth noticing: not every finding gets fixed immediately; some get consciously deferred with a stated reason, which is different from silently ignoring them.
