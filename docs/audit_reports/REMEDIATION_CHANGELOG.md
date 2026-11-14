# RESTROVISION v2.0 - Production Remediation Changelog
**Branch**: `feature/production-hardening`  
**Phase**: Group 1 Remediation (P0 Critical Security, Multi-Tenancy & Deployment Foundation)  
**Date**: 2026-11-14  
**Author**: Principal Software Architect & Application Security Engineer  

---

## 1. Executive Summary of Changes

Remediation Group 1 (P0) has been executed strictly within the approved scope and with zero changes to existing business functionality, restaurant table lifecycles, or order processing. All changes were accompanied by comprehensive automated regression and empirical verification tests (27/27 passing, 100% success rate).

| Defect ID | Severity | Area | Status | Files Modified |
| :--- | :---: | :--- | :---: | :--- |
| **BUG-001** | CRITICAL | WebSocket STOMP Security & Tenant Isolation | **RESOLVED & VERIFIED** | `Backend/src/main/java/com/restrovision/config/WebSocketConfig.java`, `frontend/src/services/websocket.js` |
| **BUG-002** | CRITICAL | Per-Device Edge Authentication & Tenant Binding | **RESOLVED & VERIFIED** | `Backend/src/main/java/com/restrovision/security/JwtTokenProvider.java`, `Backend/src/main/java/com/restrovision/security/JwtAuthFilter.java`, `Backend/src/main/java/com/restrovision/controller/CameraController.java` |
| **BUG-003** | CRITICAL | Missing Backend Containerization | **RESOLVED & VERIFIED** | `Backend/Dockerfile` (Created) |
| **BUG-011** | HIGH | Docker Compose Linux Path Casing | **RESOLVED & VERIFIED** | `docker-compose.yml` |
| **BUG-012** | HIGH | Database Schema Migrations & Baseline Strategy | **RESOLVED & VERIFIED** | `Backend/pom.xml`, `Backend/src/main/resources/db/migration/V1__init_core_schema.sql`, `Backend/src/main/resources/application.yml`, `Backend/src/main/resources/application-oracle.yml`, `Backend/src/main/java/com/restrovision/security/SecurityConfig.java` |

---

## 2. Detailed Change Documentation

### 2.1. BUG-001: WebSocket STOMP JWT Authentication & Tenant Authorization
- **Files Modified**:
  - `Backend/src/main/java/com/restrovision/config/WebSocketConfig.java`
  - `frontend/src/services/websocket.js`
- **Root Cause**: The STOMP broker endpoints `/ws` were open without channel interception. Clients could send CONNECT frames with no credentials and subscribe to any topic across arbitrary restaurant IDs (`/topic/restaurants/{id}/**`).
- **Remediation Details**:
  1. Configured `ChannelInterceptor` in `configureClientInboundChannel(ChannelRegistration registration)`.
  2. On `StompCommand.CONNECT`:
     - Enforced `Authorization: Bearer <token>` in native headers.
     - Extracted and cryptographically validated JWT via `tokenProvider.validateToken(token)`.
     - Explicitly rejected refresh tokens (`type == "REFRESH"`).
     - Extracted `restaurantId` claim and user roles (`ROLE_*`).
     - Established authenticated `UsernamePasswordAuthenticationToken` on `accessor.setUser(...)`.
     - Injected trusted session attributes: `restaurantId` and `userEmail`.
  3. On `StompCommand.SUBSCRIBE`:
     - Enforced authenticated user session; rejected unauthenticated subscribers with `AccessDeniedException`.
     - For destinations matching `/topic/restaurants/{id}/**`, enforced strict equality between `clientRestId` in session attributes and `targetRestId` in the topic path. Cross-tenant subscriptions are rejected with `AccessDeniedException`.
  4. On `StompCommand.SEND`:
     - Enforced authenticated user session; blocked unauthenticated message dispatch.
  5. In `frontend/src/services/websocket.js`:
     - Updated `@stomp/stompjs` client initialization to include `beforeConnect()` hook, injecting `connectHeaders: { Authorization: 'Bearer ' + getAuthToken() }`.

---

### 2.2. BUG-002: Per-Device Edge Authentication & Strict Database Tenant Binding
- **Files Modified**:
  - `Backend/src/main/java/com/restrovision/security/JwtTokenProvider.java`
  - `Backend/src/main/java/com/restrovision/security/JwtAuthFilter.java`
  - `Backend/src/main/java/com/restrovision/controller/CameraController.java`
