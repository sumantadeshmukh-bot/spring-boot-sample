// Manual MCP protocol smoke test for server.mjs's resources/prompts, mirroring
// docs/agentic-concepts/mcp-smoke-test.js's approach for the filesystem-demo tools server.
// Requires the Spring Boot app running on localhost:8080 (the resource reads from it).
// Run with: node smoke-test.js

const { spawn } = require("child_process");
const path = require("path");

const server = spawn("node", ["server.mjs"], {
  cwd: __dirname,
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
server.stderr.on("data", (chunk) => process.stderr.write("[server stderr] " + chunk.toString()));

function send(msg) {
  server.stdin.write(JSON.stringify(msg) + "\n");
}
async function wait(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  await wait(1000);
  send({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: { protocolVersion: "2024-11-05", capabilities: {}, clientInfo: { name: "smoke-test", version: "1.0" } },
  });
  await wait(500);
  send({ jsonrpc: "2.0", method: "notifications/initialized" });
  await wait(300);

  send({ jsonrpc: "2.0", id: 2, method: "resources/list" });
  await wait(500);
  send({ jsonrpc: "2.0", id: 3, method: "resources/read", params: { uri: "item://catalog" } });
  await wait(1000);
  send({ jsonrpc: "2.0", id: 4, method: "prompts/list" });
  await wait(500);
  send({ jsonrpc: "2.0", id: 5, method: "prompts/get", params: { name: "summarize-items", arguments: { tone: "cheerful" } } });
  await wait(500);

  console.log("\n=== RESPONSES ===");
  for (const r of responses) console.log(JSON.stringify(r, null, 2));

  server.kill();
  process.exit(0);
}
main();
