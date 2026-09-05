const { execSync } = require("child_process");
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

  const filePath = (event.tool_input && (event.tool_input.file_path || event.tool_input.path)) || "";
  if (!filePath.endsWith(".java")) {
    process.exit(0);
  }

  const repoRoot = path.join(__dirname, "..", "..");
  const mvnw = path.join(repoRoot, "mvnw.cmd");
  try {
    execSync(`"${mvnw}" -q -o compile`, { cwd: repoRoot, stdio: "pipe", timeout: 60000 });
    process.exit(0);
  } catch (err) {
    const output = (err.stdout ? err.stdout.toString() : "") + (err.stderr ? err.stderr.toString() : "");
    process.stderr.write(`Compile check failed after editing ${filePath}:\n${output}\n`);
    process.exit(2);
  }
});
