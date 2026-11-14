package com.restrovision.controller;

import com.restrovision.domain.Employee;
import com.restrovision.repository.EmployeeRepository;
import com.restrovision.security.TenantContext;
import com.restrovision.service.WorkloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employees")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final WorkloadService workloadService;

    public EmployeeController(EmployeeRepository employeeRepository, WorkloadService workloadService) {
        this.employeeRepository = employeeRepository;
        this.workloadService = workloadService;
    }

    @GetMapping
    public ResponseEntity<List<Employee>> getEmployees() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(employeeRepository.findByRestaurantId(restaurantId));
    }

    @GetMapping("/workload")
    public ResponseEntity<List<Map<String, Object>>> getWaitersWorkload() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(workloadService.calculateWaitersWorkload(restaurantId));
    }
}
