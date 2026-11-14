package com.restrovision.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ai_events", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ai_event", columnNames = {"restaurant_id", "event_uid"})
})
public class AiEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_event_pk")
    private Long aiEventPk;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "event_uid", nullable = false, length = 40)
    private String eventUid;

    @Column(name = "schema_version", nullable = false, length = 10)
    private String schemaVersion = "1.0";

    @Column(name = "edge_node_id", nullable = false, length = 60)
    private String edgeNodeId;

    @Column(name = "camera_id")
    private Long cameraId;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "table_id")
    private Long tableId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "confidence")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NUMERIC)
    private Double confidence;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Lob
    @Column(name = "model_versions")
    private String modelVersions;

    @Lob
    @Column(name = "data_json")
    private String dataJson;

    @Column(name = "snapshot_ref", length = 500)
    private String snapshotRef;

    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = "RECEIVED"; // RECEIVED, APPLIED, REJECTED_BY_RULE, STALE, DEAD

    public AiEvent() {}

    public Long getAiEventPk() { return aiEventPk; }
    public void setAiEventPk(Long aiEventPk) { this.aiEventPk = aiEventPk; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public String getEventUid() { return eventUid; }
    public void setEventUid(String eventUid) { this.eventUid = eventUid; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getEdgeNodeId() { return edgeNodeId; }
    public void setEdgeNodeId(String edgeNodeId) { this.edgeNodeId = edgeNodeId; }

    public Long getCameraId() { return cameraId; }
    public void setCameraId(Long cameraId) { this.cameraId = cameraId; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public String getModelVersions() { return modelVersions; }
    public void setModelVersions(String modelVersions) { this.modelVersions = modelVersions; }

    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }

    public String getSnapshotRef() { return snapshotRef; }
    public void setSnapshotRef(String snapshotRef) { this.snapshotRef = snapshotRef; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
}
