package com.example.sample.ai;

import com.example.sample.Item;
import com.example.sample.ItemRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                llmClient, new ToolRegistry(), itemRepository,
                new PromptInjectionGuard(), new RateLimiter(), new ObjectMapper());
    }

    @Test
    void rejectsToolNotOnTheWhitelist() {
        LlmClient rogueLlm = new LlmClient() {
            @Override
            public ToolCall decideTool(String userQuery, List<ToolSpec> availableTools) {
                return new ToolCall("delete_everything", Map.of());
            }

            @Override
            public String summarize(String userQuery, String toolName, Object toolResult) {
                return "unused";
            }
        };

        AiOrchestrationService service = serviceWith(rogueLlm, mock(ItemRepository.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.ask("do something", "test-client"));
        assertTrue(ex.getMessage().contains("delete_everything"));
    }

    @Test
    void getItemForNonexistentIdSummarizesAsNotFound() {
        LlmClient fixedDecision = new LlmClient() {
            @Override
            public ToolCall decideTool(String userQuery, List<ToolSpec> availableTools) {
                return new ToolCall("get_item", Map.of("id", 999L));
            }

            @Override
            public String summarize(String userQuery, String toolName, Object toolResult) {
                return toolResult == null ? "not found" : "found: " + toolResult;
            }
        };

        ItemRepository repo = mock(ItemRepository.class);
        when(repo.findById(999L)).thenReturn(Optional.empty());

        AiOrchestrationService service = serviceWith(fixedDecision, repo);
        AiTrace trace = service.ask("show me item 999", "test-client");

        assertNull(trace.toolResult());
        assertEquals("not found", trace.finalAnswer());
    }

    @Test
    void getItemWithNonNumericIdIsRejectedCleanly() {
        LlmClient badArgs = new LlmClient() {
            @Override
            public ToolCall decideTool(String userQuery, List<ToolSpec> availableTools) {
                return new ToolCall("get_item", Map.of("id", "not-a-number"));
            }

            @Override
            public String summarize(String userQuery, String toolName, Object toolResult) {
                return "unused";
            }
        };

        AiOrchestrationService service = serviceWith(badArgs, mock(ItemRepository.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.ask("show me item xyz", "test-client"));
        // Deliberately does NOT assert on NumberFormatException's raw message - that's the
        // whole point of the fix (internal parse detail isn't leaked to the caller).
        assertEquals("Model provided a non-numeric item id.", ex.getMessage());
    }

    @Test
    void searchDelegatesToRepositoryAndReturnsResults() {
        LlmClient searchDecision = new LlmClient() {
            @Override
            public ToolCall decideTool(String userQuery, List<ToolSpec> availableTools) {
                return new ToolCall("search_items", Map.of("name", "widget"));
            }

            @Override
            public String summarize(String userQuery, String toolName, Object toolResult) {
                return "summary";
            }
        };

        Item widget = new Item("Widget", "desc");
        ItemRepository repo = mock(ItemRepository.class);
        when(repo.findByNameContainingIgnoreCase("widget")).thenReturn(List.of(widget));

        AiOrchestrationService service = serviceWith(searchDecision, repo);
        AiTrace trace = service.ask("find widget", "test-client");

        assertEquals(List.of(widget), trace.toolResult());
        assertEquals("search_items", trace.toolChosen());
    }
}
