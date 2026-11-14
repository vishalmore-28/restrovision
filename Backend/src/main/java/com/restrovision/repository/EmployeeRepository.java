package com.restrovision.repository;

import com.restrovision.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByRestaurantId(Long restaurantId);
    Optional<Employee> findByRestaurantIdAndEmployeeId(Long restaurantId, Long employeeId);
    List<Employee> findByRestaurantIdAndEmployeeIdIn(Long restaurantId, java.util.Collection<Long> employeeIds);
    List<Employee> findByRestaurantIdAndJobRole(Long restaurantId, String jobRole);
    Optional<Employee> findByRestaurantIdAndUserId(Long restaurantId, Long userId);
    Optional<Employee> findByRestaurantIdAndEmployeeCode(Long restaurantId, String employeeCode);
}
