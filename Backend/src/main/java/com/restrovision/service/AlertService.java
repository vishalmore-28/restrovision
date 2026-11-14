package com.restrovision.service;

import com.restrovision.domain.AiFeedbackLabel;
import com.restrovision.domain.Alert;
import com.restrovision.repository.AiFeedbackLabelRepository;
import com.restrovision.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private static final Map<String, Integer> PRIORITY_RANKS = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3,
            "CRITICAL", 4
    );

    private final AlertRepository alertRepository;
    private final AiFeedbackLabelRepository feedbackLabelRepository;
    private final NotificationService notificationService;

    public AlertService(AlertRepository alertRepository,
                        AiFeedbackLabelRepository feedbackLabelRepository,
                        NotificationService notificationService) {
        this.alertRepository = alertRepository;
        this.feedbackLabelRepository = feedbackLabelRepository;
        this.notificationService = notificationService;
    }

    public List<Alert> getActiveAlerts(Long restaurantId) {
        return alertRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(
                restaurantId, List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"));
    }

    public List<Alert> getAllAlerts(Long restaurantId) {
        return alertRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    @Transactional
    public Alert createOrUpdateAlert(Long restaurantId, String alertType, String priority, String dedupeKey,
                                     String message, Long tableId, String assignedRole, Long assignedEmployeeId, Long aiEventPk) {
        // 1. Check for an active open alert with this dedupe key
        Optional<Alert> existing = alertRepository.findByRestaurantIdAndDedupeKeyAndStatusIn(
                restaurantId, dedupeKey, List.of("OPEN", "ACKNOWLEDGED", "ESCALATED"));

        if (existing.isPresent()) {
            Alert alert = existing.get();
            int currentRank = PRIORITY_RANKS.getOrDefault(alert.getPriority(), 1);
            int newRank = PRIORITY_RANKS.getOrDefault(priority, 1);

            // Escalation fix: elevate when new priority is higher (e.g. MEDIUM -> HIGH -> CRITICAL)
            if (newRank > currentRank) {
                alert.setPriority(priority);
                alert.setStatus("ESCALATED");
                alert.setEscalationLevel(alert.getEscalationLevel() + 1);
                alert.setMessage(message);
                if (assignedRole != null) alert.setAssignedRole(assignedRole);
                if (assignedEmployeeId != null) alert.setAssignedEmployeeId(assignedEmployeeId);

                alert = alertRepository.save(alert);
                log.info("Alert #{} escalated to {} (Level: {})", alert.getAlertId(), priority, alert.getEscalationLevel());
                notificationService.publishAlert(restaurantId, alert);
            }
            return alert;
        }

        // 2. Fix respawn bug: If this dedupe key was already resolved, do NOT respawn it
        Optional<Alert> resolvedRecently = alertRepository.findByRestaurantIdAndDedupeKeyAndStatusIn(
                restaurantId, dedupeKey, List.of("RESOLVED", "FALSE_ALARM"));
        if (resolvedRecently.isPresent()) {
            return resolvedRecently.get();
        }

        // 3. Create fresh alert
        Alert alert = new Alert();
        alert.setRestaurantId(restaurantId);
        alert.setAlertType(alertType);
        alert.setPriority(priority);
        alert.setStatus("OPEN");
        alert.setDedupeKey(dedupeKey);
        alert.setMessage(message);
        alert.setTableId(tableId);
        alert.setAssignedRole(assignedRole);
        alert.setAssignedEmployeeId(assignedEmployeeId);
        alert.setAiEventPk(aiEventPk);
        alert.setCreatedAt(Instant.now());

        alert = alertRepository.save(alert);
        log.info("New alert created: [{}] {} (Priority: {})", alert.getAlertId(), alert.getMessage(), alert.getPriority());
        notificationService.publishAlert(restaurantId, alert);
        return alert;
    }

    @Transactional
    public Alert acknowledgeAlert(Long restaurantId, Long alertId, Long userId) {
        Alert alert = alertRepository.findByRestaurantIdAndAlertId(restaurantId, alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        if ("RESOLVED".equals(alert.getStatus()) || "FALSE_ALARM".equals(alert.getStatus())) {
            throw new IllegalStateException("Cannot acknowledge an already resolved alert.");
        }

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedAt(Instant.now());
        alert.setAcknowledgedBy(userId);
        alert = alertRepository.save(alert);

        notificationService.publishAlert(restaurantId, alert);
        return alert;
    }

    @Transactional
    public Alert resolveAlert(Long restaurantId, Long alertId, Long userId) {
        Alert alert = alertRepository.findByRestaurantIdAndAlertId(restaurantId, alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(userId);
        alert = alertRepository.save(alert);

        notificationService.publishAlert(restaurantId, alert);
        return alert;
    }

    @Transactional
    public Alert markFalseAlarm(Long restaurantId, Long alertId, Long userId, String comment) {
        Alert alert = alertRepository.findByRestaurantIdAndAlertId(restaurantId, alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        alert.setStatus("FALSE_ALARM");
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(userId);
        alert = alertRepository.save(alert);

        // Record feedback label for self-trained ML model continuous retraining (§8.3.5)
        AiFeedbackLabel label = new AiFeedbackLabel(
                restaurantId,
                alert.getAiEventPk(),
                alert.getAlertId(),
                alert.getTableId(),
                "FALSE_ALARM",
                userId,
                comment != null ? comment : "Marked false alarm by staff"
        );
        feedbackLabelRepository.save(label);
        log.info("False alarm recorded for alert #{}. Labeled training example persisted for ML loop.", alertId);

        notificationService.publishAlert(restaurantId, alert);
        return alert;
    }
}
