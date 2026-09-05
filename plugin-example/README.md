# plugin-example (illustrative only — not installed or published)

This is a scaffold showing how the skill and subagents already in `.claude/` for this project *would* be packaged as a distributable Claude Code plugin, using the conventional plugin directory layout:

```
plugin-example/
  .claude-plugin/
    plugin.json       # manifest: name, version, description, author
  skills/
    spring-boot-sample-dev/SKILL.md   # copy of .claude/skills/spring-boot-sample-dev/SKILL.md
  agents/
    java-spring-dev.md                # copy of .claude/agents/java-spring-dev.md
    spring-code-reviewer.md           # copy of .claude/agents/spring-code-reviewer.md
```

A real plugin would typically also add a `hooks/hooks.json` and/or `.mcp.json` at this same top level to bundle the hooks and MCP server declared elsewhere in this repo's `.claude/settings.json` and `.mcp.json` — omitted here to keep the example focused, since we already have working, tested versions of both at the project level.

**Why this exists, not just documentation:** seeing the actual file layout makes concrete what "a plugin bundles skills/agents/hooks/MCP servers" means — a plugin is that same `.claude/`-style content, just repackaged with a manifest so it can be distributed and installed elsewhere (via a marketplace: a repo or JSON listing of plugins, added with `/plugin marketplace add`, then `/plugin install <name>@<marketplace>`) instead of copy-pasted by hand.

**Not done here, deliberately:** this plugin is not registered with any marketplace or installed anywhere — `ListPlugins` returned zero results for this environment when checked, confirming there's nothing plugin-related active to conflict with or demonstrate installation against. This stays a file-structure reference, not a working install.

See `docs/agentic-concepts/plugins.md` for the fuller explanation.
