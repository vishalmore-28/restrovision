package com.restrovision.repository;

import com.restrovision.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByRestaurantId(Long restaurantId);
    List<OrderItem> findByRestaurantIdAndStationIdAndStatusIn(Long restaurantId, Long stationId, List<String> statuses);
    List<OrderItem> findByRestaurantIdAndStatusIn(Long restaurantId, List<String> statuses);
    java.util.Optional<OrderItem> findByRestaurantIdAndOrderItemId(Long restaurantId, Long orderItemId);
}
