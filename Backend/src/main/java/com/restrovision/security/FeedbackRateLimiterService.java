package com.restrovision.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Server-side abuse and flooding protection for public customer feedback (BUG-013).
 * Enforces:
 *  1. Client IP sliding-window request throttling.
 *  2. Per-table cooldown window to prevent rapid-fire burst flooding.
 *  3. Duplicate submission detection across identical table/rating/comment tuples.
 *
 * NOTE ON DEPLOYMENT ARCHITECTURE:
 * This rate-limiting mechanism is process-local (in-memory ConcurrentHashMap). In distributed multi-node
 * deployments behind a load balancer, sticky sessions or a shared Redis distributed token bucket
 * should be configured.
 */
@Service
public class FeedbackRateLimiterService {
    private static final Logger log = LoggerFactory.getLogger(FeedbackRateLimiterService.class);

    @Value("${restrovision.security.feedback.max-per-ip-window:10}")
    private int maxPerIpWindow = 10;

    @Value("${restrovision.security.feedback.ip-window-seconds:300}")
    private long ipWindowSeconds = 300;

    @Value("${restrovision.security.feedback.table-cooldown-seconds:15}")
    private long tableCooldownSeconds = 15;

    @Value("${restrovision.security.feedback.duplicate-window-seconds:60}")
    private long duplicateWindowSeconds = 60;

    @Value("${restrovision.security.feedback.max-comment-length:1000}")
    private int maxCommentLength = 1000;

    private final ConcurrentHashMap<String, Deque<Instant>> ipHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> clientTableCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> duplicateFingerprints = new ConcurrentHashMap<>();
    private static final int MAX_CAPACITY = 5000;

    public static class FeedbackLimitResult {
        private final boolean blocked;
        private final String reason;
        private final long retryAfterSeconds;

        public FeedbackLimitResult(boolean blocked, String reason, long retryAfterSeconds) {
            this.blocked = blocked;
            this.reason = reason;
            this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        }

        public boolean isBlocked() {
            return blocked;
        }

        public String getReason() {
            return reason;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public FeedbackLimitResult checkLimits(String clientIp, Long tableId, Integer rating, String cleanComment) {
        return checkLimits(clientIp, null, tableId, rating, cleanComment);
    }

    public FeedbackLimitResult checkLimits(String clientIp, String clientDeviceFingerprint, Long tableId, Integer rating, String cleanComment) {
        Instant now = Instant.now();

        // 0. Fail-closed defense under extreme cache saturation:
        // If the cache is at/above capacity and all existing entries are actively blocked (no safe eviction candidate),
        // do not admit new unmetered IPs. Fail closed to prevent unbounded heap growth and DoS bypass.
        if (clientIp != null && !clientIp.isBlank() && !ipHistory.containsKey(clientIp)
                && ipHistory.size() >= MAX_CAPACITY && isAllBlocked(ipHistory, maxPerIpWindow)) {
            log.warn("Feedback limiter IP cache saturated (all {} entries actively blocked). Failing closed for new IP {}", MAX_CAPACITY, clientIp);
            return new FeedbackLimitResult(true, "Feedback rate limit capacity reached. Please try again later.", ipWindowSeconds);
        }

        // 1. IP Sliding Window Rate Limit
        if (clientIp != null && !clientIp.isBlank()) {
            Deque<Instant> history = ipHistory.get(clientIp);
            if (history != null) {
                Instant cutoff = now.minusSeconds(ipWindowSeconds);
                while (!history.isEmpty() && history.peekFirst().isBefore(cutoff)) {
                    history.pollFirst();
                }
                if (history.size() >= maxPerIpWindow) {
                    Instant oldest = history.peekFirst();
                    long remaining = oldest != null ? ipWindowSeconds - Duration.between(oldest, now).getSeconds() : ipWindowSeconds;
                    log.warn("Feedback flooding: IP {} exceeded limit ({} submissions in {}s)", clientIp, history.size(), ipWindowSeconds);
                    return new FeedbackLimitResult(true, "Feedback submission limit reached. Please wait before submitting more feedback.", remaining);
                }
            }
        }

        // 2. Duplicate Submission Detection (checked before table cooldown so identical feedback gets explicit duplicate notification)
        String fingerprint = generateFingerprint(clientIp, tableId, rating, cleanComment);
        Instant lastDupTime = duplicateFingerprints.get(fingerprint);
        if (lastDupTime != null) {
            long elapsed = Duration.between(lastDupTime, now).getSeconds();
            if (elapsed < duplicateWindowSeconds) {
                long remaining = duplicateWindowSeconds - elapsed;
                log.info("Duplicate feedback detected for table {} from IP {} (submitted {}s ago)", tableId, clientIp, elapsed);
                return new FeedbackLimitResult(true, "Duplicate feedback detected. Thank you, your feedback has already been recorded.", remaining);
            } else {
                duplicateFingerprints.remove(fingerprint);
            }
        }

        // 3. Client-Table Cooldown Window (prevents rapid automated bursts from the same client targeting this table)
        String clientTableKey = generateClientTableKey(clientIp, clientDeviceFingerprint, tableId);
        Instant lastTableTime = clientTableCooldown.get(clientTableKey);
        if (lastTableTime != null) {
            long elapsed = Duration.between(lastTableTime, now).getSeconds();
            if (elapsed < tableCooldownSeconds) {
                long remaining = tableCooldownSeconds - elapsed;
                log.warn("Feedback flooding: Client {} table {} submission within cooldown ({}s < {}s)", clientIp, tableId, elapsed, tableCooldownSeconds);
                return new FeedbackLimitResult(true, "Feedback was recently submitted from your device for this table. Please wait a moment.", remaining);
            }
        }

        return new FeedbackLimitResult(false, null, 0);
    }

    public void recordSubmission(String clientIp, Long tableId, Integer rating, String cleanComment) {
        recordSubmission(clientIp, null, tableId, rating, cleanComment);
    }

    public void recordSubmission(String clientIp, String clientDeviceFingerprint, Long tableId, Integer rating, String cleanComment) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(ipWindowSeconds);
        if (clientIp != null && !clientIp.isBlank()) {
            if (ipHistory.containsKey(clientIp) || ipHistory.size() < MAX_CAPACITY || !isAllBlocked(ipHistory, maxPerIpWindow)) {
                ipHistory.compute(clientIp, (k, deque) -> {
                    if (deque == null) deque = new ConcurrentLinkedDeque<>();
                    while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                        deque.pollFirst();
                    }
                    deque.addLast(now);
                    return deque;
                });
            }
        }
        String clientTableKey = generateClientTableKey(clientIp, clientDeviceFingerprint, tableId);
        clientTableCooldown.put(clientTableKey, now);

