package com.example.sample.ai;

import java.time.Instant;
import java.util.Map;

/**
 * A structured record of one full agentic loop: what was asked, what the model decided,
 * what actually executed, and what came back. This is the minimal shape real observability
 * tooling (OpenTelemetry spans, LangSmith traces, etc.) builds on at scale — the point isn't
 * the specific fields, it's that every step of the decide -> act -> observe -> respond loop
 * is captured individually, not just the final answer. Without this, a wrong answer is
 * undebuggable: was the tool choice wrong, the arguments wrong, or the summary wrong?
 */
public record AiTrace(
        Instant timestamp,
        String userQuery,
        String toolChosen,
        Map<String, Object> toolArguments,
        Object toolResult,
        String finalAnswer,
        long durationMillis
) {
}
