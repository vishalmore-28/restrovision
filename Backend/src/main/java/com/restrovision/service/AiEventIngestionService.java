package com.restrovision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restrovision.domain.*;
import com.restrovision.dto.AiEventEnvelopeDto;
import com.restrovision.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiEventIngestionService {
    private static final Logger log = LoggerFactory.getLogger(AiEventIngestionService.class);

    @Value("${restrovision.edge.freshness.max-event-age-seconds:7200}")
    private long maxEventAgeSeconds = 7200;

    @Value("${restrovision.edge.freshness.clock-skew-tolerance-seconds:600}")
    private long clockSkewToleranceSeconds = 600;

    private final ConcurrentHashMap<String, Instant> recentEventCache = new ConcurrentHashMap<>();

    private final AiEventRepository aiEventRepository;
    private final TableStateMachineService tableStateMachine;
    private final AlertService alertService;
    private final ServiceRequestRepository serviceRequestRepository;
    private final CameraRepository cameraRepository;
    private final RestaurantTableRepository tableRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiEventIngestionService(
            AiEventRepository aiEventRepository,
            TableStateMachineService tableStateMachine,
            AlertService alertService,
            ServiceRequestRepository serviceRequestRepository,
            CameraRepository cameraRepository,
            RestaurantTableRepository tableRepository,
            NotificationService notificationService) {
        this.aiEventRepository = aiEventRepository;
        this.tableStateMachine = tableStateMachine;
        this.alertService = alertService;
        this.serviceRequestRepository = serviceRequestRepository;
        this.cameraRepository = cameraRepository;
        this.tableRepository = tableRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Map<String, Object> ingestEvent(AiEventEnvelopeDto envelope) {
        Long restaurantId = envelope.getRestaurantId();
        if (restaurantId == null) {
            throw new IllegalArgumentException("Tenant restaurantId must not be null for AI event ingestion");
        }
        String eventId = envelope.getEventId();
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("AI eventId must not be null or blank");
        }

        String cacheKey = restaurantId + ":" + eventId;
        // 1. In-memory fast check for duplicate
        if (recentEventCache.containsKey(cacheKey)) {
            log.info("Duplicate event ignored via in-memory cache: {} for restaurant {}", eventId, restaurantId);
            return Map.of("success", true, "eventId", eventId, "status", "DUPLICATE_IGNORED");
        }

        // 2. Persistent DB check for duplicate (survives backend restarts)
        Optional<AiEvent> existing = aiEventRepository.findByRestaurantIdAndEventUid(restaurantId, eventId);
        if (existing.isPresent()) {
            recentEventCache.put(cacheKey, Instant.now());
            log.info("Duplicate event ignored via DB record: {} for restaurant {}", eventId, restaurantId);
            return Map.of("success", true, "eventId", eventId, "status", "DUPLICATE_IGNORED");
        }

        // 3. Prepare event entity
        Instant now = Instant.now();
        Instant occurredAt = envelope.getOccurredAt() != null ? envelope.getOccurredAt() : now;

        AiEvent aiEvent = new AiEvent();
        aiEvent.setRestaurantId(restaurantId);
        aiEvent.setEventUid(eventId);
        aiEvent.setSchemaVersion(envelope.getSchemaVersion() != null ? envelope.getSchemaVersion() : "1.0");
        aiEvent.setEdgeNodeId(envelope.getEdgeNodeId() != null ? envelope.getEdgeNodeId() : "edge-01");
        aiEvent.setCameraId(envelope.getCameraId());
        aiEvent.setZoneId(envelope.getZoneId());
        aiEvent.setTableId(envelope.getTableId());
        aiEvent.setEventType(envelope.getEventType());
        aiEvent.setConfidence(envelope.getConfidence());
        aiEvent.setOccurredAt(occurredAt);
        aiEvent.setDetectedAt(envelope.getDetectedAt() != null ? envelope.getDetectedAt() : now);
        aiEvent.setSnapshotRef(envelope.getSnapshotRef());

        try {
            if (envelope.getModelVersions() != null) {
                aiEvent.setModelVersions(objectMapper.writeValueAsString(envelope.getModelVersions()));
            }
            if (envelope.getData() != null) {
                aiEvent.setDataJson(objectMapper.writeValueAsString(envelope.getData()));
            }
        } catch (Exception ex) {
            log.warn("Failed to serialize modelVersions or data to JSON: {}", ex.getMessage());
        }

        // 4. Timestamp Freshness & Clock Skew Validation
        // A. Reject events far in the future (beyond clock skew tolerance)
        if (occurredAt.isAfter(now.plusSeconds(clockSkewToleranceSeconds))) {
            log.warn("AI Event {} rejected: occurredAt {} is in the future beyond {}s clock skew limit",
                    eventId, occurredAt, clockSkewToleranceSeconds);
            aiEvent.setProcessingStatus("REJECTED_CLOCK_SKEW");
            try {
                aiEventRepository.saveAndFlush(aiEvent);
                recentEventCache.put(cacheKey, now);
            } catch (DataIntegrityViolationException dive) {
                log.info("Concurrent duplicate caught for future event {}: {}", eventId, dive.getMessage());
            }
            return Map.of("success", true, "eventId", eventId, "status", "REJECTED_CLOCK_SKEW");
        }

        // B. Reject stale telemetry exceeding maximum retry/shift window (e.g. > 2 hours)
        if (occurredAt.isBefore(now.minusSeconds(maxEventAgeSeconds))) {
            log.warn("AI Event {} rejected: occurredAt {} is stale (exceeds max event age of {}s)",
                    eventId, occurredAt, maxEventAgeSeconds);
            aiEvent.setProcessingStatus("REJECTED_STALE");
            try {
                aiEventRepository.saveAndFlush(aiEvent);
                recentEventCache.put(cacheKey, now);
            } catch (DataIntegrityViolationException dive) {
                log.info("Concurrent duplicate caught for stale event {}: {}", eventId, dive.getMessage());
            }
            return Map.of("success", true, "eventId", eventId, "status", "REJECTED_STALE");
        }

        // 5. Atomic DB insert catching concurrent unique-key collisions
        try {
            aiEvent = aiEventRepository.saveAndFlush(aiEvent);
            recentEventCache.put(cacheKey, now);
            if (recentEventCache.size() > 5000) {
                Instant cutoff = now.minusSeconds(maxEventAgeSeconds);
                recentEventCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            }
        } catch (DataIntegrityViolationException ex) {
            recentEventCache.put(cacheKey, now);
            log.info("Concurrent duplicate event caught via DB constraint: {} for restaurant {}", eventId, restaurantId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("eventId", eventId);
            resp.put("status", "DUPLICATE_IGNORED");
            return resp;
        }

        final Long eventPk = aiEvent.getAiEventPk();

        // 6. Confidence threshold verification (§1.2 AI emits evidence, not truth)
        Double confidence = envelope.getConfidence() != null ? envelope.getConfidence() : 0.0;
        if (confidence < 0.70) {
            log.warn("AI Event {} rejected: confidence {} below 0.70 gate threshold", eventId, confidence);
            aiEvent.setProcessingStatus("REJECTED_BY_RULE");
            aiEventRepository.save(aiEvent);
            return Map.of("success", true, "eventId", eventId, "status", "REJECTED_BY_RULE");
        }

        // 4. Apply business rules (§7.3)
        String eventType = envelope.getEventType();
        Long tableId = envelope.getTableId();

        if ("TABLE_OCCUPIED".equals(eventType) && tableId != null) {
            Integer partySize = null;
            if (envelope.getData() != null && envelope.getData().containsKey("estimatedPartySize")) {
                Object pSizeObj = envelope.getData().get("estimatedPartySize");
                if (pSizeObj instanceof Number) {
                    partySize = ((Number) pSizeObj).intValue();
                }
            }
            tableStateMachine.handleAiOccupied(restaurantId, tableId, confidence, envelope.getOccurredAt(), partySize);
        } else if ("TABLE_VACATED".equals(eventType) && tableId != null) {
            tableStateMachine.handleAiVacated(restaurantId, tableId, confidence, envelope.getOccurredAt());
        } else if ("POSSIBLE_SERVICE_REQUEST".equals(eventType)) {
            String tableCode = "Unknown";
            Long assignedWaiterId = null;
            if (tableId != null) {
                RestaurantTable tbl = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId).orElse(null);
                if (tbl != null) {
                    tableCode = tbl.getTableCode();
                    assignedWaiterId = tbl.getAssignedWaiterId();
                }
            }

            // Create service request
            ServiceRequest sr = new ServiceRequest();
            sr.setRestaurantId(restaurantId);
            sr.setTableId(tableId);
            sr.setTableCode(tableCode);
            sr.setRequestType("CALL_WAITER");
            sr.setSource("AI_ADVISORY");
            sr.setStatus("OPEN");
            sr.setAssignedEmployeeId(assignedWaiterId);
            serviceRequestRepository.save(sr);

            alertService.createOrUpdateAlert(
                    restaurantId,
                    "SERVICE_REQUEST_GESTURE",
                    "MEDIUM",
                    "SR-GESTURE-" + tableId,
                    "Customer wave / gesture detected at " + tableCode + " (Confidence: " + Math.round(confidence * 100) + "%)",
                    tableId,
                    "WAITER",
                    assignedWaiterId,
                    eventPk
            );
        } else if ("POSSIBLE_FLOOR_HAZARD".equals(eventType)) {
            alertService.createOrUpdateAlert(
                    restaurantId,
                    "FLOOR_HAZARD",
                    "HIGH",
                    "HAZARD-ZONE-" + envelope.getZoneId(),
                    "Floor hazard/spill detected in camera zone " + (envelope.getZoneId() != null ? envelope.getZoneId() : "floor") + " (Advisory)",
                    null,
                    "CLEANING",
                    null,
                    eventPk
            );
        } else if ("POSSIBLE_FALL".equals(eventType)) {
            alertService.createOrUpdateAlert(
                    restaurantId,
                    "FALL_DETECTION",
                    "CRITICAL",
                    "FALL-ZONE-" + envelope.getZoneId(),
                    "CRITICAL EMERGENCY: Possible fall detected in camera zone " + envelope.getZoneId() + "! Immediate assistance required.",
                    null,
                    "MANAGER",
                    null,
                    eventPk
            );
        } else if ("RESTRICTED_AREA_ENTRY".equals(eventType)) {
            alertService.createOrUpdateAlert(
                    restaurantId,
                    "RESTRICTED_ENTRY",
                    "HIGH",
                    "RESTRICTED-ZONE-" + envelope.getZoneId(),
                    "Security Notice: Person detected entering restricted zone " + envelope.getZoneId(),
                    null,
                    "MANAGER",
                    null,
                    eventPk
            );
        } else if ("CAMERA_STATE_CHANGED".equals(eventType) && envelope.getCameraId() != null) {
            cameraRepository.findByRestaurantIdAndCameraId(restaurantId, envelope.getCameraId()).ifPresent(cam -> {
                if (envelope.getData() != null && envelope.getData().containsKey("state")) {
                    String state = String.valueOf(envelope.getData().get("state"));
                    cam.setState(state);
                    cam.setLastSeenAt(Instant.now());
                    cameraRepository.save(cam);

                    if ("DISCONNECTED".equals(state) || "ERROR".equals(state)) {
                        alertService.createOrUpdateAlert(
                                restaurantId,
                                "CAMERA_DISCONNECTED",
                                "MEDIUM",
                                "CAM-DISCONNECTED-" + cam.getCameraId(),
                                "Camera '" + cam.getName() + "' disconnected or signal lost.",
                                null,
                                "MANAGER",
                                null,
                                eventPk
                        );
                    }
                }
            });
        }

        aiEvent.setProcessingStatus("APPLIED");
        aiEvent = aiEventRepository.save(aiEvent);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("eventId", eventId);
        resp.put("status", "ACCEPTED");
        return resp;
    }
}
