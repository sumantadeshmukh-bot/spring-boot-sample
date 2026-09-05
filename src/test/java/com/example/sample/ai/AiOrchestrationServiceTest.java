package com.example.sample.ai;

import com.example.sample.Item;
import com.example.sample.ItemRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the orchestration loop's own control flow, isolated from both real tool
 * execution details and from MockLlmClient's specific heuristics (a fake LlmClient is used
 * instead, so these tests exercise AiOrchestrationService's logic even in scenarios
 * MockLlmClient itself would never produce - specifically, a model choosing a tool that
 * isn't on the whitelist. A prior review flagged this exact path as untested.
 */
class AiOrchestrationServiceTest {

    private AiOrchestrationService serviceWith(LlmClient llmClient, ItemRepository itemRepository) {
        return new AiOrchestrationService(
                llmClient, new ToolRegistry(), itemRepository, new PromptInjectionGuard(),
                new RateLimiter(), new ConfirmationStore(), new ObjectMapper());
    }

    /** A scripted LlmClient returning a fixed sequence of Decisions, one per decideNextStep call. */
    private static LlmClient scripted(Function<List<ToolExecutionStep>, String> summarizer, Decision... decisions) {
        Deque<Decision> queue = new ArrayDeque<>(List.of(decisions));
        return new LlmClient() {
            @Override
            public Decision decideNextStep(String userQuery, List<ToolSpec> availableTools, List<ToolExecutionStep> history) {
                return queue.isEmpty() ? new Decision.Finish() : queue.poll();
            }

            @Override
            public String summarize(String userQuery, List<ToolExecutionStep> history) {
                return summarizer.apply(history);
            }
        };
    }

    @Test
    void rejectsToolNotOnTheWhitelist() {
        LlmClient rogueLlm = scripted(h -> "unused",
                new Decision.CallTool(new ToolCall("delete_everything", Map.of())));

        AiOrchestrationService service = serviceWith(rogueLlm, mock(ItemRepository.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.ask("do something", "test-client"));
        assertTrue(ex.getMessage().contains("delete_everything"));
    }

    @Test
    void getItemForNonexistentIdSummarizesAsNotFound() {
        LlmClient fixedDecision = scripted(
                h -> h.get(0).result() == null ? "not found" : "found: " + h.get(0).result(),
                new Decision.CallTool(new ToolCall("get_item", Map.of("id", 999L))));

        ItemRepository repo = mock(ItemRepository.class);
        when(repo.findById(999L)).thenReturn(Optional.empty());

        AiOrchestrationService service = serviceWith(fixedDecision, repo);
        AiTrace trace = service.ask("show me item 999", "test-client");

        assertNull(trace.toolResult());
        assertEquals("not found", trace.finalAnswer());
    }

    @Test
    void getItemWithNonNumericIdIsRejectedCleanly() {
        LlmClient badArgs = scripted(h -> "unused",
                new Decision.CallTool(new ToolCall("get_item", Map.of("id", "not-a-number"))));

        AiOrchestrationService service = serviceWith(badArgs, mock(ItemRepository.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.ask("show me item xyz", "test-client"));
        // Deliberately does NOT assert on NumberFormatException's raw message - that's the
        // whole point of the fix (internal parse detail isn't leaked to the caller).
        assertEquals("Model provided a non-numeric item id.", ex.getMessage());
    }

    @Test
    void searchDelegatesToRepositoryAndReturnsResults() {
        LlmClient searchDecision = scripted(h -> "summary",
                new Decision.CallTool(new ToolCall("search_items", Map.of("name", "widget"))));

        Item widget = new Item("Widget", "desc");
        ItemRepository repo = mock(ItemRepository.class);
        when(repo.findByNameContainingIgnoreCase("widget")).thenReturn(List.of(widget));

        AiOrchestrationService service = serviceWith(searchDecision, repo);
        AiTrace trace = service.ask("find widget", "test-client");

        assertEquals(List.of(widget), trace.toolResult());
        assertEquals("search_items", trace.toolChosen());
    }

    @Test
    void multiStepCompositionSearchThenDeleteQueuesConfirmation() {
        // Scripted to mirror real composition: step 1 resolves the name via search_items,
        // step 2 (informed by step 1's result, which the real orchestration loop passes
        // back through decideNextStep's history parameter) calls delete_item.
        Item widget = new Item("Widget", "desc");
        widget.setId(5L);
        ItemRepository repo = mock(ItemRepository.class);
        when(repo.findByNameContainingIgnoreCase("Widget")).thenReturn(List.of(widget));
        when(repo.existsById(5L)).thenReturn(true);

        LlmClient composingClient = scripted(h -> "queued for confirmation",
                new Decision.CallTool(new ToolCall("search_items", Map.of("name", "Widget"))),
                new Decision.CallTool(new ToolCall("delete_item", Map.of("id", 5L))));

        AiOrchestrationService service = serviceWith(composingClient, repo);
        AiTrace trace = service.ask("delete the item named Widget", "test-client");

        assertEquals("delete_item", trace.toolChosen());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) trace.toolResult();
        assertEquals("confirmation_required", result.get("status"));
        assertNotNull(result.get("confirmToken"));

        // The item must NOT actually be gone yet - only queued.
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).deleteById(5L);
    }

    @Test
    void confirmingExecutesTheQueuedDeletion() {
        ItemRepository repo = mock(ItemRepository.class);
        when(repo.existsById(5L)).thenReturn(true);

        LlmClient deleteById = scripted(h -> "queued",
                new Decision.CallTool(new ToolCall("delete_item", Map.of("id", 5L))));
        AiOrchestrationService service = serviceWith(deleteById, repo);

        AiTrace trace = service.ask("delete item 5", "test-client");
        @SuppressWarnings("unchecked")
        String token = (String) ((Map<String, Object>) trace.toolResult()).get("confirmToken");

        org.mockito.Mockito.verify(repo, org.mockito.Mockito.never()).deleteById(5L);

        assertTrue(service.confirm(token));
        org.mockito.Mockito.verify(repo).deleteById(5L);

        // A token is single-use - confirming again must fail rather than deleting twice.
        assertFalse(service.confirm(token));
    }

    @Test
    void confirmWithUnknownTokenReturnsFalse() {
        AiOrchestrationService service = serviceWith(scripted(h -> "unused"), mock(ItemRepository.class));
        assertFalse(service.confirm("nonexistent-token"));
    }
}
