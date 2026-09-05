package com.example.sample.ai;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Isolates the controller's exception -> HTTP status mapping from whatever specifically
 * causes each exception (AiOrchestrationService is mocked) - a prior review noted the 429
 * and 502 paths were only reachable in practice through scenarios MockLlmClient can't
 * produce, so they were effectively untested at the HTTP layer.
 */
class AiQueryControllerUnitTest {

    private final AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
    private final AiQueryController controller = new AiQueryController(orchestrationService);
    private final HttpServletRequest httpRequest = mock(HttpServletRequest.class);

    private ResponseEntity<?> ask(String query) {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        return controller.ask(new AiQueryController.AskRequest(query), httpRequest);
    }

    @Test
    void invalidArgumentMapsTo400() {
        when(orchestrationService.ask(anyString(), any())).thenThrow(new IllegalArgumentException("bad input"));
        ResponseEntity<?> response = ask("whatever");
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void rateLimitExceededMapsTo429() {
        when(orchestrationService.ask(anyString(), any())).thenThrow(new RateLimitExceededException("slow down"));
        ResponseEntity<?> response = ask("whatever");
        assertEquals(429, response.getStatusCode().value());
    }

    @Test
    void modelMisbehaviorMapsTo502() {
        when(orchestrationService.ask(anyString(), any())).thenThrow(new IllegalStateException("model went rogue"));
        ResponseEntity<?> response = ask("whatever");
        assertEquals(502, response.getStatusCode().value());
    }

    @Test
    void successMapsTo200() {
        AiTrace trace = new AiTrace(java.time.Instant.now(), "q", "list_items", java.util.Map.of(), java.util.List.of(), "answer", 5);
        when(orchestrationService.ask(anyString(), any())).thenReturn(trace);
        ResponseEntity<?> response = ask("whatever");
        assertEquals(200, response.getStatusCode().value());
    }
}
