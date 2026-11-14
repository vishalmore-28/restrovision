package com.restrovision.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_feedback_labels")
public class AiFeedbackLabel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_label_id")
    private Long feedbackLabelId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "ai_event_pk")
    private Long aiEventPk;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "table_id")
    private Long tableId;

    @Column(name = "feedback_type", nullable = false, length = 40)
    private String feedbackType; // FALSE_ALARM, WRONG_TABLE_STATUS, MISSED_DETECTION

    @Column(name = "reported_by_user_id")
    private Long reportedByUserId;

    @Column(name = "user_comment", length = 500)
    private String userComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AiFeedbackLabel() {}

    public AiFeedbackLabel(Long restaurantId, Long aiEventPk, Long alertId, Long tableId, String feedbackType, Long reportedByUserId, String userComment) {
        this.restaurantId = restaurantId;
        this.aiEventPk = aiEventPk;
        this.alertId = alertId;
        this.tableId = tableId;
        this.feedbackType = feedbackType;
        this.reportedByUserId = reportedByUserId;
        this.userComment = userComment;
        this.createdAt = Instant.now();
    }

    public Long getFeedbackLabelId() { return feedbackLabelId; }
    public void setFeedbackLabelId(Long feedbackLabelId) { this.feedbackLabelId = feedbackLabelId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public Long getAiEventPk() { return aiEventPk; }
    public void setAiEventPk(Long aiEventPk) { this.aiEventPk = aiEventPk; }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }

    public Long getReportedByUserId() { return reportedByUserId; }
    public void setReportedByUserId(Long reportedByUserId) { this.reportedByUserId = reportedByUserId; }

    public String getUserComment() { return userComment; }
    public void setUserComment(String userComment) { this.userComment = userComment; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
