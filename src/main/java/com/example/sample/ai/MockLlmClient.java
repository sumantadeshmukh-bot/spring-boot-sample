package com.example.sample.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deterministic stand-in for a real model, so the tool-calling loop (decide -> act ->
 * summarize) can be demonstrated, tested, and run in CI without an API key or real cost.
 *
 * This is NOT how a real model decides things — it's regex/keyword heuristics, good enough
 * to exercise the same control flow AnthropicLlmClient uses. Default provider (app.ai.provider
 * unset or "mock"), so the app is safe to run out of the box with zero external dependencies.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Pattern ID_PATTERN = Pattern.compile("\\b(?:item\\s*#?|id\\s*[:=]?\\s*)(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_TERM_PATTERN = Pattern.compile(
            "(?:find|search|look\\s*for|containing|named|called)\\s+(?:.*?\\b(?:for|item[s]?)\\b\\s+)?['\"]?([a-zA-Z0-9][\\w\\- ]{1,40}?)['\"]?(?:\\s*(?:item|items)?)?[.?!]?$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public ToolCall decideTool(String userQuery, List<ToolSpec> availableTools) {
        String query = userQuery == null ? "" : userQuery.trim();

        Matcher idMatcher = ID_PATTERN.matcher(query);
        if (idMatcher.find() && hasTool(availableTools, "get_item")) {
            return new ToolCall("get_item", Map.of("id", Long.parseLong(idMatcher.group(1))));
        }

        Matcher searchMatcher = SEARCH_TERM_PATTERN.matcher(query);
        if (searchMatcher.find() && hasTool(availableTools, "search_items")) {
            String term = searchMatcher.group(1).trim();
            return new ToolCall("search_items", Map.of("name", term));
        }

        if (hasTool(availableTools, "list_items")) {
            return new ToolCall("list_items", Map.of());
        }

        throw new IllegalStateException("No tool available to handle query: " + userQuery);
    }

    @Override
    public String summarize(String userQuery, String toolName, Object toolResult) {
        return switch (toolName) {
            case "get_item" -> toolResult == null
                    ? "I couldn't find an item with that ID."
                    : "Here's the item you asked about: " + toolResult;
            case "search_items" -> {
                if (toolResult instanceof List<?> list) {
                    yield list.isEmpty()
                            ? "No items matched your search."
                            : "Found " + list.size() + " matching item(s): " + list;
                }
                yield "Search result: " + toolResult;
            }
            case "list_items" -> "Here are all the items: " + toolResult;
            default -> String.valueOf(toolResult);
        };
    }

    private boolean hasTool(List<ToolSpec> tools, String name) {
        return tools.stream().anyMatch(t -> t.toolName().equals(name));
    }
}
