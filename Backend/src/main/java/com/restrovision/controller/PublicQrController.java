package com.restrovision.controller;

import com.restrovision.domain.CustomerFeedback;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.ServiceRequest;
import com.restrovision.domain.TableSession;
import com.restrovision.dto.FeedbackDtos.CustomerFeedbackRequest;
import com.restrovision.repository.CustomerFeedbackRepository;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.repository.ServiceRequestRepository;
import com.restrovision.repository.TableSessionRepository;
import com.restrovision.security.FeedbackRateLimiterService;
import com.restrovision.security.HtmlSanitizer;
import com.restrovision.security.LoginRateLimiterService;
import com.restrovision.service.AlertService;
import com.restrovision.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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
    private final FeedbackRateLimiterService feedbackRateLimiter;
    private final LoginRateLimiterService loginRateLimiterService;

    public PublicQrController(
            RestaurantTableRepository tableRepository,
            TableSessionRepository sessionRepository,
            ServiceRequestRepository serviceRequestRepository,
            CustomerFeedbackRepository feedbackRepository,
            AlertService alertService,
            NotificationService notificationService,
            FeedbackRateLimiterService feedbackRateLimiter,
            LoginRateLimiterService loginRateLimiterService) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.feedbackRepository = feedbackRepository;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.feedbackRateLimiter = feedbackRateLimiter;
        this.loginRateLimiterService = loginRateLimiterService;
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
    public ResponseEntity<?> submitFeedback(@RequestBody CustomerFeedbackRequest request, HttpServletRequest httpRequest) {
        // 1. Rating validation: Rating must be strictly between 1 and 5
        if (request == null || request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Invalid rating. Rating must be an integer between 1 and 5.");
        }

        // 2. Table validation & ownership
        Long tableId = request.getTableId();
        if (tableId == null) {
            throw new IllegalArgumentException("Table ID is required to associate verified feedback.");
        }

        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        if ("OUT_OF_SERVICE".equalsIgnoreCase(table.getStatus())) {
            throw new IllegalArgumentException("Cannot submit feedback for a table that is out of service: " + tableId);
        }

        Long restaurantId = table.getRestaurantId();

        // 3. Session validation & secure session binding
        Long feedbackSessionId;
        if (request.getSessionId() != null) {
            TableSession session = sessionRepository.findByRestaurantIdAndSessionId(restaurantId, request.getSessionId())
                    .filter(s -> table.getTableId().equals(s.getTableId()))
                    .orElseThrow(() -> new IllegalArgumentException("Provided sessionId does not belong to table " + tableId));

            // If a new session has started on this table, reject feedback for the superseded/old session
            if (table.getCurrentSessionId() != null && !table.getCurrentSessionId().equals(session.getSessionId())) {
                throw new IllegalArgumentException("Session " + request.getSessionId() + " is expired. A new session has already started for table " + tableId);
            }
            if (!"ACTIVE".equalsIgnoreCase(session.getStatus()) && (table.getCurrentSessionId() == null || !table.getCurrentSessionId().equals(session.getSessionId()))) {
                throw new IllegalArgumentException("Session " + request.getSessionId() + " is closed or inactive for table " + tableId);
            }
            feedbackSessionId = session.getSessionId();
        } else {
            // Missing feedback session ID: establish legitimate current session securely
            if (table.getCurrentSessionId() == null) {
                throw new IllegalArgumentException("No active session found for table " + tableId + ". Feedback must be associated with an active dining session.");
            }
            TableSession currentSession = sessionRepository.findByRestaurantIdAndSessionId(restaurantId, table.getCurrentSessionId())
                    .filter(s -> table.getTableId().equals(s.getTableId()) && "ACTIVE".equalsIgnoreCase(s.getStatus()))
                    .orElseThrow(() -> new IllegalArgumentException("Current session for table " + tableId + " is invalid or inactive."));
            feedbackSessionId = currentSession.getSessionId();
        }

        // 4. Payload size limit (BUG-013)
        String rawComment = request.getComment();
        if (rawComment != null && rawComment.length() > feedbackRateLimiter.getMaxCommentLength()) {
            throw new IllegalArgumentException("Feedback comment exceeds maximum allowed length of " + feedbackRateLimiter.getMaxCommentLength() + " characters.");
        }

        // 5. Plain text sanitization (BUG-009)
        String cleanComment = HtmlSanitizer.sanitizeComment(rawComment);

        // 6. Anti-Flooding & Abuse Protection (BUG-013)
        String clientIp = loginRateLimiterService.resolveClientIp(httpRequest);
        String clientFingerprint = resolveDeviceFingerprint(httpRequest);
        FeedbackRateLimiterService.FeedbackLimitResult limit = feedbackRateLimiter.checkLimits(
                clientIp, clientFingerprint, tableId, request.getRating(), cleanComment);
        if (limit.isBlocked()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS, limit.getReason());
            problem.setType(URI.create("urn:restrovision:error:feedback-rate-limit"));
            problem.setTitle("Too Many Requests");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(limit.getRetryAfterSeconds()))
                    .body(problem);
        }

        feedbackRateLimiter.recordSubmission(clientIp, clientFingerprint, tableId, request.getRating(), cleanComment);

        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setRestaurantId(restaurantId);
        feedback.setTableId(table.getTableId());
        feedback.setSessionId(feedbackSessionId);
        feedback.setRating(request.getRating());
        feedback.setCommentText(cleanComment);

        // Simple sentiment & category classification (M10 baseline)
        String commentLower = cleanComment != null ? cleanComment.toLowerCase() : "";
        if (request.getRating() >= 4) {
            feedback.setSentiment("POSITIVE");
        } else if (request.getRating() <= 2) {
            feedback.setSentiment("NEGATIVE");
        } else {
            feedback.setSentiment("NEUTRAL");
        }

        if (commentLower.contains("food") || commentLower.contains("taste") || commentLower.contains("delicious") || commentLower.contains("spicy")) {
            feedback.setCategory("FOOD_QUALITY");
        } else if (commentLower.contains("slow") || commentLower.contains("quick") || commentLower.contains("wait") || commentLower.contains("fast")) {
            feedback.setCategory("SERVICE_SPEED");
        } else if (commentLower.contains("clean") || commentLower.contains("dirty") || commentLower.contains("table")) {
            feedback.setCategory("CLEANLINESS");
        } else {
            feedback.setCategory("AMBIENCE");
        }

        feedback = feedbackRepository.save(feedback);
        return ResponseEntity.ok(feedback);
    }

    private String resolveDeviceFingerprint(HttpServletRequest request) {
        if (request == null) {
            return "DEFAULT";
        }
        if (request.getSession(false) != null) {
            return "SESS:" + request.getSession(false).getId();
        }
        String userAgent = request.getHeader("User-Agent");
        String acceptLang = request.getHeader("Accept-Language");
        if (org.springframework.util.StringUtils.hasText(userAgent) || org.springframework.util.StringUtils.hasText(acceptLang)) {
            String raw = (userAgent != null ? userAgent.trim() : "") + "|" + (acceptLang != null ? acceptLang.trim() : "");
            return "FP:" + Integer.toHexString(raw.hashCode());
        }
        return "DEFAULT";
    }
}
