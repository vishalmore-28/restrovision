# RESTROVISION Master Defect & Vulnerability Register (Updated)
**Document ID**: `TRACKER-DEFECTS-20261114-REV1`  
**Application Target**: RESTROVISION v2.0  
**Branch**: `feature/production-hardening`  
**Last Updated**: 2026-11-14T13:54:00+05:30  
**Audit Lead**: Principal Software QA Engineer & Security Auditor  

---

## 1. Defect Remediation Status Summary

| Total Defects | Resolved (Group 1) | Pending (Group 2-5) | Critical Unresolved | Release Verdict |
| :---: | :---: | :---: | :---: | :---: |
| **23** | **5** | **18** | **0** | **GROUP 1 HARDENING COMPLETED (Awaiting Approval for Group 2)** |

### Severity Distribution
- **CRITICAL**: 3 Total | **3 Resolved (100%)** | 0 Remaining
- **HIGH**: 12 Total | **2 Resolved (16.7%)** | 10 Remaining
- **MEDIUM**: 7 Total | 0 Resolved | 7 Remaining
- **LOW**: 1 Total | 0 Resolved | 1 Remaining

---

## 2. Master Defect Register

| Defect ID | Severity | Category | Component / File | Summary | Status | Resolution / Verification Reference |
| :--- | :---: | :--- | :--- | :--- | :---: | :--- |
| **BUG-001** | **CRITICAL** | Security | `WebSocketConfig.java` | Unauthenticated STOMP & cross-tenant subscription bypass | **RESOLVED & VERIFIED** | Implemented `ChannelInterceptor` in `WebSocketConfig.java` checking JWT on `CONNECT`, enforcing tenant match on `SUBSCRIBE` (`/topic/restaurants/{id}/**`), and checking auth on `SEND`. Tests 23–27 passing. |
| **BUG-002** | **CRITICAL** | Security | `JwtAuthFilter.java` | Static edge token & hardcoded tenant ID (`1L`) | **RESOLVED & VERIFIED** | Replaced static string with per-device JWT signed by backend. Device identity validated against DB, status checked (`ACTIVE`/`ONLINE`), and tenant strictly resolved from DB. Tests 04, 05, 16–20 passing. |
| **BUG-003** | **CRITICAL** | DevOps | `Backend/Dockerfile` | Dockerfile missing from disk; compose build fails | **RESOLVED & VERIFIED** | Created production multi-stage `Backend/Dockerfile` (`maven:3.9.9` -> `eclipse-temurin:21-jre-alpine`) with non-root user `appuser:appgroup` and Actuator health check probe. Test 21 passing. |
| **BUG-004** | **HIGH** | Multi-Tenancy| `TenantContext.java` | ThreadLocal defaults and resets to `1L` (Tenant bleed) | PENDING (Group 2) | Scope of Group 2 (ThreadLocal safety, event replay protection, N+1 query optimization). |
| **BUG-005** | **HIGH** | Security | `AiEventIngestionService.java` | Zero timestamp freshness validation (Event replay) | PENDING (Group 2) | Scope of Group 2. |
| **BUG-006** | **HIGH** | Performance | `TableStateMachineService.java`| $1 + 2N$ database query cascade on table broadcasts | PENDING (Group 2) | Scope of Group 2. |
| **BUG-007** | **HIGH** | Performance | `Order.java:43` | `FetchType.EAGER` on `items` collection (Heap memory risk) | PENDING (Group 2) | Scope of Group 2. |
| **BUG-008** | **HIGH** | Security | `AuthController.java` | No brute-force rate limiting or account lockout on login | PENDING (Group 3) | Scope of Group 3 (Stored XSS, rate limiting, UI error handling). |
| **BUG-009** | **HIGH** | Security | `PublicQrController.java` | Stored XSS in customer feedback comments | PENDING (Group 3) | Scope of Group 3. |
| **BUG-010** | **HIGH** | Code Quality | `src/pages/*.jsx` (17 places) | Empty catch blocks silently swallow API mutation failures | PENDING (Group 3) | Scope of Group 3. |
| **BUG-011** | **HIGH** | DevOps | `docker-compose.yml` | Case-sensitivity path mismatch (`./backend` vs `./Backend`) | **RESOLVED & VERIFIED** | Fixed path casing in `docker-compose.yml` to `./Backend`. |
| **BUG-012** | **HIGH** | Database | `pom.xml`, `application.yml` | Missing Flyway with `ddl-auto: validate` enabled | **RESOLVED & VERIFIED** | Added `flyway-core`, `flyway-database-oracle`, `V1__init_core_schema.sql` (27 tables matching JPA entities), and `baseline-on-migrate: true`. Test 22 passing. |
| **BUG-013** | **MEDIUM** | Security | `PublicQrController.java` | Unauthenticated feedback flooding / storage DoS | PENDING (Group 3) | Scope of Group 3. |
| **BUG-014** | **MEDIUM** | Security | `src/services/api.js` | JWT tokens stored in browser `localStorage` | PENDING (Group 4) | Scope of Group 4. |
| **BUG-015** | **MEDIUM** | Architecture| `src/pages/Analytics.jsx` | 1,015-line God component containing embedded chart logic | PENDING (Group 4) | Scope of Group 4. |
| **BUG-016** | **MEDIUM** | Database | `TableSession.java`, `StaffTask.java` | Missing `@Version` optimistic locking on mutable entities | PENDING (Group 4) | Scope of Group 4. |
| **BUG-017** | **HIGH** | Performance | `TableStateMachineService.java` | Concurrency latency degradation (p99 > 1,021ms at 100 VUs) | PENDING (Group 2) | Scope of Group 2. |
| **BUG-018** | **MEDIUM** | Accessibility| `Login.jsx` & `CustomerQrPortal.jsx` | WCAG 2.2 AA violations: Missing `<label>` & unlabelled star buttons | PENDING (Group 4) | Scope of Group 4. |
| **BUG-019** | **LOW** | UI/UX | `CustomerQrPortal.jsx` & `FloorPlan` | 6px horizontal scroll overflow on mobile viewport (375px) | PENDING (Group 4) | Scope of Group 4. |
| **BUG-020** | **MEDIUM** | Engineering | `traffic.py` & `sentiment.py` | Superficial ML mock implementations (Hardcoded weights) | PENDING (Group 5) | Scope of Group 5. |
| **BUG-021** | **HIGH** | Testing | `frontend/package.json` | 0.0% frontend automated test coverage (No Vitest/Jest/Cypress) | PENDING (Group 5) | Scope of Group 5. |
| **BUG-022** | **HIGH** | DevOps | Root repository | Zero CI/CD automation (.github/workflows, Jenkinsfile absent) | PENDING (Group 5) | Scope of Group 5. |
| **BUG-023** | **MEDIUM** | DevOps | `frontend/Dockerfile:3` | Non-multi-stage frontend Dockerfile requires manual host build | PENDING (Group 5) | Scope of Group 5. |

