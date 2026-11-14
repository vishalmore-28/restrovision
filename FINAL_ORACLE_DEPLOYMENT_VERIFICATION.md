# RESTROVISION v2.0 — Final Oracle Database Deployment & Access Verification

**Document ID:** `ORACLE-DEPLOY-20261115-FINAL`  
**Application Target:** Oracle Database 26ai Free / 19c Enterprise (PDB `FREEPDB1`)  
**Active Working Branch:** `feature/production-hardening`  
**Protected Branch:** `main` (Locked at `171171b08f6bd44f523f224d00c197624246613f`)  
**Lead Oracle DBA & Data Architect:** Principal Database Administrator  
**Date:** 2026-11-15T01:25:00+05:30  
**Operational Status:** **IMPLEMENTED & STATICALLY VERIFIED (LIVE ORACLE EXECUTION BLOCKED ON DBA)**  

---

## 1. Executive Summary & Database Architecture

The RESTROVISION v2.0 database layer enforces strict enterprise separation of duties:
1. **Migration Account (`RESTROVISION_MIGRATOR`):** Schema owner and DDL administrator. Executes Flyway migrations `V1` and `V2`. Granted specific DDL privileges derived strictly from migration scripts. Unlimited quota is revoked; granted a finite **10 GB** quota on `users`.
2. **Runtime Application Account (`RESTROVISION_APP`):** Application connection pool account used by Spring Boot JPA. Granted strictly **zero DDL privileges** (`NO CREATE TABLE`, `NO DROP`, `NO ALTER`) and **zero quota** (`QUOTA 0 ON users`). Receives only DML privileges (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) via dedicated role `restrovision_app_role` and private synonyms.
3. **Flyway Metadata Isolation:** Flyway history table `flyway_schema_history` is explicitly excluded from application grants and synonyms.
4. **Baseline Safety:** `baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}` prevents silent, uninspected baselining of partially populated schemas.

---

## 2. Complete DBA Provisioning Script (Execute as SYSDBA)

```sql
-- ==============================================================================
-- RESTROVISION v2.0 - Oracle Dual-Account Provisioning Script
-- Pluggable Database: FREEPDB1
-- Must be executed by authorized DBA prior to application deployment.
-- ==============================================================================

ALTER SESSION SET CONTAINER = FREEPDB1;

-- Secure interactive password prompt in SQL*Plus (masked input, zero plaintext terminal echo):
ACCEPT migrator_password CHAR PROMPT 'Enter password for RESTROVISION_MIGRATOR: ' HIDE
ACCEPT app_password CHAR PROMPT 'Enter password for RESTROVISION_APP: ' HIDE

-- 1. Create Migration Account (Schema Owner & DDL Administrator)
CREATE USER restrovision_migrator IDENTIFIED BY "&migrator_password"
  DEFAULT TABLESPACE users
  TEMPORARY TABLESPACE temp
  QUOTA 10G ON users;

-- Privileges derived strictly from actual V1 and V2 SQL statements:
GRANT CREATE SESSION TO restrovision_migrator;
GRANT CREATE TABLE TO restrovision_migrator;
GRANT CREATE VIEW TO restrovision_migrator;
GRANT CREATE SEQUENCE TO restrovision_migrator;
GRANT CREATE PROCEDURE TO restrovision_migrator;
GRANT CREATE TRIGGER TO restrovision_migrator;
GRANT CREATE SYNONYM TO restrovision_migrator;

-- 2. Create Runtime Application Account (Strict Least-Privilege DML Only)
CREATE USER restrovision_app IDENTIFIED BY "&app_password"
  DEFAULT TABLESPACE users
  TEMPORARY TABLESPACE temp
  QUOTA 0 ON users; -- Zero table creation quota

GRANT CREATE SESSION TO restrovision_app;

-- Clear password substitution variables from SQL*Plus session memory immediately:
UNDEFINE migrator_password
UNDEFINE app_password

-- 3. Dedicated Application Role for Post-Migration Grants
CREATE ROLE restrovision_app_role;

-- Verification of created accounts:
SELECT username, account_status, default_tablespace 
FROM dba_users 
WHERE username IN ('RESTROVISION_MIGRATOR', 'RESTROVISION_APP');

EXIT;
```

