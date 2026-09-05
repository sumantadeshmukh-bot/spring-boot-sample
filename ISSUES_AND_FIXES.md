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
