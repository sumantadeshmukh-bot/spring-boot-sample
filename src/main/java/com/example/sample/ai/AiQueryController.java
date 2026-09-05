package com.example.sample.ai;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Deliberately NOT under /api/items despite querying items — this is a different bounded
 * context (AI orchestration) layered on top of the CRUD resource, not an extension of it.
 */
@RestController
@RequestMapping("/api/ai")
public class AiQueryController {

    private final AiOrchestrationService orchestrationService;
    private final LlmClient llmClient;

    public AiQueryController(AiOrchestrationService orchestrationService, LlmClient llmClient) {
        this.orchestrationService = orchestrationService;
        this.llmClient = llmClient;
    }

    public record AskRequest(@NotBlank String query) {
    }

    public record AskResponse(String answer, String toolCalled, Object toolArguments, Object toolResult) {
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@Valid @RequestBody AskRequest request, HttpServletRequest httpRequest) {
        try {
            // Rate limiting is per-client, keyed by remote address - a real deployment behind
            // a proxy would need to read a trusted forwarded-for header instead, configured
            // deliberately (trusting an untrusted client-supplied header would defeat the point).
            String clientKey = httpRequest.getRemoteAddr();
            AiTrace trace = orchestrationService.ask(request.query(), clientKey);
            return ResponseEntity.ok(new AskResponse(
                    trace.finalAnswer(), trace.toolChosen(), trace.toolArguments(), trace.toolResult()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(429).body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(502).body(new ErrorResponse("AI provider returned an unusable response: " + e.getMessage()));
        }
    }

    public record ConfirmRequest(@NotBlank String token) {
    }

    public record ConfirmResponse(boolean confirmed) {
    }

    /**
     * Executes a destructive action the AI decided on but didn't perform - see
     * ConfirmationStore. The human confirming is a deliberate separate step, not a
     * formality: the token proves a specific decision was reviewed, not just re-sent.
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@Valid @RequestBody ConfirmRequest request) {
        boolean confirmed = orchestrationService.confirm(request.token());
        if (!confirmed) {
            return ResponseEntity.status(404).body(new ErrorResponse("No pending confirmation for that token (unknown, expired, or already used)."));
        }
        return ResponseEntity.ok(new ConfirmResponse(true));
    }

    public record BatchRequest(List<String> prompts) {
    }

    public record BatchResponse(String batchId) {
    }

    /**
     * Bulk/async processing via Anthropic's Message Batches API — only meaningful with the
     * real provider (batching mock responses would just be per-item mocking with extra
     * steps, so this deliberately isn't given mock parity; see docs/agentic-concepts).
     */
    @PostMapping("/batch")
    public ResponseEntity<?> submitBatch(@RequestBody BatchRequest request) {
        if (!(llmClient instanceof AnthropicLlmClient anthropic)) {
            return ResponseEntity.status(501).body(new ErrorResponse(
                    "Batch processing requires app.ai.provider=anthropic with a real API key."));
        }
        String batchId = anthropic.submitBatch(request.prompts());
        return ResponseEntity.ok(new BatchResponse(batchId));
    }

    @GetMapping("/batch/{id}/status")
    public ResponseEntity<?> batchStatus(@PathVariable String id) {
        if (!(llmClient instanceof AnthropicLlmClient anthropic)) {
            return ResponseEntity.status(501).body(new ErrorResponse(
                    "Batch processing requires app.ai.provider=anthropic with a real API key."));
        }
        return ResponseEntity.ok(Map.of("status", anthropic.pollBatch(id)));
    }

    public record ErrorResponse(String error) {
    }
}
