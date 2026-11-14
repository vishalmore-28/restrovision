package com.restrovision.service;

import com.restrovision.domain.Employee;
import com.restrovision.domain.Order;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WorkloadService {

    private final EmployeeRepository employeeRepository;
    private final RestaurantTableRepository tableRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final OrderRepository orderRepository;
    private final StaffTaskRepository staffTaskRepository;

    public WorkloadService(
            EmployeeRepository employeeRepository,
            RestaurantTableRepository tableRepository,
            ServiceRequestRepository serviceRequestRepository,
            OrderRepository orderRepository,
            StaffTaskRepository staffTaskRepository) {
        this.employeeRepository = employeeRepository;
        this.tableRepository = tableRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.orderRepository = orderRepository;
        this.staffTaskRepository = staffTaskRepository;
    }

    public List<Map<String, Object>> calculateWaitersWorkload(Long restaurantId) {
        List<Employee> waiters = employeeRepository.findByRestaurantIdAndJobRole(restaurantId, "WAITER");
        List<Map<String, Object>> result = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (Employee waiter : waiters) {
            Long waiterId = waiter.getEmployeeId();

            // 1. Assigned occupied tables
            List<RestaurantTable> tables = tableRepository.findByRestaurantIdAndAssignedWaiterId(restaurantId, waiterId);
            long occupiedCount = tables.stream().filter(t -> "OCCUPIED".equals(t.getStatus())).count();

            // 2. Pending service requests
            long pendingRequests = serviceRequestRepository.findByRestaurantIdAndAssignedEmployeeIdAndStatus(restaurantId, waiterId, "OPEN").size();

            // 3. Undelivered ready orders
            List<Long> assignedTableIds = tables.stream().map(RestaurantTable::getTableId).toList();
            long undeliveredReadyOrders = 0;
            if (!assignedTableIds.isEmpty()) {
                List<Order> readyOrders = orderRepository.findByRestaurantIdAndStatus(restaurantId, "READY");
                undeliveredReadyOrders = readyOrders.stream().filter(o -> assignedTableIds.contains(o.getTableId())).count();
            }

            // 4. Open tasks
            long openTasks = staffTaskRepository.findByRestaurantIdAndAssignedEmployeeIdAndStatusIn(restaurantId, waiterId, List.of("OPEN", "ASSIGNED", "IN_PROGRESS")).size();

            // Workload formula (§4.4)
            double score = (occupiedCount * 1.0) + (pendingRequests * 1.5) + (undeliveredReadyOrders * 1.0) + (openTasks * 0.5);
            scores.add(score);

            Map<String, Object> map = new HashMap<>();
            map.put("waiterId", waiterId);
            map.put("displayName", waiter.getDisplayName());
            map.put("employeeCode", waiter.getEmployeeCode());
            map.put("occupiedTables", occupiedCount);
            map.put("totalAssignedTables", tables.size());
            map.put("pendingRequests", pendingRequests);
            map.put("undeliveredReadyOrders", undeliveredReadyOrders);
            map.put("openTasks", openTasks);
            map.put("workloadScore", Math.round(score * 10.0) / 10.0);
            result.add(map);
        }

        // Calculate median
        double median = 0.0;
        if (!scores.isEmpty()) {
            Collections.sort(scores);
            int mid = scores.size() / 2;
            median = (scores.size() % 2 == 0) ? (scores.get(mid - 1) + scores.get(mid)) / 2.0 : scores.get(mid);
        }

        for (Map<String, Object> map : result) {
            double s = (Double) map.get("workloadScore");
            map.put("shiftMedian", median);
            map.put("isOverloaded", s > (median + 2.0));
        }

        return result;
    }
}
