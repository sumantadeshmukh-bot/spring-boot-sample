package com.example.sample.ai;

/**
 * What the model decided to do next, given the query and whatever's already been
 * executed this turn: call one more tool, or stop because it has enough to answer.
 */
public sealed interface Decision {
    record CallTool(ToolCall toolCall) implements Decision {
    }

    record Finish() implements Decision {
    }
}
