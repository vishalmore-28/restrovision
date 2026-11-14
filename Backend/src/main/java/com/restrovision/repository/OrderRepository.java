package com.restrovision.repository;

import com.restrovision.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Order> findByRestaurantIdAndStatus(Long restaurantId, String status);
    List<Order> findByRestaurantIdAndStatusIn(Long restaurantId, List<String> statuses);
    Optional<Order> findByRestaurantIdAndOrderId(Long restaurantId, Long orderId);
    Optional<Order> findByRestaurantIdAndOrderCode(Long restaurantId, String orderCode);
    List<Order> findByRestaurantIdAndSessionId(Long restaurantId, Long sessionId);
}
