package com.restrovision.repository;

import com.restrovision.domain.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);
    List<ServiceRequest> findByRestaurantIdAndStatus(Long restaurantId, String status);
    List<ServiceRequest> findByRestaurantIdAndTableIdAndStatus(Long restaurantId, Long tableId, String status);
    List<ServiceRequest> findByRestaurantIdAndTableIdAndStatusIn(Long restaurantId, Long tableId, List<String> statuses);
    List<ServiceRequest> findByRestaurantIdAndTableId(Long restaurantId, Long tableId);
    List<ServiceRequest> findByRestaurantIdAndStatusIn(Long restaurantId, List<String> statuses);
    Optional<ServiceRequest> findByRestaurantIdAndServiceRequestId(Long restaurantId, Long id);
    List<ServiceRequest> findByRestaurantIdAndAssignedEmployeeIdAndStatus(Long restaurantId, Long employeeId, String status);
}
