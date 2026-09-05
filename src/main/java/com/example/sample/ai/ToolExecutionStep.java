package com.example.sample.ai;

import java.util.Map;

/**
 * One completed step in a multi-step tool-calling turn: what was called, with what
 * arguments, and what it returned. A growing list of these is how composition works -
 * "delete the item named Widget" resolves to search_items (find the id) then
 * delete_item (act on it), each step informed by what came before.
 *
 * This mirrors how Anthropic's real API represents multi-turn tool use: each step
 * becomes an assistant tool_use block followed by a user tool_result block in the
 * message history sent on the *next* request - AnthropicLlmClient builds exactly
 * that from a list of these.
 */
public record ToolExecutionStep(String toolName, Map<String, Object> arguments, Object result) {
}
