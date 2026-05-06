package com.doller.platform.service;

import com.doller.platform.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimitService {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String action, String subject) {
        String key = key(action, subject);
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState());
        synchronized (state) {
            Instant now = Instant.now();
            if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts. Try again later.");
            }
            if (state.windowStartedAt == null || now.isAfter(state.windowStartedAt.plus(WINDOW))) {
                state.reset(now);
            }
        }
    }

    public void recordSuccess(String action, String subject) {
        attempts.remove(key(action, subject));
    }

    public void recordFailure(String action, String subject) {
        String key = key(action, subject);
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState());
        synchronized (state) {
            Instant now = Instant.now();
            if (state.windowStartedAt == null || now.isAfter(state.windowStartedAt.plus(WINDOW))) {
                state.reset(now);
            }
            state.failures++;
            if (state.failures >= MAX_FAILURES) {
                state.lockedUntil = now.plus(LOCK_DURATION);
            }
        }
    }

    private String key(String action, String subject) {
        return action + ':' + subject;
    }

    private static final class AttemptState {
        private int failures;
        private Instant windowStartedAt;
        private Instant lockedUntil;

        private void reset(Instant now) {
            failures = 0;
            windowStartedAt = now;
            lockedUntil = null;
        }
    }
}
