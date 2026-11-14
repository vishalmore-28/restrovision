package com.restrovision.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTableUpdate(Long restaurantId, Object tableData) {
        String destination = "/topic/restaurants/" + restaurantId + "/tables";
        log.debug("Publishing table update to {}", destination);
        messagingTemplate.convertAndSend(destination, tableData);
    }

    public void publishAlert(Long restaurantId, Object alertData) {
        String destination = "/topic/restaurants/" + restaurantId + "/alerts";
        log.info("Publishing real-time alert to {}", destination);
        messagingTemplate.convertAndSend(destination, alertData);
    }

    public void publishOrderUpdate(Long restaurantId, Object orderData) {
        String destination = "/topic/restaurants/" + restaurantId + "/orders";
        log.debug("Publishing order update to {}", destination);
        messagingTemplate.convertAndSend(destination, orderData);
    }

    public void publishTaskUpdate(Long restaurantId, Object taskData) {
        String destination = "/topic/restaurants/" + restaurantId + "/tasks";
        log.debug("Publishing task update to {}", destination);
        messagingTemplate.convertAndSend(destination, taskData);
    }

    public void broadcastFloorEvent(Long restaurantId, String eventType, Map<String, Object> details) {
        String destination = "/topic/restaurants/" + restaurantId + "/events";
        messagingTemplate.convertAndSend(destination, details);
    }
}
