package com.smartdonation.security;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowSeconds;
    private final Clock clock = Clock.systemUTC();

    public LoginRateLimiter(
            @Value("${AUTH_LOGIN_MAX_ATTEMPTS:10}") int maxAttempts,
            @Value("${AUTH_LOGIN_WINDOW_SECONDS:900}") long windowSeconds) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public synchronized boolean isBlocked(String key) {
        purgeExpired();
        Deque<Instant> attempts = failures.get(key);
        prune(attempts);
        return attempts != null && attempts.size() >= maxAttempts;
    }

    public synchronized void recordFailure(String key) {
        purgeExpired();
        if (!failures.containsKey(key) && failures.size() >= 10_000) {
            failures.keySet().stream().findFirst().ifPresent(failures::remove);
        }
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        prune(attempts);
        attempts.addLast(clock.instant());
    }

    public synchronized void recordSuccess(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Instant> attempts) {
        if (attempts == null) return;
        Instant threshold = clock.instant().minusSeconds(windowSeconds);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(threshold)) {
            attempts.removeFirst();
        }
    }

    private void purgeExpired() {
        failures.entrySet().removeIf(entry -> {
            prune(entry.getValue());
            return entry.getValue().isEmpty();
        });
    }
}
