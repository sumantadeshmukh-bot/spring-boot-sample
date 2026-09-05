package com.example.sample.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
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
 * knowledge, not verified against a live API call this session (tracked as GitHub
 * issue #2). Check the model id in application.properties (app.ai.anthropic.model)
 * against https://docs.anthropic.com before real use — model ids change over time.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic")
public class AnthropicLlmClient implements LlmClient {

    /**
     * Deliberately structured (not a wall of prose), principles-first, with one worked
     * example - the technique matters more than the specific words. A vague or purely
     * conditional ("if X do Y, if Z do W...") system prompt tends to dilute under a long
     * tool list; a short set of principles the model can apply generally holds up better.
     */
    private static final String SYSTEM_PROMPT = """
            You help manage an item inventory by choosing the right tool for the user's request.

            Principles, not a decision tree:
            - Prefer the most specific tool that directly answers the request. Don't call list_items \
            if search_items or get_item would answer more precisely.
            - Destructive actions (delete_item) require the target to be unambiguous. If a search \
            for the item to delete returns zero or multiple matches, stop and say so rather than guessing.
            - Never invent an item id. Only use an id you were given directly or that came back from \
            a prior tool result this turn.

            Example: "delete the broken widget" -> call search_items(name="broken widget") first to \
            resolve which item that refers to, THEN call delete_item with the id it returns - never \
            delete_item directly from a name-only request.""";

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
    public Decision decideNextStep(String userQuery, List<ToolSpec> availableTools, List<ToolExecutionStep> history) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.set("system", systemPromptJson());
        body.set("tools", toToolsJson(availableTools));
        body.set("messages", toMessagesJson(userQuery, history));

        JsonNode response = post(body);
        for (JsonNode block : response.path("content")) {
            if ("tool_use".equals(block.path("type").asString())) {
                String toolName = block.path("name").asString();
                Map<String, Object> args = new LinkedHashMap<>();
                block.path("input").properties().forEach(e -> args.put(e.getKey(), asJavaValue(e.getValue())));
                return new Decision.CallTool(new ToolCall(toolName, args));
            }
        }
        return new Decision.Finish();
    }

    @Override
    public String summarize(String userQuery, List<ToolExecutionStep> history) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.set("system", systemPromptJson());

        ArrayNode messages = toMessagesJson(userQuery, history);
        messages.addObject().put("role", "user")
                .put("content", "Give a short, natural-language answer to the original request based on the above.");
        // Response prefill: seeding the start of the assistant's own reply is a real Anthropic
        // API technique for nudging output format without needing tool-use machinery for it -
        // here it just pins a consistent "Summary: " prefix rather than anything elaborate.
        messages.addObject().put("role", "assistant").put("content", "Summary: ");
        body.set("messages", messages);

        JsonNode response = post(body);
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asString())) {
                return "Summary: " + block.path("text").asString();
            }
        }
        return history.isEmpty() ? "No information was gathered for this query."
                : String.valueOf(history.get(history.size() - 1).result());
    }

    /**
     * Submits a batch of independent prompts via Anthropic's Message Batches API - for
     * bulk, non-interactive work (e.g. summarizing every item overnight) where the
     * latency of one-at-a-time synchronous calls doesn't matter but cost does; batched
     * requests are billed at a discount relative to the same calls made individually.
     * Returns the batch id to poll with {@link #pollBatch}. Not part of the LlmClient
     * interface - this is provider-specific bulk-processing, not a decide/summarize step.
     */
    public String submitBatch(List<String> prompts) {
        ArrayNode requests = objectMapper.createArrayNode();
        for (int i = 0; i < prompts.size(); i++) {
            ObjectNode entry = requests.addObject();
            entry.put("custom_id", "batch-item-" + i);
            ObjectNode params = entry.putObject("params");
            params.put("model", model);
            params.put("max_tokens", maxTokens);
            params.putArray("messages").addObject().put("role", "user").put("content", prompts.get(i));
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.set("requests", requests);

        JsonNode response = restClient.post()
                .uri("/v1/messages/batches")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return response.path("id").asString();
    }

    /** Poll a batch's processing status ("in_progress", "ended", etc.) — results are fetched separately once ended. */
    public String pollBatch(String batchId) {
        JsonNode response = restClient.get()
                .uri("/v1/messages/batches/{id}", batchId)
                .retrieve()
                .body(JsonNode.class);
        return response.path("processing_status").asString();
    }

    private ArrayNode toMessagesJson(String userQuery, List<ToolExecutionStep> history) {
        ArrayNode messages = objectMapper.createArrayNode();
        messages.addObject().put("role", "user").put("content", userQuery);

        for (int i = 0; i < history.size(); i++) {
            ToolExecutionStep step = history.get(i);
            String toolUseId = "toolu_synthetic_" + i;

            ObjectNode assistantMsg = messages.addObject();
            assistantMsg.put("role", "assistant");
            ArrayNode assistantContent = assistantMsg.putArray("content");
            ObjectNode toolUseBlock = assistantContent.addObject();
            toolUseBlock.put("type", "tool_use");
            toolUseBlock.put("id", toolUseId);
            toolUseBlock.put("name", step.toolName());
            ObjectNode input = toolUseBlock.putObject("input");
            step.arguments().forEach((k, v) -> input.putPOJO(k, v));

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode userContent = userMsg.putArray("content");
            ObjectNode toolResultBlock = userContent.addObject();
            toolResultBlock.put("type", "tool_result");
            toolResultBlock.put("tool_use_id", toolUseId);
            toolResultBlock.put("content", String.valueOf(step.result()));
        }
        return messages;
    }

    private ArrayNode systemPromptJson() {
        ArrayNode system = objectMapper.createArrayNode();
        ObjectNode block = system.addObject();
        block.put("type", "text");
        block.put("text", SYSTEM_PROMPT);
        // Prompt caching: the system prompt is identical across every call in this app, so
        // marking it as a cache breakpoint means subsequent calls only pay the (much cheaper)
        // cache-read price for these tokens instead of full input pricing every time.
        block.putObject("cache_control").put("type", "ephemeral");
        return system;
    }

    private JsonNode post(ObjectNode body) {
        int maxAttempts = 3;
        long backoffMillis = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.post()
                        .uri("/v1/messages")
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (HttpStatusCodeException e) {
                boolean transientError = (e instanceof HttpServerErrorException) // 5xx: upstream problem, safe to retry
                        || (e instanceof HttpClientErrorException client && client.getStatusCode().value() == 429); // rate limited
                if (!transientError || attempt == maxAttempts) {
                    // Permanent errors (400 bad request, 401 unauthorized, etc.) fail immediately -
                    // retrying a malformed request or a bad key just wastes time and quota.
                    throw e;
                }
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoffMillis *= 2;
            }
        }
        throw new IllegalStateException("Unreachable: retry loop exited without returning or throwing.");
    }

    // Package-private (not private) specifically so AnthropicLlmClientJsonTest can exercise the
    // JSON shape directly without needing a real or mocked HTTP round-trip for every case.
    ArrayNode toToolsJson(List<ToolSpec> tools) {
        ArrayNode array = objectMapper.createArrayNode();
        for (int i = 0; i < tools.size(); i++) {
            ToolSpec tool = tools.get(i);
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

            // Prompt caching: the tools list is identical across calls too, and larger than the
            // system prompt - marking the LAST tool as a cache breakpoint caches everything up
            // to and including it (the whole tools array), same rationale as the system prompt.
            if (i == tools.size() - 1) {
                toolNode.putObject("cache_control").put("type", "ephemeral");
            }
        }
        return array;
    }

    Object asJavaValue(JsonNode node) {
        if (node.isNumber()) return node.asLong();
        if (node.isBoolean()) return node.asBoolean();
        return node.asString();
    }
}
