package com.example.sample.ai;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * An EVAL, not just a unit test — the distinction matters. A unit test asserts one
 * behavior is correct; an eval suite asserts a *golden dataset* of representative
 * inputs all resolve to the expected decision, so a change to the decision logic
 * (here: MockLlmClient's heuristics; in a real system: a prompt or model version)
 * can be checked against many cases at once and regressions in specific query
 * shapes are caught individually, not just "something broke."
 *
 * This is the same pattern `claude plugin eval` applies to Claude Code skills —
 * a table of (input, expected outcome) pairs run against the thing being evaluated.
 * Swap MockLlmClient for a real model and this exact table becomes a regression
 * suite for prompt changes: if a new prompt makes "find X" stop resolving to
 * search_items, this table catches it before a user does.
 */
class MockLlmClientEvalTest {

    private final MockLlmClient client = new MockLlmClient();
    private final List<ToolSpec> tools = new ToolRegistry().allTools();

    private Decision.CallTool decide(String query) {
        return assertInstanceOf(Decision.CallTool.class, client.decideNextStep(query, tools, List.of()));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "find widget,               search_items",
            "search for the blue one,   search_items",
            "look for sprocket,         search_items",
            "items named gadget,        search_items",
            "show me item 42,           get_item",
            "get item #7,               get_item",
            "id: 3,                     get_item",
            "show me everything,        list_items",
            "what do you have,          list_items",
            "list all items,            list_items",
    })
    void resolvesToExpectedTool(String query, String expectedTool) {
        assertEquals(expectedTool, decide(query).toolCall().toolName(), "query: " + query);
    }

    @ParameterizedTest(name = "\"{0}\" extracts id {1}")
    @CsvSource({
            "show me item 42, 42",
            "get item #7,     7",
            "id: 3,           3",
    })
    void extractsCorrectIdArgument(String query, long expectedId) {
        assertEquals(expectedId, decide(query).toolCall().arguments().get("id"));
    }

    @ParameterizedTest(name = "\"{0}\" (no history) -> search_items, to resolve the name first")
    @CsvSource({
            "delete the item named Widget",
            "remove the broken sprocket",
    })
    void deleteByNameFirstResolvesViaSearch(String query) {
        // Composition: can't call delete_item with a name, only an id - the first step
        // must be search_items regardless of delete intent being present in the query.
        assertEquals("search_items", decide(query).toolCall().toolName());
    }

    @ParameterizedTest(name = "\"delete item {0}\" (no history, explicit id) -> delete_item directly")
    @CsvSource({"42", "7"})
    void deleteByExplicitIdSkipsSearch(String id) {
        assertEquals("delete_item", decide("delete item " + id).toolCall().toolName());
    }
}
