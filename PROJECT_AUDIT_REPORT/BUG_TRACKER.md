# RESTROVISION Master Defect & Vulnerability Register (Bug Tracker)
**Document ID**: `TRACKER-DEFECTS-20261114`  
**Application Target**: RESTROVISION v2.0  
**Audit Lead**: Principal Software QA Engineer & Security Auditor  
**Last Updated**: 2026-11-14T13:31:00+05:30  
**Total Defects Logged**: 23  
**Defects Summary**: Critical: 3 | High: 12 | Medium: 7 | Low: 1  

---

## 1. Master Defect Summary Table

| Defect ID | Severity | Category | Component / File | Summary | Verification Status |
| :--- | :---: | :--- | :--- | :--- | :---: |
| **BUG-001** | **CRITICAL** | Security | `WebSocketConfig.java:55-80` | Unauthenticated STOMP & cross-tenant subscription bypass | **CONFIRMED** |
| **BUG-002** | **CRITICAL** | Security | `JwtAuthFilter.java:57-67` | Static edge token & hardcoded tenant ID (`1L`) | **CONFIRMED** |
| **BUG-003** | **CRITICAL** | DevOps | `Backend/Dockerfile` | Dockerfile missing from disk; compose build fails | **CONFIRMED** |
| **BUG-004** | **HIGH** | Multi-Tenancy| `TenantContext.java:12-25` | ThreadLocal defaults and resets to `1L` (Tenant bleed) | **STATICALLY VERIFIED** |
| **BUG-005** | **HIGH** | Security | `AiEventIngestionService.java:45-70` | Zero timestamp freshness validation (Event replay) | **CONFIRMED** |
| **BUG-006** | **HIGH** | Performance | `TableStateMachineService.java:59-86`| $1 + 2N$ database query cascade on table broadcasts | **STATICALLY VERIFIED** |
| **BUG-007** | **HIGH** | Performance | `Order.java:43` | `FetchType.EAGER` on `items` collection (Heap memory risk) | **STATICALLY VERIFIED** |
| **BUG-008** | **HIGH** | Security | `AuthController.java:35` | No brute-force rate limiting or account lockout on login | **CONFIRMED** |
| **BUG-009** | **HIGH** | Security | `PublicQrController.java:120-135` | Stored XSS in customer feedback comments | **CONFIRMED** |
| **BUG-010** | **HIGH** | Code Quality | `src/pages/*.jsx` (17 places) | Empty catch blocks silently swallow API mutation failures | **STATICALLY VERIFIED** |
| **BUG-011** | **HIGH** | DevOps | `docker-compose.yml:7` | Case-sensitivity path mismatch (`./backend` vs `./Backend`) | **CONFIRMED** |
| **BUG-012** | **HIGH** | Database | `pom.xml` / `application-oracle.yml`| Missing Flyway/Liquibase with `ddl-auto: validate` enabled | **CONFIRMED** |
| **BUG-013** | **MEDIUM** | Security | `PublicQrController.java:120` | Unauthenticated feedback flooding / storage DoS | **CONFIRMED** |
| **BUG-014** | **MEDIUM** | Security | `src/services/api.js:15` | JWT tokens stored in browser `localStorage` | **STATICALLY VERIFIED** |
| **BUG-015** | **MEDIUM** | Architecture| `src/pages/Analytics.jsx` | 1,015-line God component containing embedded chart logic | **STATICALLY VERIFIED** |
| **BUG-016** | **MEDIUM** | Database | `TableSession.java`, `StaffTask.java` | Missing `@Version` optimistic locking on mutable entities | **STATICALLY VERIFIED** |
| **BUG-017** | **HIGH** | Performance | `TableStateMachineService.java:59` | Concurrency latency degradation (p99 > 1,021ms at 100 VUs) | **CONFIRMED** |
| **BUG-018** | **MEDIUM** | Accessibility| `Login.jsx` & `CustomerQrPortal.jsx` | WCAG 2.2 AA violations: Missing `<label>` & unlabelled star buttons | **CONFIRMED** |
| **BUG-019** | **LOW** | UI/UX | `CustomerQrPortal.jsx` & `FloorPlan` | 6px horizontal scroll overflow on mobile viewport (375px) | **CONFIRMED** |
| **BUG-020** | **MEDIUM** | Engineering | `traffic.py` & `sentiment.py` | Superficial ML mock implementations (Hardcoded weights) | **STATICALLY VERIFIED** |
| **BUG-021** | **HIGH** | Testing | `frontend/package.json` | 0.0% frontend automated test coverage (No Vitest/Jest/Cypress) | **CONFIRMED** |
| **BUG-022** | **HIGH** | DevOps | Root repository | Zero CI/CD automation (.github/workflows, Jenkinsfile absent) | **CONFIRMED** |
| **BUG-023** | **MEDIUM** | DevOps | `frontend/Dockerfile:3` | Non-multi-stage frontend Dockerfile requires manual host build | **STATICALLY VERIFIED** |

