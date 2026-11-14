package com.restrovision.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiEventEnvelopeDto {
    private String schemaVersion = "1.0";
    private String eventId;
    private Long restaurantId;
    private String edgeNodeId;
    private Long cameraId;
    private Long zoneId;
    private Long tableId;
    private String eventType;
    private Double confidence;
    private Instant occurredAt;
    private Instant detectedAt;
    private Map<String, String> modelVersions;
    private Map<String, Object> data;
    private String snapshotRef;

    public AiEventEnvelopeDto() {}

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

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

    public Map<String, String> getModelVersions() { return modelVersions; }
    public void setModelVersions(Map<String, String> modelVersions) { this.modelVersions = modelVersions; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getSnapshotRef() { return snapshotRef; }
    public void setSnapshotRef(String snapshotRef) { this.snapshotRef = snapshotRef; }
}
