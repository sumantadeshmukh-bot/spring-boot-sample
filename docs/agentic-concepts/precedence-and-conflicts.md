# Precedence and conflicts, across all of it

Eight distinct mechanisms are now live in this workspace: `CLAUDE.md`, skills, subagents, hooks, MCP servers, plugins, settings/permissions, and (via `agent-sdk.md`) the Agent SDK's own settings-resolution API. They don't all resolve conflicts the same way. This is the one thing worth internalizing above all the individual feature docs.

## Two fundamentally different resolution models

**Deterministic override** (settings.json — permissions, hooks config, env vars): a real precedence chain exists, but it's more textured than "five tiers, highest wins" — corrected here after inspecting the Claude Agent SDK's own type definitions (`resolveSettings()` in `@anthropic-ai/claude-agent-sdk`), which document the merge engine's actual source names rather than requiring anyone to guess:

- The three filesystem-backed tiers are named, precisely: **`user`** (`~/.claude/settings.json`), **`project`** (`.claude/settings.json` — committed, team-wide — what this repo's hooks live in), and **`local`** (`.claude/settings.local.json` — personal, gitignored).
- Above those sits a **`managed`** (policy) tier — but it is *not* a single file. Its own sub-origins are named explicitly: `helper`, `remote`, `plist` (macOS MDM), `hklm`/`hkcu` (Windows registry), `file` (an on-disk `managed-settings.json`), and `parent`. An admin's policy can arrive through any of these.
- A **`flag`** source exists too — the `--settings` CLI flag.
- **The managed tier is a restriction mechanism, not a general override**: the SDK's docs are explicit that managed settings are "filtered through a restrictive-key allowlist (`allowManaged*Only` locks, `permissions.deny`/`ask`, sandbox restrictions); non-restrictive keys such as `model`, `env`, `cleanupPeriodDays` are silently dropped." An enterprise policy can *forbid* things; it cannot use this channel to silently set your model or inject environment variables.
- **A repo cannot silently grant itself elevated trust**: `permissions.defaultMode` is subject to "a separate trust filter" before an escalating mode (`bypassPermissions`, `auto`, `acceptEdits`) committed in a repo's own settings file is honored — a compromised or malicious repository can't just write `bypassPermissions` into `.claude/settings.json` and have it silently take effect.

What's **not** independently verified here: the exact total ranking across all five source names (`user`/`project`/`local`/`managed`/`flag`) in every case — the SDK's own comments name the sources and describe `managed`'s restrictive scope precisely, but don't spell out one single ordered list in the excerpt inspected. Treat "more specific/local generally wins, except a restrictive managed policy always wins for what it explicitly restricts" as the safe practical summary, and check Anthropic's docs directly before relying on exact ordering for anything security-critical. This kind of "found real evidence but not the complete picture" moment is itself worth noticing: even primary-source type definitions don't always spell out everything, and knowing where the gap is matters more than pretending there isn't one.

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
| MCP server (tools) | `filesystem-demo` (`.mcp.json`) | Real connection, proven via raw protocol script |
| MCP server (resources/prompts) | `item-inventory` (`mcp-resources-prompts-example/`) | Real connection, proven via raw protocol script — including a resource that reads this app's own live data (see `mcp-resources-and-prompts.md`) |
| Plugins | `plugin-example/` | Illustrative scaffold only — not installed, nothing in this environment's catalog to install |
| Agent SDK | `agent-sdk-example/` | Real, run live twice — real auth inheritance, real cost, one real bug found and fixed (see `agent-sdk.md`) |
| App-level agentic loop | `src/main/java/com/example/sample/ai/` | Real feature — tool composition, a confirmation flow, prompt caching/prefill/retries, the Batches API — hardened by two real review cycles (an initial 3-agent parallel review, then a second pass closing composition/caching/error-handling gaps) and one live bug found by running the composed flow end-to-end (see `agentic-application-layer.md`) |
