package com.restrovision.controller;

import com.restrovision.domain.CustomerFeedback;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.ServiceRequest;
import com.restrovision.dto.FeedbackDtos.CustomerFeedbackRequest;
import com.restrovision.repository.CustomerFeedbackRepository;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.repository.ServiceRequestRepository;
import com.restrovision.repository.TableSessionRepository;
import com.restrovision.service.AlertService;
import com.restrovision.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
public class PublicQrController {

    private final RestaurantTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final CustomerFeedbackRepository feedbackRepository;
    private final AlertService alertService;
    private final NotificationService notificationService;

    public PublicQrController(
            RestaurantTableRepository tableRepository,
            TableSessionRepository sessionRepository,
            ServiceRequestRepository serviceRequestRepository,
            CustomerFeedbackRepository feedbackRepository,
            AlertService alertService,
            NotificationService notificationService) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.feedbackRepository = feedbackRepository;
        this.alertService = alertService;
        this.notificationService = notificationService;
    }

    @GetMapping("/qr/{token}/status")
    public ResponseEntity<Map<String, Object>> getQrStatus(@PathVariable String token) {
        // P0 Security Fix: Lookup by secure unguessable QR token. No silent fallback to table 1!
        RestaurantTable table = tableRepository.findByQrToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired table QR token: " + token));

        Map<String, Object> resp = new HashMap<>();
        resp.put("tableId", table.getTableId());
        resp.put("tableCode", table.getTableCode());
        resp.put("status", table.getStatus());
        resp.put("capacity", table.getCapacity());
        resp.put("restaurantId", table.getRestaurantId());

        if (table.getCurrentSessionId() != null) {
            sessionRepository.findByRestaurantIdAndSessionId(table.getRestaurantId(), table.getCurrentSessionId())
                    .ifPresent(s -> {
                        resp.put("sessionId", s.getSessionId());
                        resp.put("seatedAt", s.getSeatedAt());
                        resp.put("attended", s.getAttendedAt() != null);
                    });
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/qr/{token}/call-waiter")
    public ResponseEntity<Map<String, Object>> callWaiterFromQr(
            @PathVariable String token,
            @RequestBody(required = false) Map<String, String> body) {
        // P0 Security Fix: Lookup by secure token
        RestaurantTable table = tableRepository.findByQrToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired table QR token: " + token));

        String requestType = (body != null && body.containsKey("requestType")) ? body.get("requestType") : "CALL_WAITER";

        ServiceRequest sr = new ServiceRequest();
        sr.setRestaurantId(table.getRestaurantId());
        sr.setTableId(table.getTableId());
        sr.setTableCode(table.getTableCode());
        sr.setSessionId(table.getCurrentSessionId());
        sr.setRequestType(requestType);
        sr.setSource("QR");
        sr.setStatus("OPEN");
        sr.setAssignedEmployeeId(table.getAssignedWaiterId());
        sr = serviceRequestRepository.save(sr);

        alertService.createOrUpdateAlert(
                table.getRestaurantId(),
                "QR_" + requestType,
                "MEDIUM",
                "QR-CALL-" + table.getTableId(),
                "Customer at " + table.getTableCode() + " requested: " + requestType + " via Table QR",
                table.getTableId(),
                "WAITER",
                table.getAssignedWaiterId(),
                null
        );

        notificationService.publishTaskUpdate(table.getRestaurantId(), sr);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("serviceRequestId", sr.getServiceRequestId());
        resp.put("message", "Assigned server notified. Assistance is on the way.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/feedback")
    public ResponseEntity<CustomerFeedback> submitFeedback(@RequestBody CustomerFeedbackRequest request) {
        // P0 Validation: Rating must be strictly between 1 and 5
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Invalid rating: " + request.getRating() + ". Rating must be an integer between 1 and 5.");
        }

        // P0 Multi-Tenancy Fix: Derive restaurantId from verified table, NEVER hardcode to 1!
        Long tableId = request.getTableId();
        if (tableId == null) {
            throw new IllegalArgumentException("Table ID is required to associate verified feedback.");
        }

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));
        Long restaurantId = table.getRestaurantId();

        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setRestaurantId(restaurantId);
        feedback.setTableId(table.getTableId());
        feedback.setSessionId(request.getSessionId());
        feedback.setRating(request.getRating());
        feedback.setCommentText(request.getComment());

        // Simple sentiment & category classification (M10 baseline)
        String comment = request.getComment() != null ? request.getComment().toLowerCase() : "";
        if (request.getRating() >= 4) {
            feedback.setSentiment("POSITIVE");
        } else if (request.getRating() <= 2) {
            feedback.setSentiment("NEGATIVE");
        } else {
            feedback.setSentiment("NEUTRAL");
        }

        if (comment.contains("food") || comment.contains("taste") || comment.contains("delicious") || comment.contains("spicy")) {
            feedback.setCategory("FOOD_QUALITY");
        } else if (comment.contains("slow") || comment.contains("quick") || comment.contains("wait") || comment.contains("fast")) {
            feedback.setCategory("SERVICE_SPEED");
        } else if (comment.contains("clean") || comment.contains("dirty") || comment.contains("table")) {
            feedback.setCategory("CLEANLINESS");
        } else {
            feedback.setCategory("AMBIENCE");
        }

        feedback = feedbackRepository.save(feedback);
        return ResponseEntity.ok(feedback);
    }
}
