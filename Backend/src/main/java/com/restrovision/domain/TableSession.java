package com.restrovision.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "table_sessions")
public class TableSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(nullable = false, length = 10)
    private String status = "ACTIVE"; // ACTIVE, CLOSED

    @Column(name = "opened_by", nullable = false, length = 10)
    private String openedBy = "SYSTEM"; // AI, STAFF, SYSTEM

    @Column(name = "seated_at", nullable = false)
    private Instant seatedAt = Instant.now();

    @Column(name = "attended_at")
    private Instant attendedAt;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "party_size_est")
    private Integer partySizeEst;

    @Column(name = "pre_attended_flag")
    @Convert(converter = org.hibernate.type.NumericBooleanConverter.class)
    private Boolean preAttendedFlag = false;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public TableSession() {}

    public TableSession(Long restaurantId, Long tableId, String openedBy, Integer partySizeEst) {
        this.restaurantId = restaurantId;
        this.tableId = tableId;
        this.openedBy = openedBy;
        this.partySizeEst = partySizeEst;
        this.status = "ACTIVE";
        this.seatedAt = Instant.now();
    }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOpenedBy() { return openedBy; }
    public void setOpenedBy(String openedBy) { this.openedBy = openedBy; }

    public Instant getSeatedAt() { return seatedAt; }
    public void setSeatedAt(Instant seatedAt) { this.seatedAt = seatedAt; }

    public Instant getAttendedAt() { return attendedAt; }
    public void setAttendedAt(Instant attendedAt) { this.attendedAt = attendedAt; }

    public Instant getOrderedAt() { return orderedAt; }
    public void setOrderedAt(Instant orderedAt) { this.orderedAt = orderedAt; }

    public Instant getDepartedAt() { return departedAt; }
    public void setDepartedAt(Instant departedAt) { this.departedAt = departedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public Integer getPartySizeEst() { return partySizeEst; }
    public void setPartySizeEst(Integer partySizeEst) { this.partySizeEst = partySizeEst; }

    public Boolean getPreAttendedFlag() { return preAttendedFlag; }
    public void setPreAttendedFlag(Boolean preAttendedFlag) { this.preAttendedFlag = preAttendedFlag; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
