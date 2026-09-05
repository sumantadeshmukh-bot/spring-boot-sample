package com.example.sample.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real implementation, talking to Anthropic's Messages API tool-use protocol directly
 * over HTTP (no Spring AI / SDK dependency, deliberately — see docs/agentic-concepts
 * for why). Inactive unless app.ai.provider=anthropic is explicitly set, so the app
 * never makes a real network call or incurs cost by default.
 *
 * NOTE: model name and exact request/response shape are written from best available
 * knowledge, not verified against a live API call this session (no key was provided).
 * Check the model id in application.properties (app.ai.anthropic.model) against
 * https://docs.anthropic.com before real use — model ids change over time.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;

    public AnthropicLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.ai.anthropic.api-key:${ANTHROPIC_API_KEY:}}") String apiKey,
            @Value("${app.ai.anthropic.model:claude-sonnet-4-5-20250929}") String model,
            @Value("${app.ai.anthropic.max-tokens:1024}") int maxTokens,
            @Value("${app.ai.anthropic.base-url:https://api.anthropic.com}") String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "app.ai.provider=anthropic requires an API key via ANTHROPIC_API_KEY env var "
                            + "(or app.ai.anthropic.api-key) — never hardcode it in application.properties.");
        }
        this.objectMapper = objectMapper;
        this.model = model;
        this.maxTokens = maxTokens;

        // Without an explicit timeout, a slow/unresponsive upstream (any external API call,
        // not just Anthropic's) can hang the calling thread indefinitely - an availability
        // risk, not just a performance nicety.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(30_000);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public ToolCall decideTool(String userQuery, List<ToolSpec> availableTools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.set("tools", toToolsJson(availableTools));
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", userQuery);

        JsonNode response = post(body);
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asString())) {
                String toolName = block.path("name").asString();
                Map<String, Object> args = new LinkedHashMap<>();
                block.path("input").properties().forEach(e -> args.put(e.getKey(), asJavaValue(e.getValue())));
                return new ToolCall(toolName, args);
            }
        }
        throw new IllegalStateException("Model did not choose a tool for query: " + userQuery);
    }

    @Override
    public String summarize(String userQuery, String toolName, Object toolResult) {
        String prompt = "The user asked: \"" + userQuery + "\". Calling " + toolName
                + " returned: " + toolResult + ". Give a short, natural-language answer.";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);

        JsonNode response = post(body);
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asString())) {
                return block.path("text").asString();
            }
        }
        return String.valueOf(toolResult);
    }

    private JsonNode post(ObjectNode body) {
        return restClient.post()
                .uri("/v1/messages")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    // Package-private (not private) specifically so MockAnthropicJsonTest can exercise the
    // JSON shape directly without needing a real or mocked HTTP round-trip for every case.
    ArrayNode toToolsJson(List<ToolSpec> tools) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ToolSpec tool : tools) {
            ObjectNode toolNode = array.addObject();
            toolNode.put("name", tool.toolName());
            toolNode.put("description", tool.description());
            ObjectNode schema = toolNode.putObject("input_schema");
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            List<String> required = new ArrayList<>();
            tool.parameterDescriptions().forEach((paramName, description) -> {
                properties.putObject(paramName).put("type", "string").put("description", description);
                required.add(paramName);
            });
            ArrayNode requiredArray = schema.putArray("required");
            required.forEach(requiredArray::add);
        }
        return array;
    }

    Object asJavaValue(JsonNode node) {
        if (node.isNumber()) return node.asLong();
        if (node.isBoolean()) return node.asBoolean();
        return node.asString();
    }
}
