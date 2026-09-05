const fs = require("fs");
const path = require("path");

let input = "";
process.stdin.on("data", (chunk) => (input += chunk));
process.stdin.on("end", () => {
  let event;
  try {
    event = JSON.parse(input);
  } catch (e) {
    process.exit(0);
  }

  const filePath = event.tool_input && (event.tool_input.file_path || event.tool_input.path) || "(unknown)";
  const line = `${new Date().toISOString()}  ${event.tool_name}  ${filePath}\n`;

  const logPath = path.join(__dirname, "..", "hooks.log");
  fs.appendFileSync(logPath, line);
  process.exit(0);
});
