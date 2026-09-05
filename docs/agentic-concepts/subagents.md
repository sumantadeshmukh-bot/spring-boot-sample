# Subagents

A subagent is a separately-invokable Claude instance with its own system prompt (persona + conventions), its own restricted tool list, and its own context window — spawned by a main session via the Agent tool, running independently, and reporting back a result rather than streaming its work into the main conversation.

## The two subagents in this repo

Both live in `.claude/agents/` (project scope):

- **`java-spring-dev.md`** — implements features. Full tool access (`Read, Grep, Glob, Bash, Edit, Write`). Its prompt encodes this repo's conventions (no service layer, manual getters/setters, `jakarta.validation`, 404-via-`ResponseEntity`) so it doesn't need to be told them every time.
- **`spring-code-reviewer.md`** — reviews changes. Deliberately **read-only tools** (`Read, Grep, Glob, Bash` — no `Edit`/`Write`), so it structurally cannot "fix" what it finds; it can only report. This is an intentional design choice, not an oversight: separating "can implement" from "can approve" is what makes a review step meaningful instead of theatrical.

## Known limitation: no hot-reload (confirmed, not theoretical)

This was discovered directly, not inferred: attempting to spawn `subagent_type: "java-spring-dev"` mid-session — right after creating the file — failed with:

```
Agent type 'java-spring-dev' not found. Available agents: claude, claude-code-guide, Explore, general-purpose, Plan, statusline-setup
```

Project-defined subagents are enumerated once when a session starts; creating or editing one doesn't register it into the *current* session. The workaround used for this repo's live demo: spawn the built-in `general-purpose` agent and explicitly tell it to read the persona file (`.claude/agents/java-spring-dev.md`) and follow it — which works, because the file's content is just a prompt, readable by any agent with `Read` access, even if the harness doesn't recognize it as a named `subagent_type` yet. A genuinely fresh session (new window/process) would show it in the "Available agents" list.

## What a subagent buys you over just asking the main session

- **Context isolation**: a subagent's exploration/trial-and-error doesn't bloat the main conversation's context window — only its final report does.
- **Tool restriction as a real constraint**: `spring-code-reviewer` *cannot* edit files, which is a stronger guarantee than "please don't edit anything" in a prompt.
- **Reusable persona**: the conventions in `java-spring-dev.md` don't need restating every time a feature gets added — see `multi-agent-flow.md` for this working end-to-end on a real feature.

## Precedence with same-named subagents

Following the same pattern documented for skills (most specific wins: project over user-level, over unscoped), a project-level `.claude/agents/foo.md` should take precedence over a user-level `~/.claude/agents/foo.md` of the same name. This wasn't independently re-verified here (no naming collision was set up for subagents the way `quick-note` was for skills), but it follows the same discovery mechanism and is the consistent pattern across skills/agents/hooks/MCP in this environment.
