# RESTROVISION v2.0 - Group 1 Production Verification Report (Revision 2)

**Document ID**: `VERIFY-GROUP1-20261114-REV2`  
**Execution Timestamp**: 2026-11-14T14:09:00+05:30  
**Target Environment**: Windows 11 Enterprise (x64) / Eclipse Temurin JDK 21.0.4 / Maven 3.10.0 / H2 Oracle Mode & Real Network Sockets  
**Git Branch**: `feature/production-hardening`  
**Git Baseline**: `171171b` (on `main`)  
**Remediation Commits**:
- `22d1d5d` (*feat(security, devops): implement Group 1 P0 remediation*)
- `5ccd1a5` (*fix(flyway): update baseline-version to 1 for safe schema baselining on existing databases*)  
**Lead Auditor / Architect**: Principal Software QA Engineer, Application Security Engineer & DevSecOps Lead  

---

## 1. Executive Summary & Verification Matrix

Pursuant to the User Mandate for Group 1 Production Verification and specific Flyway correction approval, the following focused investigations, empirical tests, and security audits were executed:

| Mandate / Area | Target Verification | Empirical Method | Status & Outcome | Verdict |
| :--- | :--- | :--- | :--- | :---: |
| **Flyway Baseline Correction** | Change `baseline-version: 0` to `1` | `application.yml` & `application-oracle.yml` updated & tested | Committed separately in `5ccd1a5`. Empty DB runs V1 cleanly; existing DB baselines at 1 without re-running V1. Records preserved. | **VERIFIED PASS** |
| **Schema Compatibility Audit** | Legacy DBA script vs V1 vs JPA Entities | AST / Object diff between `01_...Core_Schema.sql`, `V1__init_core_schema.sql`, & JPA entities | Discovered legacy DBA script has 4 incompatible discrepancies (`qr_token`, `message`, `role_code`, `feedback_analysis`). Cannot blindly baseline legacy script. | **CONFIRMED FINDING** |
| **WebSocket SEND Auth** | Destination-level authorization on STOMP SEND | Live network STOMP test (`testDestinationAuthorizationOnSend`) | **Confirmed Vulnerability**: Interceptor authenticates client on SEND but does NOT inspect destination. Tenant 1 can send to Tenant 2 topic. | **CONFIRMED DEFECT** (Awaiting Approval) |
| **Commit Discrepancy** | `10f760f` vs `171171b` | Git object inspection & full transcript JSONL audit | `171171b` is the genuine git baseline commit. `10f760f` was a typographical transcription error in step 1910 report text. | **INVESTIGATION RESOLVED** |
| **Live Oracle Database** | Oracle 23ai Free local instance check | Listener check (`lsnrctl status`) & SQL*Plus check | Oracle 23ai listener is active (`freepdb1`), but `restrovision` user schema is unprovisioned. Live Oracle tests marked pending DBA setup. | **MARKED PENDING** |
| **Docker Container Runtime** | Build & run container image | Windows service inspection (`com.docker.service`) | Docker CLI & compose installed. Windows host service stopped; starting requires host admin elevation. Runtime marked pending. | **MARKED PENDING** |

---

## 2. Flyway Baseline Correction & Lifecycle Verification

