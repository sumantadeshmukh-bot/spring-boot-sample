// Manual MCP protocol smoke test — proves the filesystem-demo server configured
// in .mcp.json actually works, by speaking the protocol directly over stdio
// rather than relying on Claude Code's own (session-scoped) MCP connection.
// Run with: node docs/agentic-concepts/mcp-smoke-test.js
//
// See docs/agentic-concepts/mcp-servers.md for what this proved and why
// it was necessary (MCP servers don't hot-reload into a running session).

const { spawn } = require("child_process");

const server = spawn("npx", ["-y", "@modelcontextprotocol/server-filesystem", "E:/Projects/spring-boot-sample"], {
  stdio: ["pipe", "pipe", "pipe"],
  shell: true,
});

let buffer = "";
const responses = [];

server.stdout.on("data", (chunk) => {
  buffer += chunk.toString();
  let idx;
  while ((idx = buffer.indexOf("\n")) >= 0) {
    const line = buffer.slice(0, idx).trim();
    buffer = buffer.slice(idx + 1);
    if (line) {
      try {
        responses.push(JSON.parse(line));
      } catch (e) {
        // ignore non-JSON lines
      }
    }
  }
});

server.stderr.on("data", (chunk) => {
  process.stderr.write("[server stderr] " + chunk.toString());
});

function send(msg) {
  server.stdin.write(JSON.stringify(msg) + "\n");
}

async function wait(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  await wait(1500);

  send({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {
      protocolVersion: "2024-11-05",
      capabilities: {},
      clientInfo: { name: "mcp-smoke-test", version: "1.0.0" },
    },
  });
  await wait(1000);

  send({ jsonrpc: "2.0", method: "notifications/initialized" });
  await wait(300);

  send({ jsonrpc: "2.0", id: 2, method: "tools/list" });
  await wait(1000);

  send({
    jsonrpc: "2.0",
    id: 3,
    method: "tools/call",
    params: {
      name: "list_directory",
      arguments: { path: "E:/Projects/spring-boot-sample/src/main/java/com/example/sample" },
    },
  });
  await wait(1000);

  console.log("\n=== RESPONSES ===");
  for (const r of responses) {
    console.log(JSON.stringify(r, null, 2));
  }

  server.kill();
  process.exit(0);
}

main();
