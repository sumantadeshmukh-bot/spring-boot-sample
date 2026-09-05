package com.example.sample.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** MockLlmClientEvalTest covers decideTool(); this covers the summarize() branches it doesn't. */
class MockLlmClientSummarizeTest {

    private final MockLlmClient client = new MockLlmClient();

    @Test
    void summarizeGetItemFound() {
        String result = client.summarize("show me item 1", "get_item", "Item{id=1, name='Widget'}");
        assertTrue(result.contains("Widget"));
    }

    @Test
    void summarizeGetItemNotFound() {
        String result = client.summarize("show me item 999", "get_item", null);
        assertTrue(result.toLowerCase().contains("couldn't find"));
    }

    @Test
    void summarizeSearchItemsWithResults() {
        String result = client.summarize("find widget", "search_items", List.of("Widget"));
        assertTrue(result.contains("Found 1"));
    }

    @Test
    void summarizeSearchItemsEmpty() {
        String result = client.summarize("find nothing", "search_items", List.of());
        assertTrue(result.toLowerCase().contains("no items matched"));
    }

    @Test
    void summarizeListItems() {
        String result = client.summarize("show everything", "list_items", List.of("A", "B"));
        assertTrue(result.contains("[A, B]"));
    }
}