---

## 2. Detailed Defect Dossiers

### BUG-001: Unauthenticated STOMP WebSocket Access & Cross-Tenant Eavesdropping
- **Severity**: **CRITICAL (CVSS 9.8)**
- **Component**: Backend Messaging (`WebSocketConfig.java`)
- **Preconditions**: Application backend running on port 8080.
- **Reproduction Steps**:
  1. Establish WebSocket handshake to `ws://localhost:8080/ws`.
  2. Send STOMP `CONNECT` frame without `Authorization` or `token` headers.
  3. Send `SUBSCRIBE` frame to destination `/topic/restaurants/999/alerts`.
- **Expected Result**: Server rejects unauthenticated connection with STOMP `ERROR` frame and closes socket.
- **Actual Result**: Server responds with `CONNECTED` and permits subscription to foreign tenant topic `/topic/restaurants/999/alerts`.
- **Evidence**:
  ```text
  [ARCH-001] Testing Anonymous WebSocket STOMP Handshake:
    WebSocket connection opened.
    [CONFIRMED] STOMP CONNECTED received with ZERO authentication headers!
    Subscribed to /topic/restaurants/999/alerts (Cross-Tenant eavesdropping).
  ```
- **Remediation**: In `WebSocketConfig.java`, implement `ChannelInterceptor.preSend` to validate JWT from STOMP headers on `StompCommand.CONNECT` and enforce strict tenant matching.

---

### BUG-002: Static Shared Edge Token & Hardcoded Tenant Binding
- **Severity**: **CRITICAL (CVSS 9.1)**
- **Component**: Backend Security Filter (`JwtAuthFilter.java:57-67`)
- **Preconditions**: Valid HMAC SHA-256 secret configured.
- **Reproduction Steps**:
  1. Issue HTTP POST to `/api/v1/ai/events`.
  2. Provide header `Authorization: Bearer edge-token-pune-01`.
  3. Include JSON body with `restaurantId: 999` and computed `X-Signature`.
- **Expected Result**: Server validates dynamic token per edge camera and assigns correct tenant.
- **Actual Result**: Server accepts static string `edge-token-pune-01` and overrides tenant to hardcoded `1L`:
  ```json
  {"eventId":"test-evt-1794642602386","success":true,"status":"ACCEPTED"}
  ```
- **Remediation**: Eliminate hardcoded strings; issue asymmetric per-node JWTs containing verified tenant scopes.

---

### BUG-003: Missing Backend Dockerfile
- **Severity**: **CRITICAL (Deployment Blocker)**
- **Component**: Deployment / Containerization (`docker-compose.yml`)
- **Preconditions**: Clean clone of project repository.
- **Reproduction Steps**:
  1. Open terminal at project root.
  2. Execute `docker compose build`.
- **Expected Result**: Backend container builds successfully.
- **Actual Result**: Build crashes immediately: `failed to solve: failed to read dockerfile`.
- **Evidence**: `Test-Path D:\RESTROVISION\Backend\Dockerfile` returns `False`.
- **Remediation**: Create a multi-stage `Backend/Dockerfile` based on `maven:3.9-eclipse-temurin-21` and `eclipse-temurin:21-jre-alpine`.

---

### BUG-004: ThreadLocal Tenant Context Bleed
- **Severity**: **HIGH (CVSS 8.2)**
- **Component**: Backend Context (`TenantContext.java:12-25`)
- **Preconditions**: Concurrent requests processed via pooled threads.
- **Technical Evidence**:
  ```java
  private static final ThreadLocal<Long> CURRENT_TENANT = ThreadLocal.withInitial(() -> 1L);
  public static void clear() {
      CURRENT_TENANT.set(1L); // Should be remove()!
  }
  ```
- **Impact**: Any unauthenticated request or improperly authenticated request running on a recycled thread inherits Tenant 1 privileges.
- **Remediation**: Replace `ThreadLocal.withInitial(() -> 1L)` with standard `new ThreadLocal<>()` and use `CURRENT_TENANT.remove()` in `clear()`.

---

### BUG-005: Event Timestamp Freshness Omission (AI Event Replay)
- **Severity**: **HIGH (CVSS 7.5)**
- **Component**: Backend Ingestion (`AiEventIngestionService.java:45-70`)
- **Preconditions**: Edge service access.
- **Reproduction Steps**:
  1. Generate valid AI event payload with `occurredAt` set to 10 days in the past (`2026-11-04T10:00:00Z`).
  2. Compute valid HMAC signature and POST to `/api/v1/ai/events`.
