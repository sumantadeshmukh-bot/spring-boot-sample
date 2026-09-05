package com.example.sample.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A minimal, illustrative defense-in-depth check — NOT a complete prompt-injection
 * solution. Real defense is architectural: the model only ever gets a whitelisted
 * tool list to choose from (see ToolRegistry) and the app validates every tool call
 * against that whitelist before executing it (see AiOrchestrationService) — so even
 * a successfully "injected" instruction can't make the app do anything it couldn't
 * already do. This denylist just catches the laziest, most obvious attempts early,
 * the same way input validation catches obviously-malformed data before it reaches
 * business logic, without claiming to be a complete security boundary on its own.
 */
@Component
public class PromptInjectionGuard {

    private static final int MAX_QUERY_LENGTH = 500;

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore (all )?(previous|prior|above) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (your|the) (system prompt|instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal (your|the) (system prompt|api key|instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE)
    );

    /** @throws IllegalArgumentException if the query is too long or matches a known injection pattern. */
    public void check(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank.");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Query exceeds maximum length of " + MAX_QUERY_LENGTH + " characters.");
        }
        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(query).find()) {
                throw new IllegalArgumentException("Query rejected: matches a known prompt-injection pattern.");
            }
        }
    }
}
