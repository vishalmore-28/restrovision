package com.restrovision.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "employees", uniqueConstraints = {
    @UniqueConstraint(name = "uq_employee_code", columnNames = {"restaurant_id", "employee_code"})
})
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "employee_code", nullable = false, length = 40)
    private String employeeCode;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "job_role", nullable = false, length = 20)
    private String jobRole; // MANAGER, WAITER, KITCHEN, CLEANING

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    public Employee() {}

    public Employee(Long restaurantId, Long userId, String employeeCode, String displayName, String jobRole, String status) {
        this.restaurantId = restaurantId;
        this.userId = userId;
        this.employeeCode = employeeCode;
        this.displayName = displayName;
        this.jobRole = jobRole;
        this.status = status;
    }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getRestaurantId() { return restaurantId; }
    public void setRestaurantId(Long restaurantId) { this.restaurantId = restaurantId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getJobRole() { return jobRole; }
    public void setJobRole(String jobRole) { this.jobRole = jobRole; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
