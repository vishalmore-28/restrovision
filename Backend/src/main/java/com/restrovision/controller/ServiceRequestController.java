package com.restrovision.controller;

import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.ServiceRequest;
import com.restrovision.dto.FeedbackDtos.ServiceRequestCreateDto;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.repository.ServiceRequestRepository;
import com.restrovision.security.TenantContext;
import com.restrovision.service.AlertService;
import com.restrovision.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/service-requests")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER')")
public class ServiceRequestController {

    private final ServiceRequestRepository serviceRequestRepository;
    private final RestaurantTableRepository tableRepository;
    private final AlertService alertService;
    private final NotificationService notificationService;

    public ServiceRequestController(
            ServiceRequestRepository serviceRequestRepository,
            RestaurantTableRepository tableRepository,
            AlertService alertService,
            NotificationService notificationService) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.tableRepository = tableRepository;
        this.alertService = alertService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<ServiceRequest>> getRequests() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(serviceRequestRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId));
    }

    @PostMapping
    public ResponseEntity<ServiceRequest> createRequest(@RequestBody ServiceRequestCreateDto dto) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long tableId = dto.getTableId();
        String tableCode = "T" + tableId;
        Long waiterId = null;

        if (tableId != null) {
            RestaurantTable tbl = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId).orElse(null);
            if (tbl != null) {
                tableCode = tbl.getTableCode();
                waiterId = tbl.getAssignedWaiterId();
            }
        }

        ServiceRequest sr = new ServiceRequest();
        sr.setRestaurantId(restaurantId);
        sr.setTableId(tableId);
        sr.setTableCode(tableCode);
        sr.setRequestType(dto.getRequestType());
        sr.setSource(dto.getSource());
        sr.setStatus("OPEN");
        sr.setAssignedEmployeeId(waiterId);
        sr = serviceRequestRepository.save(sr);

        alertService.createOrUpdateAlert(
                restaurantId,
                "SERVICE_REQUEST_" + dto.getRequestType(),
                "MEDIUM",
                "SR-" + sr.getServiceRequestId(),
                "Customer request at Table " + tableCode + ": " + dto.getRequestType(),
                tableId,
                "WAITER",
                waiterId,
                null
        );

        notificationService.publishTaskUpdate(restaurantId, sr);
        return ResponseEntity.ok(sr);
    }

    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<ServiceRequest> acknowledge(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        ServiceRequest sr = serviceRequestRepository.findByRestaurantIdAndServiceRequestId(restaurantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Service request not found"));
        sr.setStatus("ACKNOWLEDGED");
        sr.setAcknowledgedAt(Instant.now());
        sr = serviceRequestRepository.save(sr);
        return ResponseEntity.ok(sr);
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<ServiceRequest> complete(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        ServiceRequest sr = serviceRequestRepository.findByRestaurantIdAndServiceRequestId(restaurantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Service request not found"));
        sr.setStatus("COMPLETED");
        sr.setCompletedAt(Instant.now());
        sr = serviceRequestRepository.save(sr);
        return ResponseEntity.ok(sr);
    }
}
