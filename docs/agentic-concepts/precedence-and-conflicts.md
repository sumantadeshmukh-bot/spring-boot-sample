# Precedence and conflicts, across all of it

Seven distinct mechanisms are now live in this workspace: `CLAUDE.md`, skills, subagents, hooks, MCP servers, plugins, and settings/permissions. They don't all resolve conflicts the same way. This is the one thing worth internalizing above all the individual feature docs.

## Two fundamentally different resolution models

**Deterministic override** (settings.json — permissions, hooks config, env vars): a strict precedence chain, highest wins outright:

1. Enterprise managed policy (cannot be overridden by anything below)
2. Command-line arguments
3. Local project settings (`.claude/settings.local.json` — personal, gitignored)
4. Shared project settings (`.claude/settings.json` — committed, team-wide — what this repo's hooks live in)
5. User settings (`~/.claude/settings.json` — global default)

**Contextual judgment** (`CLAUDE.md`): no strict override. Every applicable file — user, parent-directory, project, subdirectory — is loaded as context simultaneously. If two disagree, Claude weighs the more specific one as more relevant, but this is a strong tendency, not a guarantee, unlike the settings chain above. See `claude-md-hierarchy.md` for why this distinction matters practically.

**Most-specific-wins by name** (skills, subagents): documented directly in this session's own tooling — "directory-scoped skills are listed with a path prefix... when both scoped and unscoped variants of a name exist, pick the one whose directory contains the files you're working on; most specific wins, unscoped otherwise." This repo's `quick-note` skill exists at both project and user scope specifically to exercise this. Subagents follow the same pattern by convention, though it wasn't independently re-verified with a naming collision the way skills was.

**Namespaced, so no collision at all** (plugins): a plugin's skills/agents are addressed as `plugin-name:skill-name`, sidestepping the collision question entirely rather than resolving it.

## The pattern that cuts across almost everything: no hot-reload

This is the single biggest practical nuance discovered while building this workspace, and it applies uniformly to **skills, subagents, hooks, and MCP servers** — all four are discovered once when a Claude Code session starts, and none of them watch for changes:

- A subagent (`java-spring-dev`) created mid-session was rejected outright: `Agent type 'java-spring-dev' not found.`
- An MCP server (`filesystem-demo`) added to `.mcp.json` mid-session never appeared in the tool list — had to be verified out-of-band with a raw protocol script instead.
- A hook (`log-edit.js`) added to `.claude/settings.json` mid-session did not fire on a real edit made in that same session — had to be verified by invoking the script directly with a synthetic payload.

**The practical rule: if you add or edit a skill, subagent, hook, or MCP server, test it in a new session, not the one you used to create it.** Everything in this repo that falls into these four categories was verified working through direct/manual invocation rather than live in-session behavior, precisely because of this constraint — and that verification approach is itself worth reusing whenever you build one of these and can't restart to check it.

`CLAUDE.md` is the one exception worth naming: it's read fresh at the relevant point (session start for user/project files, and when Claude reads/edits into a subdirectory for that directory's file), so a subdirectory `CLAUDE.md` added mid-session can still get picked up the next time work touches that subdirectory — it doesn't require a full restart the way the other four do.

## Quick reference: what's real vs. illustrative in this repo

| Mechanism | Files | Status |
|---|---|---|
| `CLAUDE.md` (4 levels) | user, `E:\Projects`, project root, one subdirectory | All real, all load at their respective scopes |
| Skills | `spring-boot-sample-dev`, `quick-note` × 2 scopes | Real; collision demo untested live (see hot-reload note) |
| Subagents | `java-spring-dev`, `spring-code-reviewer` | Real personas; actually exercised via `general-purpose` stand-in this session |
| Multi-agent flow | build → review → fix, on the search feature | Genuinely run, not scripted — see `multi-agent-flow.md` |
| Hooks | `log-edit.js`, `compile-check.js` | Real, both success and failure paths manually verified |
| MCP server | `filesystem-demo` (`.mcp.json`) | Real connection, proven via raw protocol script |
| Plugins | `plugin-example/` | Illustrative scaffold only — not installed, nothing in this environment's catalog to install |
