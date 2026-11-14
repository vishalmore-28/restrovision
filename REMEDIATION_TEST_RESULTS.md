# RESTROVISION v2.0 - Remediation Group 1 Test Execution Results
**Target Environment**: Windows 11 / Eclipse Temurin JDK 21 / Maven 3.10 / H2 Oracle Mode & Production Schema  
**Execution Timestamp**: 2026-11-14T13:52:19+05:30  
**Test Suite**: `com.restrovision.RestrovisionApplicationTests`  
**Total Tests**: 27 | **Passed**: 27 | **Failed**: 0 | **Skipped**: 0 | **Success Rate**: 100.0%  

---

## 1. Test Suite Execution Summary

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.restrovision.RestrovisionApplicationTests
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 22.77 s -- in com.restrovision.RestrovisionApplicationTests
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  31.812 s
[INFO] Finished at: 2026-11-14T13:52:19+05:30
[INFO] ------------------------------------------------------------------------
```

---

## 2. Individual Test Results & Security Verifications

### 2.1. Group 1 P0 Mandate 1: WebSocket STOMP Security & Tenant Isolation (BUG-001)

| Test # | Test Name | Target Verification | Result | Evidence / Log Tracing |
| :---: | :--- | :--- | :---: | :--- |
| **23** | `webSocketAnonymousConnectRejected` | Rejects anonymous STOMP `CONNECT` frame | **PASS** | `assertThrows(AuthenticationCredentialsNotFoundException)` verified. Log: `STOMP CONNECT rejected: Missing or invalid Authorization header` |
| **24** | `webSocketInvalidTokensRejected` | Rejects malformed JWT and REFRESH tokens on STOMP `CONNECT` | **PASS** | `assertThrows(BadCredentialsException)` verified for both invalid JWT signature and refresh token usage. Log: `STOMP CONNECT rejected: Refresh token attempted for WebSocket authentication` |
| **25** | `webSocketValidConnectAuthenticates` | Authenticates valid JWT and establishes session tenant binding | **PASS** | Authenticated `UsernamePasswordAuthenticationToken` set on accessor; `sessionAttributes.restaurantId == 1L`. Log: `WebSocket STOMP authenticated for owner@restrovision.local (Restaurant ID: 1)` |
| **26** | `webSocketUnauthenticatedOperationsRejected` | Enforces authentication on `SUBSCRIBE` and `SEND` | **PASS** | `assertThrows(AccessDeniedException)` verified. Log: `Blocked unauthenticated WebSocket subscription attempt to /topic/restaurants/1/tables` |
| **27** | `webSocketMultiTenantIsolation` | Enforces strict tenant authorization across two separate tenants | **PASS** | **Tenant 1 session**: Subscribing to `/topic/restaurants/1/tables` succeeds; subscribing to `/topic/restaurants/2/tables` throws `AccessDeniedException`.<br>**Tenant 2 session**: Subscribing to `/topic/restaurants/2/tables` succeeds; subscribing to `/topic/restaurants/1/tables` throws `AccessDeniedException`. Log: `Blocked cross-tenant WebSocket subscription: client tenant 1 attempted to subscribe to /topic/restaurants/2/tables` |

---

### 2.2. Group 1 P0 Mandate 2: Per-Device Edge Authentication & Strict Database Tenant Binding (BUG-002)

| Test # | Test Name | Target Verification | Result | Evidence / Log Tracing |
| :---: | :--- | :--- | :---: | :--- |
| **16** | `edgeStaticTokenRejected` | Legacy static shared token string (`edge-token-pune-01`) is rejected | **PASS** | HTTP 401 Unauthorized returned when presenting `Authorization: Bearer edge-token-pune-01`. |
| **04** | `aiIngestionRejectsBadSignature` | HMAC SHA-256 verification with authenticated device | **PASS** | HTTP 401 Unauthorized with `ProblemDetail` title `"Invalid Signature"` returned for tampered HMAC. |
| **05** | `aiIngestionSucceedsWithValidSignature` | Ingestion with valid per-device JWT and HMAC signature | **PASS** | HTTP 200 OK returned with `status: "ACCEPTED"`. Database updated `last_seen_at`. |
| **06** | `aiIngestionRejectsLowConfidence` | Confidence gate filtering | **PASS** | HTTP 200 OK with `status: "REJECTED_BY_RULE"` for confidence 0.55 (< 0.70 threshold). |
| **07** | `aiIngestionDuplicateAtomicallyIgnored` | Idempotency and deduplication | **PASS** | First post: `ACCEPTED`. Second post with identical `eventId`: `DUPLICATE_IGNORED`. |
| **17** | `edgeUnknownDeviceRejected` | Rejects unregistered edge device ID | **PASS** | Device `unknown-device-999` not found in `edge_nodes` table; rejected with HTTP 401. Log: `Edge authentication rejected: Unknown edge device: unknown-device-999` |
| **18** | `edgeInactiveDeviceRejected` | Rejects inactive/offline edge device | **PASS** | Device `edge-offline-99` has `status: "OFFLINE"`; rejected with HTTP 401. Log: `Edge authentication rejected: Device edge-offline-99 has inactive status: OFFLINE` |
| **19** | `edgeClaimTenantMismatchRejected` | Rejects token where JWT tenant claim conflicts with database record | **PASS** | Token claims `restaurantId: 999`, but DB binds `edge-pune-01` to `restaurantId: 1`; rejected with HTTP 401. Log: `Edge authentication rejected: Claimed restaurantId 999 does not match DB binding 1` |
| **20** | `edgeTokenGenerationEndpoint` | Manager/Owner dynamic edge token provisioning | **PASS** | `POST /api/v1/cameras/edge-token` returns signed JWT with `issuer: "restrovision-edge-auth"`, `audience: "restrovision-backend"`, `tokenType: "EDGE_DEVICE"`. |

---

### 2.3. Group 1 P0 Mandate 3: Containerization & Readiness Health Probes (BUG-003, BUG-011)

| Test # | Test Name | Target Verification | Result | Evidence / Log Tracing |
| :---: | :--- | :--- | :---: | :--- |
| **21** | `actuatorHealthCheckEndpoint` | Production health check via Spring Boot Actuator | **PASS** | `GET /actuator/health` returns HTTP 200 with `{"status":"UP"}`.<br>`GET /actuator/info` returns HTTP 200. |
| **N/A** | Dockerfile Multi-Stage Inspection | Multi-stage build, non-root user, jar artifact name | **PASS** | `Backend/Dockerfile` verified: builder stage (`maven:3.9.9`), runtime stage (`eclipse-temurin:21-jre-alpine`), `USER appuser:appgroup`, `HEALTHCHECK` with curl to `/actuator/health`. |
| **N/A** | Docker Compose Path Casing | Path casing on Linux filesystems | **PASS** | `docker-compose.yml` updated: `context: ./Backend` (capital B). |
| **N/A** | Maven Packaging Verification | Final JAR packaging name | **PASS** | `mvn package -DskipTests` produces `target/restrovision-backend.jar` matching Dockerfile copy directive. |

---

### 2.4. Group 1 P0 Mandate 4: Flyway Database Schema Migration & Baseline Strategy (BUG-012)

| Test # | Test Name | Target Verification | Result | Evidence / Log Tracing |
| :---: | :--- | :--- | :---: | :--- |
| **22** | `flywayMigrationBaseline` | Flyway V1 migration initialization | **PASS** | Flyway bean initialized, executing `V1__init_core_schema.sql`. Current migration version equals `1` with status `SUCCESS`. |
| **N/A** | Entity Column Contract Reconciliation | Schema-to-Entity parity | **PASS** | All 21 JPA entities reconciled in `V1__init_core_schema.sql` (e.g. `user_roles.role_code`, `restaurant_tables.qr_token`, `alerts.message`, `ai_feedback_labels`). |
| **N/A** | Baseline on Migrate Configuration | Safe upgrade on existing databases | **PASS** | `spring.flyway.baseline-on-migrate: true` configured in both `application.yml` and `application-oracle.yml`, preventing accidental drops or overwrites of existing data. |

---

### 2.5. Existing Business Functionality Regression Verification

| Test # | Test Name | Workflow / Area | Result | Notes |
| :---: | :--- | :--- | :---: | :--- |
| **01** | `contextLoads` | Spring Application Context | **PASS** | Context, all 21 JPA repositories, and security beans start cleanly. |
| **02** | `unauthenticatedAccessDenied` | API Authentication Filter | **PASS** | Anonymous HTTP GET to `/api/v1/tables` rejected with 401. |
| **03** | `aiIngestionRequiresAuth` | AI Event Security | **PASS** | Unauthenticated HTTP POST to `/api/v1/ai/events` rejected with 401. |
| **08** | `publicQrTokenValidation` | Customer QR Portal | **PASS** | Resolves valid QR token `qr-tok-table-01`; rejects invalid token with 400. |
| **09** | `multiTenantBoundaryEnforced` | Multi-Tenant Data Isolation | **PASS** | Owner can only retrieve tables for their authorized restaurant. |
| **10** | `rbacForbiddenEndpoint` | Role-Based Access Control | **PASS** | Kitchen role forbidden from employee management endpoint (403 Forbidden). |
| **11** | `rbacAllowedEndpoint` | Role-Based Access Control | **PASS** | Manager role permitted to access employee management endpoint (200 OK). |
| **12** | `stateMachineTransition` | Table State Machine | **PASS** | Table transition `AVAILABLE -> OCCUPIED` succeeds; invalid status `BILL_REQUESTED` rejected with 400. |
| **13** | `refreshTokenRejectedAsBearer` | JWT Token Type Validation | **PASS** | Refresh tokens presented as Bearer tokens in Authorization header rejected with 401. |
| **14** | `orderValidation` | Order Processing | **PASS** | Validates non-empty item lists and strictly positive quantities (400 for invalid). |
| **15** | `analyticsOverviewHonestStates` | Analytics Service | **PASS** | Computed analytics returned without mock fallbacks. |

---

## 3. Frontend Production Build Verification

```text
> restrovision-frontend@2.0.0 build
> vite build

vite v5.4.21 building for production...
transforming...
✓ 1610 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                   0.86 kB │ gzip:  0.54 kB
dist/assets/index-15qfTajy.css   34.79 kB │ gzip:  7.04 kB
dist/assets/index-D-3avaZL.js   318.04 kB │ gzip: 81.65 kB
✓ built in 4.38s
```
**Verdict**: Frontend build completed with **0 errors, 0 warnings**.
