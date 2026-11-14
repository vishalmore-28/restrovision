package com.restrovision.controller;

import com.restrovision.domain.StaffTask;
import com.restrovision.dto.TaskDtos.*;
import com.restrovision.repository.StaffTaskRepository;
import com.restrovision.security.CustomUserDetails;
import com.restrovision.security.TenantContext;
import com.restrovision.service.NotificationService;
import com.restrovision.service.TableStateMachineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final StaffTaskRepository taskRepository;
    private final TableStateMachineService tableStateMachine;
    private final NotificationService notificationService;

    public TaskController(StaffTaskRepository taskRepository,
                          TableStateMachineService tableStateMachine,
                          NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.tableStateMachine = tableStateMachine;
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'CLEANING')")
    public ResponseEntity<List<StaffTask>> getTasks() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(taskRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<StaffTask> createTask(@RequestBody CreateTaskRequest request) {
        Long restaurantId = TenantContext.getRestaurantId();
        StaffTask task = new StaffTask();
        task.setRestaurantId(restaurantId);
        task.setTableId(request.getTableId());
        task.setTaskType(request.getTaskType());
        task.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");
        task.setAssignedEmployeeId(request.getAssignedEmployeeId());
        task.setStatus("OPEN");
        task = taskRepository.save(task);

        notificationService.publishTaskUpdate(restaurantId, task);
        return ResponseEntity.ok(task);
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<StaffTask> assignTask(
            @PathVariable Long id,
            @RequestBody AssignTaskRequest request) {
        Long restaurantId = TenantContext.getRestaurantId();
        StaffTask task = taskRepository.findByRestaurantIdAndTaskId(restaurantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        task.setAssignedEmployeeId(request.getEmployeeId());
        task.setStatus("ASSIGNED");
        task = taskRepository.save(task);

        notificationService.publishTaskUpdate(restaurantId, task);
        return ResponseEntity.ok(task);
    }

    @PutMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CLEANING', 'WAITER')")
    public ResponseEntity<StaffTask> startTask(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        StaffTask task = taskRepository.findByRestaurantIdAndTaskId(restaurantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));

        task.setStatus("IN_PROGRESS");
        task = taskRepository.save(task);

        notificationService.publishTaskUpdate(restaurantId, task);
        return ResponseEntity.ok(task);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'CLEANING', 'WAITER')")
    public ResponseEntity<StaffTask> completeTask(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long userId = userDetails != null ? userDetails.getUserId() : 1L;

        tableStateMachine.completeCleaningTask(restaurantId, id, userId);

        StaffTask task = taskRepository.findByRestaurantIdAndTaskId(restaurantId, id).orElseThrow();
        return ResponseEntity.ok(task);
    }
}
