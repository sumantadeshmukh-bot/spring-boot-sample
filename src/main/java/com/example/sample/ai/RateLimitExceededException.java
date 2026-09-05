package com.example.sample.ai;

/** Distinct from a generic IllegalStateException so the controller can map it to 429, not 500. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
