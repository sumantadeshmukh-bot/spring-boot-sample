# Project commands

Every command run against this repo specifically, in order, with why. Bash (Git Bash) unless noted. See `MACHINE_SETUP_COMMANDS.md` (workspace root) for machine-level tooling setup, and `ISSUES_AND_FIXES.md` for the problems these commands surfaced.

## Scaffolding

```bash
curl -sS "https://start.spring.io/starter.zip" \
  -d dependencies=web,data-jpa,h2,validation -d type=maven-project -d language=java \
  -d baseDir=spring-boot-sample -d groupId=com.example -d artifactId=spring-boot-sample \
  -d name=spring-boot-sample -d packageName=com.example.sample -d javaVersion=17 \
  -o spring-boot-sample.zip
```
Generate the project via Spring Initializr's API. First attempt pinned `bootVersion=3.3.4` and was rejected (HTTP 400 — out of Initializr's supported range); this final version omits `bootVersion` and lets it default (resolved to Spring Boot 4.1.1).

```bash
unzip -q spring-boot-sample.zip && rm spring-boot-sample.zip
```
Unpack the generated project.

## Build

```bash
./mvnw clean package -DskipTests
```
Compile and package into `target/spring-boot-sample-0.0.1-SNAPSHOT.jar`, skipping tests for a fast first build. Run via Maven's wrapper so no local Maven install is needed — just a JDK.

## Run & smoke test

```powershell
Start-Process -FilePath "...\java.exe" -ArgumentList "-jar target\spring-boot-sample-0.0.1-SNAPSHOT.jar" -RedirectStandardOutput "app.log" -RedirectStandardError "app-err.log" -WindowStyle Hidden -PassThru
```
Run the built jar in the background, logging to files so startup could be inspected without blocking the shell.

```bash
curl http://localhost:8080/api/hello
curl -X POST http://localhost:8080/api/items -H "Content-Type: application/json" -d '{"name":"Widget","description":"A sample widget"}'
curl http://localhost:8080/api/items
curl http://localhost:8080/api/items/1
curl -X PUT http://localhost:8080/api/items/1 -H "Content-Type: application/json" -d '{"name":"Widget v2","description":"Updated widget"}'
curl -X DELETE http://localhost:8080/api/items/1
```
Exercise the full CRUD lifecycle end-to-end against the running app.