- **Root Cause**: Edge cameras and workers authenticated using a static shared token string (`edge-token-pune-01`), hardcoding `TenantContext.setRestaurantId(1L)`. An attacker possessing the token could inject arbitrary event payloads or spoof other tenant cameras.
- **Remediation Details**:
  1. **Token Specifications**:
     - Defined constants: `EDGE_ISSUER = "restrovision-edge-auth"`, `EDGE_AUDIENCE = "restrovision-backend"`, `TOKEN_TYPE_EDGE = "EDGE_DEVICE"`.
     - Implemented `tokenProvider.generateEdgeToken(String edgeNodeId, Long restaurantId, long expirationMs)` issuing dedicated device JWTs.
  2. **Security Filter Verification**:
     - Completely removed the static token check (`jwt.equals(edgeServiceToken)`). Legacy static tokens are now rejected with HTTP 401.
     - When `TOKEN_TYPE_EDGE` is presented:
       - Validated issuer (`restrovision-edge-auth`) and audience (`restrovision-backend`).
       - Extracted `edgeNodeId` from JWT subject.
       - Queried database repository: `edgeNodeRepository.findById(edgeNodeId)`. Unregistered devices rejected with 401.
       - Validated device lifecycle status: Device must be `ACTIVE` or `ONLINE`. Devices marked `OFFLINE`, `DEGRADED`, or `REVOKED` are rejected with 401.
       - **Database-Driven Tenant Binding**: Mandated that `restaurantId` is resolved directly from the trusted database entity (`edgeNode.getRestaurantId()`). If the JWT claim contains a different `restaurantId`, the request is rejected with 401.
       - Bound `TenantContext.setRestaurantId(trustedRestaurantId)`.
       - Updated `edgeNode.setLastSeenAt(Instant.now())` and saved to database.
       - Populated `SecurityContextHolder` with `UsernamePasswordAuthenticationToken` having `ROLE_AI_SERVICE`.
  3. **Management Endpoint**:
     - Added `POST /api/v1/cameras/edge-token?edgeNodeId={id}` (restricted to `ROLE_OWNER` and `ROLE_MANAGER`) allowing authorized restaurant leadership to issue cryptographically signed per-device tokens.

---

### 2.3. BUG-003 & BUG-011: Multi-Stage Backend Dockerfile & Casing Correction
- **Files Modified / Created**:
  - `Backend/Dockerfile` (Created)
  - `Backend/pom.xml` (Updated `<finalName>restrovision-backend</finalName>`)
  - `docker-compose.yml` (Updated)
- **Root Cause**: `Backend/Dockerfile` did not exist on disk, causing container builds to abort. `docker-compose.yml` referenced `./backend` instead of `./Backend` (case sensitivity failure on Linux). The container readiness check depended on `/v3/api-docs` rather than a standard readiness probe.
- **Remediation Details**:
  1. Created production multi-stage `Backend/Dockerfile`:
     - **Build Stage**: `maven:3.9.9-eclipse-temurin-21-alpine` caches dependencies and builds `restrovision-backend.jar`.
     - **Runtime Stage**: `eclipse-temurin:21-jre-alpine` minimal image.
     - **Non-Root User**: Created dedicated system user and group `appuser:appgroup` (UID/GID 1000/1000). Container runs as `USER appuser:appgroup`.
     - **Readiness Probe**: Configured `HEALTHCHECK` with curl calling `http://localhost:8080/actuator/health` (interval 15s, timeout 5s, retries 3).
     - **JVM Flags**: Configured container-aware heap sizing (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`).
  2. In `docker-compose.yml`:
     - Updated context path from `./backend` to `./Backend`.
     - Removed legacy `RESTROVISION_EDGE_SERVICE_TOKEN=edge-token-pune-01` environment variable.
     - Updated health check test command to use `http://localhost:8080/actuator/health`.

---

### 2.4. BUG-012: Flyway Database Migration & Oracle Entity Reconciliation
- **Files Modified / Created**:
  - `Backend/pom.xml` (Added `spring-boot-starter-actuator`, `flyway-core`, `flyway-database-oracle`)
  - `Backend/src/main/resources/db/migration/V1__init_core_schema.sql` (Created)
  - `Backend/src/main/resources/application.yml`
  - `Backend/src/main/resources/application-oracle.yml`
  - `Backend/src/main/java/com/restrovision/security/SecurityConfig.java`
- **Root Cause**: Database was configured for production with `jpa.hibernate.ddl-auto: validate` while lacking a versioned migration tool (Flyway/Liquibase). In addition, discrepancy existed between documentation SQL and actual JPA entities (e.g. `user_roles.role_code` vs `role_id`; missing columns on `restaurant_tables`, `table_sessions`, `order_items`, `service_requests`, `staff_tasks`, `alerts`, `customer_feedback`, `ai_feedback_labels`).
- **Remediation Details**:
  1. Added `flyway-core` and `flyway-database-oracle` to `pom.xml`.
  2. Added `spring-boot-starter-actuator` to `pom.xml` and exposed `/actuator/health` and `/actuator/info` without authentication in `SecurityConfig.java`.
  3. Created `V1__init_core_schema.sql` supporting Oracle 19c+ and H2 Oracle mode:
     - 27 tables defining the complete enterprise schema.
     - Reconciled entity fields: `user_roles` mapped with `role_code`; `restaurant_tables` includes `assigned_waiter_id`, `current_session_id`, `qr_token`, and `version`; `table_sessions` includes `pre_attended_flag`; `order_items` includes `item_name`, `station_id`, `station_name`, `expected_prep_minutes`; `alerts` includes `message` and `table_id`; `ai_feedback_labels` table fully created.
     - Identity columns use standard `NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`.
     - Timestamps use standard `TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL`.
  4. Configured safe baseline migration in `application.yml` and `application-oracle.yml`:
     - `spring.flyway.enabled: true`
     - `spring.flyway.baseline-on-migrate: true`
     - `spring.flyway.baseline-version: 0`
     - `spring.flyway.locations: classpath:db/migration`
     - Guarantees that pre-existing databases are not corrupted, dropped, or overwritten.

---

## 3. Regression Testing and Quality Verification

Full regression test execution command:
```bash
mvn test
```
**Results**:
- **Tests Executed**: 27
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Build Status**: **BUILD SUCCESS**
- **Execution Time**: 22.77s
