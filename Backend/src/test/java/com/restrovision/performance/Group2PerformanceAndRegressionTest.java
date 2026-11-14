package com.restrovision.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restrovision.controller.AnalyticsController;
import com.restrovision.controller.KitchenController;
import com.restrovision.controller.OrderController;
import com.restrovision.domain.Employee;
import com.restrovision.domain.Order;
import com.restrovision.domain.Restaurant;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.TableSession;
import com.restrovision.dto.OrderDtos.CreateOrderItemRequest;
import com.restrovision.dto.OrderDtos.CreateOrderRequest;
import com.restrovision.dto.TableDtos.TableViewDto;
import com.restrovision.repository.EmployeeRepository;
import com.restrovision.repository.OrderRepository;
import com.restrovision.repository.RestaurantRepository;
import com.restrovision.repository.RestaurantTableRepository;
import com.restrovision.repository.TableSessionRepository;
import com.restrovision.security.TenantContext;
import com.restrovision.service.OrderService;
import com.restrovision.service.TableStateMachineService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("default")
class Group2PerformanceAndRegressionTest {

    @Autowired
    private TableStateMachineService tableStateMachineService;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TableSessionRepository sessionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderController orderController;

    @Autowired
    private KitchenController kitchenController;

    @Autowired
    private AnalyticsController analyticsController;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ObjectMapper objectMapper;

