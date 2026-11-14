-- RESTROVISION Oracle 19c+ core schema, derived from Master Specification v2.0 §§10.1-10.4.
-- Engineering baseline only: NOT yet executed against an Oracle instance.
-- Apply to a fresh schema using Flyway/Liquibase; test tenant isolation and all constraints.

CREATE TABLE restaurants (
 restaurant_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 name VARCHAR2(160) NOT NULL, timezone VARCHAR2(64) DEFAULT 'Asia/Kolkata' NOT NULL,
 status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED','CLOSED')),
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL
);
CREATE TABLE restaurant_settings (
 restaurant_id NUMBER PRIMARY KEY REFERENCES restaurants(restaurant_id),
 settings_json CLOB DEFAULT '{}' NOT NULL CHECK(settings_json IS JSON),
 updated_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL
);
CREATE TABLE users (
 user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 email VARCHAR2(254) NOT NULL, password_hash VARCHAR2(255) NOT NULL,
 full_name VARCHAR2(160) NOT NULL, status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL
 CHECK(status IN ('ACTIVE','DISABLED','LOCKED')),
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT uq_user_email UNIQUE(restaurant_id,email),
 CONSTRAINT uq_user_tenant UNIQUE(restaurant_id,user_id)
);
CREATE TABLE roles (
 role_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 role_code VARCHAR2(40) NOT NULL, role_name VARCHAR2(80) NOT NULL,
 CONSTRAINT uq_role_code UNIQUE(restaurant_id,role_code),
 CONSTRAINT uq_role_tenant UNIQUE(restaurant_id,role_id)
);
CREATE TABLE user_roles (
 restaurant_id NUMBER NOT NULL, user_id NUMBER NOT NULL, role_id NUMBER NOT NULL,
 assigned_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT pk_user_roles PRIMARY KEY(restaurant_id,user_id,role_id),
 CONSTRAINT fk_ur_user FOREIGN KEY(restaurant_id,user_id) REFERENCES users(restaurant_id,user_id),
 CONSTRAINT fk_ur_role FOREIGN KEY(restaurant_id,role_id) REFERENCES roles(restaurant_id,role_id)
);
CREATE TABLE refresh_tokens (
 refresh_token_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL, user_id NUMBER NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
 expires_at TIMESTAMP NOT NULL, revoked_at TIMESTAMP,
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT fk_refresh_user FOREIGN KEY(restaurant_id,user_id) REFERENCES users(restaurant_id,user_id)
);
CREATE TABLE audit_logs (
 audit_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), actor_user_id NUMBER,
 action VARCHAR2(100) NOT NULL, entity_type VARCHAR2(80) NOT NULL, entity_id VARCHAR2(80),
 before_json CLOB CHECK(before_json IS JSON), after_json CLOB CHECK(after_json IS JSON),
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL
);
CREATE TABLE employees (
 employee_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), user_id NUMBER,
 employee_code VARCHAR2(40) NOT NULL, display_name VARCHAR2(160) NOT NULL,
 job_role VARCHAR2(20) NOT NULL CHECK(job_role IN ('MANAGER','WAITER','KITCHEN','CLEANING')),
 status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL CHECK(status IN ('ACTIVE','INACTIVE')),
 CONSTRAINT uq_employee_code UNIQUE(restaurant_id,employee_code),
 CONSTRAINT uq_employee_tenant UNIQUE(restaurant_id,employee_id),
 CONSTRAINT fk_employee_user FOREIGN KEY(restaurant_id,user_id) REFERENCES users(restaurant_id,user_id)
);
CREATE TABLE shifts (
 shift_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), employee_id NUMBER NOT NULL,
 starts_at TIMESTAMP NOT NULL, ends_at TIMESTAMP NOT NULL,
 status VARCHAR2(20) DEFAULT 'SCHEDULED' NOT NULL CHECK(status IN ('SCHEDULED','ACTIVE','COMPLETED','CANCELLED')),
 CONSTRAINT ck_shift_time CHECK(ends_at>starts_at),
 CONSTRAINT fk_shift_employee FOREIGN KEY(restaurant_id,employee_id) REFERENCES employees(restaurant_id,employee_id)
);
CREATE TABLE restaurant_tables (
 table_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 table_code VARCHAR2(20) NOT NULL, capacity NUMBER(3) NOT NULL CHECK(capacity>0),
 status VARCHAR2(20) DEFAULT 'AVAILABLE' NOT NULL
 CHECK(status IN ('AVAILABLE','OCCUPIED','CLEANING','RESERVED','OUT_OF_SERVICE')),
 status_source VARCHAR2(10) DEFAULT 'SYSTEM' NOT NULL CHECK(status_source IN ('AI','STAFF','SYSTEM')),
 status_changed_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 version NUMBER DEFAULT 0 NOT NULL,
 CONSTRAINT uq_table_code UNIQUE(restaurant_id,table_code),
 CONSTRAINT uq_table_tenant UNIQUE(restaurant_id,table_id)
);
CREATE TABLE table_sessions (
 session_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), table_id NUMBER NOT NULL,
 status VARCHAR2(10) DEFAULT 'ACTIVE' NOT NULL CHECK(status IN ('ACTIVE','CLOSED')),
 opened_by VARCHAR2(10) NOT NULL CHECK(opened_by IN ('AI','STAFF')),
 seated_at TIMESTAMP NOT NULL, attended_at TIMESTAMP, ordered_at TIMESTAMP,
 departed_at TIMESTAMP, closed_at TIMESTAMP, party_size_est NUMBER(3),
 CONSTRAINT uq_session_tenant UNIQUE(restaurant_id,session_id),
 CONSTRAINT fk_session_table FOREIGN KEY(restaurant_id,table_id) REFERENCES restaurant_tables(restaurant_id,table_id)
);
CREATE UNIQUE INDEX uq_active_session ON table_sessions
 (CASE WHEN status='ACTIVE' THEN restaurant_id END, CASE WHEN status='ACTIVE' THEN table_id END);
