package com.restrovision.repository;

import com.restrovision.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<AuditLog> findTop100ByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
}
