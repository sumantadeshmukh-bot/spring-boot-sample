# Multi-agent orchestration patterns

`multi-agent-flow.md` covers one pattern (sequential build → review → fix) in depth. This doc covers the others actually exercised in this workspace, plus one covered conceptually, so the comparison is concrete rather than abstract.

## 1. Sequential (build → review → fix) — see `multi-agent-flow.md`

One agent implements, a second independent agent reviews, findings get fixed. Cheapest to reason about; strictly serial, so wall-clock cost is the sum of every step.

## 2. Parallel fan-out / fan-in — real, run on this repo's `ai/` package

Three independent agents were spawned **in one batch** (not sequentially), each reviewing the same code from a different, non-overlapping angle: security, test coverage, architecture. None saw the others' output while working.

```
                 ┌──► security review    ──┐
implemented code ├──► test-coverage review ─┼──► synthesized, real fixes applied
                 └──► architecture review ─┘
```

**Why parallel here, specifically:** the three review angles don't depend on each other's findings — a security reviewer doesn't need to know what the architecture reviewer thinks to do their job. Running them sequentially would have added wall-clock time with zero benefit; running them in parallel got three independent, non-contaminating perspectives in roughly the time of one.

**What it actually caught** (not staged — this was real code from earlier in the same session): a rate-limiter DoS vector, dead validation (`@Valid` declared but never applied), a leaked internal exception message, zero test coverage on the single most important line in the service (the tool whitelist check), and a triplicated tool-name list with no single source of truth. All fixed — see `agentic-application-layer.md` for the full list and `../src/main/java/com/example/sample/ai/` for the result.

**When parallel fan-out doesn't pay for itself:** if the review angles overlap heavily (e.g., three agents all doing general code review with no distinct focus), you get three redundant passes and no independence benefit — the value comes specifically from *non-overlapping* scopes, not from "more reviewers."

## 3. Iterative self-critique loop — real, one full cycle run twice in this session

Build → review → fix → (implicitly) re-verified, bounded by a max-iteration mental model rather than looping indefinitely:

- **Cycle 1** (documented in `multi-agent-flow.md`): search feature built → reviewed → one missing test found → fixed → suite re-run green.
- **Cycle 2** (this doc, pattern #2 above): the `ai/` package built → three-way parallel review → five real findings across security/coverage/architecture → all fixed → full suite re-run green (53/53 tests).

Two real cycles is enough to show the shape of the pattern without it becoming performative — a real iterative loop in production would bound itself with an explicit max-iteration count or a cost budget (the Agent SDK's `maxTurns`/`maxBudgetUsd`, covered in `agent-sdk.md`, are exactly this control built into the platform rather than hand-rolled), because an unbounded "keep reviewing until perfect" loop has no natural stopping point and will burn cost chasing diminishing returns.

## 4. Supervisor / router pattern — conceptual, not separately run here

Not executed as a distinct live demo in this workspace, because patterns #1–#3 already used the one orchestration decision that matters in practice: **which pattern fits the task at hand**, decided by a supervisor (in this case, the main session acting as one) before delegating:

| Task shape | Pattern chosen | Why |
|---|---|---|
| One feature, needs correctness sign-off | Sequential build → review | The reviewer needs the finished implementation to review; there's nothing to parallelize |
| One piece of code, several independent quality angles | Parallel fan-out | The angles don't depend on each other |
| A finding needs fixing before calling the work done | Iterative loop (bounded) | Re-verification after a fix is itself a small review, not a new task |

A production supervisor would encode this same decision as an actual routing function (task metadata → pattern selection) rather than a human/main-session judgment call each time — the value of formalizing it grows with the number of distinct task shapes a system needs to handle, and wasn't worth building as its own component for three patterns exercised directly in one session.
