package com.restrovision.repository;

import com.restrovision.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByRestaurantId(Long restaurantId);
    List<MenuItem> findByRestaurantIdAndCategoryId(Long restaurantId, Long categoryId);
    List<MenuItem> findByRestaurantIdAndStationId(Long restaurantId, Long stationId);
    Optional<MenuItem> findByRestaurantIdAndItemId(Long restaurantId, Long itemId);
}
