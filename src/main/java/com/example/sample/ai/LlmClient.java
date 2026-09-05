package com.example.sample.ai;

import java.util.List;

/**
 * The seam between the app's orchestration logic and whatever actually reasons
 * about the user's query — a real model (AnthropicLlmClient) or a deterministic
 * stand-in (MockLlmClient). Swappable via the app.ai.provider property so the
 * rest of the app never knows which one it's talking to.
 */
public interface LlmClient {

    /** Given a free-text query and the tools available, decide which one to call and with what arguments. */
    ToolCall decideTool(String userQuery, List<ToolSpec> availableTools);

    /** Turn a raw tool result back into a natural-language answer to the original query. */
    String summarize(String userQuery, String toolName, Object toolResult);
}
