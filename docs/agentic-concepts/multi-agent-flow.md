# Multi-agent flow

This is a record of a real orchestration run against this repo, not a hypothetical — build agent implements, review agent critiques, gaps get closed. The feature: a case-insensitive item search endpoint (`GET /api/items/search?name={query}`).

## The flow, as it actually happened

**1. Build.** Spawned an agent (standing in for `java-spring-dev` — see `subagents.md` for why it ran as `general-purpose` instead) with a scoped brief: add the endpoint, the repository method, tests, run the suite. It reported back:

> All 8 tests pass, build succeeds... No service layer, DTOs, or unrelated refactors were introduced — change is scoped exactly to the requested endpoint/repository method/tests.

It added `findByNameContainingIgnoreCase` to `ItemRepository`, a `GET /search` handler to `ItemController` (returning an empty list for null/blank queries rather than hitting the database), and two tests.

**2. Review.** Spawned a second, independent agent (standing in for `spring-code-reviewer`) with no access to the build agent's reasoning — only the diff, the repo, and instructions to run the tests itself. It confirmed the suite was green, validated the generated JPQL directly, and found a real, specific gap:

> No test exercises the null/blank-query branch itself... This is the one edge case the reviewer checklist calls out for new endpoints and it's currently untested.
>
> **Verdict: Approve**, with a suggestion (not a blocker) to add one more test for the blank/missing `name` param case before merging.

This wasn't a fabricated finding for demo purposes — the two tests the build agent wrote both exercised the repository call (one matching, one not), but neither exercised the early-return-on-blank branch, which was genuinely new logic. A single agent reviewing its own work is prone to missing exactly this kind of gap between "I tested the feature" and "I tested every branch I wrote."

**3. Close the loop.** The missing test (`searchItemsWithBlankOrMissingNameReturnsEmptyList`) was added directly, and the full suite re-run: 9/9 passing.

## Why two agents instead of one doing it all

The value isn't speed — running two agents plus a fix pass took longer than one agent doing everything would have. The value is **independence**: the reviewer had no stake in the implementation being correct and no memory of the build agent's assumptions, so it was checking against the repo's actual conventions and the actual generated SQL, not rubber-stamping its own prior reasoning. This is the same reason human code review works even when the reviewer is less experienced than the author — a second, differently-motivated pass catches a different class of mistake than more effort from the first pass would.

## Where this pattern doesn't pay for itself

For a one-line fix or a question with an obvious answer, spawning two agents is pure overhead — context cost and wall-clock time with no corresponding gain, since there's nothing substantial enough for independent review to catch. It earned its keep here specifically because the task had a real edge case (null/blank handling) that's easy to under-test on a first pass. Reach for this pattern when the change has more than one plausible failure mode, not as a default for every task.