```powershell
Stop-Process -Id <pid> -Force
```
Stop the running instance once verified (and again later, before adding tests/Docker/CI, so a rebuild wouldn't collide with a live jar).

## Version control & GitHub

```bash
git init
git add -A
git status
```
Initialize the repo and review what would be committed.

```bash
git rm --cached -f app.log app-err.log
```
Un-stage runtime log files that got swept in by `git add -A` before `*.log` was added to `.gitignore`.

```bash
git commit -m "..."
```
First commit (scaffold + CRUD implementation), and later commits for tests/Docker/CI/Claude config, and for README/issues docs.

```bash
"/c/Program Files/GitHub CLI/gh.exe" repo create spring-boot-sample --private --source=. --remote=origin --push
```
Create the GitHub repo and push in one step (used `gh`'s full path since this shell's `PATH` predated its install — see `MACHINE_SETUP_COMMANDS.md`).

```bash
"/c/Program Files/GitHub CLI/gh.exe" repo view sumantadeshmukh-bot/spring-boot-sample --json name,url,visibility,owner
```
Diagnose a "Page not found" report from the user — confirmed the repo existed and was private, owned by the `gh`-authenticated account (`sumantadeshmukh-bot`), which turned out to differ from the user's browser login.

```bash
"/c/Program Files/GitHub CLI/gh.exe" repo edit sumantadeshmukh-bot/spring-boot-sample --visibility public --accept-visibility-change-consequences
```
Made the repo public to sidestep the account-mismatch access issue.

```bash
git push
```
Push each subsequent round of commits.

## Tests

```bash
./mvnw test
```
Run the suite. First run failed to compile — `ItemControllerTest` used `com.fasterxml.jackson.databind.ObjectMapper` and `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`, both moved in Spring Boot 4.1's Jackson 3 migration.

```bash
./mvnw dependency:tree | grep -E "jackson|webmvc|test|validation|h2"
```
Diagnose the compile failure by seeing what was actually on the classpath (revealed `tools.jackson.core:jackson-databind:3.1.5`, not the classic `com.fasterxml.jackson.core`).

```bash
find "$HOME/.m2/repository/org/springframework/boot/spring-boot-webmvc-test" -name "*.jar"
unzip -l <jar> | grep -i "MockMvc"
find "$HOME/.m2/repository/tools/jackson/core/jackson-databind" -name "*.jar"
unzip -l <jar> | grep "ObjectMapper.class"
```
Locate the classes' actual new package paths directly inside the downloaded jars, rather than guessing from outdated docs. Fixed the imports accordingly and re-ran `./mvnw test` to green.

```bash
git ls-files -s mvnw
git update-index --chmod=+x mvnw
```
`mvnw` was tracked as `100644` (no executable bit) because it was created/staged on Windows. GitHub Actions' `ubuntu-latest` runner needs it executable, so the bit was set directly in git's index.

## Docker

```bash
docker build -t spring-boot-sample .
docker run --rm -p 8080:8080 spring-boot-sample
```
The intended local verification commands — not yet run successfully on this machine because Docker Desktop's engine can't start until WSL2 is enabled (see `ISSUES_AND_FIXES.md` item 7). The Dockerfile itself was instead validated via the CI workflow below, whose runner has Docker preinstalled.

## CI

```bash
"/c/Program Files/GitHub CLI/gh.exe" run list --repo sumantadeshmukh-bot/spring-boot-sample --limit 5
"/c/Program Files/GitHub CLI/gh.exe" run watch <run-id> --repo sumantadeshmukh-bot/spring-boot-sample --exit-status
```
Confirm the just-pushed `.github/workflows/ci.yml` actually triggered, then watch it through to completion — build, test, and Docker build all passed.

## Agentic features (skills, subagents, hooks, MCP) — see `docs/agentic-concepts/`

```bash
node docs/agentic-concepts/mcp-smoke-test.js
```
Manually drive the MCP protocol (`initialize` → `tools/list` → `tools/call`) against the `filesystem-demo` server declared in `.mcp.json`, proving it works without relying on this session's own (session-scoped) MCP connection. First attempt used the official `@modelcontextprotocol/inspector --cli` wrapper, which failed on Windows due to a nested-`npx` path-parsing bug (see `ISSUES_AND_FIXES.md` item 9) — this direct script sidesteps that.

```bash
node .claude/hooks/log-edit.js < payload.json
node .claude/hooks/compile-check.js < payload.json
```
Manually invoke each `PostToolUse` hook with a synthetic JSON payload (`{"tool_name":"Edit","tool_input":{"file_path":"..."}}`) to verify both the success and failure paths, since hooks added mid-session don't fire on real edits in that same session (see `ISSUES_AND_FIXES.md` item 11). The failure path was verified by temporarily breaking `Item.java`'s syntax, confirming the hook caught the real compiler error (exit 2), then restoring the file and re-running `./mvnw test`.

**Live multi-agent demo** (via the `Agent` tool, not a shell command): spawned a `general-purpose` agent instructed to follow `.claude/agents/java-spring-dev.md`'s persona to implement the `/api/items/search` endpoint, then a second independent `general-purpose` agent instructed to follow `.claude/agents/spring-code-reviewer.md`'s persona to review it. (Spawning them as their actual named subagent types failed — `Agent type 'java-spring-dev' not found` — since subagents created mid-session aren't hot-loaded; see `ISSUES_AND_FIXES.md` item 11.) The reviewer's one real finding (missing test for the blank-query branch) was then fixed directly. Full writeup: `docs/agentic-concepts/multi-agent-flow.md`.

**Live parallel fan-out demo**: three `general-purpose` agents spawned in one batch (security / test-coverage / architecture review of `src/main/java/com/example/sample/ai/`), each independent. Five real findings across all three, all fixed directly afterward. Full writeup: `docs/agentic-concepts/orchestration-patterns.md`.

## App-level agentic layer (`src/main/java/com/example/sample/ai/`)

```bash
./mvnw clean package -DskipTests   # rebuild the jar after adding the ai/ package
java -jar target/spring-boot-sample-0.0.1-SNAPSHOT.jar
curl -X POST http://localhost:8080/api/ai/ask -H "Content-Type: application/json" -d '{"query":"find widget"}'
```
Smoke-test the mocked tool-calling loop end-to-end against the real running app (default `app.ai.provider=mock`, no API key needed). Confirmed the structured trace log (`ai_trace ...`) and the security-rejection log (`ai_security_reject ...`) both appear in stdout for their respective cases.

```bash
./mvnw -o clean test
```
Forces a real recompile before testing — plain `./mvnw -o test` was seen to report `Nothing to compile - all classes are up to date` despite real source edits (see `ISSUES_AND_FIXES.md` item 15); `clean` avoids relying on Maven's timestamp-based staleness check.

## Claude Agent SDK (`agent-sdk-example/`)

```bash
npm view @anthropic-ai/claude-agent-sdk versions --json
npm view @anthropic-ai/claude-agent-sdk dist-tags version description
```
Verified the package is real (not a guessed/hallucinated name) before writing any code against it, and got its actual latest version.

```bash
npm install @anthropic-ai/claude-agent-sdk --no-save   # in a scratch dir
```
Installed it into a throwaway location to read its actual `.d.ts` type definitions and `README.md` directly, rather than writing example code from assumption. This is where the `resolveSettings()`/`SettingSource`/`PolicySettingsOrigin` types were found, correcting `docs/agentic-concepts/precedence-and-conflicts.md`'s settings-precedence section with primary-source evidence.

```bash
node minimal-test.mjs        # trivial query(), proved auth inheritance + real cost reporting
node custom-tool-test.mjs    # tool()+createSdkMcpServer() calling this repo's live /api/items
```
Two real, paid API calls (~$0.15 + ~$0.04) run once each — deliberately not repeated further given the real cost per call. See `docs/agentic-concepts/agent-sdk.md` and `ISSUES_AND_FIXES.md` items 16–17 for what each run revealed. The scratch directory these ran from was deleted afterward; the cleaned-up, corrected version lives in `agent-sdk-example/item-agent.mjs`.

## Tool composition, confirmation flow, and multi-step orchestration (`ai/`)

```bash
./mvnw -o clean test
```
Ran repeatedly through the `LlmClient` interface refactor (single-shot `decideTool` → history-aware `decideNextStep`) that enabled composition — `clean` needed every time since Maven's staleness check kept reporting "nothing to compile" despite real interface changes (same pattern as `ISSUES_AND_FIXES.md` item 15).

```bash
curl -X POST http://localhost:8080/api/items -H "Content-Type: application/json" -d '{"name":"Widget","description":"to be deleted"}'
curl -X POST http://localhost:8080/api/ai/ask -H "Content-Type: application/json" -d '{"query":"delete the item named Widget"}'
curl http://localhost:8080/api/items   # confirm NOT yet deleted
curl -X POST http://localhost:8080/api/ai/confirm -H "Content-Type: application/json" -d '{"token":"<token from ask response>"}'
curl http://localhost:8080/api/items   # confirm now deleted
curl -X POST http://localhost:8080/api/ai/confirm -H "Content-Type: application/json" -d '{"token":"<same token>"}'   # expect 404, single-use
```
Live end-to-end proof of the composed search→delete flow and the confirmation gate — this first run caught a real bug (item 19: the name-extraction regex left "item named Widget" instead of "Widget"), which was fixed and this exact sequence re-run to confirm the fix before moving on.

## MCP resources & prompts (`mcp-resources-prompts-example/`)

```bash
npm view @modelcontextprotocol/sdk version
npm install @modelcontextprotocol/sdk --no-save   # in a scratch dir, to read its .d.ts for the real registerResource/registerPrompt signatures
```
Verified the package and its actual API shape before writing server code against it, same discipline as the Agent SDK check above.

```bash
node smoke-test.js
```
Drove the raw MCP protocol (`resources/list`, `resources/read`, `prompts/list`, `prompts/get`) against the custom server with the Spring Boot app running live on port 8080 — `resources/read` returned the actual live item data, not a fixture, proving the resource's HTTP call inside its own handler works for real.
