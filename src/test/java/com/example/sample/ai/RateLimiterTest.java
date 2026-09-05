package com.example.sample.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterTest {

    @Test
    void allowsRequestsUnderTheLimitThenRejects() {
        RateLimiter limiter = new RateLimiter();

        // Matches MAX_REQUESTS_PER_WINDOW in RateLimiter - kept in sync deliberately rather
        // than reading the constant, so a change there forces a conscious look at this test.
        for (int i = 0; i < 30; i++) {
            assertDoesNotThrow(() -> limiter.checkAndIncrement("client-a"), "request " + i + " should be within budget");
        }

        assertThrows(RateLimitExceededException.class, () -> limiter.checkAndIncrement("client-a"));
    }

    @Test
    void differentClientsHaveIndependentBudgets() {
        RateLimiter limiter = new RateLimiter();

        for (int i = 0; i < 30; i++) {
            assertDoesNotThrow(() -> limiter.checkAndIncrement("client-a"));
        }
        assertThrows(RateLimitExceededException.class, () -> limiter.checkAndIncrement("client-a"));

        // client-b's budget is untouched by client-a exhausting theirs - this is the actual
        // fix for the DoS finding: one noisy/malicious client can no longer starve everyone else.
        assertDoesNotThrow(() -> limiter.checkAndIncrement("client-b"));
    }
}
