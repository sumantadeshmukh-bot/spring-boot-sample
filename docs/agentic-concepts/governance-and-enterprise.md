# Governance and enterprise controls

The `precedence-and-conflicts.md` update in this pass corrected an earlier, less precise description of the "managed" settings tier using the Claude Agent SDK's own type definitions as a primary source. This doc builds one concrete artifact on top of that corrected understanding, plus the two other governance concerns a senior review would expect: audit logging and cost attribution.

## A concrete managed-settings example — scoped correctly this time

The key fact from `precedence-and-conflicts.md`: a managed/enterprise policy is filtered through a **restrictive-key allowlist** — it can lock down `permissions.deny`/`ask`, sandbox restrictions, and `allowManaged*Only` flags, but it **cannot** set non-restrictive keys like `model` or `env` (those are silently dropped if present). An illustrative `managed-settings.json` that respects this — i.e., one that would actually survive the SDK's own filtering rather than silently losing half its content:

```json
{
  "permissions": {
    "deny": [
      "Bash(rm -rf *)",
      "Bash(git push --force *)"
    ],
    "ask": [
      "Bash(git push *)"
    ]
  }
}
```

Deliberately **not** included, because the real filtering rule would drop them anyway: a `model` override, an `env` block, or anything else outside the restrictive-key set. This is the opposite of the naive assumption an architect might otherwise carry into a design review ("enterprise policy overrides everything") — it doesn't; it can only narrow what's already allowed, never expand or redirect it.

On Windows specifically, this same policy can also arrive via the registry (`HKLM`/`HKCU`, per the SDK's `PolicySettingsOrigin` type) or a macOS `plist` via MDM — the JSON-file form above is one of several possible carriers, not the only one.

## Audit logging

This repo already has the raw material for an audit trail, built for a different reason (hooks demo, `agentic-application-layer.md`'s security-reject logging) but genuinely serving this purpose too:

- `.claude/hooks/log-edit.js` — every `Edit`/`Write` tool call, logged with timestamp, tool, and file path. This is a rudimentary version of what a compliance-driven deployment (SOC 2, etc.) would formalize: a durable, tamper-evident record of what an agent changed and when.
- `AiOrchestrationService`'s `ai_security_reject` / `ai_rate_limit_reject` log lines — the audit trail for the *rejected* side of the app's AI feature, which matters as much as the accepted side for detecting abuse patterns.

What a real enterprise deployment adds beyond this: log shipping to an immutable store (not a local file a user process can edit), a `PreToolUse` hook (not just `PostToolUse`) so denied actions are captured before execution rather than only observed after, and correlation IDs tying an agent's actions back to a specific user/session/ticket for compliance review.

## Cost attribution

`AiTrace.durationMillis` is the primitive version of cost tracking already present in this codebase — timing, not spend. Two real, better examples surfaced directly while building the Agent SDK example (`agent-sdk.md`): every `result` message from a live `query()` call included `total_cost_usd` directly, computed and reported by the platform itself, not estimated after the fact from token counts. That's the shape a real system should track per call: not just "did this succeed" but "what did it cost," attributed to whatever dimension governance cares about (per-user, per-team, per-feature) — something this repo's mocked `LlmClient` path deliberately has none of, since mock mode has zero real cost to attribute.

A production version would extend `AiTrace` with a cost field, populated from the real provider's reported usage (present in `AnthropicLlmClient`'s untested-but-real path) rather than only from the mock, and roll those up by whatever key (user id, API client, team) the organization needs for chargeback or budget alerting.
