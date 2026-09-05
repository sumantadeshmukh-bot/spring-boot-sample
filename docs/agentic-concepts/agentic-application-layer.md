# The app itself becomes agentic

Everything documented elsewhere in this folder is about Claude Code's *own* tooling — skills, subagents, hooks helping build this app. This doc covers the gap a senior-architect review flagged directly: the app itself had zero AI capability. `src/main/java/com/example/sample/ai/` closes that gap with a real (if intentionally mocked-by-default) tool-calling agent loop, plus the guardrails, tracing, and evaluation harness a production version would need.

## The design: hand-rolled, not framework-wrapped

`LlmClient` is a two-method interface (`decideTool`, `summarize`) with two implementations:

- **`MockLlmClient`** (default, `app.ai.provider=mock`) — regex/keyword heuristics standing in for a model's reasoning. No network call, no cost, deterministic — safe for tests and CI.
- **`AnthropicLlmClient`** (`app.ai.provider=anthropic` + `ANTHROPIC_API_KEY`) — the real Anthropic Messages API tool-use protocol, built with plain Spring `RestClient` and Jackson, not Spring AI. This was a deliberate choice: Spring AI's compatibility with a bleeding-edge Spring Boot 4.1 was an unverifiable risk, and hand-rolling means the actual HTTP/JSON tool-use mechanics stay visible instead of hidden behind a framework — see `agent-sdk.md` for the contrasting case where wrapping *is* the right call.

`AiOrchestrationService` runs the loop: guard input → rate-limit → ask the model to decide → **validate the decision against a whitelist before trusting it** → execute → ask the model to summarize → trace. That whitelist check is the single most important line in the class — a model (mocked or real) is never allowed to invoke anything outside three fixed tools (`search_items`, `get_item`, `list_items`), regardless of what it returns.

This is a deliberate, documented exception to the repo's "no service layer" rule (see the main `CLAUDE.md`): orchestrating a multi-step decision loop is categorically different from CRUD, and hiding it inside a controller would hide the very steps worth inspecting and testing independently.

## Guardrails (not optional once an LLM touches user input)

- **`PromptInjectionGuard`** — length limit + a denylist of obvious injection phrases. Explicitly documented as *not* a complete defense; the real defense is architectural (the whitelist above). This mirrors exactly the "instruction source boundary" principle governing how Claude Code itself treats tool output as data, not commands — the same idea, implemented one level down in application code.
- **`RateLimiter`** — per-client (keyed by remote address), fixed-window. A security review of this exact code caught that the *first* version was a single global counter — a trivial DoS where one caller could exhaust every other user's budget. Fixed to per-client keying; documented as still not production-grade (in-memory, no eviction, doesn't survive a restart or scale past one instance).
- **Rejections are logged too, not just successes** — an easy blind spot: if only successful runs are traced, a pattern of injection attempts or rate-limit hits from one source is invisible. `AiOrchestrationService` logs `ai_security_reject` and `ai_rate_limit_reject` distinctly from the success-path `ai_trace`.

## Tracing (the eval/observability half)

Every call produces an `AiTrace` — timestamp, query, tool chosen, arguments, raw result, final answer, duration — logged as structured JSON. This is the minimal shape real observability tooling (OpenTelemetry spans, LangSmith traces) builds on at scale: without per-step visibility, a wrong answer is undebuggable — was the tool choice wrong, the arguments wrong, or just the summary wrong?

## The eval harness

`MockLlmClientEvalTest` is explicitly an **eval**, not a unit test — the distinction is real: a unit test asserts one behavior; an eval runs a golden dataset (here, ~13 query→expected-tool pairs) against the decision-making component, so a change to the heuristics (or, with a real model, a prompt/model-version change) gets checked against many representative cases at once. This is the exact pattern `claude plugin eval` applies to Claude Code skills — a table of (input, expected outcome) pairs run against the thing being evaluated. Swap `MockLlmClient` for a real model and this table becomes a regression suite: if a prompt change makes "find X" stop resolving to `search_items`, this catches it before a user does.

## This code went through a real review cycle — here's what it caught

A three-agent parallel review (security / test-coverage / architecture — see `orchestration-patterns.md`) ran against this exact package after it was first written. Real findings, all fixed:

- **Security**: the global-rate-limiter DoS (above); `@Valid` was declared on the request DTO but never applied at the controller, so validation was silently dead code; a malformed `id` argument leaked a raw `NumberFormatException` message to the caller instead of a clean error; the Anthropic `RestClient` had no timeout, so a hung upstream could block a thread indefinitely.
- **Test coverage**: the whitelist-rejection path (the most important line in the service) had zero tests; `get_item` for a nonexistent id was untested end-to-end; `MockLlmClient.summarize()`'s four branches were entirely unexercised; the controller's 429/502 mappings were only reachable through scenarios the mock client can't produce.
- **Architecture**: the tool name list was hardcoded independently in three places (now derived from `ToolRegistry` in two of the three — the `execute()` switch is an acknowledged remaining seam); `ToolSpec.name` vs `ToolCall.toolName` was inconsistent naming (unified to `toolName`); each class built its own `ObjectMapper` instead of using Spring's shared bean.

**Not fixed, deliberately, and documented as acknowledged debt rather than silently ignored:** `ToolSpec`'s parameter shape is Anthropic-tool-schema-shaped, not fully provider-neutral (a second real provider with a different function-calling schema would strain it) — flagged as a "mild leak" by the review, judged not worth a bigger redesign for a 3-tool demo app, and tracked as [issue #3](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/3) rather than left as an unfindable note. This triage itself is worth noticing: not every finding gets fixed immediately; some get consciously deferred with a stated reason, which is different from silently ignoring them.
