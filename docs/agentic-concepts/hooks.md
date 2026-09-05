# Hooks

A hook is a shell command Claude Code runs automatically on a lifecycle event (a tool about to run, a tool that just ran, the session stopping, etc.), configured in `settings.json`. Unlike a skill (invoked by name or by the model's judgment) or a subagent (invoked deliberately), a hook fires unconditionally whenever its event and matcher fire — it's the one mechanism here that isn't "invoked" so much as "always on."

## The two hooks in this repo

Both are `PostToolUse` hooks matching `Edit|Write`, configured in `.claude/settings.json`, implemented as small Node scripts in `.claude/hooks/`:

1. **`log-edit.js`** (observational) — reads the hook's JSON payload from stdin, appends one line (`timestamp  tool_name  file_path`) to `.claude/hooks.log`. Zero risk, zero side effects beyond a log line.
2. **`compile-check.js`** (functional) — same payload, but if the edited file ends in `.java`, it runs `mvnw.cmd -q -o compile` synchronously and, on failure, writes the compiler output to stderr and exits with code `2`.

## Exit codes are the actual mechanism — this matters

A hook communicates back to Claude Code purely through its exit code and stdout/stderr:

- **Exit 0**: success. Stdout is only shown in transcript mode, otherwise silent.
- **Exit 2**: blocking-style error. Stderr is fed back to Claude as context. For a `PreToolUse` hook this can actually prevent the tool call; for `PostToolUse` (what's used here) the tool already ran, so exit 2 instead surfaces the failure as immediate feedback — which is exactly the point of `compile-check.js`: catch a broken edit before it's discovered three steps later.
- **Any other non-zero**: shown to the user as a non-blocking error, not fed back to Claude.

## Verified failure and success paths (not just written, actually tested)

Both scripts were manually driven with synthetic hook-payload JSON before being trusted:

- `log-edit.js` against a real payload → correctly appended `2026-09-05T01:38:09.314Z  Edit  README.md` to `.claude/hooks.log`.
- `compile-check.js` against a clean file → exit 0, silent.
- `compile-check.js` against a deliberately broken `Item.java` (a stray line inserted, forcing an actual `mvnw compile` failure) → exit 2, with the real `[ERROR] ... class, interface, enum, or record expected` compiler output on stderr. The file was then restored and the suite re-confirmed green (9/9).

One real bug surfaced during this: the first version of `compile-check.js` called `mvnw.cmd` as a bare command via `execSync`, which failed with `'mvnw.cmd' is not recognized` — Windows' `execSync` doesn't reliably resolve a relative script name against `cwd` the way a shell prompt does. Fixed by building the full path (`path.join(repoRoot, "mvnw.cmd")`) explicitly rather than relying on shell resolution.

## Known limitation: no hot-reload (same pattern as skills/agents/MCP)

Adding both hooks to `.claude/settings.json` and then making a real `Edit` in the *same* session did **not** produce a `.claude/hooks.log` entry — confirming hooks are read once at session start, same as skills, subagents, and MCP servers (see `precedence-and-conflicts.md`). The scripts were verified correct by invoking them directly with piped test payloads instead; they'll actually fire on edits starting from the next fresh session in this directory.

## Why both a safe and a disruptive hook, deliberately

The observational hook (`log-edit.js`) has no failure mode worth worrying about — it's a reasonable default to just leave on. The functional one (`compile-check.js`) trades editing speed for immediate feedback: every `.java` edit now costs a `mvnw compile` round-trip (a few seconds, using `-o` for offline/cached dependencies to keep it fast). That's a real cost, which is why it's the one flagged as easy to disable later — comment out its block in `.claude/settings.json`'s `PostToolUse` array if it becomes more annoying than useful.