CREATE INDEX ix_sessions_table ON table_sessions(restaurant_id,table_id,seated_at);
CREATE TABLE edge_nodes (
 edge_node_id VARCHAR2(60) PRIMARY KEY, restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 display_name VARCHAR2(100) NOT NULL, status VARCHAR2(20) DEFAULT 'OFFLINE' NOT NULL
 CHECK(status IN ('ONLINE','OFFLINE','DEGRADED')), last_seen_at TIMESTAMP,
 CONSTRAINT uq_edge_tenant UNIQUE(restaurant_id,edge_node_id)
);
CREATE TABLE cameras (
 camera_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), edge_node_id VARCHAR2(60) NOT NULL,
 name VARCHAR2(80) NOT NULL, location_desc VARCHAR2(200), credential_ref VARCHAR2(200),
 state VARCHAR2(15) DEFAULT 'DISCONNECTED' NOT NULL
 CHECK(state IN ('CONNECTED','DISCONNECTED','RECONNECTING','MAINTENANCE','ERROR')),
 last_seen_at TIMESTAMP, target_fps NUMBER(4,1) DEFAULT 2 NOT NULL,
 CONSTRAINT uq_camera_tenant UNIQUE(restaurant_id,camera_id),
 CONSTRAINT fk_camera_edge FOREIGN KEY(restaurant_id,edge_node_id) REFERENCES edge_nodes(restaurant_id,edge_node_id)
);
CREATE TABLE camera_zones (
 zone_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), camera_id NUMBER NOT NULL,
 zone_type VARCHAR2(15) NOT NULL CHECK(zone_type IN ('TABLE','FLOOR','RESTRICTED','ENTRANCE','QUEUE','KITCHEN_PASS')),
 table_id NUMBER, polygon_json CLOB NOT NULL CHECK(polygon_json IS JSON),
 ref_width NUMBER(5) NOT NULL, ref_height NUMBER(5) NOT NULL, schedule_json CLOB CHECK(schedule_json IS JSON),
 version NUMBER DEFAULT 0 NOT NULL,
 CONSTRAINT uq_zone_tenant UNIQUE(restaurant_id,zone_id),
 CONSTRAINT fk_zone_camera FOREIGN KEY(restaurant_id,camera_id) REFERENCES cameras(restaurant_id,camera_id),
 CONSTRAINT fk_zone_table FOREIGN KEY(restaurant_id,table_id) REFERENCES restaurant_tables(restaurant_id,table_id),
 CONSTRAINT ck_table_zone CHECK((zone_type='TABLE' AND table_id IS NOT NULL) OR zone_type<>'TABLE')
);
CREATE TABLE menu_categories (
 category_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), name VARCHAR2(100) NOT NULL,
 display_order NUMBER(5) DEFAULT 0 NOT NULL, active CHAR(1) DEFAULT 'Y' NOT NULL CHECK(active IN ('Y','N')),
 CONSTRAINT uq_category_name UNIQUE(restaurant_id,name),
 CONSTRAINT uq_category_tenant UNIQUE(restaurant_id,category_id)
);
CREATE TABLE kitchen_stations (
 station_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), name VARCHAR2(100) NOT NULL,
 CONSTRAINT uq_station_name UNIQUE(restaurant_id,name),
 CONSTRAINT uq_station_tenant UNIQUE(restaurant_id,station_id)
);
CREATE TABLE menu_items (
 item_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), category_id NUMBER NOT NULL, station_id NUMBER,
 name VARCHAR2(160) NOT NULL, description VARCHAR2(1000), price NUMBER(12,2) NOT NULL CHECK(price>=0),
 expected_prep_minutes NUMBER(5) DEFAULT 15 NOT NULL CHECK(expected_prep_minutes>0),
 available CHAR(1) DEFAULT 'Y' NOT NULL CHECK(available IN ('Y','N')),
 CONSTRAINT uq_item_tenant UNIQUE(restaurant_id,item_id),
 CONSTRAINT fk_item_category FOREIGN KEY(restaurant_id,category_id) REFERENCES menu_categories(restaurant_id,category_id),
 CONSTRAINT fk_item_station FOREIGN KEY(restaurant_id,station_id) REFERENCES kitchen_stations(restaurant_id,station_id)
);
CREATE TABLE orders (
 order_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), session_id NUMBER,
 order_code VARCHAR2(40) NOT NULL, status VARCHAR2(20) DEFAULT 'CREATED' NOT NULL
 CHECK(status IN ('CREATED','CONFIRMED','PREPARING','READY','SERVED','CANCELLED')),
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 updated_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, version NUMBER DEFAULT 0 NOT NULL,
 CONSTRAINT uq_order_code UNIQUE(restaurant_id,order_code),
 CONSTRAINT uq_order_tenant UNIQUE(restaurant_id,order_id),
 CONSTRAINT fk_order_session FOREIGN KEY(restaurant_id,session_id) REFERENCES table_sessions(restaurant_id,session_id)
);
CREATE TABLE order_items (
 order_item_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), order_id NUMBER NOT NULL, item_id NUMBER NOT NULL,
 quantity NUMBER(5) NOT NULL CHECK(quantity>0), unit_price NUMBER(12,2) NOT NULL CHECK(unit_price>=0),
 status VARCHAR2(20) DEFAULT 'CREATED' NOT NULL
 CHECK(status IN ('CREATED','CONFIRMED','IN_PREPARATION','READY','SERVED','CANCELLED')),
 preparation_started_at TIMESTAMP, ready_at TIMESTAMP,
 CONSTRAINT fk_oi_order FOREIGN KEY(restaurant_id,order_id) REFERENCES orders(restaurant_id,order_id),
 CONSTRAINT fk_oi_item FOREIGN KEY(restaurant_id,item_id) REFERENCES menu_items(restaurant_id,item_id)
);
CREATE TABLE order_status_history (
 history_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), order_id NUMBER NOT NULL,
 old_status VARCHAR2(20), new_status VARCHAR2(20) NOT NULL, changed_by NUMBER,
 changed_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT fk_osh_order FOREIGN KEY(restaurant_id,order_id) REFERENCES orders(restaurant_id,order_id)
);
CREATE TABLE service_requests (
 service_request_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), session_id NUMBER, table_id NUMBER,
 request_type VARCHAR2(40) DEFAULT 'CALL_WAITER' NOT NULL,
 source VARCHAR2(20) NOT NULL CHECK(source IN ('STAFF','QR','AI_ADVISORY')),
 status VARCHAR2(20) DEFAULT 'OPEN' NOT NULL CHECK(status IN ('OPEN','ACKNOWLEDGED','COMPLETED','DISMISSED')),
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 acknowledged_at TIMESTAMP, completed_at TIMESTAMP, assigned_employee_id NUMBER,
 CONSTRAINT fk_sr_session FOREIGN KEY(restaurant_id,session_id) REFERENCES table_sessions(restaurant_id,session_id),
 CONSTRAINT fk_sr_table FOREIGN KEY(restaurant_id,table_id) REFERENCES restaurant_tables(restaurant_id,table_id)
);
CREATE TABLE staff_tasks (
 task_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), table_id NUMBER,
 task_type VARCHAR2(40) NOT NULL, status VARCHAR2(20) DEFAULT 'OPEN' NOT NULL
 CHECK(status IN ('OPEN','ASSIGNED','IN_PROGRESS','COMPLETED','CANCELLED')),
 priority VARCHAR2(10) DEFAULT 'MEDIUM' NOT NULL CHECK(priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
 assigned_employee_id NUMBER, due_at TIMESTAMP, created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 completed_at TIMESTAMP,
 CONSTRAINT fk_task_table FOREIGN KEY(restaurant_id,table_id) REFERENCES restaurant_tables(restaurant_id,table_id)
);
CREATE TABLE cleaning_records (
 cleaning_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), table_id NUMBER NOT NULL,
 task_id NUMBER, started_at TIMESTAMP, completed_at TIMESTAMP, completed_by NUMBER,
 status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL CHECK(status IN ('PENDING','IN_PROGRESS','COMPLETED','ISSUE')),
 CONSTRAINT fk_clean_table FOREIGN KEY(restaurant_id,table_id) REFERENCES restaurant_tables(restaurant_id,table_id),
 CONSTRAINT fk_clean_task FOREIGN KEY(restaurant_id,task_id) REFERENCES staff_tasks(restaurant_id,task_id)
);
CREATE TABLE model_registry (
 model_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, model_name VARCHAR2(60) NOT NULL,
 version VARCHAR2(20) NOT NULL, task VARCHAR2(40) NOT NULL, framework VARCHAR2(30),
 artifact_sha256 CHAR(64) NOT NULL, dataset_version VARCHAR2(60), metrics_json CLOB CHECK(metrics_json IS JSON),
 status VARCHAR2(15) DEFAULT 'CANDIDATE' NOT NULL CHECK(status IN ('CANDIDATE','SHADOW','ACTIVE','RETIRED','ROLLED_BACK')),
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, activated_at TIMESTAMP,
 CONSTRAINT uq_model_version UNIQUE(model_name,version)
);
CREATE TABLE ai_events (
 ai_event_pk NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), event_uid VARCHAR2(40) NOT NULL,
 schema_version VARCHAR2(10) NOT NULL, edge_node_id VARCHAR2(60) NOT NULL,
 camera_id NUMBER, zone_id NUMBER, table_id NUMBER, event_type VARCHAR2(40) NOT NULL,
 confidence NUMBER(5,4) CHECK(confidence BETWEEN 0 AND 1), occurred_at TIMESTAMP NOT NULL,
 received_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, detected_at TIMESTAMP,
 model_versions CLOB CHECK(model_versions IS JSON), data_json CLOB CHECK(data_json IS JSON), snapshot_ref VARCHAR2(500),
 processing_status VARCHAR2(20) DEFAULT 'RECEIVED' NOT NULL
 CHECK(processing_status IN ('RECEIVED','APPLIED','REJECTED_BY_RULE','STALE','DEAD')),
 CONSTRAINT uq_ai_event UNIQUE(restaurant_id,event_uid),
 CONSTRAINT fk_event_edge FOREIGN KEY(restaurant_id,edge_node_id) REFERENCES edge_nodes(restaurant_id,edge_node_id),
 CONSTRAINT fk_event_camera FOREIGN KEY(restaurant_id,camera_id) REFERENCES cameras(restaurant_id,camera_id),
 CONSTRAINT fk_event_zone FOREIGN KEY(restaurant_id,zone_id) REFERENCES camera_zones(restaurant_id,zone_id),
 CONSTRAINT fk_event_table FOREIGN KEY(restaurant_id,table_id) REFERENCES restaurant_tables(restaurant_id,table_id)
);
CREATE INDEX ix_ai_event_time ON ai_events(restaurant_id,occurred_at);
CREATE TABLE alerts (
 alert_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), ai_event_pk NUMBER,
 alert_type VARCHAR2(40) NOT NULL, priority VARCHAR2(10) NOT NULL CHECK(priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
 status VARCHAR2(15) DEFAULT 'OPEN' NOT NULL CHECK(status IN ('OPEN','ACKNOWLEDGED','ESCALATED','RESOLVED','AUTO_CLOSED','FALSE_ALARM')),
 dedupe_key VARCHAR2(200) NOT NULL, assigned_role VARCHAR2(20), assigned_employee_id NUMBER,
 escalation_level NUMBER(2) DEFAULT 0 NOT NULL, created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 acknowledged_at TIMESTAMP, acknowledged_by NUMBER, resolved_at TIMESTAMP, resolved_by NUMBER, version NUMBER DEFAULT 0 NOT NULL,
 CONSTRAINT fk_alert_event FOREIGN KEY(ai_event_pk) REFERENCES ai_events(ai_event_pk)
);
CREATE UNIQUE INDEX uq_alert_open ON alerts
 (CASE WHEN status IN ('OPEN','ACKNOWLEDGED','ESCALATED') THEN restaurant_id END,
  CASE WHEN status IN ('OPEN','ACKNOWLEDGED','ESCALATED') THEN dedupe_key END);
