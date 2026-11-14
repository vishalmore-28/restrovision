package com.restrovision.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "kitchen_stations", uniqueConstraints = {
    @UniqueConstraint(name = "uq_station_name", columnNames = {"restaurant_id", "name"})
})
public class KitchenStation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    public KitchenStation() {}

    public KitchenStation(Long restaurantId, String name) {
        this.restaurantId = restaurantId;
        this.name = name;
    }

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
