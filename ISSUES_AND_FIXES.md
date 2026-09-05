# Issues encountered and fixes

A chronological log of problems hit while setting up this project and its environment, kept so the same wall isn't hit twice.

## 1. Spring Initializr rejected the requested Spring Boot version

**Symptom:** Generating the project via `curl https://start.spring.io/starter.zip -d bootVersion=3.3.4 ...` returned HTTP 400:
```
Invalid Spring Boot version '3.3.4', Spring Boot compatibility range is >=4.0.0
```

**Cause:** Spring Boot has moved past 3.x by now; 3.3.4 is out of Initializr's supported range.

**Fix:** Dropped the `bootVersion` parameter and let Initializr pick its current default, which resolved to **4.1.1**.

## 2. No JDK installed on the machine

**Symptom:** `java -version` → "not recognized as the name of a cmdlet...".

**Fix:** Installed Oracle JDK 21 via winget (`winget install --id Oracle.JDK.21`) and set `JAVA_HOME` as a user-level environment variable pointing at `C:\Program Files\Java\jdk-21.0.12`.

## 3. `gh` (GitHub CLI) not resolving in the shell

**Symptom:** `gh` reported as an unrecognized command, despite being installed and authenticated.

**Cause:** The running shell's `PATH` was captured before `gh` was added to the system `PATH` (it was installed earlier in the same setup pass).

**Fix:** Not an actual bug — resolves automatically in any new terminal. Used the full path (`C:\Program Files\GitHub CLI\gh.exe`) as a workaround within the existing session.

## 4. New private GitHub repo returned "Page not found"

**Symptom:** Visiting the newly created repo's URL in a browser returned GitHub's 404 page, even though `gh repo view` confirmed the repo existed.

**Cause:** `gh` was authenticated as account `sumantadeshmukh-bot` (likely a token/bot-style account), which is a different login than the one browsing github.com. A private repo 404s for anyone without access, including "the same person" logged in under a different account.

**Fix:** Switched the repo to public (`gh repo edit --visibility public --accept-visibility-change-consequences`), since it's a harmless sample project — sidesteps the account-matching problem entirely.

## 5. Spring Boot 4's Jackson 3 migration broke test compilation

**Symptom:** Adding a MockMvc test failed to compile:
```
package com.fasterxml.jackson.databind does not exist
package org.springframework.boot.test.autoconfigure.web.servlet does not exist
```

**Cause:** Spring Boot 4.1 migrated from Jackson 2 to **Jackson 3**, which lives under a new Maven coordinate and package (`tools.jackson.core:jackson-databind` → `tools.jackson.databind.ObjectMapper`, not `com.fasterxml.jackson.databind`). It also reorganized MockMvc test-support classes into `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (the old `org.springframework.boot.test.autoconfigure.web.servlet` path is gone).

**Diagnosis:** `./mvnw dependency:tree` to see what was actually on the classpath, then `unzip -l` on the relevant jars in `~/.m2/repository` to find the classes' real package names.

**Fix:** Updated the test's imports to the Boot 4 paths. Documented in `CLAUDE.md` so it isn't rediscovered from scratch next time.

## 6. `mvnw` missing its executable bit for the GitHub Actions (Linux) runner

**Symptom:** Not yet hit at runtime, but would have failed `./mvnw` with "Permission denied" on `ubuntu-latest` — Windows doesn't track the Unix executable bit, so a file created/edited on this machine defaults to `100644` in git.

**Fix:** `git update-index --chmod=+x mvnw` before committing, so git tracks the file as `100755` regardless of the OS that staged it.

## 7. Docker Desktop installed but its engine won't start (WSL2 not enabled)

**Symptom:** `docker ps` failed with:
```
request returned 500 Internal Server Error for API route ... dockerDesktopLinuxEngine ...
```
and `wsl --status` / `wsl -l -v` printed old legacy usage text instead of real status output.

**Cause:** WSL2 itself (not just the `wsl.exe` binary) was never enabled on this machine — a hard prerequisite for Docker Desktop's Linux engine on Windows.

**Fix:** Requires an **elevated** `wsl --install` followed by a reboot — outside what Claude Code can do from a non-admin shell. Flagged to the user and deferred; in the meantime, the Dockerfile itself was validated via the GitHub Actions workflow, whose `ubuntu-latest` runner has Docker preinstalled — confirming the image builds correctly independent of the local Docker Desktop issue.

## 8. Node.js already installed, but same stale-PATH issue as `gh`

**Symptom:** `node`/`npx` not recognized, despite `winget install --id OpenJS.NodeJS.LTS` reporting "no upgrade found" (i.e., already installed).

**Cause:** Same root cause as issue with `gh` earlier — the running shell's `PATH` predates Node's installation on this machine.

**Fix:** Used the full path (`C:\Program Files\nodejs\node.exe`) within the session; resolves normally in a new terminal.

## 9. Nested `npx`-inside-`npx` fails on Windows with a cryptic path error

**Symptom:** Running the official MCP Inspector CLI to smoke-test a server (`npx @modelcontextprotocol/inspector --cli npx @modelcontextprotocol/server-filesystem <path> --method tools/list`) failed with `The filename, directory name, or volume label syntax is incorrect.` — regardless of whether the target path used forward or back slashes.

**Cause:** Windows' `npx.cmd` wrapper spawning another `npx.cmd` (inspector spawning the target server) doesn't reliably pass through arguments/paths across that double layer of `.cmd` wrapper scripts.

**Fix:** Bypassed the Inspector CLI entirely — wrote a small Node script (`docs/agentic-concepts/mcp-smoke-test.js`) that spawns the target MCP server directly and speaks the JSON-RPC protocol over its stdio by hand (`initialize` → `notifications/initialized` → `tools/list` → `tools/call`). More code, but no nested-wrapper fragility, and it doubles as a clear illustration of what MCP actually looks like under the hood.

## 10. `execSync("mvnw.cmd ...")` fails despite the file existing in `cwd`

**Symptom:** A hook script (`.claude/hooks/compile-check.js`) calling `execSync("mvnw.cmd -q -o compile", { cwd: repoRoot })` failed with `'mvnw.cmd' is not recognized as an internal or external command`, even though `mvnw.cmd` exists directly in `repoRoot`.

**Cause:** Node's `execSync` (via `cmd.exe`) doesn't reliably resolve a bare relative command name against the `cwd` option the way an interactive shell prompt would.

**Fix:** Built the full path explicitly — `path.join(repoRoot, "mvnw.cmd")` — instead of relying on relative resolution.

## 11. Newly-created subagents/skills/hooks/MCP servers aren't available mid-session

**Symptom:** Immediately after creating `.claude/agents/java-spring-dev.md`, trying to spawn it (`subagent_type: "java-spring-dev"`) failed: `Agent type 'java-spring-dev' not found. Available agents: claude, claude-code-guide, Explore, general-purpose, Plan, statusline-setup`. The same session-scoped-discovery pattern was then independently confirmed for hooks (a new `PostToolUse` hook didn't fire on a real edit in the same session) and MCP servers (`.mcp.json` additions didn't appear in the tool list).

**Cause:** Claude Code enumerates project-level subagents, skills, hooks, and MCP servers once at session start — none of the four are hot-reloaded when their backing files change mid-session.

**Fix:** No fix, just a workflow adjustment — for anything in these four categories, verify by direct/manual invocation within the session that created it (e.g., running a hook script by hand with a synthetic payload, or having a generic agent read a persona file explicitly), and expect the "real" registered version to only be available starting from the next fresh session. See `docs/agentic-concepts/precedence-and-conflicts.md` for the full pattern.
