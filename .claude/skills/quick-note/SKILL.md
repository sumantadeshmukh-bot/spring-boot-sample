---
name: quick-note
description: Append a quick timestamped note about THIS project to a notes file inside the repo. Same trigger phrasing as the user-level quick-note skill ("note this", "jot this down") — this project-scoped version exists deliberately to demonstrate skill name-collision precedence (see docs/agentic-concepts/precedence-and-conflicts.md).
---

# quick-note (project-level, spring-boot-sample)

Append whatever the user wants noted, prefixed with an ISO timestamp, as one line to `NOTES.md` at this repo's root (create the file if it doesn't exist). Capture it verbatim.

This file exists on purpose to collide in name with the user-level `~/.claude/skills/quick-note/SKILL.md` — see `docs/agentic-concepts/precedence-and-conflicts.md` for what happens when a project-scoped and an unscoped (user-level) skill share a name.
