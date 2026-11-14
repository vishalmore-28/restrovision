package com.restrovision.repository;

import com.restrovision.domain.CleaningRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CleaningRecordRepository extends JpaRepository<CleaningRecord, Long> {
    List<CleaningRecord> findByRestaurantId(Long restaurantId);
    Optional<CleaningRecord> findByRestaurantIdAndTaskId(Long restaurantId, Long taskId);
    Optional<CleaningRecord> findByRestaurantIdAndTableIdAndStatus(Long restaurantId, Long tableId, String status);
}
