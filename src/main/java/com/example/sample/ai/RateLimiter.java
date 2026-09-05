package com.example.sample.ai;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-client (keyed, e.g. by remote address) fixed-window limiter — an earlier version of
 * this class used one global counter, which a security review correctly flagged as a
 * trivial DoS: one caller sending 30 quick requests would exhaust the *entire app's* AI
 * query budget for every other user. Keying by client fixes that specific vector.
 *
 * Still the minimal illustrative version of what a real system would put behind an API
 * gateway or a distributed limiter (Redis, etc.): per-instance memory only (doesn't survive
 * a restart or scale past one instance), and the bucket map grows without bound as new
 * clients appear (no eviction) - fine for a demo, not for production traffic.
 */
@Component
public class RateLimiter {

    private static final int MAX_REQUESTS_PER_WINDOW = 30;
    private static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicReference<Instant> windowStart = new AtomicReference<>(Instant.now());
    }

    /** @throws RateLimitExceededException if this client's current window budget is exhausted. */
    public void checkAndIncrement(String clientKey) {
        Bucket bucket = buckets.computeIfAbsent(clientKey, k -> new Bucket());
        Instant now = Instant.now();
        Instant start = bucket.windowStart.get();
        if (now.isAfter(start.plusSeconds(WINDOW_SECONDS))) {
            if (bucket.windowStart.compareAndSet(start, now)) {
                bucket.count.set(0);
            }
        }
        if (bucket.count.incrementAndGet() > MAX_REQUESTS_PER_WINDOW) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded for client '" + clientKey + "': max " + MAX_REQUESTS_PER_WINDOW
                            + " AI queries per " + WINDOW_SECONDS + "s.");
        }
    }
}
