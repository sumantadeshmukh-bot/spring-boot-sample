package com.example.sample.ai;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Deliberately NOT under /api/items despite querying items — this is a different bounded
 * context (AI orchestration) layered on top of the CRUD resource, not an extension of it.
 */
@RestController
@RequestMapping("/api/ai")
public class AiQueryController {

    private final AiOrchestrationService orchestrationService;

    public AiQueryController(AiOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
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

    public record ErrorResponse(String error) {
    }
}
