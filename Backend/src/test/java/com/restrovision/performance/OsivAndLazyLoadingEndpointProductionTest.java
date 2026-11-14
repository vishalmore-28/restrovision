package com.restrovision.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restrovision.controller.AnalyticsController;
import com.restrovision.controller.OrderController;
import com.restrovision.controller.TableController;
import com.restrovision.domain.CustomerFeedback;
import com.restrovision.domain.Employee;
import com.restrovision.domain.Order;
import com.restrovision.domain.OrderItem;
import com.restrovision.domain.Restaurant;
import com.restrovision.domain.RestaurantTable;
import com.restrovision.domain.TableSession;
import com.restrovision.domain.User;
import com.restrovision.dto.TableDtos.TableViewDto;
import com.restrovision.repository.*;
import com.restrovision.security.JwtTokenProvider;
import com.restrovision.security.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG-022D Production Verification Test Suite:
 * Disabling Open Session in View (spring.jpa.open-in-view=false) and verifying
 * that all critical endpoints function correctly without throwing LazyInitializationException
 * during JSON serialization outside transaction boundaries, while enforcing bounded SQL query counts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OsivAndLazyLoadingEndpointProductionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private OrderController orderController;

    @Autowired
    private TableController tableController;

    @Autowired
    private AnalyticsController analyticsController;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private TableSessionRepository sessionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerFeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ObjectMapper objectMapper;

    private SessionFactory sessionFactory;
    private Statistics statistics;

    private Restaurant populatedRestaurant;
    private Restaurant emptyRestaurant;
    private List<Order> seededOrders = new ArrayList<>();
    private List<RestaurantTable> seededTables = new ArrayList<>();
    private List<TableSession> seededSessions = new ArrayList<>();
    private Employee seededWaiter;
    private CustomerFeedback seededFeedback;
    private User userChef1;
    private User userWaiter;
    private User userManager;
    private User userChef2;

    @BeforeAll
    void setUpAll() {
        sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // Seed isolated restaurants for empty and populated states
        emptyRestaurant = restaurantRepository.save(new Restaurant("OSIV Empty Restaurant", "Asia/Kolkata", "ACTIVE"));
        populatedRestaurant = restaurantRepository.save(new Restaurant("OSIV Populated Restaurant", "Asia/Kolkata", "ACTIVE"));

        // Seed users with assigned roles for security and tenant isolation testing
        userChef1 = new User(populatedRestaurant.getRestaurantId(), "chef_osiv1@restrovision.local", passwordEncoder.encode("Chef@123"), "Chef OSIV 1", "ACTIVE");
        userChef1.setRoles(Set.of("KITCHEN"));
        userChef1 = userRepository.save(userChef1);

        userWaiter = new User(populatedRestaurant.getRestaurantId(), "waiter_osiv@restrovision.local", passwordEncoder.encode("Waiter@123"), "Waiter OSIV", "ACTIVE");
        userWaiter.setRoles(Set.of("WAITER"));
        userWaiter = userRepository.save(userWaiter);

        userManager = new User(populatedRestaurant.getRestaurantId(), "mgr_osiv@restrovision.local", passwordEncoder.encode("Mgr@123"), "Manager OSIV", "ACTIVE");
        userManager.setRoles(Set.of("MANAGER"));
        userManager = userRepository.save(userManager);

        userChef2 = new User(emptyRestaurant.getRestaurantId(), "chef_osiv2@restrovision.local", passwordEncoder.encode("Chef@123"), "Chef OSIV 2", "ACTIVE");
        userChef2.setRoles(Set.of("KITCHEN"));
        userChef2 = userRepository.save(userChef2);

        // Seed populated restaurant data
        seededWaiter = employeeRepository.save(new Employee(populatedRestaurant.getRestaurantId(), userWaiter.getUserId(), "EMP-OSIV", "OSIV Waiter", "WAITER", "ACTIVE"));

        for (int i = 1; i <= 5; i++) {
            RestaurantTable table = new RestaurantTable(populatedRestaurant.getRestaurantId(), "T-OSIV-" + i, 4, "OCCUPIED");
            table.setAssignedWaiterId(seededWaiter.getEmployeeId());
            table = tableRepository.save(table);

            TableSession session = new TableSession(populatedRestaurant.getRestaurantId(), table.getTableId(), "STAFF", 4);
            session.setStatus("OCCUPIED");
            session.setSeatedAt(Instant.now().minusSeconds(300));
            session = sessionRepository.save(session);

            table.setCurrentSessionId(session.getSessionId());
            table = tableRepository.save(table);

            seededTables.add(table);
            seededSessions.add(session);
        }

        // Seed orders with items
        for (int i = 1; i <= 3; i++) {
            Order order = new Order(populatedRestaurant.getRestaurantId(), seededSessions.get(i - 1).getSessionId(), seededTables.get(i - 1).getTableId(), "ORD-OSIV-" + i);
            order.setStatus(i == 1 ? "CONFIRMED" : (i == 2 ? "PREPARING" : "READY"));
            order = orderRepository.save(order);

            for (int j = 1; j <= 2; j++) {
                OrderItem item = new OrderItem();
                item.setRestaurantId(populatedRestaurant.getRestaurantId());
                item.setOrder(order);
                item.setItemId((long) (100 + j));
                item.setItemName("Dish " + j + " for Order " + i);
                item.setStationId(1L);
                item.setStationName("Tandoor & Grill");
                item.setQuantity(j);
                item.setUnitPrice(new BigDecimal("150.00"));
                item.setExpectedPrepMinutes(12);
                item.setStatus(i == 1 ? "CONFIRMED" : (i == 2 ? "IN_PREPARATION" : "READY"));
                if ("IN_PREPARATION".equals(item.getStatus())) {
                    item.setPreparationStartedAt(Instant.now().minusSeconds(60));
                }
                item = orderItemRepository.save(item);
                order.getItems().add(item);
            }
            seededOrders.add(order);
        }

        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setRestaurantId(populatedRestaurant.getRestaurantId());
        feedback.setSessionId(seededSessions.get(0).getSessionId());
        feedback.setRating(5);
        feedback.setCommentText("Outstanding dining experience and food!");
        feedback.setSentiment("POSITIVE");
        feedback.setCategory("FOOD_QUALITY");
        seededFeedback = feedbackRepository.save(feedback);
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "manager@restrovision.local", null,
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"),
                                new SimpleGrantedAuthority("ROLE_MANAGER"),
                                new SimpleGrantedAuthority("ROLE_WAITER"),
                                new SimpleGrantedAuthority("ROLE_KITCHEN"))
                )
        );
        statistics.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        statistics.clear();
    }

    @AfterAll
    void tearDownAll() {
        if (seededFeedback != null && seededFeedback.getFeedbackId() != null) {
            feedbackRepository.delete(seededFeedback);
        }
        for (Order order : seededOrders) {
            orderItemRepository.deleteAll(order.getItems());
            orderRepository.delete(order);
        }
        for (RestaurantTable t : tableRepository.findAllById(seededTables.stream().map(RestaurantTable::getTableId).toList())) {
            t.setCurrentSessionId(null);
            tableRepository.save(t);
        }
        sessionRepository.deleteAllById(seededSessions.stream().map(TableSession::getSessionId).toList());
        tableRepository.deleteAllById(seededTables.stream().map(RestaurantTable::getTableId).toList());
        if (seededWaiter != null) {
            employeeRepository.delete(seededWaiter);
        }
        if (userChef1 != null || userWaiter != null || userManager != null || userChef2 != null) {
            userRepository.deleteAll(List.of(userChef1, userWaiter, userManager, userChef2));
        }
        if (emptyRestaurant != null) {
            restaurantRepository.delete(emptyRestaurant);
        }
        if (populatedRestaurant != null) {
            restaurantRepository.delete(populatedRestaurant);
        }
    }

    // =========================================================================
    // Endpoint 1: /api/v1/orders
    // =========================================================================

    @Test
    @DisplayName("BUG-022D: Endpoint 1 - GET /api/v1/orders (Empty state: 0 LazyInitializationException, queries <= 1)")
    void testOrdersEndpoint_EmptyState() {
        TenantContext.setRestaurantId(emptyRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<List<Order>> response = orderController.getOrders();

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        assertThat(executedQueries).isLessThanOrEqualTo(1L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isEqualTo("[]");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUG-022D: Endpoint 1 - GET /api/v1/orders (Populated state: 0 LazyInitializationException, queries <= 2 via @EntityGraph)")
    void testOrdersEndpoint_PopulatedState() {
        TenantContext.setRestaurantId(populatedRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<List<Order>> response = orderController.getOrders();

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(seededOrders.size());
        // @EntityGraph fetches orders and items in a single query (or at most 2)
        assertThat(executedQueries).isLessThanOrEqualTo(2L);

        // Outside-transaction Jackson serialization simulation (must not trigger LazyInitializationException)
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isNotEmpty();
            assertThat(json).contains("ORD-OSIV-1");
            assertThat(json).contains("Dish 1 for Order 1");
        }).doesNotThrowAnyException();
    }

    // =========================================================================
    // Endpoint 2: /api/v1/orders/{id}
    // =========================================================================

    @Test
    @DisplayName("BUG-022D: Endpoint 2 - GET /api/v1/orders/{id} (Empty/Not found state: 404, queries <= 1)")
    void testOrderByIdEndpoint_EmptyOrNotFoundState() {
        TenantContext.setRestaurantId(emptyRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<Order> response = orderController.getOrder(9999999L);

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(executedQueries).isLessThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("BUG-022D: Endpoint 2 - GET /api/v1/orders/{id} (Populated state: 0 LazyInitializationException, queries <= 2)")
    void testOrderByIdEndpoint_PopulatedState() {
        TenantContext.setRestaurantId(populatedRestaurant.getRestaurantId());
        Order targetOrder = seededOrders.get(0);
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<Order> response = orderController.getOrder(targetOrder.getOrderId());

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderCode()).isEqualTo(targetOrder.getOrderCode());
        assertThat(executedQueries).isLessThanOrEqualTo(2L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isNotEmpty();
            assertThat(json).contains(targetOrder.getOrderCode());
            assertThat(json).contains("Dish 1 for Order 1");
        }).doesNotThrowAnyException();
    }

    // =========================================================================
    // Endpoint 3: /api/v1/orders/kds-queue
    // =========================================================================

    @Test
    @DisplayName("BUG-022D: Endpoint 3 - GET /api/v1/orders/kds-queue (Empty state: 0 LazyInitializationException, queries <= 1)")
    void testKdsQueueEndpoint_EmptyState() {
        TenantContext.setRestaurantId(emptyRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<List<OrderItem>> response = orderController.getKdsQueue(null);

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        assertThat(executedQueries).isLessThanOrEqualTo(1L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isEqualTo("[]");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUG-022D: Endpoint 3 - GET /api/v1/orders/kds-queue (Populated state: 0 LazyInitializationException, queries <= 2)")
    void testKdsQueueEndpoint_PopulatedState() {
        TenantContext.setRestaurantId(populatedRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<List<OrderItem>> response = orderController.getKdsQueue(null);

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        // Items with status CONFIRMED and IN_PREPARATION are returned
        assertThat(executedQueries).isLessThanOrEqualTo(2L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isNotEmpty();
            assertThat(json).contains("CONFIRMED");
            // Verify @JsonIgnore on Order item order proxy prevents reverse lazy loading
            assertThat(json).doesNotContain("orderCode");
        }).doesNotThrowAnyException();
    }

    // =========================================================================
    // Endpoint 4: /api/v1/analytics/overview
    // =========================================================================

    @Test
    @DisplayName("BUG-022D: Endpoint 4 - GET /api/v1/analytics/overview (Empty state: 0 LazyInitializationException, queries <= 10)")
    void testAnalyticsOverviewEndpoint_EmptyState() {
        TenantContext.setRestaurantId(emptyRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<Map<String, Object>> response = analyticsController.getOverview("daily");

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(executedQueries).isLessThanOrEqualTo(10L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isNotEmpty();
            assertThat(json).contains("Daily Review");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUG-022D: Endpoint 4 - GET /api/v1/analytics/overview (Populated state: 0 LazyInitializationException, queries <= 10)")
    void testAnalyticsOverviewEndpoint_PopulatedState() {
        TenantContext.setRestaurantId(populatedRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<Map<String, Object>> response = analyticsController.getOverview("daily");

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(executedQueries).isLessThanOrEqualTo(10L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isNotEmpty();
            assertThat(json).contains("Daily Review");
        }).doesNotThrowAnyException();
    }

    // =========================================================================
    // Endpoint 5: /api/v1/tables
    // =========================================================================

    @Test
    @DisplayName("BUG-022D: Endpoint 5 - GET /api/v1/tables (Empty state: 0 LazyInitializationException, queries <= 1)")
    void testTablesEndpoint_EmptyState() {
        TenantContext.setRestaurantId(emptyRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<List<TableViewDto>> response = tableController.getTables();

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        assertThat(executedQueries).isLessThanOrEqualTo(1L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isEqualTo("[]");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUG-022D: Endpoint 5 - GET /api/v1/tables (Populated state: 0 LazyInitializationException, queries <= 3 via batch fetch)")
    void testTablesEndpoint_PopulatedState() {
        TenantContext.setRestaurantId(populatedRestaurant.getRestaurantId());
        statistics.clear();
        long queriesBefore = statistics.getPrepareStatementCount();

        ResponseEntity<List<TableViewDto>> response = tableController.getTables();

        long queriesAfter = statistics.getPrepareStatementCount();
        long executedQueries = queriesAfter - queriesBefore;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(5);
        // TableViewDto batch query optimization guarantees <= 3 SQL queries
        assertThat(executedQueries).isLessThanOrEqualTo(3L);

        // Outside-transaction Jackson serialization simulation
        assertThatCode(() -> {
            String json = objectMapper.writeValueAsString(response.getBody());
            assertThat(json).isNotEmpty();
            assertThat(json).contains("T-OSIV-1");
            assertThat(json).contains("OSIV Waiter");
        }).doesNotThrowAnyException();
    }

    // =========================================================================
    // Security & Tenant Isolation Tests for /api/v1/orders/kds-queue
    // =========================================================================

    @Test
    @DisplayName("BUG-022D Security: GET /api/v1/orders/kds-queue unauthenticated request returns 401 Unauthorized")
    void testKdsQueue_Unauthenticated_ReturnsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        mockMvc.perform(get("/api/v1/orders/kds-queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BUG-022D Security: GET /api/v1/orders/kds-queue unauthorized role (ROLE_WAITER) returns 403 Forbidden")
    void testKdsQueue_UnauthorizedRole_ReturnsForbidden() throws Exception {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        String waiterToken = tokenProvider.generateAccessToken(
                userWaiter.getUserId(), populatedRestaurant.getRestaurantId(), userWaiter.getEmail(), Set.of("ROLE_WAITER"));

        mockMvc.perform(get("/api/v1/orders/kds-queue")
                .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BUG-022D Security: GET /api/v1/orders/kds-queue authorized roles (ROLE_KITCHEN, ROLE_MANAGER) succeed")
    void testKdsQueue_AuthorizedRoles_ReturnsOk() throws Exception {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        String kitchenToken = tokenProvider.generateAccessToken(
                userChef1.getUserId(), populatedRestaurant.getRestaurantId(), userChef1.getEmail(), Set.of("ROLE_KITCHEN"));

        mockMvc.perform(get("/api/v1/orders/kds-queue")
                .header("Authorization", "Bearer " + kitchenToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].itemName").exists())
                .andExpect(jsonPath("$[0].stationName").value("Tandoor & Grill"))
                .andExpect(jsonPath("$[0].quantity").exists())
                .andExpect(jsonPath("$[0].unitPrice").exists())
                .andExpect(jsonPath("$[0].expectedPrepMinutes").exists());

        SecurityContextHolder.clearContext();
        TenantContext.clear();
        String managerToken = tokenProvider.generateAccessToken(
                userManager.getUserId(), populatedRestaurant.getRestaurantId(), userManager.getEmail(), Set.of("ROLE_MANAGER"));

        mockMvc.perform(get("/api/v1/orders/kds-queue")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("BUG-022D Security: GET /api/v1/orders/kds-queue cross-tenant isolation enforcement (Negative Cross-Tenant Test)")
    void testKdsQueue_CrossTenantIsolation_StrictlyScoped() throws Exception {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        String tenant1Token = tokenProvider.generateAccessToken(
                userChef1.getUserId(), populatedRestaurant.getRestaurantId(), userChef1.getEmail(), Set.of("ROLE_KITCHEN"));

        // Tenant 1 sees seeded orders belonging to populatedRestaurant
        mockMvc.perform(get("/api/v1/orders/kds-queue")
                .header("Authorization", "Bearer " + tenant1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].restaurantId").value(populatedRestaurant.getRestaurantId()));

        SecurityContextHolder.clearContext();
        TenantContext.clear();
        String tenant2Token = tokenProvider.generateAccessToken(
                userChef2.getUserId(), emptyRestaurant.getRestaurantId(), userChef2.getEmail(), Set.of("ROLE_KITCHEN"));

        // Tenant 2 must NOT see any items from Tenant 1 (empty list returned)
        mockMvc.perform(get("/api/v1/orders/kds-queue")
                .header("Authorization", "Bearer " + tenant2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // =========================================================================
    // API Contract & Frontend Compatibility Verification
    // =========================================================================

    @Test
    @DisplayName("BUG-022D Contract: Verify orders and KDS response fields match frontend requirements and @JsonIgnore prevents recursion")
    void testOrdersAndKdsResponseJsonCompatibility_AllRequiredFrontendFieldsPresent() throws Exception {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        String token = tokenProvider.generateAccessToken(
                userChef1.getUserId(), populatedRestaurant.getRestaurantId(), userChef1.getEmail(), Set.of("ROLE_KITCHEN"));

        // 1. Verify GET /api/v1/orders contract (consumed by KitchenKds.jsx loadData)
        String ordersJson = mockMvc.perform(get("/api/v1/orders")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").exists())
                .andExpect(jsonPath("$[0].orderCode").exists())
                .andExpect(jsonPath("$[0].tableId").exists())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].items").isArray())
                .andExpect(jsonPath("$[0].items[0].orderItemId").exists())
                .andExpect(jsonPath("$[0].items[0].itemName").exists())
                .andExpect(jsonPath("$[0].items[0].quantity").exists())
                .andExpect(jsonPath("$[0].items[0].unitPrice").exists())
                .andExpect(jsonPath("$[0].items[0].expectedPrepMinutes").exists())
                .andExpect(jsonPath("$[0].items[0].status").exists())
                .andReturn().getResponse().getContentAsString();

        // Ensure child item does not contain back-reference "order" (preventing cyclic serialization & lazy loading)
        assertThat(ordersJson).doesNotContain("\"items\":[{\"order\":");

        // 2. Verify GET /api/v1/orders/kds-queue contract
        mockMvc.perform(get("/api/v1/orders/kds-queue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderItemId").exists())
                .andExpect(jsonPath("$[0].restaurantId").value(populatedRestaurant.getRestaurantId()))
                .andExpect(jsonPath("$[0].itemName").exists())
                .andExpect(jsonPath("$[0].stationName").value("Tandoor & Grill"))
                .andExpect(jsonPath("$[0].quantity").exists())
                .andExpect(jsonPath("$[0].unitPrice").exists())
                .andExpect(jsonPath("$[0].expectedPrepMinutes").exists())
                .andExpect(jsonPath("$[0].status").exists());
    }
}