    private SessionFactory sessionFactory;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        TenantContext.setRestaurantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "owner@restrovision.local", null,
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"),
                                new SimpleGrantedAuthority("ROLE_MANAGER"),
                                new SimpleGrantedAuthority("ROLE_WAITER"),
                                new SimpleGrantedAuthority("ROLE_KITCHEN"))
                )
        );
        sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        statistics.clear();
    }

    @Test
    @DisplayName("BUG-006 & BUG-017: Verify N+1 Query Elimination in getTableView (At most 3 SQL queries)")
    void testTableViewQueryCountOptimization() {
        // Create an isolated test restaurant to test exact query counts cleanly
        Restaurant testRest = restaurantRepository.save(new Restaurant("Perf Benchmark Restaurant", "Asia/Kolkata", "ACTIVE"));
        final Long testRestaurantId = testRest.getRestaurantId();

        Employee waiter = employeeRepository.save(new Employee(testRestaurantId, 1L, "EMP999", "Test Waiter", "WAITER", "ACTIVE"));
        
        List<RestaurantTable> seededTables = new ArrayList<>();
        List<TableSession> seededSessions = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            RestaurantTable t = new RestaurantTable(testRestaurantId, "T999-" + i, 4, "OCCUPIED");
            t.setAssignedWaiterId(waiter.getEmployeeId());
            RestaurantTable savedTable = tableRepository.save(t);

            TableSession s = new TableSession(testRestaurantId, savedTable.getTableId(), "STAFF", 4);
            s.setStatus("OCCUPIED");
            s.setSeatedAt(Instant.now().minusSeconds(180));
            TableSession savedSession = sessionRepository.save(s);

            savedTable.setCurrentSessionId(savedSession.getSessionId());
            tableRepository.save(savedTable);
            seededTables.add(savedTable);
            seededSessions.add(savedSession);
        }

        // Clear hibernate statistics before calling getTableView
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        // Execute getTableView
        List<TableViewDto> result = tableStateMachineService.getTableView(testRestaurantId);

        long queriesAfter = statistics.getPrepareStatementCount();
        long totalQueries = queriesAfter - queriesBefore;

        System.out.println("==================================================================");
        System.out.println("  BUG-006 / BUG-017 EMPIRICAL VERIFICATION REPORT");
        System.out.println("  Number of Tables Tested: " + seededTables.size());
        System.out.println("  Legacy Unoptimized Queries (1 + 2N): " + (1 + 2 * seededTables.size()) + " queries");
        System.out.println("  Actual Measured Queries (Batch Fetching): " + totalQueries + " queries");
        System.out.println("==================================================================");

        assertThat(result).hasSize(10);
        assertThat(result.get(0).getAssignedWaiterName()).isEqualTo("Test Waiter");
        assertThat(result.get(0).getPartySizeEst()).isEqualTo(4);

        // Under N+1, 10 tables would trigger 1 + 10 + 10 = 21 queries.
        // With batch in-clause optimization, exactly 3 queries are executed (1 tables + 1 waiters + 1 sessions).
        assertThat(totalQueries)
                .as("Query count must not exceed 3 queries (N+1 cascade eliminated)")
                .isLessThanOrEqualTo(3L);

        // Clean up test data: Break FK references first, then delete child sessions, then parent tables
        for (RestaurantTable t : tableRepository.findAllById(seededTables.stream().map(RestaurantTable::getTableId).toList())) {
            t.setCurrentSessionId(null);
            tableRepository.save(t);
        }
        sessionRepository.deleteAllById(seededSessions.stream().map(TableSession::getSessionId).toList());
        tableRepository.deleteAllById(seededTables.stream().map(RestaurantTable::getTableId).toList());
        employeeRepository.delete(waiter);
        restaurantRepository.delete(testRest);
    }

    @Test
    @DisplayName("BUG-017: Latency Benchmark for getTableView (100 Iterations p50, p95, p99)")
    void testTableViewLatencyBenchmark() {
        int iterations = 100;
        long[] latenciesNs = new long[iterations];

        // Warm up JIT
        for (int i = 0; i < 10; i++) {
            tableStateMachineService.getTableView(1L);
        }

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            tableStateMachineService.getTableView(1L);
            long end = System.nanoTime();
            latenciesNs[i] = end - start;
        }

        Arrays.sort(latenciesNs);

        double p50Ms = latenciesNs[(int) (iterations * 0.50)] / 1_000_000.0;
        double p95Ms = latenciesNs[(int) (iterations * 0.95)] / 1_000_000.0;
        double p99Ms = latenciesNs[(int) (iterations * 0.99)] / 1_000_000.0;
        double minMs = latenciesNs[0] / 1_000_000.0;
        double maxMs = latenciesNs[iterations - 1] / 1_000_000.0;

        System.out.println("==================================================================");
        System.out.println("  BUG-017 LATENCY BENCHMARK (" + iterations + " iterations)");
        System.out.printf("  Min Latency: %.3f ms%n", minMs);
        System.out.printf("  p50 Median:  %.3f ms%n", p50Ms);
        System.out.printf("  p95 Latency: %.3f ms%n", p95Ms);
        System.out.printf("  p99 Latency: %.3f ms%n", p99Ms);
        System.out.printf("  Max Latency: %.3f ms%n", maxMs);
        System.out.println("==================================================================");

        assertThat(p95Ms).as("p95 latency should be well under 50ms for in-memory / local calls").isLessThan(50.0);
    }

    @Test
    @DisplayName("BUG-007: Order items are LAZY loaded for count/status queries and loaded via EntityGraph for order views")
    void testOrderLazyLoadingAndEntityGraph() {
        // Query by status (used in WorkloadService for count) - items must NOT be loaded eagerly
        List<Order> readyOrders = orderRepository.findByRestaurantIdAndStatus(1L, "CONFIRMED");
        if (!readyOrders.isEmpty()) {
            Order sample = readyOrders.get(0);
            boolean isItemsLoaded = Persistence.getPersistenceUtil().isLoaded(sample, "items");
            System.out.println("[BUG-007] Lazy check for findByRestaurantIdAndStatus: isLoaded(items) = " + isItemsLoaded);
            assertThat(isItemsLoaded)
                    .as("Order.items must not be loaded eagerly for status queries")
                    .isFalse();
        }

        // Query with EntityGraph (used in Order views and API serialization) - items MUST be loaded in 1 query
        List<Order> ordersWithItems = orderRepository.findByRestaurantIdOrderByCreatedAtDesc(1L);
        assertThat(ordersWithItems).isNotEmpty();
        Order orderWithItems = ordersWithItems.get(0);
        boolean isLoaded = Persistence.getPersistenceUtil().isLoaded(orderWithItems, "items");
        System.out.println("[BUG-007] EntityGraph check for findByRestaurantIdOrderByCreatedAtDesc: isLoaded(items) = " + isLoaded);
        assertThat(isLoaded)
                .as("Order.items must be initialized via EntityGraph in a single query")
                .isTrue();
    }

    @Test
    @DisplayName("BUG-007: Serialization of Orders, KDS Queue, Delayed Orders, and Analytics Overview produces zero LazyInitializationException")
    void testSerializationWithoutLazyInitializationException() throws Exception {
        TenantContext.setRestaurantId(1L);

        // 1. OrderController.getOrders()
        ResponseEntity<List<Order>> ordersResp = orderController.getOrders();
        assertThat(ordersResp.getStatusCode().is2xxSuccessful()).isTrue();
        String ordersJson = objectMapper.writeValueAsString(ordersResp.getBody());
        assertThat(ordersJson).contains("orderId").contains("items");

        // 2. OrderController.getOrder(1L)
        if (!ordersResp.getBody().isEmpty()) {
            Long firstOrderId = ordersResp.getBody().get(0).getOrderId();
            ResponseEntity<Order> singleOrderResp = orderController.getOrder(firstOrderId);
            assertThat(singleOrderResp.getStatusCode().is2xxSuccessful()).isTrue();
            String singleOrderJson = objectMapper.writeValueAsString(singleOrderResp.getBody());
            assertThat(singleOrderJson).contains("orderCode");
        }

        // 3. KitchenController.getKitchenQueue()
        ResponseEntity<?> kdsResp = kitchenController.getKitchenQueue(null);
        assertThat(kdsResp.getStatusCode().is2xxSuccessful()).isTrue();
        String kdsJson = objectMapper.writeValueAsString(kdsResp.getBody());
        assertThat(kdsJson).isNotNull();

        // 4. OrderController.getDelayedOrders()
        ResponseEntity<?> delayedResp = orderController.getDelayedOrders();
        assertThat(delayedResp.getStatusCode().is2xxSuccessful()).isTrue();
        String delayedJson = objectMapper.writeValueAsString(delayedResp.getBody());
        assertThat(delayedJson).isNotNull();

        // 5. AnalyticsController.getOverview()
        ResponseEntity<Map<String, Object>> analyticsResp = analyticsController.getOverview("daily");
        assertThat(analyticsResp.getStatusCode().is2xxSuccessful()).isTrue();
        String analyticsJson = objectMapper.writeValueAsString(analyticsResp.getBody());
        assertThat(analyticsJson).contains("grossRevenue");
    }
}