- **Expected Result**: Server rejects payload with 400 Bad Request (Stale event timestamp).
- **Actual Result**: Server accepts event with `HTTP 200 OK {"status":"ACCEPTED"}`.
- **Remediation**: Validate that $|t_{now} - t_{event}| \le 60\text{ seconds}$.

---

### BUG-006: $1 + 2N$ Database Query Cascade in `TableStateMachineService`
- **Severity**: **HIGH (Performance Bottleneck)**
- **Component**: Service Layer (`TableStateMachineService.java:59-86` & `384-386`)
- **Technical Evidence**:
  `getTableView` iterates over all tables and queries `employeeRepository` and `sessionRepository` once per table. Called synchronously inside `broadcastTable`.
- **Impact**: 81 database queries executed on every table state transition for a 40-table venue.
- **Remediation**: Refactor to a single JPQL query using `LEFT JOIN`.

---

### BUG-007: `FetchType.EAGER` Memory Hazard on `Order.items`
- **Severity**: **HIGH (Resource Exhaustion)**
- **Component**: Domain Model (`Order.java:43`)
- **Technical Evidence**:
  `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)`
- **Impact**: Order queries force immediate child joins, exhausting JVM heap memory under high transaction volume.
- **Remediation**: Change to `fetch = FetchType.LAZY`.

---

### BUG-008: Login Brute-Force Susceptibility
- **Severity**: **HIGH (CVSS 7.5)**
- **Component**: Authentication Controller (`AuthController.java:35`)
- **Preconditions**: Public access to login endpoint.
- **Reproduction Steps**:
  1. Send 20 rapid invalid POST requests to `/api/v1/auth/login`.
- **Expected Result**: Endpoint responds with `HTTP 429 Too Many Requests` or locks account.
- **Actual Result**: All 20 requests returned `HTTP 401 Unauthorized` without throttling or lockout.
- **Remediation**: Deploy Bucket4j rate limiting filter (5 attempts/min per IP).

---

### BUG-009: Stored Cross-Site Scripting (XSS) in Feedback
- **Severity**: **HIGH (CVSS 7.2)**
- **Component**: Public Controller (`PublicQrController.java:120-135`)
- **Preconditions**: Public access to feedback endpoint.
- **Reproduction Steps**:
  1. POST `/api/v1/public/feedback` with `comment: "<img src=x onerror=\"alert('XSS')\">"`.
- **Expected Result**: Script stripped or HTML escaped.
- **Actual Result**: Stored verbatim in DB (Feedback ID 11); rendered into staff dashboards.
- **Remediation**: Sanitize input using OWASP Java HTML Sanitizer before persistence.

---

### BUG-010: Silent UI Error Swallowing
- **Severity**: **HIGH (Operational Resilience)**
- **Component**: Frontend (`src/pages/*.jsx`, 17 occurrences)
- **Technical Evidence**:
  Multiple `} catch (_) {}` blocks across floor, kitchen, and alert components.
- **Impact**: Network failures or server 500s appear to succeed in the UI, misleading staff.
- **Remediation**: Add error toast notifications (`react-hot-toast` or similar) in all catch handlers.

---

### BUG-011: Docker Compose Case-Sensitivity Failure
- **Severity**: **HIGH (Deployment Failure)**
- **Component**: `docker-compose.yml:7`
- **Technical Evidence**:
  `context: ./backend` on a Windows filesystem where the directory is actually `Backend`.
- **Impact**: Fails on Linux production CI/CD build agents.
- **Remediation**: Rename context to `./Backend` or rename folder to lowercase `backend`.

---

### BUG-012: Missing Production Database Migration Automation
- **Severity**: **HIGH (Production Risk)**
- **Component**: Maven Build (`pom.xml`) & Config (`application-oracle.yml`)
- **Technical Evidence**:
  `ddl-auto: validate` is enabled for Oracle profile, but no Flyway or Liquibase dependency is present.
- **Impact**: Application startup aborts if DB schema is not manually populated.
- **Remediation**: Integrate Flyway in `pom.xml`.

---

### BUG-013: Unauthenticated Feedback Flooding (Denial of Service)
- **Severity**: **MEDIUM (CVSS 5.3)**
- **Component**: Public Controller (`PublicQrController.java:120`)
- **Impact**: Attacker can flood database with millions of fake feedback entries.
- **Remediation**: Implement IP rate limiting and Cloudflare Turnstile / CAPTCHA.

---

### BUG-014: Sensitive Tokens in Browser `localStorage`
- **Severity**: **MEDIUM (CVSS 5.0)**
- **Component**: Frontend Storage (`src/services/api.js:15`)
- **Impact**: Vulnerable to theft via XSS (**BUG-009**).
- **Remediation**: Transition JWT access and refresh tokens to `HttpOnly`, `SameSite=Strict` cookies.

---

