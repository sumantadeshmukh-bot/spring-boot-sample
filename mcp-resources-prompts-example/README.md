# mcp-resources-prompts-example

A custom MCP server demonstrating **resources** and **prompts** — the two MCP primitives not covered by `.mcp.json`'s `filesystem-demo` server elsewhere in this repo, which only exercises **tools**. Three genuinely different primitives, easy to conflate if tools are the only one ever touched:

- **Tools**: the model decides to call something, with arguments, expecting a result — an action.
- **Resources**: something readable at a URI, with no "calling" involved — data, the same way a file path or a web URL is.
- **Prompts**: a reusable, user-selectable template — surfaced by the host (e.g. as a slash command), not something the model decides to invoke mid-conversation.

## What's here

- `item-catalog` (a **resource**, `item://catalog`) — reads the spring-boot-sample app's live `/api/items` and returns it as JSON. No arguments, no side effects.
- `summarize-items` (a **prompt**) — a reusable template with one optional argument (`tone`), returning a ready-to-send message asking for an inventory summary.

## Verified working, not just written

`smoke-test.js` drives the raw MCP protocol directly (the same approach as `docs/agentic-concepts/mcp-smoke-test.js`), and was actually run against this server with the Spring Boot app live on port 8080. Real results:

- `resources/list` correctly returned the `item-catalog` resource with its metadata.
- `resources/read` returned the **actual live item data** from the running app, not a fixture.
- `prompts/list` correctly returned `summarize-items` with its `tone` argument marked optional.
- `prompts/get` with `{"tone": "cheerful"}` correctly interpolated the argument into the returned message text.

## Running it

```bash
npm install
# with the Spring Boot app running on localhost:8080:
node smoke-test.js
```

To wire this into a Claude Code session's own `.mcp.json` (as a second server alongside `filesystem-demo`), see the repo root's `.mcp.json`.
