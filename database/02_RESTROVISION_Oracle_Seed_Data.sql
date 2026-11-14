-- ==============================================================================
-- RESTROVISION Production Seed Script for Oracle 19c+
-- Aligned with Master Specification v2.0 §§10.1-10.4 & Section 3
-- ==============================================================================

-- 1. Restaurant
INSERT INTO restaurants (name, timezone, status) 
VALUES ('The Bombay Grill & Bistro', 'Asia/Kolkata', 'ACTIVE');

-- 2. Restaurant Settings
INSERT INTO restaurant_settings (restaurant_id, settings_json)
VALUES (1, '{"waiting_time_attention_sec": 120, "waiting_time_escalated_sec": 300, "waiting_time_critical_sec": 600, "prep_delay_factor": 1.5, "staff_lock_sec": 120}');

-- 3. Users (Bcrypt Passwords)
-- Passwords: Admin@123, Manager@123, Waiter@123, Chef@123, Clean@123
INSERT INTO users (restaurant_id, email, password_hash, full_name, status)
VALUES (1, 'owner@restrovision.local', '$2a$10$wE47P8wK0csmQGf6z5QWueK5Yh8B2P7bL/4kC5yY71C9T9jB0a7ea', 'Rajesh Singhania', 'ACTIVE');

INSERT INTO users (restaurant_id, email, password_hash, full_name, status)
VALUES (1, 'manager@restrovision.local', '$2a$10$wE47P8wK0csmQGf6z5QWueK5Yh8B2P7bL/4kC5yY71C9T9jB0a7ea', 'Ananya Sharma', 'ACTIVE');

INSERT INTO users (restaurant_id, email, password_hash, full_name, status)
VALUES (1, 'waiter1@restrovision.local', '$2a$10$wE47P8wK0csmQGf6z5QWueK5Yh8B2P7bL/4kC5yY71C9T9jB0a7ea', 'Kunal Verma', 'ACTIVE');

INSERT INTO users (restaurant_id, email, password_hash, full_name, status)
VALUES (1, 'chef@restrovision.local', '$2a$10$wE47P8wK0csmQGf6z5QWueK5Yh8B2P7bL/4kC5yY71C9T9jB0a7ea', 'Vikram Rawat', 'ACTIVE');

INSERT INTO users (restaurant_id, email, password_hash, full_name, status)
VALUES (1, 'cleaning@restrovision.local', '$2a$10$wE47P8wK0csmQGf6z5QWueK5Yh8B2P7bL/4kC5yY71C9T9jB0a7ea', 'Ramesh Das', 'ACTIVE');

-- 4. Roles
INSERT INTO roles (restaurant_id, role_code, role_name) VALUES (1, 'OWNER', 'Restaurant Owner');
INSERT INTO roles (restaurant_id, role_code, role_name) VALUES (1, 'MANAGER', 'Floor Manager');
INSERT INTO roles (restaurant_id, role_code, role_name) VALUES (1, 'WAITER', 'Waitstaff / Server');
INSERT INTO roles (restaurant_id, role_code, role_name) VALUES (1, 'KITCHEN', 'Kitchen Chef');
INSERT INTO roles (restaurant_id, role_code, role_name) VALUES (1, 'CLEANING', 'Housekeeping / Cleaning');

-- 5. User Roles
INSERT INTO user_roles (restaurant_id, user_id, role_id) VALUES (1, 1, 1);
INSERT INTO user_roles (restaurant_id, user_id, role_id) VALUES (1, 2, 2);
INSERT INTO user_roles (restaurant_id, user_id, role_id) VALUES (1, 3, 3);
INSERT INTO user_roles (restaurant_id, user_id, role_id) VALUES (1, 4, 4);
INSERT INTO user_roles (restaurant_id, user_id, role_id) VALUES (1, 5, 5);

-- 6. Employees
INSERT INTO employees (restaurant_id, user_id, employee_code, display_name, job_role, status)
VALUES (1, 2, 'EMP-001', 'Ananya Sharma', 'MANAGER', 'ACTIVE');

INSERT INTO employees (restaurant_id, user_id, employee_code, display_name, job_role, status)
VALUES (1, 3, 'EMP-002', 'Kunal Verma', 'WAITER', 'ACTIVE');

INSERT INTO employees (restaurant_id, user_id, employee_code, display_name, job_role, status)
VALUES (1, 4, 'EMP-003', 'Vikram Rawat', 'KITCHEN', 'ACTIVE');

INSERT INTO employees (restaurant_id, user_id, employee_code, display_name, job_role, status)
VALUES (1, 5, 'EMP-004', 'Ramesh Das', 'CLEANING', 'ACTIVE');

-- 7. Tables (10 Tables)
INSERT INTO restaurant_tables (restaurant_id, table_code, capacity, status, status_source)
VALUES (1, 'T01', 2, 'AVAILABLE', 'SYSTEM');

INSERT INTO restaurant_tables (restaurant_id, table_code, capacity, status, status_source)
VALUES (1, 'T02', 4, 'OCCUPIED', 'AI');

INSERT INTO restaurant_tables (restaurant_id, table_code, capacity, status, status_source)
VALUES (1, 'T03', 4, 'AVAILABLE', 'SYSTEM');

INSERT INTO restaurant_tables (restaurant_id, table_code, capacity, status, status_source)
VALUES (1, 'T04', 6, 'CLEANING', 'AI');

INSERT INTO restaurant_tables (restaurant_id, table_code, capacity, status, status_source)
VALUES (1, 'T05', 2, 'AVAILABLE', 'SYSTEM');

-- 8. Kitchen Stations
INSERT INTO kitchen_stations (restaurant_id, name) VALUES (1, 'Tandoor & Grill');
INSERT INTO kitchen_stations (restaurant_id, name) VALUES (1, 'Curry & Saute');
INSERT INTO kitchen_stations (restaurant_id, name) VALUES (1, 'Cold Appetizers');
INSERT INTO kitchen_stations (restaurant_id, name) VALUES (1, 'Beverage Bar');

-- 9. Edge Nodes & Cameras
INSERT INTO edge_nodes (edge_node_id, restaurant_id, display_name, status)
VALUES ('edge-pune-01', 1, 'Edge AI Machine (Pune Main Floor)', 'ONLINE');

INSERT INTO cameras (restaurant_id, edge_node_id, name, location_desc, credential_ref, state, target_fps)
VALUES (1, 'edge-pune-01', 'CAM-01: Main Dining Area', 'Ceiling 360 degree coverage', 'ref:cam-dining-01', 'CONNECTED', 2.0);

INSERT INTO cameras (restaurant_id, edge_node_id, name, location_desc, credential_ref, state, target_fps)
VALUES (1, 'edge-pune-01', 'CAM-02: Bar & Entrance Pass', 'Wall mount entrance pass', 'ref:cam-entrance-02', 'CONNECTED', 2.0);

-- 10. Model Registry
INSERT INTO model_registry (model_name, version, task, framework, artifact_sha256, status)
VALUES ('person-detector', '1.3.0', 'M1 Person Detector', 'RTMDet / TensorRT-FP16', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'ACTIVE');

INSERT INTO model_registry (model_name, version, task, framework, artifact_sha256, status)
VALUES ('table-state-classifier', '1.1.0', 'M3 Table State', 'MobileNetV3 / ONNX', '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a', 'ACTIVE');

COMMIT;
