package com.restrovision.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_categories", uniqueConstraints = {
    @UniqueConstraint(name = "uq_category_name", columnNames = {"restaurant_id", "name"})
})
public class MenuCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false, length = 1)
    private String active = "Y";

    public MenuCategory() {}

    public MenuCategory(Long restaurantId, String name, Integer displayOrder) {
        this.restaurantId = restaurantId;
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = "Y";
    }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getActive() { return active; }
    public void setActive(String active) { this.active = active; }
}
