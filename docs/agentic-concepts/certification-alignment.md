# Certification alignment

Anthropic's own certification pages (`anthropic-partners.skilljar.com`) name four technology domains (Claude Code, Claude Agent SDK, Claude API, MCP) but publish no granular syllabus. A round of web research found something more useful: multiple independent third-party sources — a community study guide on GitHub ([daronyondem/claude-architect-exam-guide](https://github.com/daronyondem/claude-architect-exam-guide)), a dedicated exam-prep site, and several blog write-ups — converge on the same finer-grained structure: **5 weighted domains**, not 4 flat ones. Two of these sources were fetched and cross-checked directly against each other, not taken on a single source's word.

**Caveat, stated plainly**: these are unofficial, third-party sources, not Anthropic's own published rubric. Treat the domain weights and sub-topics below as a strong, corroborated *signal* about what the exam actually tests — not a verified official syllabus. Where community sources disagree slightly, note that too rather than picking one silently.

## The 5 domains, weighted

| Domain | Weight | Our coverage | Real gaps |
|---|---|---|---|
| **Agentic Architecture & Orchestration** | 27% | Strong — `AiOrchestrationService`'s loop, guardrails, 3 orchestration patterns actually run (`orchestration-patterns.md`), Agent SDK live-verified | No dedicated **prompt-chaining** example; the **routing/supervisor** pattern is documented conceptually (`orchestration-patterns.md` §4) but never implemented as an actual routing function |
| **Claude Code Configuration & Workflows** | 20% | Strong — settings, hooks, permissions, the corrected precedence hierarchy, CI/CD, all with real evidence | **Plan mode** never demonstrated or discussed anywhere in this workspace |
| **Prompt Engineering & Structured Output** | 20% | **Weakest area.** The subagent `.md` files (`java-spring-dev.md`, `spring-code-reviewer.md`) *are* system prompts, but there's no deliberate documentation of prompt-engineering technique — structure, few-shot examples, dilution prevention, response prefill | A 20%-weighted domain covered only incidentally, never as a deliberate practice with its own examples/doc |
| **Tool Design & MCP Integration** | 18% | Moderate — `ToolSpec`/`ToolRegistry` cover schema design; one real MCP server connection proven | MCP's **resources** and **prompts** primitives (distinct from tools) are completely untouched; no **confirmation-flow** pattern (human sign-off before a destructive tool call); no tool-composition example; error handling doesn't distinguish transient vs. permanent failures |
| **Context Management & Reliability** | 15% | Moderate — a real eval harness exists (`MockLlmClientEvalTest`) | **Prompt caching** (a specific, checkable Anthropic API feature — `cache_control` blocks) never used or discussed; the **Message Batches API** untouched entirely |

**Rough weighted gut-check: ~55–60%.** Solid on the two Claude Code–centric domains; genuinely thin on Prompt Engineering specifically (the tied-second-highest-weighted domain) and on half of Tool Design/MCP. This number is an informal estimate from the table above, not a certified score — it exists to prioritize what to build next, not to predict an exam result.

## What to do with this workspace toward exam prep

1. **Read `precedence-and-conflicts.md` first** — most likely to matter for scenario-style questions ("which setting wins when X and Y conflict"), and it was corrected once already after finding better primary-source evidence than what was initially written from memory. That correction habit — verify against source when stakes are real — matters more than any single fact in this workspace.
2. **Prompt Engineering is the highest-value gap to close** given its weight (20%) and current thinness — closing it means writing deliberate prompt-engineering documentation/examples, not just pointing at the subagent files that happen to contain prompts.
3. **MCP resources/prompts and prompt caching are concrete, checkable gaps** — each is a specific named feature, not a vague area, which makes them cheap to verify against the exam's likely scenario-based question style ("here's a caching scenario, what's wrong with it").
