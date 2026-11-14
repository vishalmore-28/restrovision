package com.restrovision.repository;

import com.restrovision.domain.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByRestaurantId(Long restaurantId);

    @EntityGraph(attributePaths = {"order"})
    List<OrderItem> findByRestaurantIdAndStationIdAndStatusIn(Long restaurantId, Long stationId, List<String> statuses);

    @EntityGraph(attributePaths = {"order"})
    List<OrderItem> findByRestaurantIdAndStatusIn(Long restaurantId, List<String> statuses);

    Optional<OrderItem> findByRestaurantIdAndOrderItemId(Long restaurantId, Long orderItemId);
}
