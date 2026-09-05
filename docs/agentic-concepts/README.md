# Agentic concepts — a working foundation

Everything in this folder documents a feature that's actually implemented and tested in this repo (not just described in the abstract). Start with `certification-alignment.md` for the big picture, `precedence-and-conflicts.md` for the cross-cutting nuances, then whichever individual topic is relevant:

**Claude Code fundamentals**
- [`claude-md-hierarchy.md`](claude-md-hierarchy.md) — user / parent-directory / project / subdirectory `CLAUDE.md`, and how they combine
- [`skills.md`](skills.md) — the `spring-boot-sample-dev` skill, plus a deliberate `quick-note` name collision across scopes
- [`subagents.md`](subagents.md) — `java-spring-dev` (implementer) and `spring-code-reviewer` (read-only reviewer)
- [`hooks.md`](hooks.md) — an observational hook and a functional compile-check hook, both manually verified
- [`plugins.md`](plugins.md) — the plugin packaging model, plus a scaffolded (not installed) example

**MCP and the Claude API/Agent SDK**
- [`mcp-servers.md`](mcp-servers.md) — a real, working filesystem MCP server connection, proven via a raw protocol script
- [`agent-sdk.md`](agent-sdk.md) — the Claude Agent SDK, run live twice against this repo's own API — real auth inheritance, real cost, one real bug found and fixed
- [`agentic-application-layer.md`](agentic-application-layer.md) — the app itself becomes agentic: a hand-rolled Claude API tool-calling loop (`src/main/java/com/example/sample/ai/`), with guardrails, tracing, and an eval harness, hardened by a real 3-agent review

**Orchestration and governance**
- [`multi-agent-flow.md`](multi-agent-flow.md) — sequential build → review → fix, run for real on the search feature
- [`orchestration-patterns.md`](orchestration-patterns.md) — parallel fan-out, the iterative loop, and the supervisor/router pattern, compared against the sequential flow
- [`governance-and-enterprise.md`](governance-and-enterprise.md) — a correctly-scoped managed-settings example, audit logging, cost attribution
- [`precedence-and-conflicts.md`](precedence-and-conflicts.md) — ties everything together: deterministic override vs. contextual judgment vs. most-specific-wins, and the recurring "no hot-reload mid-session" limitation
- [`certification-alignment.md`](certification-alignment.md) — how all of the above maps to Anthropic's four certification domains, gaps included

See also `../../MACHINE_SETUP_COMMANDS.md`, `../../CLAUDE_PLAYGROUND.md` (workspace root) and this repo's own `CLAUDE.md` / `ISSUES_AND_FIXES.md` / `PROJECT_COMMANDS.md` for the environment-setup side of this same learning project.
