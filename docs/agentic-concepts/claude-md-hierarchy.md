# CLAUDE.md hierarchy

`CLAUDE.md` is plain-context guidance loaded automatically into a Claude Code session — not a config format with a schema, just markdown Claude reads before working. It exists at four levels in this workspace, each demonstrating a different scope:

| Level | File | Applies to |
|---|---|---|
| User (global) | `C:\Users\Hp\.claude\CLAUDE.md` | Every project on this machine |
| Parent directory | `E:\Projects\CLAUDE.md` | Everything under `E:\Projects` |
| Project | `spring-boot-sample\CLAUDE.md` | This repo |
| Subdirectory | `spring-boot-sample\src\main\java\com\example\sample\CLAUDE.md` | Just this package |

## How discovery actually works

Claude Code walks **up** from the current working directory looking for `CLAUDE.md` files, and also picks up a deeper one when you're actively reading/editing files in that subdirectory. All of them get loaded — this is additive context, not a single file that "wins." The user-level file is the broadest, loaded regardless of which project you're in; a project's own file is more specific; a subdirectory's is more specific still.

## Conflict resolution — the important nuance

Unlike `settings.json` (see `precedence-and-conflicts.md`), there is **no deterministic override rule** for conflicting `CLAUDE.md` content. If the global file says one thing and the project file says another, both are simply present in context, and Claude uses judgment — weighted toward the more specific (closer/deeper) file being more relevant to the task at hand. This is a real difference worth internalizing: `CLAUDE.md` guidance is advisory and contextual, while `settings.json` permissions/hooks are enforced and have a strict precedence order.

**Practical implication:** don't put contradictory instructions at different levels expecting the "more specific" one to cleanly win the way a CSS selector or a settings override would — it usually does, because specificity is genuinely a strong signal, but it's not guaranteed the way a programmatic override is. Keep global files to things unlikely to ever be contradicted (tool defaults, tone), and put anything load-bearing in the most specific file that's actually relevant.

## What's in each file here, and why it's scoped that way

- **User-level** (`~/.claude/CLAUDE.md`): generic, machine-wide developer preferences (shell syntax defaults) — nothing here should ever be project-specific, since it's a cost paid in every session everywhere.
- **Parent-directory-level** (`E:\Projects\CLAUDE.md`): machine *environment* facts discovered while setting up this project (JDK/Node paths, the Docker/WSL2 gap, the recurring "just-installed CLI tool isn't on PATH in this shell" pattern) — genuinely useful to any future project dropped into this same workspace, not just this one.
- **Project-level** (`spring-boot-sample\CLAUDE.md`): architecture and build/test/run commands — see that file directly.
- **Subdirectory-level** (the package folder): one narrow fact (JPA `GenerationType.IDENTITY` choice) that's true only within that package and would be noise anywhere else.

## A distinct but easily-confused concept: the VS Code workspace

`dev.code-workspace` (also at `E:\Projects`) is a **VS Code** concept — a multi-root workspace file controlling what VS Code's file explorer shows and which settings/extensions apply in the editor. It happens to live in the same folder as the parent-directory `CLAUDE.md`, but the two are unrelated: adding a folder to the VS Code workspace doesn't change what Claude Code's `CLAUDE.md` walk picks up, and vice versa. Don't conflate "workspace" (VS Code, editor-scoped) with the directory hierarchy `CLAUDE.md` walks (Claude Code, filesystem-scoped) just because this demo happens to overlap them physically.
