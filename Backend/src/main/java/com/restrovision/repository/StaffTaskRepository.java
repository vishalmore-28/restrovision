package com.restrovision.repository;

import com.restrovision.domain.StaffTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffTaskRepository extends JpaRepository<StaffTask, Long> {
    List<StaffTask> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<StaffTask> findByRestaurantIdAndStatus(Long restaurantId, String status);
    List<StaffTask> findByRestaurantIdAndStatusIn(Long restaurantId, List<String> statuses);
    Optional<StaffTask> findByRestaurantIdAndTaskId(Long restaurantId, Long taskId);
    List<StaffTask> findByRestaurantIdAndAssignedEmployeeIdAndStatusIn(Long restaurantId, Long employeeId, List<String> statuses);
}
