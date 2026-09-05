package com.example.sample.ai;

import java.util.List;

/**
 * The seam between the app's orchestration logic and whatever actually reasons
 * about the user's query — a real model (AnthropicLlmClient) or a deterministic
 * stand-in (MockLlmClient). Swappable via the app.ai.provider property so the
 * rest of the app never knows which one it's talking to.
 *
 * decideNextStep takes the history of steps already completed this turn, not just
 * the original query — this is what makes tool composition possible (e.g. "delete
 * the item named Widget" needs a search_items step to resolve the id before a
 * delete_item step can act on it). A single-step call is just history=List.of().
 */
public interface LlmClient {

    /** Given the query and what's already been done this turn, decide the next action. */
    Decision decideNextStep(String userQuery, List<ToolSpec> availableTools, List<ToolExecutionStep> history);

    /** Turn the full sequence of completed steps back into a natural-language answer. */
    String summarize(String userQuery, List<ToolExecutionStep> history);
}
