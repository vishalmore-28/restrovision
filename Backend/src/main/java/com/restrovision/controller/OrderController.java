package com.restrovision.controller;

import com.restrovision.domain.Order;
import com.restrovision.dto.OrderDtos.*;
import com.restrovision.security.TenantContext;
import com.restrovision.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'KITCHEN')")
    public ResponseEntity<List<Order>> getOrders() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.getOrders(restaurantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'KITCHEN')")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        return orderService.getOrderById(restaurantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER')")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.createOrder(restaurantId, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'KITCHEN')")
    public ResponseEntity<Order> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateOrderStatusRequest request) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, id, request.getStatus()));
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('MANAGER', 'WAITER')")
    public ResponseEntity<Order> deliverOrder(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, id, "SERVED"));
    }

    @GetMapping("/delayed")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'KITCHEN')")
    public ResponseEntity<List<Map<String, Object>>> getDelayedOrders() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.getDelayedOrders(restaurantId));
    }

    @GetMapping("/kds-queue")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'KITCHEN')")
    public ResponseEntity<List<com.restrovision.domain.OrderItem>> getKdsQueue(
            @RequestParam(required = false) Long stationId) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.getKitchenQueue(restaurantId, stationId));
    }
}
