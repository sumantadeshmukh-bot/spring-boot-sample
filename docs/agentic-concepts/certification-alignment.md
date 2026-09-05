# Certification alignment

Anthropic's own certification pages (`anthropic-partners.skilljar.com`) name four technology domains (Claude Code, Claude Agent SDK, Claude API, MCP) but publish no granular syllabus. A round of web research found something more useful: multiple independent third-party sources — a community study guide on GitHub ([daronyondem/claude-architect-exam-guide](https://github.com/daronyondem/claude-architect-exam-guide)), a dedicated exam-prep site, and several blog write-ups — converge on the same finer-grained structure: **5 weighted domains**, not 4 flat ones. Two of these sources were fetched and cross-checked directly against each other, not taken on a single source's word.

**Caveat, stated plainly**: these are unofficial, third-party sources, not Anthropic's own published rubric. Treat the domain weights and sub-topics below as a strong, corroborated *signal* about what the exam actually tests — not a verified official syllabus.

## The 5 domains, weighted — updated after closing the practical gaps

| Domain | Weight | Our coverage | Remaining gaps |
|---|---|---|---|
| **Agentic Architecture & Orchestration** | 27% | Strong — the loop, guardrails, **tool composition** and **confirmation flows** (`agentic-application-layer.md`), 3 orchestration patterns actually run, Agent SDK live-verified | No dedicated prompt-*chaining* example distinct from tool composition; the routing/supervisor pattern is still conceptual, not a live routing function (judged theoretical/low-value to force into code — see below) |
| **Claude Code Configuration & Workflows** | 20% | Strong — settings, hooks, permissions, the corrected precedence hierarchy, CI/CD, all with real evidence | Plan mode never demonstrated (judged a workflow habit, not app code — intentionally skipped, see below) |
| **Prompt Engineering & Structured Output** | 20% | **Closed.** `AnthropicLlmClient.SYSTEM_PROMPT` is a real, structured example (principles over conditionals, one worked example, explicit constraints) plus response prefill (`summarize()`'s `"Summary: "` seed) — both real code, not an essay about the topic | Not exhaustive (no discussion of e.g. XML-tag structuring or multi-shot example libraries), but the core techniques are now demonstrated in running code |
| **Tool Design & MCP Integration** | 18% | **Closed.** MCP's resources and prompts primitives now have a real, live-verified server (`mcp-resources-and-prompts.md`); confirmation flow and tool composition are real (`agentic-application-layer.md`); transient-vs-permanent error handling is real retry/backoff logic in `AnthropicLlmClient.post()` | None significant remaining |
| **Context Management & Reliability** | 15% | Strong — eval harness, plus **prompt caching** (`cache_control` on system prompt + tools) and the **Message Batches API** (`submitBatch`/`pollBatch`), both real code | Batches/caching untested live (same "verified-by-construction" caveat as the rest of `AnthropicLlmClient` — issue #2) |

**Rough weighted gut-check: ~85–90%**, up from ~55–60%. The two deliberately-skipped items (prompt chaining as its own example, plan mode, live routing-function) were judged theoretical/workflow-habit rather than codeable, per an explicit "coding and development, not theory" filter applied when prioritizing this round — see the note below. This number remains an informal estimate from the table above, not a certified score.

## What was deliberately left out, and why

Three items surfaced in the original gap analysis were **not** built, on purpose:

- **Prompt chaining** as a distinct pattern from tool composition — the two overlap enough (both are "one step's output feeds the next") that building a second, separate example would have been redundant rather than additive.
- **Plan mode** — a Claude Code *interactive habit* (a mode you invoke while working), not something that produces application code. Documenting "remember to use plan mode" isn't in the same category as the other gaps, which were all concrete, buildable features.
- **Routing/supervisor as a live function** — kept conceptual (`orchestration-patterns.md` §4) rather than forced into code, since with only 4 tools and 3 demonstrated orchestration patterns, a real routing function would be trivial to the point of not teaching anything a decision table doesn't already convey. Worth building for real if the tool/pattern count ever grows enough that routing logic would be non-trivial.

## What to do with this workspace toward exam prep

1. **Read `precedence-and-conflicts.md` first** — most likely to matter for scenario-style questions, and it was corrected once already after finding better primary-source evidence than what was initially written from memory.
2. **`AnthropicLlmClient` is the one place "built correctly" and "verified running" still diverge** — every technique in it (tool composition, caching, prefill, retries, batching) is real code, written carefully, but none of it has touched the actual API. Running it for real (issue #2) is the highest-value remaining action if hands-on API verification specifically matters for what you're preparing for.
3. **A separate RAG project** (different repo, different tech stack, in progress at time of writing) targets Context Management's retrieval-strategy and stale-data sub-topics from a different angle than anything here, rather than duplicating this repo's Java-centric coverage — deliberately kept out of this repo entirely to avoid any risk of merge conflicts with work in progress here.
