# MCP servers

MCP (Model Context Protocol) is how Claude Code connects to external tool providers — a separate process, speaking a standard JSON-RPC protocol over stdio (or HTTP/SSE for remote servers), that exposes a set of tools Claude can call. This is a genuinely different mechanism from skills/agents/hooks: those are all Claude Code reading its own markdown/JSON config, whereas an MCP server is an independent program with its own capabilities, potentially written by a third party.

## What's configured here

`.mcp.json` at the repo root (project scope, committed to git — shared with anyone who clones this repo):

```json
{
  "mcpServers": {
    "filesystem-demo": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "E:/Projects/spring-boot-sample"]
    }
  }
}
```

This runs the official reference filesystem server, scoped to read/write only within this project's own directory — least-privilege by construction, since the server enforces the allowed-directories boundary itself regardless of what Claude asks for.

## Proven working, not just configured

Because this session couldn't reload its own MCP connections mid-run (see the limitation below), the server was verified independently: a small Node script (`docs/agentic-concepts/mcp-smoke-test.js`, kept for reference) spoke the MCP protocol directly over the server's stdio — `initialize`, then `tools/list`, then a real `tools/call`. The results were real, not simulated:

- **`initialize`** succeeded, negotiating protocol version `2024-11-05` against `secure-filesystem-server` v0.2.0.
- **`tools/list`** returned 14 real tools with full JSON schemas: `read_text_file`, `write_file`, `edit_file`, `list_directory`, `directory_tree`, `search_files`, `move_file`, `get_file_info`, and more.
- **`tools/call`** for `list_directory` against the project's Java package returned the actual file listing: `HelloController.java`, `Item.java`, `ItemController.java`, `ItemRepository.java`, `SpringBootSampleApplication.java`.

## Known limitation: no hot-reload (the same pattern, once more)

Adding `.mcp.json` did not make `filesystem-demo`'s tools appear in *this* session's tool list — consistent with skills, subagents, and hooks all being discovered once at session start (see `precedence-and-conflicts.md`). A fresh Claude Code session started in this directory would prompt to approve the project's MCP server (project-scoped servers require one-time trust approval before first use) and then expose its tools normally — including through the same deferred-tool-loading mechanism you're likely already seeing in whatever session reads this: MCP-server tools often appear as names only, with schemas fetched on demand via a tool-search step, rather than all being loaded eagerly up front.

## Config scopes

MCP servers can be declared at more than one level, and (consistent with everything else in this workspace) the more specific scope should win on a name collision:

- **Project** (`.mcp.json` in the repo root) — what's used here. Shared with the team via git.
- **User** (global, keyed by project path in a personal config) — private to you, available without modifying the repo.
- **Local** — a narrower, non-shared variant of project scope for a single checkout.

This repo only exercises the project scope, since the point was a shareable, reproducible example — a user-scoped server would be invisible to anyone else who clones this repo.

## Why `filesystem-demo` and not something more ambitious

A filesystem server is the "hello world" of MCP: single dependency (Node, already needed elsewhere in this workspace), no API keys or external accounts, and its allowed-directory sandboxing makes the safety story easy to reason about. It was also the fastest path to a *real*, verifiable connection rather than a plausible-looking one — which mattered more here than showcasing a more elaborate server.
