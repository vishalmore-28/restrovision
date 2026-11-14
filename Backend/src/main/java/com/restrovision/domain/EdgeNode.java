package com.restrovision.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "edge_nodes")
public class EdgeNode {
    @Id
    @Column(name = "edge_node_id", length = 60)
    private String edgeNodeId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String status = "ONLINE"; // ONLINE, OFFLINE, DEGRADED

    @Column(name = "last_seen_at")
    private Instant lastSeenAt = Instant.now();

    public EdgeNode() {}

    public EdgeNode(String edgeNodeId, Long restaurantId, String displayName, String status) {
        this.edgeNodeId = edgeNodeId;
        this.restaurantId = restaurantId;
        this.displayName = displayName;
        this.status = status;
        this.lastSeenAt = Instant.now();
    }

    public String getEdgeNodeId() { return edgeNodeId; }
    public void setEdgeNodeId(String edgeNodeId) { this.edgeNodeId = edgeNodeId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