### BUG-015: 1,015-Line God Component in `Analytics.jsx`
- **Severity**: **MEDIUM (Maintainability)**
- **Component**: Frontend Architecture (`Analytics.jsx`)
- **Impact**: High cyclomatic complexity, difficult to maintain and test.
- **Remediation**: Extract modular sub-components: `PnLCard.jsx`, `CrowdCurveChart.jsx`, `MenuMatrix.jsx`.

---

### BUG-016: Missing Optimistic Locking on Concurrency-Prone Entities
- **Severity**: **MEDIUM (Data Integrity)**
- **Component**: Domain Entities (`TableSession.java`, `StaffTask.java`, `ServiceRequest.java`)
- **Impact**: Lost update race conditions when multiple staff update records simultaneously.
- **Remediation**: Add `@Version private Long version;` to `TableSession`, `StaffTask`, and `ServiceRequest`.

---

### BUG-017: Concurrency Tail Latency Degradation (N+1 Query Bottleneck)
- **Severity**: **HIGH (Performance)**
- **Component**: Service Layer (`TableStateMachineService.java:59`)
- **Preconditions**: 100 concurrent virtual users querying table status.
- **Reproduction Steps**:
  1. Execute load test against `GET /api/v1/tables` at 100 VUs.
- **Expected Result**: Sub-200ms p95 latency under local testing.
- **Actual Result**: Average latency degraded to 248.45 ms; p95 reached 637.82 ms; p99 exceeded 1,021.12 ms.
- **Remediation**: Replace iterative loops with a single batch `LEFT JOIN` JPQL projection.

---

### BUG-018: WCAG 2.2 AA Violations (Missing Labels & Unlabelled Rating Buttons)
- **Severity**: **MEDIUM (Accessibility Compliance)**
- **Component**: Frontend (`Login.jsx` & `CustomerQrPortal.jsx`)
- **Preconditions**: Screen reader assistive technology in use.
- **Reproduction Steps**:
  1. Inspect email and password fields on `/login`.
  2. Inspect 5 star rating buttons on `/qr-portal`.
- **Expected Result**: Inputs have explicit `<label for="...">` or `aria-label`; star buttons have `aria-label="Rate N stars"`.
- **Actual Result**: Inputs rely on visual placeholder; star buttons render bare SVG icons without accessible name.
- **Remediation**: Add semantic `<label>` elements and `aria-label` attributes.

---

### BUG-019: Mobile Horizontal Scroll Layout Overflow
- **Severity**: **LOW (UI Ergonomics)**
- **Component**: Frontend (`CustomerQrPortal.jsx` & `FloorPlan.jsx`)
- **Preconditions**: Mobile device viewport width $\le 375\text{px}$.
- **Reproduction Steps**:
  1. Set viewport to 375px $\times$ 812px (iPhone 13).
  2. Evaluate `document.documentElement.scrollWidth`.
- **Expected Result**: `scrollWidth == 375px` (Zero horizontal overflow).
- **Actual Result**: `scrollWidth == 381px` (6px horizontal overflow causing horizontal jitter).
- **Remediation**: Add `overflow-x-hidden` on parent container and verify fixed margins.

---

### BUG-020: Superficial Machine Learning Implementations
- **Severity**: **MEDIUM (Engineering Quality)**
- **Component**: Python Edge AI (`traffic.py` & `sentiment.py`)
- **Impact**: Code advertises "LightGBM Quantile Regression" and "IndicBERT Transformer", but implements static dictionary lookups and scalar arithmetic.
- **Remediation**: Integrate genuine ONNX runtime inference or clarify documentation that heuristics are in use.

---

### BUG-021: Zero Automated Frontend Test Coverage
- **Severity**: **HIGH (Quality & Regression Risk)**
- **Component**: Frontend (`frontend/package.json`)
- **Impact**: 9 critical operational pages possess zero unit, integration, or E2E tests, risking undetected frontend regressions.
- **Remediation**: Set up Vitest, React Testing Library, and Playwright.

---

### BUG-022: Total Absence of CI/CD Automated Pipelines
- **Severity**: **HIGH (DevOps & Delivery Risk)**
- **Component**: Repository Root (`.github/workflows`)
- **Impact**: All builds, test runs, and deployments are entirely manual with zero automated quality gates.
- **Remediation**: Create `.github/workflows/ci.yml` running Maven test, Vite build, and Docker build.

---

### BUG-023: Non-Multi-Stage Frontend Dockerfile
- **Severity**: **MEDIUM (DevOps Packaging)**
- **Component**: Frontend (`frontend/Dockerfile:3`)
- **Impact**: `COPY dist /usr/share/nginx/html` fails on clean CI/CD runners if `dist` is not pre-built on host.
- **Remediation**: Convert to multi-stage Docker build with `node:20-alpine` build stage.
