# MCP resources and prompts

`mcp-servers.md` covers MCP's **tools** primitive via `.mcp.json`'s `filesystem-demo` server. That's only one of three primitives MCP defines — this doc covers the other two, closing a real, specifically-named certification gap (Tool Design & MCP Integration, 18% weight) rather than leaving "MCP" as a single covered checkbox.

## The three primitives, genuinely different

| Primitive | What it is | Who decides to use it |
|---|---|---|
| **Tools** | Something callable, with arguments, that does or returns something — an action | The model, mid-conversation, based on the request |
| **Resources** | Readable data at a URI — no arguments, no side effects, just content | The host/client, to include as context (or the user, browsing available resources) |
| **Prompts** | A reusable, pre-written template with optional arguments | The user (or host), selecting it explicitly — e.g. as a slash command |

Conflating these is easy if only tools ever get built — a resource is not "a tool with no arguments," and a prompt is not "a tool that returns text." The distinction is about *who initiates* and *what kind of thing* is being exposed, which is exactly why they're separate primitives in the protocol rather than one generalized "capability."

## What's built and verified: `mcp-resources-prompts-example/`

A second custom MCP server (Node.js, the official `@modelcontextprotocol/sdk`, not the Claude Agent SDK's `createSdkMcpServer` convenience wrapper — that wrapper only exposes the tools shorthand, so a genuine resources/prompts example needed the full SDK):

- **`item-catalog`** resource (`item://catalog`) — reads this repo's own live `/api/items` endpoint, returns the current inventory as JSON.
- **`summarize-items`** prompt — a reusable template with one optional argument (`tone`), returning a ready-to-send request for an inventory summary.

## Proven working, the same way the filesystem-demo server was

Same reasoning as `mcp-servers.md`: a session can't hot-reload a newly-added `.mcp.json` server into itself, so this was verified with a standalone protocol script (`smoke-test.js`) rather than trusting it blindly. Run live, with the Spring Boot app actually running on port 8080:

- `resources/list` correctly returned `item-catalog` with its declared metadata.
- `resources/read` returned the **actual live item data** from the running app — not a fixture, a real HTTP round-trip from inside the MCP server's own handler.
- `prompts/list` correctly returned `summarize-items`, with `tone` marked as an optional argument.
- `prompts/get` called with `{"tone": "cheerful"}` correctly interpolated that argument into the returned message text.

## Why this matters beyond "one more server"

The filesystem-demo server could be (and often is) treated as *the* MCP example, because tools are the most immediately useful primitive and the one most tutorials cover. But a real MCP integration frequently needs resources (exposing a document store, a database schema, a live dashboard as browsable context) and prompts (a team's shared library of vetted prompt templates) just as much as it needs tools — an architecture conversation that only reasons about tool schemas is missing two-thirds of what the protocol actually offers.
