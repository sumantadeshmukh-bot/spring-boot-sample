# Certification alignment

Anthropic's "Claude Certified Architect" programs (Foundations and Professional, at `anthropic-partners.skilljar.com`) assess four core technology domains — confirmed directly from the certification pages, not assumed. Neither page publishes a granular syllabus beyond naming these four, so this maps what's been built in this workspace against the domains themselves, honestly noting depth per area rather than claiming complete coverage of an unpublished syllabus.

| Domain | What's built here | Depth |
|---|---|---|
| **Claude Code** | `CLAUDE.md` at 4 levels, skills (incl. a real name-collision demo), 2 subagents run through a real review cycle, 2 tested hooks, a scaffolded plugin, and the cross-cutting "no hot-reload mid-session" finding | Deep — this was the starting point of the whole exercise |
| **MCP** | A real filesystem server connection (`.mcp.json`), proven via a raw protocol script since MCP servers don't hot-reload; config scopes documented | Solid — one real server, core mechanics covered, not every transport (SSE/HTTP) exercised |
| **Claude API** | `AnthropicLlmClient` — a hand-rolled Messages API tool-use client (not a framework wrapper), built to teach the actual HTTP/JSON mechanics. Untested live (no API key available this session) but its JSON-building logic is unit-tested | Solid on mechanics, thin on live verification — the one domain where "built correctly" and "verified running" diverge in this workspace |
| **Claude Agent SDK** | `agent-sdk-example/` — a real, twice-run example using `query()`, `tool()`, and `createSdkMcpServer()` against this repo's live API, with real cost and one real bug found and fixed | Deep, and uniquely **live-verified** — the domain with the most direct evidence, precisely because it was the identified gap and got focused attention |

## The honest gaps

- **Neither certification page publishes objectives beyond the four domain names** — this table can't claim alignment with specific exam questions, only with the stated technology areas. Treat this as "the four domains, covered with real depth and evidence," not "syllabus complete."
- **Claude API domain is verified-by-construction, not verified-by-running**: `AnthropicLlmClient` was built carefully (correct Messages API shape, tool-use protocol, error handling) and unit-tested for its JSON logic, but never actually called the real API — no key was available this session. This is a materially different confidence level than the Agent SDK example, which *did* run against the real thing twice. If preparing for an exam that tests hands-on API behavior specifically, running `AnthropicLlmClient` for real (setting `app.ai.provider=anthropic` and a real key) is the one remaining gap worth closing — tracked as [issue #2](https://github.com/sumantadeshmukh-bot/spring-boot-sample/issues/2).
- **MCP coverage is one server, one transport** (stdio, via `npx`). The protocol also supports remote servers over HTTP/SSE — not exercised here.

## What to do with this workspace toward exam prep

Read `precedence-and-conflicts.md` first — it's the doc most likely to matter for scenario-style questions ("which setting wins when X and Y conflict"), and it's been corrected once already in this pass after finding better primary-source evidence (the SDK's own type definitions) than what was initially written from memory. That correction itself is worth remembering as a habit: verify against source when the stakes are an exam or a production decision, don't rely on a first confident-sounding answer.
