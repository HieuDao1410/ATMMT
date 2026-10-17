package vn.edu.atmmt.sessionlab.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_ACCOUNTS = 2048;

    private final Map<String, Window> accounts = new HashMap<>();
    private final int maxFailures;
    private final long windowNanos;
    private final long blockNanos;

    public LoginAttemptLimiter(
            @Value("${lab.security.login.max-failures:5}") int maxFailures,
            @Value("${lab.security.login.window-seconds:300}") int windowSeconds,
            @Value("${lab.security.login.block-seconds:60}") int blockSeconds) {
        if (maxFailures < 1 || maxFailures > 100 || windowSeconds < 1 || windowSeconds > 3600
                || blockSeconds < 1 || blockSeconds > 3600) {
            throw new IllegalArgumentException("Login limits require 1..100 failures and 1..3600 seconds");
        }
        this.maxFailures = maxFailures;
        this.windowNanos = Duration.ofSeconds(windowSeconds).toNanos();
        this.blockNanos = Duration.ofSeconds(blockSeconds).toNanos();
    }

    public synchronized Admission begin(String username) {
        long now = System.nanoTime();
        // Expire idle entries without evicting active limits when the table is full.
        accounts.values().removeIf(window -> expired(window, now) && window.inFlight == 0);
        String account = accountKey(username);
        Window window = accounts.get(account);
        if (window == null) {
            if (accounts.size() >= MAX_ACCOUNTS) {
                return new Admission(null, 1);
            }
            window = new Window(now);
            accounts.put(account, window);
        }
        if (window.blocked) {
            long remaining = Math.max(1, blockNanos - (now - window.blockedAt));
            long retryAfter = 1 + (remaining - 1) / 1_000_000_000L;
            return new Admission(null, retryAfter);
        }
        if (expired(window, now) || window.failures + window.inFlight >= maxFailures) {
            return new Admission(null, 1);
        }
        // Reserve capacity before password verification so concurrent requests cannot bypass it.
        window.inFlight++;
        return new Admission(new Attempt(account, window), 0);
    }

    // null means no credential result (for example an internal error), so only release the reservation.
    public synchronized boolean finish(Attempt attempt, Boolean failed) {
        if (attempt.finished) {
            return false;
        }
        attempt.finished = true;
        Window window = attempt.window;
        window.inFlight--;
        long now = System.nanoTime();
        boolean reachedLimit = false;
        if (Boolean.TRUE.equals(failed)) {
            if (!window.blocked && expired(window, now)) {
                window.startedAt = now;
                window.failures = 0;
            }
            window.failures++;
            if (!window.blocked && window.failures >= maxFailures) {
                window.blocked = true;
                window.blockedAt = now;
                reachedLimit = true;
            }
        } else if (Boolean.FALSE.equals(failed) && !window.blocked) {
            window.failures = 0;
            window.startedAt = now;
        }
        // A login admitted before the threshold cannot cancel a concurrent lockout.
        if (window.inFlight == 0 && (window.failures == 0 || expired(window, now))) {
            accounts.remove(attempt.account, window);
        }
        return reachedLimit;
    }

    private boolean expired(Window window, long now) {
        return window.blocked ? now - window.blockedAt >= blockNanos
            : now - window.startedAt >= windowNanos;
    }

    private String accountKey(String username) {
        // Match form-login trimming and the in-memory user store's case-insensitive lookup.
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    public record Admission(Attempt attempt, long retryAfterSeconds) {
    }

    public static final class Attempt {
        private final String account;
        private final Window window;
        private boolean finished;

        private Attempt(String account, Window window) {
            this.account = account;
            this.window = window;
        }
    }

    private static final class Window {
        private long startedAt;
        private long blockedAt;
        private boolean blocked;
        private int failures;
        private int inFlight;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
