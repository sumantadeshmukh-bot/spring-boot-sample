# Agentic concepts — a working foundation

Everything in this folder documents a feature that's actually implemented and tested in this repo (not just described in the abstract) — see `precedence-and-conflicts.md` first for the cross-cutting nuances, then whichever individual topic is relevant:

- [`claude-md-hierarchy.md`](claude-md-hierarchy.md) — user / parent-directory / project / subdirectory `CLAUDE.md`, and how they combine
- [`skills.md`](skills.md) — the `spring-boot-sample-dev` skill, plus a deliberate `quick-note` name collision across scopes
- [`subagents.md`](subagents.md) — `java-spring-dev` (implementer) and `spring-code-reviewer` (read-only reviewer)
- [`multi-agent-flow.md`](multi-agent-flow.md) — a real build → review → fix run on this repo's search feature
- [`hooks.md`](hooks.md) — an observational hook and a functional compile-check hook, both manually verified
- [`mcp-servers.md`](mcp-servers.md) — a real, working filesystem MCP server connection, proven via a raw protocol script
- [`plugins.md`](plugins.md) — the plugin packaging model, plus a scaffolded (not installed) example
- [`precedence-and-conflicts.md`](precedence-and-conflicts.md) — ties all of the above together: which mechanisms have deterministic override rules, which use contextual judgment, and the recurring "no hot-reload mid-session" limitation

See also `../../MACHINE_SETUP_COMMANDS.md`, `../../CLAUDE_PLAYGROUND.md` (workspace root) and this repo's own `CLAUDE.md` / `ISSUES_AND_FIXES.md` / `PROJECT_COMMANDS.md` for the environment-setup side of this same learning project.
