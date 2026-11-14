package com.restrovision.controller;

import com.restrovision.domain.RestaurantTable;
import com.restrovision.dto.TableDtos.*;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.security.CustomUserDetails;
import com.restrovision.security.TenantContext;
import com.restrovision.service.TableStateMachineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tables")
public class TableController {

    private final TableStateMachineService tableStateMachineService;
    private final RestaurantTableRepository tableRepository;

    public TableController(TableStateMachineService tableStateMachineService,
                           RestaurantTableRepository tableRepository) {
        this.tableStateMachineService = tableStateMachineService;
        this.tableRepository = tableRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'CLEANING')")
    public ResponseEntity<List<TableViewDto>> getTables() {
        Long restaurantId = TenantContext.getRestaurantId();
        return ResponseEntity.ok(tableStateMachineService.getTableView(restaurantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'CLEANING')")
    public ResponseEntity<RestaurantTable> getTable(@PathVariable Long id) {
        Long restaurantId = TenantContext.getRestaurantId();
        return tableRepository.findByRestaurantIdAndTableId(restaurantId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER', 'CLEANING')")
    public ResponseEntity<RestaurantTable> overrideStatus(
            @PathVariable Long id,
            @RequestBody TableStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long actorUserId = userDetails != null ? userDetails.getUserId() : 1L;
        Set<String> roles = userDetails != null
                ? userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .map(a -> a.replace("ROLE_", "")).collect(Collectors.toSet())
                : Set.of("MANAGER");

        RestaurantTable table = tableStateMachineService.overrideTableStatus(
                restaurantId, id, request.getStatus(), request.getReason(), actorUserId, roles);
        return ResponseEntity.ok(table);
    }

    @PostMapping("/{id}/seat")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER')")
    public ResponseEntity<RestaurantTable> seatParty(
            @PathVariable Long id,
            @RequestBody SeatPartyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long actorUserId = userDetails != null ? userDetails.getUserId() : 1L;
        RestaurantTable table = tableStateMachineService.seatParty(
                restaurantId, id, request.getPartySize(), request.getAssignedWaiterId(), actorUserId);
        return ResponseEntity.ok(table);
    }

    @PostMapping("/{id}/attend")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'WAITER')")
    public ResponseEntity<?> attendTable(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long restaurantId = TenantContext.getRestaurantId();
        Long actorUserId = userDetails != null ? userDetails.getUserId() : 1L;
        RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));

        if (table.getCurrentSessionId() != null) {
            tableStateMachineService.markTableAttended(restaurantId, table.getCurrentSessionId(), actorUserId);
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/waiter")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<RestaurantTable> assignWaiter(
            @PathVariable Long id,
            @RequestBody AssignWaiterRequest request) {
        Long restaurantId = TenantContext.getRestaurantId();
        RestaurantTable table = tableRepository.findByRestaurantIdAndTableId(restaurantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));
        table.setAssignedWaiterId(request.getWaiterId());
        table = tableRepository.save(table);
        return ResponseEntity.ok(table);
    }
}
