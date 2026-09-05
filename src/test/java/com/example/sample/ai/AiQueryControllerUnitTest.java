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
    private final LlmClient llmClient = mock(LlmClient.class); // mock, not MockLlmClient/AnthropicLlmClient - stands in for "whichever provider"
    private final AiQueryController controller = new AiQueryController(orchestrationService, llmClient);
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

    @Test
    void confirmWithValidTokenReturns200() {
        when(orchestrationService.confirm("tok")).thenReturn(true);
        ResponseEntity<?> response = controller.confirm(new AiQueryController.ConfirmRequest("tok"));
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void confirmWithUnknownTokenReturns404() {
        when(orchestrationService.confirm("bad-tok")).thenReturn(false);
        ResponseEntity<?> response = controller.confirm(new AiQueryController.ConfirmRequest("bad-tok"));
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void batchEndpointReturns501WhenNotUsingRealProvider() {
        // llmClient is a plain Mockito mock here, not an AnthropicLlmClient instance -
        // exercises the same "batch requires the real provider" guard mock mode hits.
        ResponseEntity<?> response = controller.submitBatch(new AiQueryController.BatchRequest(java.util.List.of("a")));
        assertEquals(501, response.getStatusCode().value());
    }
}
