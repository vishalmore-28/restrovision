package com.restrovision.config;

import com.restrovision.domain.*;
import com.restrovision.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final KitchenStationRepository stationRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final CameraRepository cameraRepository;
    private final CameraZoneRepository cameraZoneRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final OrderRepository orderRepository;
    private final StaffTaskRepository taskRepository;
    private final AlertRepository alertRepository;
    private final CustomerFeedbackRepository feedbackRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            RestaurantRepository restaurantRepository,
            UserRepository userRepository,
            EmployeeRepository employeeRepository,
            RestaurantTableRepository tableRepository,
            TableSessionRepository sessionRepository,
            KitchenStationRepository stationRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository menuItemRepository,
            CameraRepository cameraRepository,
            CameraZoneRepository cameraZoneRepository,
            EdgeNodeRepository edgeNodeRepository,
            OrderRepository orderRepository,
            StaffTaskRepository taskRepository,
            AlertRepository alertRepository,
            CustomerFeedbackRepository feedbackRepository,
            PasswordEncoder passwordEncoder) {
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.stationRepository = stationRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.cameraRepository = cameraRepository;
        this.cameraZoneRepository = cameraZoneRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.orderRepository = orderRepository;
        this.taskRepository = taskRepository;
        this.alertRepository = alertRepository;
        this.feedbackRepository = feedbackRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (restaurantRepository.count() > 0) {
            return;
        }

        log.info("Seeding RESTROVISION database with initial pilot data...");

        // 1. Restaurant
        Restaurant restaurant = new Restaurant("The Bombay Grill & Bistro", "Asia/Kolkata", "ACTIVE");
        restaurant = restaurantRepository.save(restaurant);
        Long restId = restaurant.getRestaurantId();

        // 2. Users & Roles
        String defaultPw = passwordEncoder.encode("Admin@123");
        User owner = new User(restId, "owner@restrovision.local", defaultPw, "Rajesh Singhania", "ACTIVE");
        owner.setRoles(Set.of("OWNER", "MANAGER"));
        userRepository.save(owner);

        User manager = new User(restId, "manager@restrovision.local", passwordEncoder.encode("Manager@123"), "Ananya Sharma", "ACTIVE");
        manager.setRoles(Set.of("MANAGER"));
        manager = userRepository.save(manager);

        User waiter1 = new User(restId, "waiter1@restrovision.local", passwordEncoder.encode("Waiter@123"), "Kunal Verma", "ACTIVE");
        waiter1.setRoles(Set.of("WAITER"));
        waiter1 = userRepository.save(waiter1);

        User waiter2 = new User(restId, "waiter2@restrovision.local", passwordEncoder.encode("Waiter@123"), "Pooja Hegde", "ACTIVE");
        waiter2.setRoles(Set.of("WAITER"));
        waiter2 = userRepository.save(waiter2);

        User chef = new User(restId, "chef@restrovision.local", passwordEncoder.encode("Chef@123"), "Chef Vikram Rawat", "ACTIVE");
        chef.setRoles(Set.of("KITCHEN"));
        chef = userRepository.save(chef);

        User cleaner = new User(restId, "cleaning@restrovision.local", passwordEncoder.encode("Clean@123"), "Ramesh Das", "ACTIVE");
        cleaner.setRoles(Set.of("CLEANING"));
        cleaner = userRepository.save(cleaner);

        // 3. Employees
        Employee empMgr = employeeRepository.save(new Employee(restId, manager.getUserId(), "EMP-001", "Ananya Sharma", "MANAGER", "ACTIVE"));
        Employee empW1 = employeeRepository.save(new Employee(restId, waiter1.getUserId(), "EMP-002", "Kunal Verma", "WAITER", "ACTIVE"));
        Employee empW2 = employeeRepository.save(new Employee(restId, waiter2.getUserId(), "EMP-003", "Pooja Hegde", "WAITER", "ACTIVE"));
        Employee empChef = employeeRepository.save(new Employee(restId, chef.getUserId(), "EMP-004", "Vikram Rawat", "KITCHEN", "ACTIVE"));
        Employee empClean = employeeRepository.save(new Employee(restId, cleaner.getUserId(), "EMP-005", "Ramesh Das", "CLEANING", "ACTIVE"));

        // 4. Kitchen Stations
        KitchenStation grill = stationRepository.save(new KitchenStation(restId, "Tandoor & Grill"));
        KitchenStation saute = stationRepository.save(new KitchenStation(restId, "Curry & Saute"));
        KitchenStation cold = stationRepository.save(new KitchenStation(restId, "Cold Appetizers & Salads"));
        KitchenStation bar = stationRepository.save(new KitchenStation(restId, "Beverage Bar"));
        KitchenStation dessert = stationRepository.save(new KitchenStation(restId, "Dessert Station"));

        // 5. Menu Categories & Items
        MenuCategory catStarters = categoryRepository.save(new MenuCategory(restId, "Signature Appetizers", 1));
        MenuCategory catMains = categoryRepository.save(new MenuCategory(restId, "Royal Mains", 2));
        MenuCategory catBreads = categoryRepository.save(new MenuCategory(restId, "Artisan Breads", 3));
        MenuCategory catDrinks = categoryRepository.save(new MenuCategory(restId, "Cocktails & Drinks", 4));

        MenuItem item1 = menuItemRepository.save(new MenuItem(restId, catStarters.getCategoryId(), grill.getStationId(), "Paneer Tikka Angara", "Chargrilled cottage cheese with spicy marinade", new BigDecimal("380.00"), 12));
        MenuItem item2 = menuItemRepository.save(new MenuItem(restId, catStarters.getCategoryId(), grill.getStationId(), "Murg Malai Tikka", "Melt-in-mouth chicken kebabs in cardamom cream", new BigDecimal("460.00"), 14));
        MenuItem item3 = menuItemRepository.save(new MenuItem(restId, catStarters.getCategoryId(), cold.getStationId(), "Dahi Ke Kebab", "Crisp yogurt patties with mint relish", new BigDecimal("340.00"), 10));
        MenuItem item4 = menuItemRepository.save(new MenuItem(restId, catMains.getCategoryId(), saute.getStationId(), "Dal Makhani 24-Hour", "Slow cooked black lentils simmered overnight", new BigDecimal("420.00"), 8));
        MenuItem item5 = menuItemRepository.save(new MenuItem(restId, catMains.getCategoryId(), saute.getStationId(), "Butter Chicken Delhi 6", "Classic tandoori chicken in velvety tomato-butter gravy", new BigDecimal("540.00"), 15));
        MenuItem item6 = menuItemRepository.save(new MenuItem(restId, catBreads.getCategoryId(), grill.getStationId(), "Garlic Butter Naan", "Clay oven roasted leavened flatbread", new BigDecimal("95.00"), 5));
        MenuItem item7 = menuItemRepository.save(new MenuItem(restId, catDrinks.getCategoryId(), bar.getStationId(), "Kokum Spiced Cooler", "Tangy kokum fruit with cumin and sparkling water", new BigDecimal("220.00"), 5));
        MenuItem item8 = menuItemRepository.save(new MenuItem(restId, catDrinks.getCategoryId(), bar.getStationId(), "Mango Lassi Frappe", "Alphonso mango pulp blended with rich yogurt", new BigDecimal("190.00"), 4));

        // 6. Restaurant Tables (10 Tables) with secure QR tokens
        RestaurantTable t1 = new RestaurantTable(restId, "T01", 2, "OCCUPIED");
        t1.setQrToken("qr-tok-table-01");
        t1.setAssignedWaiterId(empW1.getEmployeeId());
        t1 = tableRepository.save(t1);

        RestaurantTable t2 = new RestaurantTable(restId, "T02", 4, "OCCUPIED");
        t2.setQrToken("qr-tok-table-02");
        t2.setAssignedWaiterId(empW1.getEmployeeId());
        t2 = tableRepository.save(t2);

        RestaurantTable t3 = new RestaurantTable(restId, "T03", 4, "AVAILABLE");
        t3.setQrToken("qr-tok-table-03");
        t3.setAssignedWaiterId(empW1.getEmployeeId());
        t3 = tableRepository.save(t3);

        RestaurantTable t4 = new RestaurantTable(restId, "T04", 6, "CLEANING");
        t4.setQrToken("qr-tok-table-04");
        t4.setAssignedWaiterId(empW2.getEmployeeId());
        t4 = tableRepository.save(t4);

        RestaurantTable t5 = new RestaurantTable(restId, "T05", 2, "OCCUPIED");
        t5.setQrToken("qr-tok-table-05");
        t5.setAssignedWaiterId(empW2.getEmployeeId());
        t5 = tableRepository.save(t5);

        RestaurantTable t6 = new RestaurantTable(restId, "T06", 4, "AVAILABLE");
        t6.setQrToken("qr-tok-table-06");
        t6.setAssignedWaiterId(empW2.getEmployeeId());
        t6 = tableRepository.save(t6);

        RestaurantTable t7 = new RestaurantTable(restId, "T07", 8, "RESERVED");
        t7.setQrToken("qr-tok-table-07");
        t7 = tableRepository.save(t7);

        RestaurantTable t8 = new RestaurantTable(restId, "T08", 4, "AVAILABLE");
        t8.setQrToken("qr-tok-table-08");
        t8 = tableRepository.save(t8);

        RestaurantTable t9 = new RestaurantTable(restId, "T09", 2, "AVAILABLE");
        t9.setQrToken("qr-tok-table-09");
        t9 = tableRepository.save(t9);

        RestaurantTable t10 = new RestaurantTable(restId, "T10", 6, "OUT_OF_SERVICE");
        t10.setQrToken("qr-tok-table-10");
        t10 = tableRepository.save(t10);

        // Edge node
        edgeNodeRepository.save(new EdgeNode("edge-pune-01", restId, "Edge Node Main Floor", "ONLINE"));

        // 7. Table Sessions for Occupied Tables
        Instant now = Instant.now();
        TableSession s1 = new TableSession(restId, t1.getTableId(), "AI", 2);
        s1.setSeatedAt(now.minus(4, ChronoUnit.MINUTES));
        // Table 1 not attended yet! Will trigger attention alert (elapsed 4 mins)
        s1 = sessionRepository.save(s1);
        t1.setCurrentSessionId(s1.getSessionId());
        tableRepository.save(t1);

        TableSession s2 = new TableSession(restId, t2.getTableId(), "STAFF", 3);
        s2.setSeatedAt(now.minus(25, ChronoUnit.MINUTES));
        s2.setAttendedAt(now.minus(24, ChronoUnit.MINUTES));
        s2.setOrderedAt(now.minus(20, ChronoUnit.MINUTES));
        s2 = sessionRepository.save(s2);
        t2.setCurrentSessionId(s2.getSessionId());
        tableRepository.save(t2);

        TableSession s5 = new TableSession(restId, t5.getTableId(), "AI", 2);
        s5.setSeatedAt(now.minus(12, ChronoUnit.MINUTES));
        s5.setAttendedAt(now.minus(11, ChronoUnit.MINUTES));
        s5 = sessionRepository.save(s5);
        t5.setCurrentSessionId(s5.getSessionId());
        tableRepository.save(t5);

        // 8. Orders
        Order order1 = new Order(restId, s2.getSessionId(), t2.getTableId(), "ORD-84920");
        order1.setStatus("PREPARING");
        OrderItem oi1 = new OrderItem();
        oi1.setRestaurantId(restId);
        oi1.setOrder(order1);
        oi1.setItemId(item2.getItemId());
        oi1.setItemName(item2.getName());
        oi1.setStationId(grill.getStationId());
        oi1.setStationName(grill.getName());
        oi1.setQuantity(2);
        oi1.setUnitPrice(item2.getPrice());
        oi1.setExpectedPrepMinutes(14);
        oi1.setStatus("IN_PREPARATION");
        oi1.setPreparationStartedAt(now.minus(22, ChronoUnit.MINUTES)); // Exceeded expected prep time -> DELAYED!

        OrderItem oi2 = new OrderItem();
        oi2.setRestaurantId(restId);
        oi2.setOrder(order1);
        oi2.setItemId(item4.getItemId());
        oi2.setItemName(item4.getName());
        oi2.setStationId(saute.getStationId());
        oi2.setStationName(saute.getName());
        oi2.setQuantity(1);
        oi2.setUnitPrice(item4.getPrice());
        oi2.setExpectedPrepMinutes(8);
        oi2.setStatus("READY");
        oi2.setPreparationStartedAt(now.minus(18, ChronoUnit.MINUTES));
        oi2.setReadyAt(now.minus(5, ChronoUnit.MINUTES));

        order1.setItems(List.of(oi1, oi2));
        orderRepository.save(order1);

        // 9. Cleaning Tasks
        StaffTask cleanTask = new StaffTask();
        cleanTask.setRestaurantId(restId);
        cleanTask.setTableId(t4.getTableId());
        cleanTask.setTableCode(t4.getTableCode());
        cleanTask.setTaskType("TABLE_CLEANING");
        cleanTask.setPriority("HIGH");
        cleanTask.setStatus("OPEN");
        taskRepository.save(cleanTask);

        // 10. Cameras & Zones
        Camera cam1 = cameraRepository.save(new Camera(restId, "edge-pune-01", "CAM-01: Main Dining Area", "Ceiling-mounted 360 wide angle covering T01-T05", 2.0));
        Camera cam2 = cameraRepository.save(new Camera(restId, "edge-pune-01", "CAM-02: Bar & Entrance Pass", "Wall mount covering entrance and tables T06-T08", 2.0));
        Camera cam3 = cameraRepository.save(new Camera(restId, "edge-pune-01", "CAM-03: Terrace & Patio Lounge", "High resolution outdoor dome covering T09-T10", 2.0));

        cameraZoneRepository.save(new CameraZone(restId, cam1.getCameraId(), "TABLE", t1.getTableId(), "[[120,240],[360,240],[360,480],[120,480]]", 1920, 1080));
        cameraZoneRepository.save(new CameraZone(restId, cam1.getCameraId(), "TABLE", t2.getTableId(), "[[420,240],[680,240],[680,480],[420,480]]", 1920, 1080));
        cameraZoneRepository.save(new CameraZone(restId, cam1.getCameraId(), "FLOOR", null, "[[50,600],[1800,600],[1800,1000],[50,1000]]", 1920, 1080));
        cameraZoneRepository.save(new CameraZone(restId, cam2.getCameraId(), "ENTRANCE", null, "[[80,100],[450,100],[450,900],[80,900]]", 1920, 1080));

        // 11. Alerts
        Alert a1 = new Alert();
        a1.setRestaurantId(restId);
        a1.setAlertType("CUSTOMER_WAITING_ATTENTION");
        a1.setPriority("MEDIUM");
        a1.setStatus("OPEN");
        a1.setDedupeKey("WAITING-" + s1.getSessionId());
        a1.setMessage("Customer seated at Table T01 waiting for first service (> 4 mins). Assigned waiter: Kunal Verma");
        a1.setTableId(t1.getTableId());
        a1.setAssignedRole("WAITER");
        a1.setAssignedEmployeeId(empW1.getEmployeeId());
        alertRepository.save(a1);

        Alert a2 = new Alert();
        a2.setRestaurantId(restId);
        a2.setAlertType("ORDER_DELAYED_PREPARATION");
        a2.setPriority("HIGH");
        a2.setStatus("OPEN");
        a2.setDedupeKey("DELAY-ORD-" + order1.getOrderId());
        a2.setMessage("Kitchen delay alert: Item 'Murg Malai Tikka' on station 'Tandoor & Grill' is at 157% of expected prep time (22m / 14m)!");
        a2.setTableId(t2.getTableId());
        a2.setAssignedRole("KITCHEN");
        alertRepository.save(a2);

        // 12. Customer Feedback sample with M10 IndicBERT Sentiment & Topic Classification
        CustomerFeedback fb1 = new CustomerFeedback();
        fb1.setRestaurantId(restId);
        fb1.setTableId(t4.getTableId());
        fb1.setRating(5);
        fb1.setCommentText("Water refill and table attendance was practically instantaneous after pressing the QR button! Staff was attentive without hovering.");
        fb1.setSentiment("POSITIVE");
        fb1.setCategory("SERVICE_SPEED");
        fb1.setCreatedAt(now.minus(28, ChronoUnit.MINUTES));
        feedbackRepository.save(fb1);

        CustomerFeedback fb2 = new CustomerFeedback();
        fb2.setRestaurantId(restId);
        fb2.setTableId(t2.getTableId());
        fb2.setRating(5);
        fb2.setCommentText("Dal makhani was velvety and perfectly tempered. Garlic naan was crisp right off the tandoor pass. Top marks!");
        fb2.setSentiment("POSITIVE");
        fb2.setCategory("FOOD_QUALITY");
        fb2.setCreatedAt(now.minus(74, ChronoUnit.MINUTES));
        feedbackRepository.save(fb2);

        CustomerFeedback fb3 = new CustomerFeedback();
        fb3.setRestaurantId(restId);
        fb3.setTableId(t1.getTableId());
        fb3.setRating(4);
        fb3.setCommentText("Delicious food and pleasant ambience. Main course took about 18 minutes during the peak lunch rush, but hot and fresh.");
        fb3.setSentiment("NEUTRAL");
        fb3.setCategory("SERVICE_SPEED");
        fb3.setCreatedAt(now.minus(125, ChronoUnit.MINUTES));
        feedbackRepository.save(fb3);

        CustomerFeedback fb4 = new CustomerFeedback();
        fb4.setRestaurantId(restId);
        fb4.setTableId(t5.getTableId());
        fb4.setRating(5);
        fb4.setCommentText("Immaculately clean table and cutlery. We saw the housekeeping team sanitize the entire booth before seating us.");
        fb4.setSentiment("POSITIVE");
        fb4.setCategory("CLEANLINESS");
        fb4.setCreatedAt(now.minus(220, ChronoUnit.MINUTES));
        feedbackRepository.save(fb4);

        CustomerFeedback fb5 = new CustomerFeedback();
        fb5.setRestaurantId(restId);
        fb5.setTableId(t3.getTableId());
        fb5.setRating(2);
        fb5.setCommentText("Waited almost 4 minutes initially before server arrived to take drink orders during evening rush.");
        fb5.setSentiment("NEGATIVE");
        fb5.setCategory("SERVICE_SPEED");
        fb5.setCreatedAt(now.minus(310, ChronoUnit.MINUTES));
        feedbackRepository.save(fb5);

        log.info("Pilot seed data successfully loaded into memory!");
    }
}
