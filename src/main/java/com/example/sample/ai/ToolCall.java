package com.example.sample.ai;

import java.util.Map;

/** The LLM's decision: which tool to call, and with what arguments. */
public record ToolCall(String toolName, Map<String, Object> arguments) {
}