        String fingerprint = generateFingerprint(clientIp, tableId, rating, cleanComment);
        duplicateFingerprints.put(fingerprint, now);

        cleanOldEntries();
    }

    private String generateClientTableKey(String clientIp, String clientDeviceFingerprint, Long tableId) {
        String ipPart = (clientIp != null && !clientIp.isBlank() ? clientIp.trim() : "unknown");
        String devPart = (clientDeviceFingerprint != null && !clientDeviceFingerprint.isBlank() ? clientDeviceFingerprint.trim() : "DEFAULT");
        return ipPart + ":" + (tableId != null ? tableId : 0L) + ":" + devPart;
    }

    private String generateFingerprint(String clientIp, Long tableId, Integer rating, String cleanComment) {
        return (clientIp != null ? clientIp : "no-ip") + ":" +
                tableId + ":" +
                rating + ":" +
                Objects.hashCode(cleanComment != null ? cleanComment.trim() : "");
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
        if (ipHistory.size() > MAX_CAPACITY || duplicateFingerprints.size() > MAX_CAPACITY || clientTableCooldown.size() > MAX_CAPACITY) {
            Instant now = Instant.now();
            Instant ipCutoff = now.minusSeconds(ipWindowSeconds);
            ipHistory.entrySet().removeIf(e -> {
                while (!e.getValue().isEmpty() && e.getValue().peekFirst().isBefore(ipCutoff)) {
                    e.getValue().pollFirst();
                }
                return e.getValue().isEmpty();
            });

            Instant dupCutoff = now.minusSeconds(duplicateWindowSeconds);
            duplicateFingerprints.entrySet().removeIf(e -> e.getValue().isBefore(dupCutoff));

            Instant tableCutoff = now.minusSeconds(tableCooldownSeconds * 2);
            clientTableCooldown.entrySet().removeIf(e -> e.getValue().isBefore(tableCutoff));

            // Hard memory bounding if still above MAX_CAPACITY:
            // Remove non-blocked entries first, NEVER evict actively blocked IP entries (size >= maxPerIpWindow)
            if (ipHistory.size() > MAX_CAPACITY) {
                ipHistory.entrySet().removeIf(e -> e.getValue().size() < maxPerIpWindow);
            }
            while (duplicateFingerprints.size() > MAX_CAPACITY) {
                var it = duplicateFingerprints.keySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); } else { break; }
            }
            while (clientTableCooldown.size() > MAX_CAPACITY) {
                var it = clientTableCooldown.keySet().iterator();
                if (it.hasNext()) { it.next(); it.remove(); } else { break; }
            }
        }
    }

    public int getMaxCommentLength() {
        return maxCommentLength;
    }

    public void clear() {
        ipHistory.clear();
        clientTableCooldown.clear();
        duplicateFingerprints.clear();
    }

    public int getIpHistorySize() {
        return ipHistory.size();
    }

    public void injectIpHistoryForTesting(String ip, int count) {
        Deque<Instant> deque = new ConcurrentLinkedDeque<>();
        Instant now = Instant.now();
        for (int i = 0; i < count; i++) {
            deque.add(now);
        }
        ipHistory.put(ip, deque);
    }

    public int getClientTableCooldownSize() {
        return clientTableCooldown.size();
    }

    public int getDuplicateFingerprintsSize() {
        return duplicateFingerprints.size();
    }

    public void setMaxPerIpWindow(int maxPerIpWindow) {
        this.maxPerIpWindow = maxPerIpWindow;
    }

    public void setTableCooldownSeconds(long tableCooldownSeconds) {
        this.tableCooldownSeconds = tableCooldownSeconds;
    }

    public void setDuplicateWindowSeconds(long duplicateWindowSeconds) {
        this.duplicateWindowSeconds = duplicateWindowSeconds;
    }
}
