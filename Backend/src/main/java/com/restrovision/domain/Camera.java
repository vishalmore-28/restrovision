package com.restrovision.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cameras", uniqueConstraints = {
    @UniqueConstraint(name = "uq_camera_tenant", columnNames = {"restaurant_id", "camera_id"})
})
public class Camera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "camera_id")
    private Long cameraId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "edge_node_id", nullable = false, length = 60)
    private String edgeNodeId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "location_desc", length = 200)
    private String locationDesc;

    @Column(name = "credential_ref", length = 200)
    private String credentialRef;

    @Column(nullable = false, length = 15)
    private String state = "CONNECTED"; // CONNECTED, DISCONNECTED, RECONNECTING, MAINTENANCE, ERROR

    @Column(name = "last_seen_at")
    private Instant lastSeenAt = Instant.now();

    @Column(name = "target_fps", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NUMERIC)
    private Double targetFps = 2.0;

    public Camera() {}

    public Camera(Long restaurantId, String edgeNodeId, String name, String locationDesc, Double targetFps) {
        this.restaurantId = restaurantId;
        this.edgeNodeId = edgeNodeId;
        this.name = name;
        this.locationDesc = locationDesc;
        this.targetFps = targetFps;
        this.state = "CONNECTED";
        this.lastSeenAt = Instant.now();
    }

    public Long getCameraId() { return cameraId; }
    public void setCameraId(Long cameraId) { this.cameraId = cameraId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public String getEdgeNodeId() { return edgeNodeId; }
    public void setEdgeNodeId(String edgeNodeId) { this.edgeNodeId = edgeNodeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocationDesc() { return locationDesc; }
    public void setLocationDesc(String locationDesc) { this.locationDesc = locationDesc; }

    public String getCredentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Double getTargetFps() { return targetFps; }
    public void setTargetFps(Double targetFps) { this.targetFps = targetFps; }
}
