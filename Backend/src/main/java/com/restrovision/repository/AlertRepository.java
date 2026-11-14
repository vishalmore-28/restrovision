package com.restrovision.repository;

import com.restrovision.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<Alert> findByRestaurantIdAndStatusInOrderByCreatedAtDesc(Long restaurantId, List<String> statuses);
    Optional<Alert> findByRestaurantIdAndAlertId(Long restaurantId, Long alertId);
    Optional<Alert> findByRestaurantIdAndDedupeKeyAndStatusIn(Long restaurantId, String dedupeKey, List<String> statuses);
}
