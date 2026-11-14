package com.restrovision.repository;

import com.restrovision.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<Order> findByRestaurantIdAndStatus(Long restaurantId, String status);

    List<Order> findByRestaurantIdAndStatusIn(Long restaurantId, List<String> statuses);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByRestaurantIdAndOrderId(Long restaurantId, Long orderId);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByRestaurantIdAndOrderCode(Long restaurantId, String orderCode);

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByRestaurantIdAndSessionId(Long restaurantId, Long sessionId);
}
