package com.example.sample.ai;

import com.example.sample.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The actual agentic loop: guard the input, ask the model to decide a step at a time
 * (up to a bound), validate each decision against a whitelist before trusting it,
 * execute, and repeat until the model signals it's done or the step limit is hit -
 * then summarize the whole sequence. This is a deliberate exception to this repo's
 * usual "no service layer, controllers talk straight to repositories" rule (see
 * CLAUDE.md) — orchestrating a multi-step decision loop is a fundamentally different
 * kind of logic than CRUD, and collapsing it into the controller would hide the very
 * steps worth being able to see and test independently.
 *
 * The step-at-a-time design (not "decide everything up front") is what makes tool
 * composition possible: "delete the item named Widget" needs to see search_items'
 * result before it can decide delete_item's argument.
 */
@Service
public class AiOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AiOrchestrationService.class);
    private static final int MAX_STEPS = 4;

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ItemRepository itemRepository;
    private final PromptInjectionGuard injectionGuard;
    private final RateLimiter rateLimiter;
    private final ConfirmationStore confirmationStore;
    private final ObjectMapper objectMapper;
    // Derived from ToolRegistry rather than hardcoded separately — a tool added there is
    // automatically whitelisted here too. Still leaves the `execute()` switch below as a
    // second place that must be updated for a new tool to actually do anything; closing
    // that last seam would need a Command-pattern per tool, deferred as unneeded for 4 tools.
    private final Set<String> knownTools;

    public AiOrchestrationService(LlmClient llmClient, ToolRegistry toolRegistry, ItemRepository itemRepository,
                                   PromptInjectionGuard injectionGuard, RateLimiter rateLimiter,
                                   ConfirmationStore confirmationStore, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.itemRepository = itemRepository;
        this.injectionGuard = injectionGuard;
        this.rateLimiter = rateLimiter;
        this.confirmationStore = confirmationStore;
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
        List<ToolExecutionStep> history = new ArrayList<>();

        for (int step = 0; step < MAX_STEPS; step++) {
            Decision decision = llmClient.decideNextStep(userQuery, tools, history);
            if (decision instanceof Decision.Finish) {
                break;
            }
            ToolCall call = ((Decision.CallTool) decision).toolCall();

            // Never trust the model's tool name blindly, even though it was only offered
            // a whitelisted list — defense in depth against a malformed or manipulated response.
            if (!knownTools.contains(call.toolName())) {
                throw new IllegalStateException("Model chose an unrecognized tool: " + call.toolName());
            }

            Object result = execute(call);
            history.add(new ToolExecutionStep(call.toolName(), call.arguments(), result));

            // delete_item never actually deletes here - it queues a confirmation and the loop
            // stops, since there's nothing more to decide until a human confirms out-of-band.
            if ("delete_item".equals(call.toolName())) {
                break;
            }
        }

        String answer = llmClient.summarize(userQuery, history);

        AiTrace trace = new AiTrace(
                Instant.now(),
                userQuery,
                history.isEmpty() ? null : history.get(history.size() - 1).toolName(),
                history.isEmpty() ? Map.of() : history.get(history.size() - 1).arguments(),
                history.isEmpty() ? null : history.get(history.size() - 1).result(),
                answer,
                System.currentTimeMillis() - start
        );

        log.info("ai_trace {}", objectMapper.writeValueAsString(trace));
        return trace;
    }

    /**
     * Executes a previously-queued destructive action, if the token is valid and not
     * expired. Returns true if something was actually deleted, false if the token was
     * unknown/expired/already consumed (the controller maps that to 404).
     */
    public boolean confirm(String token) {
        return confirmationStore.consume(token)
                .map(pending -> {
                    itemRepository.deleteById(pending.itemId());
                    log.info("ai_confirmed_action tool=\"{}\" itemId={}", pending.toolName(), pending.itemId());
                    return true;
                })
                .orElse(false);
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
                Long id = parseId(call.arguments().get("id"));
                yield itemRepository.findById(id).orElse(null);
            }
            case "list_items" -> itemRepository.findAll();
            case "delete_item" -> {
                Long id = parseId(call.arguments().get("id"));
                if (!itemRepository.existsById(id)) {
                    yield Map.of("status", "not_found", "itemId", id);
                }
                String token = confirmationStore.queue("delete_item", id);
                yield Map.of("status", "confirmation_required", "itemId", id, "confirmToken", token);
            }
            default -> throw new IllegalStateException("Unreachable: " + call.toolName());
        };
    }

    private Long parseId(Object rawId) {
        try {
            return Long.parseLong(String.valueOf(rawId));
        } catch (NumberFormatException e) {
            // Deliberately not echoing the raw parse-exception text back to the caller -
            // it's an internal detail (and NumberFormatException happening to extend
            // IllegalArgumentException, which the controller maps to 400, is incidental,
            // not a validation contract worth relying on).
            throw new IllegalArgumentException("Model provided a non-numeric item id.");
        }
    }
}
