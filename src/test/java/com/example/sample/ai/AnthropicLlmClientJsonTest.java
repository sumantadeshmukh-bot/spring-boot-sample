package com.example.sample.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers AnthropicLlmClient's JSON-building/parsing logic directly (no real or mocked HTTP
 * round-trip) - a prior review noted this class had zero coverage. A full end-to-end test
 * against a stubbed HTTP server is a reasonable next addition (tracked, not done here) if
 * this client sees real production use; this at least verifies the request/response shape
 * logic doesn't silently break, which is where an API contract mismatch would first show up.
 */
class AnthropicLlmClientJsonTest {

    private AnthropicLlmClient client() {
        return new AnthropicLlmClient(
                RestClient.builder(), new ObjectMapper(),
                "test-api-key", "claude-sonnet-4-5-20250929", 1024, "https://api.anthropic.com");
    }

    @Test
    void constructorRejectsBlankApiKey() {
        assertThrows(IllegalStateException.class, () -> new AnthropicLlmClient(
                RestClient.builder(), new ObjectMapper(), "", "model", 1024, "https://api.anthropic.com"));
    }

    @Test
    void toToolsJsonProducesAnthropicShapedSchema() {
        AnthropicLlmClient client = client();
        List<ToolSpec> tools = new ToolRegistry().allTools();

        ArrayNode json = client.toToolsJson(tools);

        assertEquals(3, json.size());
        JsonNode searchTool = json.get(0);
        assertEquals("search_items", searchTool.path("name").asString());
        assertEquals("object", searchTool.path("input_schema").path("type").asString());
        assertTrue(searchTool.path("input_schema").path("properties").has("name"));
        assertEquals("name", searchTool.path("input_schema").path("required").get(0).asString());
    }

    @Test
    void toToolsJsonHandlesToolWithNoParameters() {
        AnthropicLlmClient client = client();
        ToolSpec noParamTool = new ToolSpec("list_items", "lists everything", Map.of());

        ArrayNode json = client.toToolsJson(List.of(noParamTool));

        JsonNode required = json.get(0).path("input_schema").path("required");
        assertTrue(required.isArray());
        assertEquals(0, required.size());
    }

    @Test
    void asJavaValueConvertsNumberBooleanAndString() {
        AnthropicLlmClient client = client();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("n", 42);
        node.put("b", true);
        node.put("s", "hello");

        assertEquals(42L, client.asJavaValue(node.get("n")));
        assertEquals(true, client.asJavaValue(node.get("b")));
        assertEquals("hello", client.asJavaValue(node.get("s")));
    }
}
