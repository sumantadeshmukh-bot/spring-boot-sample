package com.example.sample.ai;

import java.time.Instant;

/** A queued destructive action awaiting explicit human confirmation before it executes. */
public record PendingConfirmation(String toolName, Long itemId, Instant expiresAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
