# Skills

A skill is a packaged set of instructions for a recurring kind of task — invoked either explicitly (`/skill-name`) or automatically when Claude judges the current request matches its description. A skill's frontmatter `description` is what drives automatic triggering, so it needs to be specific enough to fire on the right requests and not others.

## The three skills in this workspace

| Skill | Scope | File |
|---|---|---|
| `spring-boot-sample-dev` | Project | `spring-boot-sample/.claude/skills/spring-boot-sample-dev/SKILL.md` |
| `quick-note` (project version) | Project | `spring-boot-sample/.claude/skills/quick-note/SKILL.md` |
| `quick-note` (user version) | User (global) | `~/.claude/skills/quick-note/SKILL.md` |

`spring-boot-sample-dev` encodes this repo's exact build/test/run/Docker commands — genuinely project-specific, would be wrong anywhere else. `quick-note` is deliberately duplicated at both scopes with the **same name** but different behavior (logs to `~/.claude/notes.md` vs. this repo's `NOTES.md`), to demonstrate what happens on a name collision — see below.

## Name-collision precedence

This is documented behavior, not a guess: when a scoped (project- or directory-specific) skill and an unscoped (user-level) skill share a name, **the one whose directory contains the files you're currently working in wins** — most specific wins, unscoped otherwise. Plugin-provided skills are namespaced as `plugin:skill-name` and don't collide with either.

**Practical implication:** if you're working inside `spring-boot-sample` and say "note this," the project-scoped `quick-note` should fire (logging to the repo's `NOTES.md`), not the user-level one. If you're in some other project on this machine with no project-level `quick-note`, the user-level one is what's available.

## Known limitation: no hot-reload

A skill file added or edited **after** a Claude Code session has started is not picked up by that running session — confirmed directly while building this: a subagent (a closely related mechanism) created mid-session was reported as "not found" until conceptually treated as a fresh load. Skills, subagents, hooks, and MCP servers all share this trait (see `precedence-and-conflicts.md`): they're discovered once at session start, not watched for changes. Test a new or edited skill in a **new** Claude Code session/window, not the one you used to create it.

## Skills vs. Claude's memory system

Don't confuse a skill like `quick-note` with Claude's structured long-term memory (`memory/*.md` + `MEMORY.md`, used elsewhere in this environment): a skill is a reusable *procedure* invoked on demand; memory is *facts about the user/project* recalled automatically across conversations. `quick-note` intentionally just appends a raw timestamped line — it is not a substitute for memory, and its own `SKILL.md` says so.
