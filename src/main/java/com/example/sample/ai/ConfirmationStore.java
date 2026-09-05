package com.example.sample.ai;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds destructive actions the model has decided on but not yet executed, pending
 * explicit human confirmation via POST /api/ai/confirm. This is the concrete
 * implementation of a "confirmation flow" pattern: the model choosing delete_item is
 * a *decision*, not an *action* - a human has to separately opt in before it happens.
 *
 * In-memory, single-instance, no persistence across restarts - the illustrative version
 * of what a real system would back with a database row and a TTL job, same category of
 * simplification as RateLimiter's in-memory buckets.
 */
@Component
public class ConfirmationStore {

    private static final long TTL_SECONDS = 300;

    private final ConcurrentHashMap<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    public String queue(String toolName, Long itemId) {
        String token = UUID.randomUUID().toString();
        pending.put(token, new PendingConfirmation(toolName, itemId, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    /** Removes and returns the pending confirmation if present and not expired; empty otherwise. */
    public Optional<PendingConfirmation> consume(String token) {
        PendingConfirmation confirmation = pending.remove(token);
        if (confirmation == null || confirmation.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(confirmation);
    }
}
