package com.restrovision.controller;

import com.restrovision.domain.CustomerFeedback;
import com.restrovision.repository.CustomerFeedbackRepository;
import com.restrovision.security.TenantContext;
import com.restrovision.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final CustomerFeedbackRepository feedbackRepository;

    public AnalyticsController(AnalyticsService analyticsService, CustomerFeedbackRepository feedbackRepository) {
        this.analyticsService = analyticsService;
        this.feedbackRepository = feedbackRepository;
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview(
            @RequestParam(value = "timeframe", defaultValue = "daily") String timeframe) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(analyticsService.getOverview(restaurantId, timeframe));
    }

    @GetMapping("/feedback")
    public ResponseEntity<List<CustomerFeedback>> getFeedbackList() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(feedbackRepository.findTop20ByRestaurantIdOrderByCreatedAtDesc(restaurantId));
    }
}
