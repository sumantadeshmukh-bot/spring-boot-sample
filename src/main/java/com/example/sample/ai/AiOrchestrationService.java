package com.example.sample.ai;

import com.example.sample.Item;
import com.example.sample.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The actual agentic loop: guard the input, ask the model to decide, validate that decision
 * against a whitelist (never trust it blindly), execute the real action, ask the model to
 * summarize, and trace every step. This is a deliberate exception to this repo's usual
 * "no service layer, controllers talk straight to repositories" rule (see CLAUDE.md) —
 * orchestrating a multi-step decision loop is a fundamentally different kind of logic than
 * CRUD, and collapsing it into the controller would hide the very steps worth being able
 * to see and test independently.
 */
@Service
public class AiOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestrationService.class);

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ItemRepository itemRepository;
    private final PromptInjectionGuard injectionGuard;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    // Derived from ToolRegistry rather than hardcoded separately — a tool added there is
    // automatically whitelisted here too. Still leaves the `execute()` switch below as a
    // second place that must be updated for a new tool to actually do anything; closing
    // that last seam would need a Command-pattern per tool, deferred as unneeded for 3 tools.
    private final Set<String> knownTools;

    public AiOrchestrationService(LlmClient llmClient, ToolRegistry toolRegistry, ItemRepository itemRepository,
                                   PromptInjectionGuard injectionGuard, RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.itemRepository = itemRepository;
        this.injectionGuard = injectionGuard;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.knownTools = toolRegistry.allTools().stream().map(ToolSpec::toolName).collect(Collectors.toSet());
    }

    public AiTrace ask(String userQuery, String clientKey) {
        long start = System.currentTimeMillis();

        try {
            injectionGuard.check(userQuery);
        } catch (IllegalArgumentException e) {
            // Rejections are a security-relevant signal in their own right (repeated attempts
            // from the same source is exactly the anomaly a real system would want to detect) -
            // logging only successful runs would make this endpoint's abuse invisible.
            log.warn("ai_security_reject client=\"{}\" reason=\"{}\" query=\"{}\"", clientKey, e.getMessage(), truncate(userQuery));
            throw e;
        }
        try {
            rateLimiter.checkAndIncrement(clientKey);
        } catch (RateLimitExceededException e) {
            log.warn("ai_rate_limit_reject client=\"{}\" query=\"{}\"", clientKey, truncate(userQuery));
            throw e;
        }

        List<ToolSpec> tools = toolRegistry.allTools();
        ToolCall decision = llmClient.decideTool(userQuery, tools);

        // Never trust the model's tool name blindly, even though it was only offered
        // a whitelisted list — defense in depth against a malformed or manipulated response.
        if (!knownTools.contains(decision.toolName())) {
            throw new IllegalStateException("Model chose an unrecognized tool: " + decision.toolName());
        }

        Object toolResult = execute(decision);
        String answer = llmClient.summarize(userQuery, decision.toolName(), toolResult);

        AiTrace trace = new AiTrace(
                Instant.now(),
                userQuery,
                decision.toolName(),
                decision.arguments(),
                toolResult,
                answer,
                System.currentTimeMillis() - start
        );

        log.info("ai_trace {}", objectMapper.writeValueAsString(trace));
        return trace;
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 200 ? s : s.substring(0, 200) + "...(truncated)";
    }

    private Object execute(ToolCall call) {
        return switch (call.toolName()) {
            case "search_items" -> itemRepository.findByNameContainingIgnoreCase(
                    String.valueOf(call.arguments().get("name")));
            case "get_item" -> {
                long id;
                try {
                    id = Long.parseLong(String.valueOf(call.arguments().get("id")));
                } catch (NumberFormatException e) {
                    // Deliberately not echoing the raw parse-exception text back to the caller -
                    // it's an internal detail (and NumberFormatException happening to extend
                    // IllegalArgumentException, which the controller maps to 400, is incidental,
                    // not a validation contract worth relying on).
                    throw new IllegalArgumentException("Model provided a non-numeric item id.");
                }
                yield itemRepository.findById(id).orElse(null);
            }
            case "list_items" -> itemRepository.findAll();
            default -> throw new IllegalStateException("Unreachable: " + call.toolName());
        };
    }
}
