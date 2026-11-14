package com.restrovision.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
public class MenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "station_id")
    private Long stationId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "expected_prep_minutes", nullable = false)
    private Integer expectedPrepMinutes = 15;

    @Column(nullable = false, length = 1)
    private String available = "Y";

    public MenuItem() {}

    public MenuItem(Long restaurantId, Long categoryId, Long stationId, String name, String description, BigDecimal price, Integer expectedPrepMinutes) {
        this.restaurantId = restaurantId;
        this.categoryId = categoryId;
        this.stationId = stationId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.expectedPrepMinutes = expectedPrepMinutes;
        this.available = "Y";
    }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getExpectedPrepMinutes() { return expectedPrepMinutes; }
    public void setExpectedPrepMinutes(Integer expectedPrepMinutes) { this.expectedPrepMinutes = expectedPrepMinutes; }

    public String getAvailable() { return available; }
    public void setAvailable(String available) { this.available = available; }
}