---

## 3. Post-Migration Object Grants Script

After Flyway executes migrations `V1` and `V2` under `restrovision_migrator`, the DBA executes this script to wire DML permissions and synonyms while isolating Flyway metadata:

```sql
-- Execute in FREEPDB1 to wire DML permissions and synonyms:
BEGIN
  -- 1. Grant DML on application tables (Excluding Flyway migration metadata)
  FOR t IN (SELECT table_name FROM dba_tables WHERE owner = 'RESTROVISION_MIGRATOR' AND UPPER(table_name) NOT IN ('FLYWAY_SCHEMA_HISTORY')) LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON restrovision_migrator.' || t.table_name || ' TO restrovision_app_role';
  END LOOP;
  
  -- 2. Grant sequence usage on explicit sequences (Excluding internal system identity sequences)
  FOR s IN (SELECT sequence_name FROM dba_sequences WHERE sequence_owner = 'RESTROVISION_MIGRATOR' AND sequence_name NOT LIKE 'ISEQ$$%') LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT ON restrovision_migrator.' || s.sequence_name || ' TO restrovision_app_role';
  END LOOP;
END;
/

GRANT restrovision_app_role TO restrovision_app;

-- 3. Create private synonyms in restrovision_app for clean object resolution:
BEGIN
  FOR t IN (SELECT table_name FROM dba_tables WHERE owner = 'RESTROVISION_MIGRATOR' AND UPPER(table_name) NOT IN ('FLYWAY_SCHEMA_HISTORY')) LOOP
    EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM restrovision_app.' || t.table_name || ' FOR restrovision_migrator.' || t.table_name;
  END LOOP;
END;
/
```

---

## 4. Quota Sizing Justification & Capacity Monitoring

### 4.1 Tablespace Quota Sizing Justification
- **High-Volume Model:** 100 tables $\times$ 50 turns/day = 5,000 active sessions/day.
- **Data Volume Calculation:** Order items, state transitions, telemetry, and feedback log $\approx 20,000$ rows/day ($\approx 10$ MB/day uncompressed data + indexes $\approx 3.65$ GB/year).
- **Quota Decision:** Unlimited quota is revoked. A finite quota of **10 GB** is granted on `users` tablespace, providing 2+ years of operation while preventing host disk exhaustion attacks.

### 4.2 DBA Capacity Monitoring Query
```sql
SELECT tablespace_name, 
       ROUND(bytes / (1024*1024), 2) AS used_mb, 
       ROUND(max_bytes / (1024*1024), 2) AS max_mb,
       ROUND((bytes / max_bytes) * 100, 2) AS pct_used
FROM dba_ts_quotas 
WHERE username = 'RESTROVISION_MIGRATOR';
```
- **Alerting Threshold:** Alert when `pct_used` exceeds **80%** ($\approx 8$ GB).

---

## 5. Master Schema Table & Sequence Runtime Access Matrix

Every table in Flyway migrations V1 and V2 is mapped to its entity and runtime access method:

