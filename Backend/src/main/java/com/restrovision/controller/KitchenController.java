package com.restrovision.controller;

import com.restrovision.domain.KitchenStation;
import com.restrovision.domain.Order;
import com.restrovision.domain.OrderItem;
import com.restrovision.dto.OrderDtos.OrderItemStatusUpdateRequest;
import com.restrovision.repository.KitchenStationRepository;
import com.restrovision.security.TenantContext;
import com.restrovision.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenController {

    private final OrderService orderService;
    private final KitchenStationRepository stationRepository;

    public KitchenController(OrderService orderService, KitchenStationRepository stationRepository) {
        this.orderService = orderService;
        this.stationRepository = stationRepository;
    }

    @GetMapping("/stations")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'KITCHEN')")
    public ResponseEntity<List<KitchenStation>> getStations() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(stationRepository.findByRestaurantId(restaurantId));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'KITCHEN')")
    public ResponseEntity<List<OrderItem>> getKitchenQueue(@RequestParam(required = false) Long stationId) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.getKitchenQueue(restaurantId, stationId));
    }

    @PutMapping("/orders/{id}/start")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ResponseEntity<Order> startOrder(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, id, "PREPARING"));
    }

    @PutMapping("/orders/{id}/ready")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ResponseEntity<Order> markOrderReady(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.updateOrderStatus(restaurantId, id, "READY"));
    }

    @PutMapping("/items/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER', 'KITCHEN')")
    public ResponseEntity<OrderItem> updateItemStatus(
            @PathVariable Long id,
            @RequestBody OrderItemStatusUpdateRequest request) {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(orderService.updateOrderItemStatus(restaurantId, id, request.getStatus()));
    }
}
