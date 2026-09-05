// A custom MCP server exposing the two primitives NOT covered by .mcp.json's
// filesystem-demo server elsewhere in this repo: resources and prompts. Tools
// are a model *calling something*; resources are the model (or its host) *reading
// something*; prompts are reusable, user-selectable templates - three genuinely
// different primitives, easy to conflate if "tools" is the only one ever exercised.
//
// See docs/agentic-concepts/mcp-resources-and-prompts.md for what running this proved.

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({ name: "item-inventory-mcp", version: "1.0.0" });

// --- Resource: the model/host can READ this without "calling" anything - no arguments,
// no side effects, just data at a URI, the same way a filesystem path or a web URL works.
server.registerResource(
  "item-catalog",
  "item://catalog",
  {
    title: "Item Catalog",
    description: "The current contents of the spring-boot-sample item inventory (live, via its REST API).",
    mimeType: "application/json",
  },
  async (uri) => {
    const res = await fetch("http://localhost:8080/api/items");
    const items = await res.json();
    return {
      contents: [{ uri: uri.href, mimeType: "application/json", text: JSON.stringify(items, null, 2) }],
    };
  }
);

// --- Prompt: a reusable, user-selectable template - the host surfaces this as something
// a person can pick (e.g. a slash command), distinct from a tool the model decides to call.
server.registerPrompt(
  "summarize-items",
  {
    title: "Summarize inventory",
    description: "A reusable prompt template asking for a concise summary of the current item inventory.",
    argsSchema: { tone: z.string().optional() },
  },
  async ({ tone }) => ({
    messages: [
      {
        role: "user",
        content: {
          type: "text",
          text: `Summarize the current item inventory (see the item-catalog resource) in a ${tone || "neutral, concise"} tone. Mention the total count and call out anything unusual.`,
        },
      },
    ],
  })
);

const transport = new StdioServerTransport();
await server.connect(transport);
