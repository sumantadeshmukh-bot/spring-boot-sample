package com.example.sample.ai;

import java.util.Map;

/**
 * Describes one tool the LLM is allowed to call, in the same shape Anthropic's
 * Messages API tool-use expects: a name, a description the model uses to decide
 * *when* to call it, and a JSON-schema-ish input description for its arguments.
 */
public record ToolSpec(String toolName, String description, Map<String, String> parameterDescriptions) {
}
