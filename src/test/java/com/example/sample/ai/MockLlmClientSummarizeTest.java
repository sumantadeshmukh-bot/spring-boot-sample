package com.example.sample.ai;

import com.example.sample.Item;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MockLlmClientEvalTest covers decideNextStep(); this covers summarize() and multi-step composition. */
class MockLlmClientSummarizeTest {

    private final MockLlmClient client = new MockLlmClient();
    private final List<ToolSpec> tools = new ToolRegistry().allTools();

    private ToolExecutionStep step(String tool, Object result) {
        return new ToolExecutionStep(tool, Map.of(), result);
    }

    @Test
    void summarizeGetItemFound() {
        String result = client.summarize("show me item 1", List.of(step("get_item", "Item{id=1, name='Widget'}")));
        assertTrue(result.contains("Widget"));
    }

    @Test
    void summarizeGetItemNotFound() {
        String result = client.summarize("show me item 999", List.of(step("get_item", null)));
        assertTrue(result.toLowerCase().contains("couldn't find"));
    }

    @Test
    void summarizeSearchItemsWithResults() {
        String result = client.summarize("find widget", List.of(step("search_items", List.of("Widget"))));
        assertTrue(result.contains("Found 1"));
    }

    @Test
    void summarizeSearchItemsEmpty() {
        String result = client.summarize("find nothing", List.of(step("search_items", List.of())));
        assertTrue(result.toLowerCase().contains("no items matched"));
    }

    @Test
    void summarizeListItems() {
        String result = client.summarize("show everything", List.of(step("list_items", List.of("A", "B"))));
        assertTrue(result.contains("[A, B]"));
    }

    @Test
    void summarizeNoHistory() {
        String result = client.summarize("gibberish query", List.of());
        assertTrue(result.toLowerCase().contains("couldn't find a relevant action"));
    }

    // --- Composition: delete-by-name across two steps ---

    @Test
    void deleteByNameSecondStepCallsDeleteWithResolvedId() {
        Item widget = new Item("Widget", "desc");
        widget.setId(5L);
        List<ToolExecutionStep> history = List.of(step("search_items", List.of(widget)));

        Decision decision = client.decideNextStep("delete the item named Widget", tools, history);
        Decision.CallTool callTool = assertInstanceOf(Decision.CallTool.class, decision);
        org.junit.jupiter.api.Assertions.assertEquals("delete_item", callTool.toolCall().toolName());
        org.junit.jupiter.api.Assertions.assertEquals(5L, callTool.toolCall().arguments().get("id"));
    }

    @Test
    void deleteByNameAmbiguousSearchResultFinishesWithoutDeleting() {
        Item a = new Item("Widget", "a");
        Item b = new Item("Widget2", "b");
        List<ToolExecutionStep> history = List.of(step("search_items", List.of(a, b)));

        Decision decision = client.decideNextStep("delete the item named Widget", tools, history);
        assertInstanceOf(Decision.Finish.class, decision);

        String summary = client.summarize("delete the item named Widget", history);
        assertTrue(summary.toLowerCase().contains("ambiguous"));
    }

    @Test
    void afterDeleteItemStepDecisionIsAlwaysFinish() {
        List<ToolExecutionStep> history = List.of(
                step("delete_item", Map.of("status", "confirmation_required", "confirmToken", "abc")));

        Decision decision = client.decideNextStep("delete item 5", tools, history);
        assertInstanceOf(Decision.Finish.class, decision);

        String summary = client.summarize("delete item 5", history);
        assertTrue(summary.toLowerCase().contains("confirmation"));
    }
}
