# Codex development guidance

Read the root `CLAUDE.md` for this project's architecture, commands, and conventions before making changes. Also read any `CLAUDE.md` files in the directories containing files you will edit. Keep shared project guidance in those existing files rather than duplicating it here.

- Use the checked-in Maven wrapper: `mvnw.cmd` on Windows or `./mvnw` on Unix.
- Run the relevant tests for changes; use `mvnw.cmd test` (Windows) or `./mvnw test` (Unix) for the full Spring application suite.
- Keep `app.ai.provider=mock` for normal development and tests. Real-provider checks require explicitly authorized API use; never commit credentials.
- Treat `agent-sdk-example/` as a separate Node.js project and consult its README when working there.
- Claude Code hooks in `.claude/` are tool-specific; run the appropriate build and test commands explicitly when working in Codex.
- Inspect the working tree before edits and preserve existing user changes.
