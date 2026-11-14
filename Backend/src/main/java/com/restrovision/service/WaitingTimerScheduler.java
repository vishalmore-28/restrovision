package com.restrovision.service;

import com.restrovision.domain.Restaurant;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.TableSession;
import com.restrovision.repository.RestaurantRepository;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.repository.TableSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@EnableScheduling
public class WaitingTimerScheduler {
    private static final Logger log = LoggerFactory.getLogger(WaitingTimerScheduler.class);

    private final RestaurantRepository restaurantRepository;
    private final TableSessionRepository sessionRepository;
    private final RestaurantTableRepository tableRepository;
    private final AlertService alertService;

    public WaitingTimerScheduler(
            RestaurantRepository restaurantRepository,
            TableSessionRepository sessionRepository,
            RestaurantTableRepository tableRepository,
            AlertService alertService) {
        this.restaurantRepository = restaurantRepository;
        this.sessionRepository = sessionRepository;
        this.tableRepository = tableRepository;
        this.alertService = alertService;
    }

    // Run every 15 seconds to monitor customer wait times (§4.2)
    @Scheduled(fixedRate = 15000)
    public void checkUnattendedSessions() {
        List<Restaurant> restaurants = restaurantRepository.findAll();
        Instant now = Instant.now();

        for (Restaurant rest : restaurants) {
            Long restaurantId = rest.getRestaurantId();
            List<TableSession> activeSessions = sessionRepository.findByRestaurantId(restaurantId).stream()
                    .filter(s -> "ACTIVE".equals(s.getStatus()) && s.getAttendedAt() == null && s.getSeatedAt() != null)
                    .toList();

            for (TableSession s : activeSessions) {
                Long tableId = s.getTableId();
                RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId).orElse(null);

                // Only alert if table is genuinely OCCUPIED
                if (table == null || !"OCCUPIED".equals(table.getStatus())) {
                    continue;
                }

                long elapsedSeconds = Duration.between(s.getSeatedAt(), now).getSeconds();
                String tableCode = table.getTableCode();
                Long waiterId = table.getAssignedWaiterId();

                if (elapsedSeconds >= 600) { // > 10 min
                    alertService.createOrUpdateAlert(
                            restaurantId,
                            "CUSTOMER_WAITING_CRITICAL",
                            "CRITICAL",
                            "WAITING-" + s.getSessionId(),
                            "CRITICAL: Table " + tableCode + " has been waiting for attendance for > 10 mins!",
                            tableId,
                            "MANAGER",
                            waiterId,
                            null
                    );
                } else if (elapsedSeconds >= 300) { // > 5 min
                    alertService.createOrUpdateAlert(
                            restaurantId,
                            "CUSTOMER_WAITING_HIGH",
                            "HIGH",
                            "WAITING-" + s.getSessionId(),
                            "ESCALATED: Table " + tableCode + " has been waiting for attendance for > 5 mins without attention.",
                            tableId,
                            "MANAGER",
                            waiterId,
                            null
                    );
                } else if (elapsedSeconds >= 120) { // > 2 min
                    alertService.createOrUpdateAlert(
                            restaurantId,
                            "CUSTOMER_WAITING_MEDIUM",
                            "MEDIUM",
                            "WAITING-" + s.getSessionId(),
                            "Attention needed: Customer seated at Table " + tableCode + " waiting for first service (> 2 mins).",
                            tableId,
                            "WAITER",
                            waiterId,
                            null
                    );
                }
            }
        }
    }
}
