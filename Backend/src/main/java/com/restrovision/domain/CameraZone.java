package com.restrovision.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "camera_zones", uniqueConstraints = {
    @UniqueConstraint(name = "uq_zone_tenant", columnNames = {"restaurant_id", "zone_id"})
})
public class CameraZone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "camera_id", nullable = false)
    private Long cameraId;

    @Column(name = "zone_type", nullable = false, length = 15)
    private String zoneType; // TABLE, FLOOR, RESTRICTED, ENTRANCE, QUEUE, KITCHEN_PASS

    @Column(name = "table_id")
    private Long tableId;

    @Lob
    @Column(name = "polygon_json", nullable = false)
    private String polygonJson;

    @Column(name = "ref_width", nullable = false)
    private Integer refWidth = 1920;

    @Column(name = "ref_height", nullable = false)
    private Integer refHeight = 1080;

    @Lob
    @Column(name = "schedule_json")
    private String scheduleJson;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public CameraZone() {}

    public CameraZone(Long restaurantId, Long cameraId, String zoneType, Long tableId, String polygonJson, Integer refWidth, Integer refHeight) {
        this.restaurantId = restaurantId;
        this.cameraId = cameraId;
        this.zoneType = zoneType;
        this.tableId = tableId;
        this.polygonJson = polygonJson;
        this.refWidth = refWidth;
        this.refHeight = refHeight;
    }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public Long getCameraId() { return cameraId; }
    public void setCameraId(Long cameraId) { this.cameraId = cameraId; }

    public String getZoneType() { return zoneType; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }

    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }

    public String getPolygonJson() { return polygonJson; }
    public void setPolygonJson(String polygonJson) { this.polygonJson = polygonJson; }

    public Integer getRefWidth() { return refWidth; }
    public void setRefWidth(Integer refWidth) { this.refWidth = refWidth; }

    public Integer getRefHeight() { return refHeight; }
    public void setRefHeight(Integer refHeight) { this.refHeight = refHeight; }

    public String getScheduleJson() { return scheduleJson; }
    public void setScheduleJson(String scheduleJson) { this.scheduleJson = scheduleJson; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
