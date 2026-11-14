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
    private final ConcurrentHashMap<Long, Instant> tableLastSubmission = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> duplicateFingerprints = new ConcurrentHashMap<>();

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
        Instant now = Instant.now();

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

        // 2. Table Cooldown Window (prevents rapid automated bursts targeting the same table)
        if (tableId != null) {
            Instant lastTableTime = tableLastSubmission.get(tableId);
            if (lastTableTime != null) {
                long elapsed = Duration.between(lastTableTime, now).getSeconds();
                if (elapsed < tableCooldownSeconds) {
                    long remaining = tableCooldownSeconds - elapsed;
                    log.warn("Feedback flooding: Table {} submission within cooldown ({}s < {}s)", tableId, elapsed, tableCooldownSeconds);
                    return new FeedbackLimitResult(true, "Feedback was recently submitted for this table. Please wait a moment.", remaining);
                }
            }
        }

        // 3. Duplicate Submission Detection
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

        return new FeedbackLimitResult(false, null, 0);
    }

    public void recordSubmission(String clientIp, Long tableId, Integer rating, String cleanComment) {
        Instant now = Instant.now();
        if (clientIp != null && !clientIp.isBlank()) {
            ipHistory.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>()).addLast(now);
        }
        if (tableId != null) {
            tableLastSubmission.put(tableId, now);
        }
        String fingerprint = generateFingerprint(clientIp, tableId, rating, cleanComment);
        duplicateFingerprints.put(fingerprint, now);

        cleanOldEntries();
    }

    private String generateFingerprint(String clientIp, Long tableId, Integer rating, String cleanComment) {
        return (clientIp != null ? clientIp : "no-ip") + ":" +
                tableId + ":" +
                rating + ":" +
                Objects.hashCode(cleanComment != null ? cleanComment.trim() : "");
    }

    private void cleanOldEntries() {
        if (ipHistory.size() > 5000 || duplicateFingerprints.size() > 5000 || tableLastSubmission.size() > 2000) {
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
            tableLastSubmission.entrySet().removeIf(e -> e.getValue().isBefore(tableCutoff));
        }
    }

    public int getMaxCommentLength() {
        return maxCommentLength;
    }

    public void clear() {
        ipHistory.clear();
        tableLastSubmission.clear();
        duplicateFingerprints.clear();
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
