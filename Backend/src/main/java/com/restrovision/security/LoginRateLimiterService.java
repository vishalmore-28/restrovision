package com.restrovision.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Sliding-window login rate limiter providing brute-force protection (BUG-008).
 * Enforces:
 *  1. IP-level sliding window (default: 10 attempts / 60 seconds).
 *  2. Account-level sliding window (default: 5 attempts / 60 seconds).
 *
 * NOTE ON DEPLOYMENT ARCHITECTURE:
 * This rate-limiting mechanism is process-local (in-memory ConcurrentHashMap) and does NOT provide
 * shared limits across multiple backend instances. In multi-node deployments behind a reverse proxy,
 * sticky sessions or a shared Redis distributed token bucket must be configured.
 */
@Service
public class LoginRateLimiterService {
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiterService.class);
    private static final int MAX_CAPACITY = 5000;

    @Value("${restrovision.security.rate-limit.max-attempts-per-ip:10}")
    private int maxAttemptsPerIp = 10;

    @Value("${restrovision.security.rate-limit.max-attempts-per-account:5}")
    private int maxAttemptsPerAccount = 5;

    @Value("${restrovision.security.rate-limit.window-seconds:60}")
    private long windowSeconds = 60;

    @Value("${restrovision.security.rate-limit.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
    private String trustedProxiesConfig = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

    private final ConcurrentHashMap<String, Deque<Instant>> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> accountAttempts = new ConcurrentHashMap<>();

    public static class RateLimitResult {
        private final boolean blocked;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean blocked, long retryAfterSeconds) {
            this.blocked = blocked;
            this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        }

        public boolean isBlocked() {
            return blocked;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null) {
            remoteAddr = "127.0.0.1";
        }

        Set<String> trusted = getTrustedProxies();
        if (trusted.contains(remoteAddr.trim())) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                String client = parts[0].trim();
                if (!client.isEmpty()) {
                    return client;
                }
            }
        }
        return remoteAddr.trim();
    }

    public String normalizeAccount(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return "unknown";
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    public RateLimitResult checkLimit(String clientIp, String normalizedEmail) {
        cleanOldEntries();
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);

        // 0. Fail-closed defense under all-blocked saturation:
        // If the cache is at/above capacity and all existing entries are actively blocked (no safe eviction candidates),
        // do not admit new unmetered identities. Fail closed to prevent unbounded heap growth and DoS bypass.
        if (clientIp != null && !clientIp.isBlank() && !ipAttempts.containsKey(clientIp)
                && ipAttempts.size() >= MAX_CAPACITY && isAllBlocked(ipAttempts, maxAttemptsPerIp)) {
            log.warn("Login limiter IP cache saturated (all {} entries actively blocked). Failing closed for new IP {}", MAX_CAPACITY, clientIp);
            return new RateLimitResult(true, windowSeconds);
        }
        if (normalizedEmail != null && !normalizedEmail.isBlank() && !accountAttempts.containsKey(normalizedEmail)
                && accountAttempts.size() >= MAX_CAPACITY && isAllBlocked(accountAttempts, maxAttemptsPerAccount)) {
            log.warn("Login limiter account cache saturated (all {} entries actively blocked). Failing closed for new account {}", MAX_CAPACITY, normalizedEmail);
            return new RateLimitResult(true, windowSeconds);
        }

        // 1. Check IP-level rate limiting
        if (clientIp != null && !clientIp.isBlank()) {
            Deque<Instant> ipHistory = ipAttempts.get(clientIp);
            if (ipHistory != null) {
                prune(ipHistory, cutoff);
                if (ipHistory.size() >= maxAttemptsPerIp) {
                    Instant oldest = ipHistory.peekFirst();
                    long remaining = oldest != null ? windowSeconds - Duration.between(oldest, now).getSeconds() : windowSeconds;
                    log.warn("Login rate limit triggered for IP {}: {} failed attempts in window (retry after {}s)",
                            clientIp, ipHistory.size(), remaining);
                    return new RateLimitResult(true, remaining);
                }
            }
        }

        // 2. Check Account-level rate limiting
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            Deque<Instant> accountHistory = accountAttempts.get(normalizedEmail);
            if (accountHistory != null) {
                prune(accountHistory, cutoff);
                if (accountHistory.size() >= maxAttemptsPerAccount) {
                    Instant oldest = accountHistory.peekFirst();
                    long remaining = oldest != null ? windowSeconds - Duration.between(oldest, now).getSeconds() : windowSeconds;
                    log.warn("Login rate limit triggered for account {}: {} failed attempts in window (retry after {}s)",
                            normalizedEmail, accountHistory.size(), remaining);
                    return new RateLimitResult(true, remaining);
                }
            }
        }

        return new RateLimitResult(false, 0);
    }

    public void recordFailure(String clientIp, String normalizedEmail) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);
        if (clientIp != null && !clientIp.isBlank()) {
            if (ipAttempts.containsKey(clientIp) || ipAttempts.size() < MAX_CAPACITY || !isAllBlocked(ipAttempts, maxAttemptsPerIp)) {
                ipAttempts.compute(clientIp, (k, deque) -> {
                    if (deque == null) deque = new ConcurrentLinkedDeque<>();
                    prune(deque, cutoff);
                    deque.addLast(now);
                    return deque;
                });
            }
        }
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            if (accountAttempts.containsKey(normalizedEmail) || accountAttempts.size() < MAX_CAPACITY || !isAllBlocked(accountAttempts, maxAttemptsPerAccount)) {
                accountAttempts.compute(normalizedEmail, (k, deque) -> {
                    if (deque == null) deque = new ConcurrentLinkedDeque<>();
                    prune(deque, cutoff);
                    deque.addLast(now);
                    return deque;
                });
            }
        }
        cleanOldEntries();
    }

    public void recordSuccess(String clientIp, String normalizedEmail) {
        // Clear account failure history on success
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            accountAttempts.remove(normalizedEmail);
        }
        // NOTE: We intentionally DO NOT clear IP failure history on success.
        // This ensures an attacker spraying multiple accounts from the same IP cannot reset their IP quota.
    }

    private void prune(Deque<Instant> queue, Instant cutoff) {
        while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
            queue.pollFirst();
        }
    }

    private boolean isAllBlocked(ConcurrentHashMap<String, Deque<Instant>> map, int threshold) {
        for (Deque<Instant> deque : map.values()) {
            if (deque.size() < threshold) {
                return false;
            }
        }
        return true;
    }

    private void cleanOldEntries() {
        if (ipAttempts.size() > MAX_CAPACITY || accountAttempts.size() > MAX_CAPACITY) {
            Instant cutoff = Instant.now().minusSeconds(windowSeconds);
            ipAttempts.entrySet().removeIf(e -> {
                prune(e.getValue(), cutoff);
                return e.getValue().isEmpty();
            });
            accountAttempts.entrySet().removeIf(e -> {
                prune(e.getValue(), cutoff);
                return e.getValue().isEmpty();
            });

            // Eviction strategy under saturation:
            // Remove noise entries with lowest attempt counts first, but NEVER evict currently rate-limited/blocked accounts or IPs!
            if (accountAttempts.size() > MAX_CAPACITY) {
                accountAttempts.entrySet().removeIf(e -> e.getValue().size() < maxAttemptsPerAccount);
            }
            if (ipAttempts.size() > MAX_CAPACITY) {
                ipAttempts.entrySet().removeIf(e -> e.getValue().size() < maxAttemptsPerIp);
            }
        }
    }

    public int getIpAttemptsSize() {
        return ipAttempts.size();
    }

    public int getAccountAttemptsSize() {
        return accountAttempts.size();
    }

    public void injectAccountHistoryForTesting(String account, int count) {
        Deque<Instant> deque = new ConcurrentLinkedDeque<>();
        Instant now = Instant.now();
        for (int i = 0; i < count; i++) {
            deque.add(now);
        }
        accountAttempts.put(account, deque);
    }

    private Set<String> getTrustedProxies() {
        Set<String> set = new HashSet<>();
        if (trustedProxiesConfig != null) {
            for (String p : trustedProxiesConfig.split(",")) {
                if (!p.isBlank()) {
                    set.add(p.trim());
                }
            }
        }
        return set;
    }

    public void clear() {
        ipAttempts.clear();
        accountAttempts.clear();
    }

    public void setMaxAttemptsPerIp(int maxAttemptsPerIp) {
        this.maxAttemptsPerIp = maxAttemptsPerIp;
    }

    public void setMaxAttemptsPerAccount(int maxAttemptsPerAccount) {
        this.maxAttemptsPerAccount = maxAttemptsPerAccount;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