---

## 3. Detailed Verification Notes for Group 1 Resolutions

### BUG-001 (WebSocket STOMP Security & Tenant Isolation)
- **Status**: **RESOLVED & VERIFIED**
- **Verification Evidence**:
  - `webSocketAnonymousConnectRejected`: Anonymous STOMP `CONNECT` rejected with `AuthenticationCredentialsNotFoundException`.
  - `webSocketInvalidTokensRejected`: Rejects invalid signatures and refresh tokens with `BadCredentialsException`.
  - `webSocketValidConnectAuthenticates`: Authenticates valid token, sets security context and session attributes (`restaurantId: 1L`).
  - `webSocketUnauthenticatedOperationsRejected`: Enforces authentication on `SUBSCRIBE` and `SEND`.
  - `webSocketMultiTenantIsolation`: Rigorously tested with 2 distinct tenants (Restaurant 1 and Restaurant 2). Cross-tenant subscription attempts are blocked with `AccessDeniedException`.
  - Frontend client (`websocket.js`) transmits Bearer token in `connectHeaders` via `beforeConnect()`.

### BUG-002 (Per-Device Edge Authentication & Strict Database Tenant Binding)
- **Status**: **RESOLVED & VERIFIED**
- **Verification Evidence**:
  - `edgeStaticTokenRejected`: Presentation of legacy static string `edge-token-pune-01` returns HTTP 401 Unauthorized.
  - `aiIngestionSucceedsWithValidSignature`: Ingestion with per-device JWT signed by backend succeeds with HTTP 200 `ACCEPTED`.
  - `edgeUnknownDeviceRejected`: Device not found in database rejected with HTTP 401.
  - `edgeInactiveDeviceRejected`: Registered device with status `OFFLINE` rejected with HTTP 401.
  - `edgeClaimTenantMismatchRejected`: Token with fraudulent `restaurantId` claim differing from database binding rejected with HTTP 401.
  - Database-driven tenant resolution: Controller binds tenant from `TenantContext` populated from database entity, ignoring any spoofed tenant IDs in request envelopes.
  - Dynamic token generation endpoint: `POST /api/v1/cameras/edge-token` verified for Managers/Owners.

### BUG-003 & BUG-011 (Backend Containerization & Path Casing)
- **Status**: **RESOLVED & VERIFIED**
- **Verification Evidence**:
  - Created multi-stage `Backend/Dockerfile` with builder (`maven:3.9.9-eclipse-temurin-21-alpine`) and runtime (`eclipse-temurin:21-jre-alpine`).
  - Container runs under unprivileged non-root user `appuser:appgroup` (UID/GID 1000/1000).
  - Readiness health check configured via Spring Boot Actuator: `http://localhost:8080/actuator/health`.
  - `docker-compose.yml` updated: context path changed to `./Backend`, eliminating Linux filesystem casing failure.
  - `mvn package -DskipTests` produces `restrovision-backend.jar` matching Dockerfile copy directive.

### BUG-012 (Flyway Database Schema Migration & Baseline Strategy)
- **Status**: **RESOLVED & VERIFIED**
- **Verification Evidence**:
  - Added `flyway-core` and `flyway-database-oracle` to `pom.xml`.
  - Authored `V1__init_core_schema.sql` (27 tables) reconciling all JPA entities (`user_roles.role_code`, `restaurant_tables` columns, `alerts` columns, `ai_feedback_labels`).
  - Configured `baseline-on-migrate: true` and `baseline-version: 0`, guaranteeing backward compatibility and data safety on existing databases.
  - `flywayMigrationBaseline`: Test 22 verifies Flyway migration baseline version 1 executed with status SUCCESS.
