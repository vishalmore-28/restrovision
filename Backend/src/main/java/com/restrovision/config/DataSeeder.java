package com.restrovision.config;

import com.restrovision.domain.*;
import com.restrovision.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Automatically seeds initial demo data when running in local development or in-memory H2 mode with local profile.
 * If the database already contains users (e.g. production Oracle or populated DB), it safely skips.
 */
@Component
@Profile("local")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantTableRepository tableRepository;
    private final EmployeeRepository employeeRepository;
    private final KitchenStationRepository kitchenStationRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final CameraRepository cameraRepository;

    // Shared BCrypt hash for passwords (Admin@123, Manager@123, Waiter@123, Chef@123, Clean@123)
    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$wE47P8wK0csmQGf6z5QWueK5Yh8B2P7bL/4kC5yY71C9T9jB0a7ea";

    public DataSeeder(
            UserRepository userRepository,
            RestaurantRepository restaurantRepository,
            RestaurantTableRepository tableRepository,
            EmployeeRepository employeeRepository,
            KitchenStationRepository kitchenStationRepository,
            EdgeNodeRepository edgeNodeRepository,
            CameraRepository cameraRepository) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.tableRepository = tableRepository;
        this.employeeRepository = employeeRepository;
        this.kitchenStationRepository = kitchenStationRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.cameraRepository = cameraRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        try {
            if (userRepository.count() > 0) {
                log.info("[DataSeeder] Database already populated with {} users. Skipping automatic seed.", userRepository.count());
                return;
            }

            log.info("[DataSeeder] Empty database detected. Seeding baseline restaurant data for local run...");

            // 1. Restaurant
            Restaurant rest = new Restaurant("The Bombay Grill & Bistro", "Asia/Kolkata", "ACTIVE");
            rest = restaurantRepository.save(rest);
            Long restId = rest.getRestaurantId();

            // 2. Preset Users
            User owner = new User(restId, "owner@restrovision.local", DEFAULT_PASSWORD_HASH, "Rajesh Singhania", "ACTIVE");
            owner.setRoles(Set.of("OWNER", "MANAGER"));
            owner = userRepository.save(owner);

            User manager = new User(restId, "manager@restrovision.local", DEFAULT_PASSWORD_HASH, "Ananya Sharma", "ACTIVE");
            manager.setRoles(Set.of("MANAGER"));
            manager = userRepository.save(manager);

            User waiter = new User(restId, "waiter1@restrovision.local", DEFAULT_PASSWORD_HASH, "Kunal Verma", "ACTIVE");
            waiter.setRoles(Set.of("WAITER"));
            waiter = userRepository.save(waiter);

            User chef = new User(restId, "chef@restrovision.local", DEFAULT_PASSWORD_HASH, "Vikram Rawat", "ACTIVE");
            chef.setRoles(Set.of("KITCHEN"));
            chef = userRepository.save(chef);

            User cleaner = new User(restId, "cleaning@restrovision.local", DEFAULT_PASSWORD_HASH, "Ramesh Das", "ACTIVE");
            cleaner.setRoles(Set.of("CLEANING"));
            cleaner = userRepository.save(cleaner);

            // 3. Employees
            Employee empManager = new Employee(restId, manager.getUserId(), "EMP-001", "Ananya Sharma", "MANAGER", "ACTIVE");
            Employee empWaiter = new Employee(restId, waiter.getUserId(), "EMP-002", "Kunal Verma", "WAITER", "ACTIVE");
            Employee empChef = new Employee(restId, chef.getUserId(), "EMP-003", "Vikram Rawat", "KITCHEN", "ACTIVE");
            Employee empCleaner = new Employee(restId, cleaner.getUserId(), "EMP-004", "Ramesh Das", "CLEANING", "ACTIVE");
            employeeRepository.saveAll(List.of(empManager, empWaiter, empChef, empCleaner));

            // 4. Restaurant Tables
            RestaurantTable t1 = new RestaurantTable(restId, "T01", 2, "AVAILABLE");
            RestaurantTable t2 = new RestaurantTable(restId, "T02", 4, "OCCUPIED");
            RestaurantTable t3 = new RestaurantTable(restId, "T03", 4, "AVAILABLE");
            RestaurantTable t4 = new RestaurantTable(restId, "T04", 6, "CLEANING");
            RestaurantTable t5 = new RestaurantTable(restId, "T05", 2, "AVAILABLE");
            RestaurantTable t6 = new RestaurantTable(restId, "T06", 4, "AVAILABLE");
            RestaurantTable t7 = new RestaurantTable(restId, "T07", 8, "RESERVED");
            RestaurantTable t8 = new RestaurantTable(restId, "T08", 4, "AVAILABLE");
            RestaurantTable t9 = new RestaurantTable(restId, "T09", 2, "AVAILABLE");
            RestaurantTable t10 = new RestaurantTable(restId, "T10", 6, "OUT_OF_SERVICE");
            tableRepository.saveAll(List.of(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10));

            // 5. Kitchen Stations
            KitchenStation ks1 = new KitchenStation(restId, "Tandoor & Grill");
            KitchenStation ks2 = new KitchenStation(restId, "Curry & Saute");
            KitchenStation ks3 = new KitchenStation(restId, "Cold Appetizers");
            KitchenStation ks4 = new KitchenStation(restId, "Beverage Bar");
            kitchenStationRepository.saveAll(List.of(ks1, ks2, ks3, ks4));

            // 6. Edge Node & Cameras
            EdgeNode edgeNode = new EdgeNode("edge-pune-01", restId, "Edge AI Machine (Pune Main Floor)", "ONLINE");
            edgeNodeRepository.save(edgeNode);

            Camera cam1 = new Camera(restId, "edge-pune-01", "CAM-01: Main Dining Area", "Ceiling 360 coverage", 2.0);
            Camera cam2 = new Camera(restId, "edge-pune-01", "CAM-02: Bar & Entrance Pass", "Wall mount entrance", 2.0);
            cameraRepository.saveAll(List.of(cam1, cam2));

            log.info("[DataSeeder] Successfully seeded baseline restaurant, users, tables, and cameras for Restaurant ID {}", restId);
        } catch (Exception e) {
            log.warn("[DataSeeder] Note during database seed: {}", e.getMessage());
        }
    }
}
