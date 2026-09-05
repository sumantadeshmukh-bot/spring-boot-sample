# Plugins

A plugin packages skills, subagents, hooks, and MCP servers — the same content that otherwise lives loose inside `.claude/` — into one directory with a manifest, so it can be distributed and installed elsewhere instead of copy-pasted by hand. Where a skill or subagent is a *unit of behavior*, a plugin is a *unit of distribution* for a bundle of them.

## What's scaffolded here

`plugin-example/` in this repo (see its own `README.md` for the full layout) mirrors the conventional plugin structure:

```
plugin-example/
  .claude-plugin/plugin.json     # manifest
  skills/spring-boot-sample-dev/SKILL.md
  agents/java-spring-dev.md
  agents/spring-code-reviewer.md
```

This is **illustrative scaffolding only** — not registered with any marketplace, not installed anywhere. It exists to make "a plugin bundles skills/agents/hooks/MCP" concrete rather than abstract: it's literally the same files already working at the project level in `.claude/`, just copied under a manifest.

## Distribution mechanics (conceptual — this repo doesn't exercise them)

Plugins are distributed via **marketplaces**: a git repo or hosted JSON listing multiple plugins by name, with metadata pointing at where each one lives. Installing one is a two-step trust model — add the marketplace, then install a specific plugin from it — rather than a single global "install anything from anywhere" action, which matters for supply-chain safety: adding a marketplace is a deliberate act of trusting its publisher, separate from trusting any one plugin in it.

## Grounded, not assumed: this environment's actual plugin state

Checked directly rather than assumed:

- **`ListPlugins`** (what's enabled for this session) → zero results.
- **`SearchPlugins`** (searching the org's plugin catalog for `java`, `code review`, `testing`) → zero results.

This environment currently has no plugins active and none discoverable in its catalog. That's a useful baseline fact, not an error — it means there's nothing to conflict with `plugin-example/`, and it means this workspace's actual skill/agent/hook/MCP setup is all hand-authored at the project/user level rather than pulled in via any plugin.

## Why this doesn't change the precedence model

A plugin's skills/agents are namespaced (`plugin-name:skill-name`), specifically so they don't collide with a project- or user-level item of the same base name the way `quick-note` deliberately does in `skills.md`. A hook or MCP server bundled in a plugin still goes through the same session-start-only discovery as everything else documented in `precedence-and-conflicts.md` — plugins are a packaging convenience, not a different runtime mechanism.
