package com.example.sample.ai;

import com.example.sample.Item;
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
 * to exercise the same control flow AnthropicLlmClient uses, including multi-step
 * composition (e.g. "delete the item named Widget" needs search_items to resolve the
 * name before delete_item can act). Default provider (app.ai.provider unset or "mock"),
 * so the app is safe to run out of the box with zero external dependencies.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Pattern ID_PATTERN = Pattern.compile("\\b(?:item\\s*#?|id\\s*[:=]?\\s*)(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_INTENT = Pattern.compile("\\b(delete|remove)\\b", Pattern.CASE_INSENSITIVE);

    // Trigger words that introduce a search term. Deliberately found via the LAST match, not
    // the first: "delete the item named Widget" has two triggers ("delete", "named") and the
    // later one sits right before the actual name, needing no further filler-stripping - the
    // earlier version of this heuristic anchored on the first trigger and only stripped one
    // leading filler word, so "item named Widget" leaked through as the search term unstripped.
    private static final Pattern TRIGGER = Pattern.compile(
            "\\b(?:find|search|look\\s*for|containing|named|called|delete|remove)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_FILLER = Pattern.compile("^(?:(?:the|item|items|a|an|for)\\s+)+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_FILLER = Pattern.compile("\\s*\\b(?:item|items)\\b\\s*[.?!]*$", Pattern.CASE_INSENSITIVE);

    /** Best-effort extraction of the thing being searched for, from free text. Not real NLU - a heuristic standing in for one. */
    private java.util.Optional<String> extractSearchTerm(String query) {
        Matcher m = TRIGGER.matcher(query);
        int lastEnd = -1;
        while (m.find()) {
            lastEnd = m.end();
        }
        if (lastEnd < 0) {
            return java.util.Optional.empty();
        }
        String remainder = query.substring(lastEnd).trim();
        remainder = LEADING_FILLER.matcher(remainder).replaceAll("");
        remainder = TRAILING_FILLER.matcher(remainder).replaceAll("");
        remainder = remainder.replaceAll("^['\"]+|['\"]+$", "").trim();
        return remainder.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(remainder);
    }

    @Override
    public Decision decideNextStep(String userQuery, List<ToolSpec> availableTools, List<ToolExecutionStep> history) {
        String query = userQuery == null ? "" : userQuery.trim();
        boolean deleteIntent = DELETE_INTENT.matcher(query).find();

        // A confirmation-required result (or any delete_item result) ends the turn - there's
        // nothing more to decide until the human confirms out-of-band via /api/ai/confirm.
        if (!history.isEmpty() && "delete_item".equals(history.get(history.size() - 1).toolName())) {
            return new Decision.Finish();
        }

        Matcher idMatcher = ID_PATTERN.matcher(query);
        if (deleteIntent && idMatcher.find() && hasTool(availableTools, "delete_item")) {
            return new Decision.CallTool(new ToolCall("delete_item", Map.of("id", Long.parseLong(idMatcher.group(1)))));
        }

        if (deleteIntent && hasTool(availableTools, "search_items")) {
            // Composition: resolve the name to an id via search_items first, then decide
            // delete_item (or Finish, if the search came back empty/ambiguous) on the next call.
            if (history.isEmpty()) {
                var term = extractSearchTerm(query);
                if (term.isPresent()) {
                    return new Decision.CallTool(new ToolCall("search_items", Map.of("name", term.get())));
                }
            } else {
                Object lastResult = history.get(history.size() - 1).result();
                if (lastResult instanceof List<?> found && found.size() == 1 && found.get(0) instanceof Item item
                        && hasTool(availableTools, "delete_item")) {
                    return new Decision.CallTool(new ToolCall("delete_item", Map.of("id", item.getId())));
                }
                return new Decision.Finish(); // ambiguous (0 or >1 matches) - let summarize explain
            }
        }

        Matcher getIdMatcher = ID_PATTERN.matcher(query);
        if (getIdMatcher.find() && hasTool(availableTools, "get_item")) {
            return new Decision.CallTool(new ToolCall("get_item", Map.of("id", Long.parseLong(getIdMatcher.group(1)))));
        }

        var term = extractSearchTerm(query);
        if (term.isPresent() && hasTool(availableTools, "search_items")) {
            return new Decision.CallTool(new ToolCall("search_items", Map.of("name", term.get())));
        }

        if (history.isEmpty() && hasTool(availableTools, "list_items")) {
            return new Decision.CallTool(new ToolCall("list_items", Map.of()));
        }

        return new Decision.Finish();
    }

    @Override
    public String summarize(String userQuery, List<ToolExecutionStep> history) {
        if (history.isEmpty()) {
            return "I couldn't find a relevant action for that query.";
        }
        ToolExecutionStep last = history.get(history.size() - 1);

        return switch (last.toolName()) {
            case "get_item" -> last.result() == null
                    ? "I couldn't find an item with that ID."
                    : "Here's the item you asked about: " + last.result();
            case "search_items" -> {
                if (last.result() instanceof List<?> list) {
                    if (DELETE_INTENT.matcher(userQuery).find()) {
                        yield list.isEmpty()
                                ? "No item matched that name, so there's nothing to delete."
                                : "Found " + list.size() + " items matching that name — too ambiguous to delete safely: " + list;
                    }
                    yield list.isEmpty()
                            ? "No items matched your search."
                            : "Found " + list.size() + " matching item(s): " + list;
                }
                yield "Search result: " + last.result();
            }
            case "list_items" -> "Here are all the items: " + last.result();
            case "delete_item" -> "Deletion requires confirmation: " + last.result();
            default -> String.valueOf(last.result());
        };
    }

    private boolean hasTool(List<ToolSpec> tools, String name) {
        return tools.stream().anyMatch(t -> t.toolName().equals(name));
    }
}
