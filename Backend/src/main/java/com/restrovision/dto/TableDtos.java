package com.restrovision.dto;

import java.time.Instant;

public class TableDtos {
    public static class TableStatusUpdateRequest {
        private String status; // AVAILABLE, OCCUPIED, CLEANING, RESERVED, OUT_OF_SERVICE
        private String reason; // Staff explanation for audit log

        public TableStatusUpdateRequest() {}
        public TableStatusUpdateRequest(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class SeatPartyRequest {
        private Integer partySize = 2;
        private Long assignedWaiterId;

        public SeatPartyRequest() {}
        public Integer getPartySize() { return partySize; }
        public void setPartySize(Integer partySize) { this.partySize = partySize; }
        public Long getAssignedWaiterId() { return assignedWaiterId; }
        public void setAssignedWaiterId(Long assignedWaiterId) { this.assignedWaiterId = assignedWaiterId; }
    }

    public static class AssignWaiterRequest {
        private Long waiterId;
        public AssignWaiterRequest() {}
        public AssignWaiterRequest(Long waiterId) { this.waiterId = waiterId; }
        public Long getWaiterId() { return waiterId; }
        public void setWaiterId(Long waiterId) { this.waiterId = waiterId; }
    }

    public static class TableViewDto {
        private Long tableId;
        private Long restaurantId;
        private String tableCode;
        private Integer capacity;
        private String status;
        private String statusSource;
        private Instant statusChangedAt;
        private Long assignedWaiterId;
        private String assignedWaiterName;
        private Long currentSessionId;
        private Instant sessionSeatedAt;
        private Instant sessionAttendedAt;
        private Long waitingSeconds;
        private String waitingAlertLevel; // NONE, ATTENTION, ESCALATED, CRITICAL
        private Integer partySizeEst;

        public TableViewDto() {}

        public Long getTableId() { return tableId; }
        public void setTableId(Long tableId) { this.tableId = tableId; }
        public Long getRestaurantId() { return restaurantId; }
        public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }
        public String getTableCode() { return tableCode; }
        public void setTableCode(String tableCode) { this.tableCode = tableCode; }
        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusSource() { return statusSource; }
        public void setStatusSource(String statusSource) { this.statusSource = statusSource; }
        public Instant getStatusChangedAt() { return statusChangedAt; }
        public void setStatusChangedAt(Instant statusChangedAt) { this.statusChangedAt = statusChangedAt; }
        public Long getAssignedWaiterId() { return assignedWaiterId; }
        public void setAssignedWaiterId(Long assignedWaiterId) { this.assignedWaiterId = assignedWaiterId; }
        public String getAssignedWaiterName() { return assignedWaiterName; }
        public void setAssignedWaiterName(String assignedWaiterName) { this.assignedWaiterName = assignedWaiterName; }
        public Long getCurrentSessionId() { return currentSessionId; }
        public void setCurrentSessionId(Long currentSessionId) { this.currentSessionId = currentSessionId; }
        public Instant getSessionSeatedAt() { return sessionSeatedAt; }
        public void setSessionSeatedAt(Instant sessionSeatedAt) { this.sessionSeatedAt = sessionSeatedAt; }
        public Instant getSessionAttendedAt() { return sessionAttendedAt; }
        public void setSessionAttendedAt(Instant sessionAttendedAt) { this.sessionAttendedAt = sessionAttendedAt; }
        public Long getWaitingSeconds() { return waitingSeconds; }
        public void setWaitingSeconds(Long waitingSeconds) { this.waitingSeconds = waitingSeconds; }
        public String getWaitingAlertLevel() { return waitingAlertLevel; }
        public void setWaitingAlertLevel(String waitingAlertLevel) { this.waitingAlertLevel = waitingAlertLevel; }
        public Integer getPartySizeEst() { return partySizeEst; }
        public void setPartySizeEst(Integer partySizeEst) { this.partySizeEst = partySizeEst; }
    }
}