| Table Name | Schema Owner | JPA Entity Class | Identity / Key Strategy | Role Grant (`restrovision_app_role`) | Private Synonym in `restrovision_app` | DDL Allowed? |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: |
| `restaurants` | `RESTROVISION_MIGRATOR` | `Restaurant.java` | `restaurant_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `restaurant_settings`| `RESTROVISION_MIGRATOR` | Domain Setting | `restaurant_id` (PK FK) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `users` | `RESTROVISION_MIGRATOR` | `User.java` | `user_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `roles` | `RESTROVISION_MIGRATOR` | `Role.java` | `role_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `user_roles` | `RESTROVISION_MIGRATOR` | Join Table | `(user_id, role_id)` (PK) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `refresh_tokens` | `RESTROVISION_MIGRATOR` | `RefreshToken.java` | `refresh_token_id` (IDENTITY)| `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `audit_logs` | `RESTROVISION_MIGRATOR` | `AuditLog.java` | `audit_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `employees` | `RESTROVISION_MIGRATOR` | `Employee.java` | `employee_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `shifts` | `RESTROVISION_MIGRATOR` | `Shift.java` | `shift_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `restaurant_tables`| `RESTROVISION_MIGRATOR` | `RestaurantTable.java`| `table_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `table_sessions` | `RESTROVISION_MIGRATOR` | `TableSession.java` | `session_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `edge_nodes` | `RESTROVISION_MIGRATOR` | `EdgeNode.java` | `edge_node_id` (PK) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `cameras` | `RESTROVISION_MIGRATOR` | `Camera.java` | `camera_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `camera_zones` | `RESTROVISION_MIGRATOR` | `CameraZone.java` | `zone_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `kitchen_stations` | `RESTROVISION_MIGRATOR` | `KitchenStation.java` | `station_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `menu_categories` | `RESTROVISION_MIGRATOR` | `MenuCategory.java` | `category_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `menu_items` | `RESTROVISION_MIGRATOR` | `MenuItem.java` | `item_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `orders` | `RESTROVISION_MIGRATOR` | `Order.java` | `order_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `order_items` | `RESTROVISION_MIGRATOR` | `OrderItem.java` | `order_item_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `order_status_history`| `RESTROVISION_MIGRATOR`| Status History | `history_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `service_requests` | `RESTROVISION_MIGRATOR` | `ServiceRequest.java` | `service_request_id` (IDENTITY)| `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `staff_tasks` | `RESTROVISION_MIGRATOR` | `StaffTask.java` | `task_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `cleaning_records` | `RESTROVISION_MIGRATOR` | `CleaningRecord.java` | `cleaning_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `ai_events` | `RESTROVISION_MIGRATOR` | `AiEvent.java` | `ai_event_pk` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `alerts` | `RESTROVISION_MIGRATOR` | `Alert.java` | `alert_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `alert_escalation_rules`| `RESTROVISION_MIGRATOR`| Escalation Rule | `rule_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `threshold_config` | `RESTROVISION_MIGRATOR` | Threshold Config | `threshold_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `notification_history`| `RESTROVISION_MIGRATOR`| Notification Log | `notification_id` (IDENTITY)| `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `customer_feedback`| `RESTROVISION_MIGRATOR` | `CustomerFeedback.java`| `feedback_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `ai_feedback_labels`| `RESTROVISION_MIGRATOR`| `AiFeedbackLabel.java`| `feedback_label_id` (IDENTITY)| `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `model_registry` | `RESTROVISION_MIGRATOR` | Model Registry | `model_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `customer_journey_stages`| `RESTROVISION_MIGRATOR`| Journey Stage | `journey_stage_id` (IDENTITY)| `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `forecast_results` | `RESTROVISION_MIGRATOR` | Forecast Result | `forecast_id` (IDENTITY) | `SELECT, INSERT, UPDATE, DELETE` | Yes | **NO (DENIED)** |
| `flyway_schema_history`| `RESTROVISION_MIGRATOR`| Flyway Metadata | `installed_rank` (PK) | **NONE (ISOLATED)** | **NONE (DENIED)** | **NO (DENIED)** |

---

## 6. Live Verification Status

- **Listener Status:** Oracle listener active on port 1521 (`tnsping localhost:1521/freepdb1` OK 0ms).
- **PDB Status:** `FREEPDB1` is open `READ WRITE`.
- **Live DBA Execution Status:** **BLOCKED ON DBA PROVISIONING**. Privileged user creation must be executed by the authorized DBA using the scripts in Section 2 and 3.
