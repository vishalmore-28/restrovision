package com.restrovision.controller;

import com.restrovision.domain.Alert;
import com.restrovision.dto.FeedbackDtos.FalseAlarmRequest;
import com.restrovision.security.CustomUserDetails;
import com.restrovision.security.TenantContext;
import com.restrovision.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'KITCHEN', 'CLEANING')")
    public ResponseEntity<List<Alert>> getActiveAlerts() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(alertService.getActiveAlerts(restaurantId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<List<Alert>> getAllAlerts() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(alertService.getAllAlerts(restaurantId));
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'KITCHEN', 'CLEANING')")
    public ResponseEntity<Alert> acknowledgeAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long userId = userDetails != null ? userDetails.getUserId() : 1L;
        return ResponseEntity.ok(alertService.acknowledgeAlert(restaurantId, id, userId));
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'KITCHEN', 'CLEANING')")
    public ResponseEntity<Alert> resolveAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long userId = userDetails != null ? userDetails.getUserId() : 1L;
        return ResponseEntity.ok(alertService.resolveAlert(restaurantId, id, userId));
    }

    @PutMapping("/{id}/false-alarm")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'KITCHEN', 'CLEANING')")
    public ResponseEntity<Alert> markFalseAlarm(
            @PathVariable Long id,
            @RequestBody(required = false) FalseAlarmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long userId = userDetails != null ? userDetails.getUserId() : 1L;
        String comment = request != null ? request.getComment() : "Staff marked as false alarm";
        return ResponseEntity.ok(alertService.markFalseAlarm(restaurantId, id, userId, comment));
    }
}
