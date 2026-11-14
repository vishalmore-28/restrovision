package com.restrovision.repository;

import com.restrovision.domain.TableSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TableSessionRepository extends JpaRepository<TableSession, Long> {
    Optional<TableSession> findByRestaurantIdAndTableIdAndStatus(Long restaurantId, Long tableId, String status);
    Optional<TableSession> findByRestaurantIdAndSessionId(Long restaurantId, Long sessionId);
    List<TableSession> findByRestaurantIdAndStatus(Long restaurantId, String status);
    List<TableSession> findByRestaurantIdAndTableIdOrderBySeatedAtDesc(Long restaurantId, Long tableId);
    List<TableSession> findByRestaurantId(Long restaurantId);
}
