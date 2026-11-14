package com.restrovision.service;

import com.restrovision.domain.*;
import com.restrovision.dto.OrderDtos.CreateOrderItemRequest;
import com.restrovision.dto.OrderDtos.CreateOrderRequest;
import com.restrovision.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Map<String, Set<String>> LEGAL_TRANSITIONS = Map.of(
            "CREATED", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("PREPARING", "CANCELLED"),
            "PREPARING", Set.of("READY", "CANCELLED"),
            "READY", Set.of("SERVED", "CANCELLED"),
            "SERVED", Set.of(), // Terminal state
            "CANCELLED", Set.of() // Terminal state
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final KitchenStationRepository stationRepository;
    private final AlertService alertService;
    private final NotificationService notificationService;

    @Value("${restrovision.state-engine.prep-delay-factor:1.5}")
    private double prepDelayFactor;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            MenuItemRepository menuItemRepository,
            RestaurantTableRepository tableRepository,
            TableSessionRepository sessionRepository,
            KitchenStationRepository stationRepository,
            AlertService alertService,
            NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.stationRepository = stationRepository;
        this.alertService = alertService;
        this.notificationService = notificationService;
    }

    public List<Order> getOrders(Long restaurantId) {
        return orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    public Optional<Order> getOrderById(Long restaurantId, Long orderId) {
        return orderRepository.findByRestaurantIdAndOrderId(restaurantId, orderId);
    }

    @Transactional
    public Order createOrder(Long restaurantId, CreateOrderRequest request) {
        // P1 Validation: Order items must not be empty
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot create order with empty items list.");
        }

        Long tableId = request.getTableId();
        Long sessionId = request.getSessionId();

        // P1 Fix: Correctly link table session
        if (tableId != null) {
            RestaurantTable tbl = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

            if (sessionId == null && tbl.getCurrentSessionId() != null) {
                sessionId = tbl.getCurrentSessionId();
            }
        }

        // P1 Fix: Unique, collision-free order code
        String orderCode = "ORD-" + restaurantId + "-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 900 + 100);
        Order order = new Order(restaurantId, sessionId, tableId, orderCode);
        order.setStatus("CONFIRMED");

        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderItemRequest itemReq : request.getItems()) {
            // P1 Validation: Quantity must be positive
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be greater than zero. Received: " + itemReq.getQuantity());
            }

            MenuItem mi = menuItemRepository.findByRestaurantIdAndItemId(restaurantId, itemReq.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Menu item not found in restaurant: " + itemReq.getItemId()));

            OrderItem item = new OrderItem();
            item.setRestaurantId(restaurantId);
            item.setOrder(order);
            item.setItemId(mi.getItemId());
            item.setItemName(mi.getName());
            item.setStationId(mi.getStationId());
            if (mi.getStationId() != null) {
                stationRepository.findById(mi.getStationId()).ifPresent(s -> item.setStationName(s.getName()));
            }
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(mi.getPrice());
            item.setExpectedPrepMinutes(mi.getExpectedPrepMinutes());
            item.setStatus("CONFIRMED");
            items.add(item);
        }
        order.setItems(items);
        order = orderRepository.save(order);

        // Update table session orderedAt timestamp if active
        if (sessionId != null) {
            sessionRepository.findByRestaurantIdAndSessionId(restaurantId, sessionId).ifPresent(s -> {
                if (s.getOrderedAt() == null) {
                    s.setOrderedAt(Instant.now());
                    sessionRepository.save(s);
                }
            });
        }

        log.info("Order created: {} with {} items for table {}", order.getOrderCode(), items.size(), tableId);
        notificationService.publishOrderUpdate(restaurantId, order);
        return order;
    }

    @Transactional
    public Order updateOrderStatus(Long restaurantId, Long orderId, String newStatus) {
        Order order = orderRepository.findByRestaurantIdAndOrderId(restaurantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        String currentStatus = order.getStatus();

        // P1 Validation: Enforce legal lifecycle transitions
        Set<String> allowedNext = LEGAL_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowedNext.contains(newStatus)) {
            throw new IllegalStateException("Illegal order transition: cannot transition order from "
                    + currentStatus + " to " + newStatus + ". Allowed next states: " + allowedNext);
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        if ("PREPARING".equals(newStatus)) {
            for (OrderItem item : order.getItems()) {
                if ("CONFIRMED".equals(item.getStatus()) || "CREATED".equals(item.getStatus())) {
                    item.setStatus("IN_PREPARATION");
                    item.setPreparationStartedAt(Instant.now());
                }
            }
        } else if ("READY".equals(newStatus)) {
            for (OrderItem item : order.getItems()) {
                // P1 Bugfix: Do NOT reset already SERVED items back to READY!
                if (!"SERVED".equals(item.getStatus()) && !"CANCELLED".equals(item.getStatus())) {
                    item.setStatus("READY");
                    item.setReadyAt(Instant.now());
                }
            }

            // Raise notification for assigned waiter to deliver food! (§4.1 F-08)
            Long tableId = order.getTableId();
            String tableCode = "T" + tableId;
            Long waiterId = null;
            if (tableId != null) {
                RestaurantTable tbl = tableRepository.findByRestaurantIdAndTableId(restaurantId, tableId).orElse(null);
                if (tbl != null) {
                    tableCode = tbl.getTableCode();
                    waiterId = tbl.getAssignedWaiterId();
                }
            }

            alertService.createOrUpdateAlert(
                    restaurantId,
                    "ORDER_READY_FOR_DELIVERY",
                    "MEDIUM",
                    "ORDER-READY-" + order.getOrderId(),
                    "Order #" + order.getOrderCode() + " for Table " + tableCode + " is READY for pickup at pass!",
                    tableId,
                    "WAITER",
                    waiterId,
                    null
            );
        } else if ("SERVED".equals(newStatus)) {
            for (OrderItem item : order.getItems()) {
                if (!"CANCELLED".equals(item.getStatus())) {
                    item.setStatus("SERVED");
                }
            }
        }

        order = orderRepository.save(order);
        notificationService.publishOrderUpdate(restaurantId, order);
        return order;
    }

    @Transactional
    public OrderItem updateOrderItemStatus(Long restaurantId, Long orderItemId, String newStatus) {
        // P1 Fix: Scoped by restaurantId to eliminate cross-tenant risk!
        OrderItem item = orderItemRepository.findByRestaurantIdAndOrderItemId(restaurantId, orderItemId)
                .orElseThrow(() -> new IllegalArgumentException("Order item not found in restaurant: " + orderItemId));

        item.setStatus(newStatus);
        if ("IN_PREPARATION".equals(newStatus)) {
            item.setPreparationStartedAt(Instant.now());
        } else if ("READY".equals(newStatus)) {
            item.setReadyAt(Instant.now());
        }
        item = orderItemRepository.save(item);

        // Check if all items in the order are ready
        Order order = item.getOrder();
        boolean allReady = order.getItems().stream().allMatch(i -> "READY".equals(i.getStatus()) || "SERVED".equals(i.getStatus()));
        if (allReady && !"READY".equals(order.getStatus()) && !"SERVED".equals(order.getStatus())) {
            updateOrderStatus(restaurantId, order.getOrderId(), "READY");
        } else {
            notificationService.publishOrderUpdate(restaurantId, order);
        }

        return item;
    }

    public List<OrderItem> getKitchenQueue(Long restaurantId, Long stationId) {
        List<String> activeStatuses = List.of("CONFIRMED", "IN_PREPARATION");
        if (stationId != null) {
            return orderItemRepository.findByRestaurantIdAndStationIdAndStatusIn(restaurantId, stationId, activeStatuses);
        }
        return orderItemRepository.findByRestaurantIdAndStatusIn(restaurantId, activeStatuses);
    }

    public List<Map<String, Object>> getDelayedOrders(Long restaurantId) {
        List<OrderItem> items = orderItemRepository.findByRestaurantIdAndStatusIn(restaurantId, List.of("IN_PREPARATION"));
        Instant now = Instant.now();
        List<Map<String, Object>> delayed = new ArrayList<>();

        for (OrderItem item : items) {
            if (item.getPreparationStartedAt() != null) {
                long elapsedMinutes = Duration.between(item.getPreparationStartedAt(), now).toMinutes();
                double threshold = item.getExpectedPrepMinutes() * prepDelayFactor;

                if (elapsedMinutes > threshold) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderItemId", item.getOrderItemId());
                    map.put("orderId", item.getOrder().getOrderId());
                    map.put("orderCode", item.getOrder().getOrderCode());
                    map.put("tableId", item.getOrder().getTableId());
                    map.put("itemName", item.getItemName());
                    map.put("stationName", item.getStationName());
                    map.put("elapsedMinutes", elapsedMinutes);
                    map.put("expectedMinutes", item.getExpectedPrepMinutes());
                    map.put("thresholdMinutes", threshold);
                    delayed.add(map);
                }
            }
        }
        return delayed;
    }
}
