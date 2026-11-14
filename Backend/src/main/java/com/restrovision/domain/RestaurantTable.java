package com.restrovision.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "restaurant_tables", uniqueConstraints = {
    @UniqueConstraint(name = "uq_table_code", columnNames = {"restaurant_id", "table_code"})
})
public class RestaurantTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "table_id")
    private Long tableId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "table_code", nullable = false, length = 20)
    private String tableCode;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE"; // AVAILABLE, OCCUPIED, CLEANING, RESERVED, OUT_OF_SERVICE

    @Column(name = "status_source", nullable = false, length = 10)
    private String statusSource = "SYSTEM"; // AI, STAFF, SYSTEM

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt = Instant.now();

    @Column(name = "assigned_waiter_id")
    private Long assignedWaiterId;

    @Column(name = "current_session_id")
    private Long currentSessionId;

    @Column(name = "qr_token", length = 64)
    private String qrToken = java.util.UUID.randomUUID().toString();

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public RestaurantTable() {}

    public RestaurantTable(Long restaurantId, String tableCode, Integer capacity, String status) {
        this.restaurantId = restaurantId;
        this.tableCode = tableCode;
        this.capacity = capacity;
        this.status = status;
        this.statusSource = "SYSTEM";
        this.statusChangedAt = Instant.now();
    }

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

    public Long getCurrentSessionId() { return currentSessionId; }
    public void setCurrentSessionId(Long currentSessionId) { this.currentSessionId = currentSessionId; }

    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
