// A standalone Claude Agent SDK example, verified working end-to-end in the session
// that wrote it (see ../ISSUES_AND_FIXES.md and ../docs/agentic-concepts/README.md for
// the real transcript, cost, and one bug this run itself surfaced).
//
// Prerequisites:
//   - spring-boot-sample running on localhost:8080 (see the repo root README)
//   - `npm install` in this directory
//   - Claude Code authenticated on this machine (this SDK spawns the Claude Code CLI's
//     bundled binary and inherits ITS auth - no ANTHROPIC_API_KEY needed if you're
//     already logged into Claude Code here; see docs/agentic-concepts/agent-sdk.md)
//
// Run: npm start

import { query, tool, createSdkMcpServer } from "@anthropic-ai/claude-agent-sdk";

const listItemsTool = tool(
  "list_items",
  "List all items in the spring-boot-sample inventory app.",
  {},
  async () => {
    const res = await fetch("http://localhost:8080/api/items");
    const items = await res.json();
    return { content: [{ type: "text", text: JSON.stringify(items) }] };
  }
);

const itemServer = createSdkMcpServer({
  name: "item-inventory",
  version: "1.0.0",
  tools: [listItemsTool],
});

for await (const message of query({
  prompt: "Use the list_items tool once, then reply with just the name of the first item.",
  options: {
    // 2 was tried first and was too low: it left no budget for the model's final text
    // reply after ToolSearch + the tool call itself each consumed a turn. Real lesson,
    // not a guess - see docs/agentic-concepts/agent-sdk.md.
    maxTurns: 4,
    mcpServers: { "item-inventory": itemServer },
    allowedTools: ["mcp__item-inventory__list_items"],
  },
})) {
  if (message.type === "assistant") {
    console.log("ASSISTANT:", JSON.stringify(message.message.content));
  } else if (message.type === "result") {
    console.log("RESULT:", message.result, "| cost: $" + message.total_cost_usd);
  }
}
