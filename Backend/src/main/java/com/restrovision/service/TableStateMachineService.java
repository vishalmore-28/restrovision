package com.restrovision.service;

import com.restrovision.domain.*;
import com.restrovision.dto.TableDtos.TableViewDto;
import com.restrovision.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TableStateMachineService {
    private static final Logger log = LoggerFactory.getLogger(TableStateMachineService.class);

    private static final Set<String> VALID_STATUSES = Set.of(
            "AVAILABLE", "OCCUPIED", "CLEANING", "RESERVED", "OUT_OF_SERVICE"
    );

    private final RestaurantTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final StaffTaskRepository taskRepository;
    private final CleaningRecordRepository cleaningRecordRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogRepository auditLogRepository;
    private final AlertService alertService;
    private final NotificationService notificationService;

    @Value("${restrovision.state-engine.staff-override-lock-seconds:120}")
    private long staffOverrideLockSeconds;

    public TableStateMachineService(
            RestaurantTableRepository tableRepository,
            TableSessionRepository sessionRepository,
            StaffTaskRepository taskRepository,
            CleaningRecordRepository cleaningRecordRepository,
            EmployeeRepository employeeRepository,
            AuditLogRepository auditLogRepository,
            AlertService alertService,
            NotificationService notificationService) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.taskRepository = taskRepository;
        this.cleaningRecordRepository = cleaningRecordRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogRepository = auditLogRepository;
        this.alertService = alertService;
        this.notificationService = notificationService;
    }

    public List<TableViewDto> getTableView(Long restaurantId) {
        List<RestaurantTable> tables = tableRepository.findByRestaurantIdOrderByTableCodeAsc(restaurantId);
        Instant now = Instant.now();

        return tables.stream().map(t -> {
            TableViewDto dto = new TableViewDto();
            dto.setTableId(t.getTableId());
            dto.setRestaurantId(t.getRestaurantId());
            dto.setTableCode(t.getTableCode());
            dto.setCapacity(t.getCapacity());
            dto.setStatus(t.getStatus());
            dto.setStatusSource(t.getStatusSource());
            dto.setStatusChangedAt(t.getStatusChangedAt());
            dto.setAssignedWaiterId(t.getAssignedWaiterId());

            if (t.getAssignedWaiterId() != null) {
                employeeRepository.findByRestaurantIdAndEmployeeId(restaurantId, t.getAssignedWaiterId())
                        .ifPresent(e -> dto.setAssignedWaiterName(e.getDisplayName()));
            }

            dto.setCurrentSessionId(t.getCurrentSessionId());
            if (t.getCurrentSessionId() != null) {
                sessionRepository.findByRestaurantIdAndSessionId(restaurantId, t.getCurrentSessionId()).ifPresent(s -> {
                    dto.setSessionSeatedAt(s.getSeatedAt());
                    dto.setSessionAttendedAt(s.getAttendedAt());
                    dto.setPartySizeEst(s.getPartySizeEst());

                    if (s.getAttendedAt() == null && s.getSeatedAt() != null) {
                        long secs = Duration.between(s.getSeatedAt(), now).getSeconds();
                        dto.setWaitingSeconds(Math.max(0, secs));
                        if (secs >= 600) {
                            dto.setWaitingAlertLevel("CRITICAL");
                        } else if (secs >= 300) {
                            dto.setWaitingAlertLevel("ESCALATED");
                        } else if (secs >= 120) {
                            dto.setWaitingAlertLevel("ATTENTION");
                        } else {
                            dto.setWaitingAlertLevel("NONE");
                        }
                    } else {
                        dto.setWaitingSeconds(0L);
                        dto.setWaitingAlertLevel("NONE");
                    }
                });
            }

            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public RestaurantTable handleAiOccupied(Long restaurantId, Long tableId, Double confidence, Instant occurredAt, Integer partySize) {
        RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        // 1. Conflict check: if staff recently overrode, lock AI out (§6.2)
        if ("STAFF".equals(table.getStatusSource())) {
            long elapsedSinceStaff = Duration.between(table.getStatusChangedAt(), Instant.now()).getSeconds();
            if (elapsedSinceStaff < staffOverrideLockSeconds) {
                log.info("Ignoring AI TABLE_OCCUPIED for table {} due to active staff lock ({}s elapsed)", tableId, elapsedSinceStaff);
                return table;
            }
        }

        // 2. Ignore OUT_OF_SERVICE tables completely
        if ("OUT_OF_SERVICE".equals(table.getStatus())) {
            log.info("Table {} is OUT_OF_SERVICE; ignoring AI occupancy observation.", tableId);
            return table;
        }

        // 3. Fix "cleaner counts as a customer" bug (§6.1 & §6.2)
        // Presence during CLEANING is assumed to be cleaning staff and ignored unless party size >= 2
        if ("CLEANING".equals(table.getStatus())) {
            if (partySize == null || partySize < 2) {
                log.info("Table {} is in CLEANING; single-person presence classified as cleaning staff, not customer.", tableId);
                return table;
            }
        }

        // 4. Handle RESERVED tables (§6.1)
        if ("RESERVED".equals(table.getStatus())) {
            // Outside reservation window -> raise unexpected occupancy alert instead of changing state
            alertService.createOrUpdateAlert(
                    restaurantId,
                    "UNEXPECTED_OCCUPANCY_RESERVED",
                    "MEDIUM",
                    "UNEXPECTED-OCC-" + tableId,
                    "Unexpected guest seating detected at RESERVED Table " + table.getTableCode(),
                    tableId,
                    "MANAGER",
                    table.getAssignedWaiterId(),
                    null
            );
            return table;
        }

        // 5. Normal transition AVAILABLE -> OCCUPIED
        if ("AVAILABLE".equals(table.getStatus()) || "CLEANING".equals(table.getStatus())) {
            // Check if there is an active session already open to prevent duplicate active sessions
            Optional<TableSession> existingActive = sessionRepository.findByRestaurantIdAndTableIdAndStatus(restaurantId, tableId, "ACTIVE");
            TableSession session;
            if (existingActive.isPresent()) {
                session = existingActive.get();
            } else {
                session = new TableSession(restaurantId, tableId, "AI", partySize != null ? partySize : 2);
                session.setSeatedAt(occurredAt != null ? occurredAt : Instant.now());
                session = sessionRepository.save(session);
            }

            table.setStatus("OCCUPIED");
            table.setStatusSource("AI");
            table.setStatusChangedAt(occurredAt != null ? occurredAt : Instant.now());
            table.setCurrentSessionId(session.getSessionId());
            table = tableRepository.save(table);

            log.info("Table {} transitioned to OCCUPIED via AI event. Session ID: {}", tableId, session.getSessionId());
            broadcastTable(table);
        }

        return table;
    }

    @Transactional
    public RestaurantTable handleAiVacated(Long restaurantId, Long tableId, Double confidence, Instant occurredAt) {
        RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        // Conflict check: if staff recently overrode, lock AI out
        if ("STAFF".equals(table.getStatusSource())) {
            long elapsedSinceStaff = Duration.between(table.getStatusChangedAt(), Instant.now()).getSeconds();
            if (elapsedSinceStaff < staffOverrideLockSeconds) {
                log.info("Ignoring AI TABLE_VACATED for table {} due to active staff lock", tableId);
                return table;
            }
        }

        if ("OCCUPIED".equals(table.getStatus())) {
            table.setStatus("CLEANING");
            table.setStatusSource("AI");
            table.setStatusChangedAt(occurredAt != null ? occurredAt : Instant.now());

            // Close active session
            if (table.getCurrentSessionId() != null) {
                sessionRepository.findByRestaurantIdAndSessionId(restaurantId, table.getCurrentSessionId()).ifPresent(s -> {
                    s.setStatus("CLOSED");
                    s.setDepartedAt(occurredAt != null ? occurredAt : Instant.now());
                    s.setClosedAt(Instant.now());
                    sessionRepository.save(s);
                });
            }
            table.setCurrentSessionId(null);

            // Automatically create Cleaning Staff Task (§4.1 F-12)
            StaffTask cleaningTask = new StaffTask();
            cleaningTask.setRestaurantId(restaurantId);
            cleaningTask.setTableId(tableId);
            cleaningTask.setTableCode(table.getTableCode());
            cleaningTask.setTaskType("TABLE_CLEANING");
            cleaningTask.setPriority("MEDIUM");
            cleaningTask.setStatus("OPEN");
            cleaningTask = taskRepository.save(cleaningTask);

            CleaningRecord cleaningRecord = new CleaningRecord();
            cleaningRecord.setRestaurantId(restaurantId);
            cleaningRecord.setTableId(tableId);
            cleaningRecord.setTaskId(cleaningTask.getTaskId());
            cleaningRecord.setStatus("PENDING");
            cleaningRecordRepository.save(cleaningRecord);

            table = tableRepository.save(table);
            log.info("Table {} transitioned to CLEANING via AI vacancy. Cleaning task #{} generated.", tableId, cleaningTask.getTaskId());
            broadcastTable(table);
            notificationService.publishTaskUpdate(restaurantId, cleaningTask);
        }

        return table;
    }

    @Transactional
    public RestaurantTable seatParty(Long restaurantId, Long tableId, Integer partySize, Long assignedWaiterId, Long actorUserId) {
        RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        // P0 State Validation: Ensure table is available before seating!
        if ("OCCUPIED".equals(table.getStatus()) || table.getCurrentSessionId() != null) {
            throw new IllegalStateException("Table " + table.getTableCode() + " is already OCCUPIED with an active session.");
        }
        if ("OUT_OF_SERVICE".equals(table.getStatus())) {
            throw new IllegalStateException("Cannot seat party: Table " + table.getTableCode() + " is OUT_OF_SERVICE.");
        }
        if ("CLEANING".equals(table.getStatus())) {
            throw new IllegalStateException("Cannot seat party: Table " + table.getTableCode() + " requires cleaning first.");
        }

        // Verify exactly one active session per table
        Optional<TableSession> existingActive = sessionRepository.findByRestaurantIdAndTableIdAndStatus(restaurantId, tableId, "ACTIVE");
        if (existingActive.isPresent()) {
            throw new IllegalStateException("Table " + table.getTableCode() + " already has an ACTIVE session: " + existingActive.get().getSessionId());
        }

        table.setStatus("OCCUPIED");
        table.setStatusSource("STAFF");
        table.setStatusChangedAt(Instant.now());
        if (assignedWaiterId != null) {
            table.setAssignedWaiterId(assignedWaiterId);
        }

        TableSession session = new TableSession(restaurantId, tableId, "STAFF", partySize != null ? partySize : 2);
        session = sessionRepository.save(session);
        table.setCurrentSessionId(session.getSessionId());

        table = tableRepository.save(table);
        recordAudit(restaurantId, actorUserId, "SEAT_PARTY", "TABLE", String.valueOf(tableId), "Party size: " + partySize);
        broadcastTable(table);
        return table;
    }

    @Transactional
    public void markTableAttended(Long restaurantId, Long sessionId, Long actorUserId) {
        TableSession session = sessionRepository.findByRestaurantIdAndSessionId(restaurantId, sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        Instant now = Instant.now();
        session.setAttendedAt(now);

        // Pre-attended flag check: if attended within 15 seconds of seating
        if (session.getSeatedAt() != null && Duration.between(session.getSeatedAt(), now).getSeconds() < 15) {
            session.setPreAttendedFlag(true);
        }
        sessionRepository.save(session);

        // Auto-resolve any open wait-time alerts for this session (§12 dedupe/auto-close rule)
        alertService.getActiveAlerts(restaurantId).stream()
                .filter(a -> a.getDedupeKey() != null && a.getDedupeKey().equals("WAITING-" + sessionId))
                .forEach(a -> alertService.resolveAlert(restaurantId, a.getAlertId(), actorUserId));

        tableRepository.findByRestaurantIdAndTableId(restaurantId, session.getTableId())
                .ifPresent(this::broadcastTable);

        recordAudit(restaurantId, actorUserId, "ATTEND_TABLE", "SESSION", String.valueOf(sessionId), "Table attended");
    }

    @Transactional
    public RestaurantTable overrideTableStatus(Long restaurantId, Long tableId, String newStatus, String reason,
                                               Long actorUserId, Set<String> actorRoles) {
        RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        // 1. Strict status validation (reject arbitrary values like "PURPLE")
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid table status: '" + newStatus + "'. Allowed values: " + VALID_STATUSES);
        }

        // 2. Role-based transition guards (§3 Permission Matrix)
        boolean isOwnerOrManager = actorRoles.contains("OWNER") || actorRoles.contains("MANAGER");
        boolean isWaiter = actorRoles.contains("WAITER");
        boolean isCleaning = actorRoles.contains("CLEANING");

        if (isCleaning && !isOwnerOrManager) {
            // Cleaning staff can ONLY transition from CLEANING to AVAILABLE
            if (!("CLEANING".equals(table.getStatus()) && "AVAILABLE".equals(newStatus))) {
                throw new AccessDeniedException("Cleaning role can only transition tables from CLEANING to AVAILABLE.");
            }
        }

        if (isWaiter && !isOwnerOrManager) {
            if ("OUT_OF_SERVICE".equals(newStatus)) {
                throw new AccessDeniedException("Waitstaff cannot mark tables OUT_OF_SERVICE. Manager role required.");
            }
        }

        String oldStatus = table.getStatus();
        table.setStatus(newStatus);
        table.setStatusSource("STAFF");
        table.setStatusChangedAt(Instant.now());

        if ("AVAILABLE".equals(newStatus) || "OUT_OF_SERVICE".equals(newStatus)) {
            if (table.getCurrentSessionId() != null) {
                sessionRepository.findByRestaurantIdAndSessionId(restaurantId, table.getCurrentSessionId()).ifPresent(s -> {
                    s.setStatus("CLOSED");
                    s.setClosedAt(Instant.now());
                    sessionRepository.save(s);
                });
                table.setCurrentSessionId(null);
            }
        }

        table = tableRepository.save(table);
        recordAudit(restaurantId, actorUserId, "STATUS_OVERRIDE", "TABLE", String.valueOf(tableId),
                "Changed from " + oldStatus + " to " + newStatus + ". Reason: " + reason);

        broadcastTable(table);
        return table;
    }

    @Transactional
    public void completeCleaningTask(Long restaurantId, Long taskId, Long completedByUserId) {
        StaffTask task = taskRepository.findByRestaurantIdAndTaskId(restaurantId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        task.setStatus("COMPLETED");
        task.setCompletedAt(Instant.now());
        taskRepository.save(task);

        // Find table and return to AVAILABLE
        if (task.getTableId() != null) {
            RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, task.getTableId())
                    .orElse(null);
            if (table != null && "CLEANING".equals(table.getStatus())) {
                table.setStatus("AVAILABLE");
                table.setStatusSource("STAFF");
                table.setStatusChangedAt(Instant.now());
                tableRepository.save(table);
                broadcastTable(table);
            }

            cleaningRecordRepository.findByRestaurantIdAndTaskId(restaurantId, taskId).ifPresent(cr -> {
                cr.setStatus("COMPLETED");
                cr.setCompletedAt(Instant.now());
                cr.setCompletedBy(completedByUserId);
                cleaningRecordRepository.save(cr);
            });
        }
    }

    private void broadcastTable(RestaurantTable table) {
        notificationService.publishTableUpdate(table.getRestaurantId(), getTableView(table.getRestaurantId()));
    }

    private void recordAudit(Long restaurantId, Long actorUserId, String action, String entityType, String entityId, String details) {
        AuditLog log = new AuditLog(restaurantId, actorUserId, action, entityType, entityId, null, "{\"details\":\"" + details + "\"}");
        auditLogRepository.save(log);
    }
}
