# CLAUDE.md (subdirectory level)

Everything about this codebase's architecture and conventions lives in the repo root's `CLAUDE.md` — read that first. This file only adds what's specific to working *inside this exact package*, which is currently the entire codebase (there's no sub-package split — controllers, entities, and repositories all live flat here).

## One thing not in the root file

`Item.id` uses `GenerationType.IDENTITY` (not `SEQUENCE` or `AUTO`) — this is deliberate for H2 compatibility and simplicity, not an oversight. If this project ever grows a second entity, default to the same strategy rather than mixing generation strategies within one small app.