CREATE TABLE alert_escalation_rules (
 rule_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 trigger_code VARCHAR2(60) NOT NULL, priority VARCHAR2(10) NOT NULL CHECK(priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
 first_recipient VARCHAR2(40) NOT NULL, escalate_after_seconds NUMBER(8), escalate_to VARCHAR2(40),
 dedupe_policy VARCHAR2(200), enabled CHAR(1) DEFAULT 'Y' NOT NULL CHECK(enabled IN ('Y','N')),
 CONSTRAINT uq_escalation_rule UNIQUE(restaurant_id,trigger_code)
);
CREATE TABLE threshold_config (
 threshold_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 config_key VARCHAR2(80) NOT NULL, config_value VARCHAR2(200) NOT NULL,
 updated_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT uq_threshold UNIQUE(restaurant_id,config_key)
);
CREATE TABLE notification_history (
 notification_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 alert_id NUMBER, channel VARCHAR2(20) NOT NULL CHECK(channel IN ('IN_APP','AUDIO','PUSH','SMS')),
 recipient_ref VARCHAR2(120) NOT NULL, delivery_status VARCHAR2(20) NOT NULL CHECK(delivery_status IN ('PENDING','SENT','FAILED','READ')),
 attempted_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, delivered_at TIMESTAMP,
 CONSTRAINT fk_notification_alert FOREIGN KEY(alert_id) REFERENCES alerts(alert_id)
);
CREATE TABLE customer_journey_stages (
 journey_stage_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id), session_id NUMBER NOT NULL,
 stage_code VARCHAR2(40) NOT NULL, stage_at TIMESTAMP, source VARCHAR2(30) NOT NULL, source_ref VARCHAR2(120),
 CONSTRAINT fk_journey_session FOREIGN KEY(restaurant_id,session_id) REFERENCES table_sessions(restaurant_id,session_id),
 CONSTRAINT uq_journey_stage UNIQUE(restaurant_id,session_id,stage_code)
);
CREATE TABLE customer_feedback (
 feedback_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 session_id NUMBER, rating NUMBER(1) CHECK(rating BETWEEN 1 AND 5), comment_text VARCHAR2(2000),
 created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT fk_feedback_session FOREIGN KEY(restaurant_id,session_id) REFERENCES table_sessions(restaurant_id,session_id)
);
CREATE TABLE feedback_analysis (
 analysis_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 feedback_id NUMBER NOT NULL, sentiment VARCHAR2(20), category VARCHAR2(60), confidence NUMBER(5,4) CHECK(confidence BETWEEN 0 AND 1),
 model_version VARCHAR2(40), analyzed_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT fk_analysis_feedback FOREIGN KEY(restaurant_id,feedback_id) REFERENCES customer_feedback(restaurant_id,feedback_id)
);
CREATE TABLE forecast_results (
 forecast_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, restaurant_id NUMBER NOT NULL REFERENCES restaurants(restaurant_id),
 forecast_type VARCHAR2(30) NOT NULL CHECK(forecast_type IN ('TRAFFIC','DEMAND','PREP_TIME')),
 horizon_start TIMESTAMP NOT NULL, horizon_end TIMESTAMP NOT NULL, result_json CLOB NOT NULL CHECK(result_json IS JSON),
 model_version VARCHAR2(40), created_at TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
 CONSTRAINT ck_forecast_window CHECK(horizon_end>horizon_start)
);
-- Catalog items still requiring approved detailed migrations: TABLE_ASSIGNMENTS, BILLS, BILL_ITEMS,
-- ANALYTICS_SUMMARY, MODEL_ASSIGNMENTS, AI_FEEDBACK_LABELS and SAFETY_INCIDENTS.
-- The source document says these are part of the table catalog but does not provide their full column contracts.