### 2.1. Configuration Update Committed
Per explicit user approval, `spring.flyway.baseline-version` was updated from `0` to `1` in:
- [Backend/src/main/resources/application.yml](file:///D:/RESTROVISION/Backend/src/main/resources/application.yml#L15)
- [Backend/src/main/resources/application-oracle.yml](file:///D:/RESTROVISION/Backend/src/main/resources/application-oracle.yml#L13)
Committed separately under Git Commit `5ccd1a5` (*"fix(flyway): update baseline-version to 1 for safe schema baselining on existing databases"*).

### 2.2. Empirical Verification Across Lifecycle Scenarios ([FlywayProductionVerificationTest.java](file:///D:/RESTROVISION/Backend/src/test/java/com/restrovision/FlywayProductionVerificationTest.java))

#### Scenario 1: Greenfield / Empty Database
- **Behavior**: Flyway detects empty schema, validates V1, builds `flyway_schema_history`, and applies `V1__init_core_schema.sql` cleanly in 128 ms.
- **Log Trace**:
```text
14:07:11.135 [main] INFO org.flywaydb.core.internal.schemahistory.JdbcTableSchemaHistory -- Creating Schema History table "PUBLIC"."flyway_schema_history" ...
14:07:11.145 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Current version of schema "PUBLIC": << Empty Schema >>
14:07:11.195 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Migrating schema "PUBLIC" to version "1 - init core schema"
14:07:11.330 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Successfully applied 1 migration to schema "PUBLIC", now at version v1 (execution time 00:00.128s)
[Flyway Scenario 1] Migrations executed on empty DB: 1
[Flyway Scenario 1] Current version: 1
[Flyway Scenario 1] Current state: SUCCESS
```

#### Scenario 2: Existing Complete Database with `baseline-version: 1`
- **Behavior**: Flyway detects pre-existing schema, creates `flyway_schema_history` stamped with baseline version `1`, recognizes that version 1 is already satisfied, and executes 0 migrations.
- **Record Integrity**: Pre-existing record `ID=42, NAME='The Heritage Grill'` verified completely intact and unmodified.
- **Log Trace**:
```text
14:07:10.950 [main] INFO org.flywaydb.core.internal.schemahistory.JdbcTableSchemaHistory -- Creating Schema History table "PUBLIC"."flyway_schema_history" with baseline ...
14:07:10.974 [main] INFO org.flywaydb.core.internal.command.DbBaseline -- Successfully baselined schema with version: 1
14:07:10.991 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Current version of schema "PUBLIC": 1
14:07:10.995 [main] INFO org.flywaydb.core.internal.command.DbMigrate -- Schema "PUBLIC" is up to date. No migration necessary.
[Flyway Scenario 2B] Migrations executed on existing DB with baseline-version 1: 0
[Flyway Scenario 2B] Current migration state: BASELINE
[Flyway Scenario 2B] Current version: 1
[Flyway Scenario 2B] Verified: Pre-existing record ID 42 is intact and unmodified!
```

---

## 3. Pre-Existing Schema Compatibility Audit (Mandatory Check 5)

We performed a deep comparative schema audit between the pre-existing DBA script ([database/01_RESTROVISION_Oracle_Core_Schema_v2.0.sql](file:///D:/RESTROVISION/database/01_RESTROVISION_Oracle_Core_Schema_v2.0.sql)), the canonical V1 migration script ([V1__init_core_schema.sql](file:///D:/RESTROVISION/Backend/src/main/resources/db/migration/V1__init_core_schema.sql)), and all JPA domain entities.

### Key Compatibility Findings:
1. **Total Table Count**: Both define 33 tables.
2. **Table Incompatibility**:
   - The DBA script defines `feedback_analysis`.
   - The Spring Boot application defines JPA Entity `AiFeedbackLabel` mapped to `@Table(name = "ai_feedback_labels")`.
   - If an existing database only has `feedback_analysis`, Hibernate boot validation (`ddl-auto: validate`) will immediately crash with:
     `Schema-validation: missing table [ai_feedback_labels]`.
3. **Column Incompatibilities**:
   - `restaurant_tables`: The DBA script lacks `assigned_waiter_id`, `current_session_id`, and `qr_token` (length 64). All three are mapped fields in `RestaurantTable.java` and are present in `V1__init_core_schema.sql`.
   - `alerts`: The DBA script lacks `message` (VARCHAR2(255)) and `table_id`. Both are mapped fields in `Alert.java` and are present in `V1__init_core_schema.sql`.
   - `user_roles`: The DBA script only defines `role_id NUMBER`. JPA Entity `User.java` maps `@CollectionTable(name="user_roles")` with `@Column(name="role_code")`. `V1__init_core_schema.sql` reconciled both columns (`role_code` and `role_id`).
4. **Architectural Recommendation**:
   > [!IMPORTANT]
   > Operators must **NOT** blindly baseline a database that was initialized using only the legacy `database/01_RESTROVISION_Oracle_Core_Schema_v2.0.sql` file. Baselining at version 1 requires that the database either was created via `V1__init_core_schema.sql` or has had the 4 reconciliation DDL adjustments applied.

---

## 4. WebSocket SEND Destination Authorization (Mandatory Check 8)

### 4.1. Empirical Test Execution
We implemented `testDestinationAuthorizationOnSend` in [WebSocketIntegrationProductionTest.java](file:///D:/RESTROVISION/Backend/src/test/java/com/restrovision/WebSocketIntegrationProductionTest.java) to test what happens when an authenticated user of Tenant 1 issues a STOMP `SEND` frame targeting Tenant 2 (`/topic/restaurants/2/tables`).

### 4.2. Test Results & Log Output
```text
2026-11-14T14:07:54.967+05:30  INFO 9960 --- [restrovision-backend] [o-auto-1-exec-5] c.restrovision.config.WebSocketConfig    : WebSocket STOMP authenticated for owner@restrovision.local (Restaurant ID: 1)
[Live STOMP Test] Destination-level check on SEND - session isConnected: true
```
**Outcome**: The session remained connected, and the frame was accepted without destination authorization.

### 4.3. Root Cause Analysis in `WebSocketConfig.java`
Inspection of `WebSocketConfig.java` lines 110–143 reveals:
- Under `StompCommand.SUBSCRIBE`, the destination is parsed and cross-tenant subscriptions are explicitly blocked:
  ```java
  if (destination.startsWith("/topic/restaurants/")) {
      Long targetRestId = Long.parseLong(parts[3]);
      if (!clientRestId.equals(targetRestId)) {
          throw new AccessDeniedException(...);
      }
  }
  ```
- Under `StompCommand.SEND` (lines 138–143):
  ```java
  } else if (StompCommand.SEND.equals(accessor.getCommand())) {
      if (accessor.getUser() == null || accessor.getSessionAttributes() == null || accessor.getSessionAttributes().get("restaurantId") == null) {
          log.warn("Blocked unauthenticated WebSocket SEND attempt to {}", accessor.getDestination());
          throw new AccessDeniedException("Access denied: Unauthenticated message dispatch.");
      }
  }
  ```
  The interceptor validates authentication, but completely skips destination-level authorization.

### 4.4. Security Implications & Proposed Fix
1. **Risk**: In RESTROVISION, clients should never publish directly to `/topic/**` (server-side broadcast streams). Permitting client SEND to `/topic/restaurants/{id}/**` allows spoofing of table states, alerts, or orders across tenants.
2. **Proposed Fix (Awaiting Approval)**:
   Add destination-level inspection to `StompCommand.SEND`:
   ```java
   } else if (StompCommand.SEND.equals(accessor.getCommand())) {
       if (accessor.getUser() == null || accessor.getSessionAttributes() == null || accessor.getSessionAttributes().get("restaurantId") == null) {
           log.warn("Blocked unauthenticated WebSocket SEND attempt to {}", accessor.getDestination());
           throw new AccessDeniedException("Access denied: Unauthenticated message dispatch.");
       }
       String destination = accessor.getDestination();
       if (destination == null || destination.startsWith("/topic/")) {
           log.warn("Blocked illegal client WebSocket SEND to broadcast topic: {}", destination);
           throw new AccessDeniedException("Access denied: Direct publishing to broadcast topics is prohibited.");
       }
       Long clientRestId = (Long) accessor.getSessionAttributes().get("restaurantId");
       if (destination.startsWith("/app/restaurants/")) {
           String[] parts = destination.split("/");
           if (parts.length >= 4) {
               Long targetRestId = Long.parseLong(parts[3]);
               if (!clientRestId.equals(targetRestId)) {
                   log.warn("Blocked cross-tenant WebSocket SEND: client {} attempted to send to {}", clientRestId, destination);
                   throw new AccessDeniedException("Access denied: Cross-tenant message dispatch is prohibited.");
               }
           }
       }
   }
   ```

---

## 5. Baseline Commit Discrepancy Investigation (Mandatory Check 9)

### 5.1. Investigation Method
1. Checked `git cat-file -t 10f760f` -> returned `fatal: Not a valid object name 10f760f`.
2. Checked `git reflog` -> recorded:
   ```text
   22d1d5d HEAD@{0}: commit: feat(security, devops): implement Group 1 P0 remediation
   171171b HEAD@{1}: checkout: moving from main to feature/production-hardening
   171171b HEAD@{2}: commit (initial): chore: baseline pre-remediation state (audit score 62/100)
   ```
3. Audited full conversation transcript `transcript.jsonl` at step 1908:
   - Command executed: `git commit -m "chore: baseline pre-remediation state (audit score 62/100)"`
   - Git generated commit hash: `171171b6ba716fb4b50c05988e0e64f79ba6368d`.
4. In step 1910, the AI assistant authored a summary report and made a typographical transcription error, writing `10f760f` instead of `171171b`.

### 5.2. Conclusion
There is no missing or diverged commit. Commit `171171b` on `main` is the genuine, single baseline commit.

---

## 6. Oracle Database & Docker Runtime Status

### 6.1. Oracle Database Verification Status: PENDING DBA PROVISIONING
- **Discovery**: The host is running `Oracle AI Database 26ai Free Release 23.26.1.0.0` with TNS listener listening on `localhost:1521` (service `freepdb1`).
- **Limitation**: The database currently does not have the `restrovision` user or tables provisioned (`all_users` has no `restrovision` user).
- **Status**: Live Oracle verification remains **PENDING** until the DBA provisions the `restrovision` database user and schema in `freepdb1`.

### 6.2. Docker Runtime Verification Status: PENDING HOST PRIVILEGES
- **Discovery**: Docker CLI 29.9.2 and Docker Compose 5.6.1 are installed. `Backend/Dockerfile` is fully constructed with multi-stage build, non-root execution (`appuser:appgroup`), and Actuator health check (`/actuator/health`).
- **Limitation**: The Windows background service `com.docker.service` is stopped. Starting it via PowerShell (`Start-Service com.docker.service`) returned an access denied error due to non-elevated permissions.
- **Status**: Docker container runtime execution remains **PENDING** until the Docker service is started with appropriate host permissions.

---

## 7. Current Git History & Test Suite Status

### Git Commit Log on `feature/production-hardening`:
```text
* 5ccd1a5 fix(flyway): update baseline-version to 1 for safe schema baselining on existing databases
* 22d1d5d feat(security, devops): implement Group 1 P0 remediation (BUG-001, BUG-002, BUG-003, BUG-011, BUG-012)
* 171171b chore: baseline pre-remediation state (audit score 62/100) [main]
```

### Complete Automated Test Suite:
- Total tests executed across 4 suites: **39 tests**
- Total passing tests: **39 passed, 0 failures, 0 errors (100.0% passing)**

---

## Next Steps & Stop-Gate Decision

1. **Review WebSocket SEND Finding**: Confirm whether you approve implementing destination-level authorization on STOMP `SEND` operations in `WebSocketConfig.java` to prevent clients from sending to `/topic/**` or foreign tenant destinations.
2. **Group 2 Authorization**: Once this finding is reviewed, confirm whether we may proceed with Group 2 implementation (*Multi-Tenancy ThreadLocal Hardening, AI Event Freshness/Replay Protection & Database Query Cascade Optimizations*).
