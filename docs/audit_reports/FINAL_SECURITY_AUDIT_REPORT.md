# RESTROVISION v2.0 — Final Comprehensive Security Audit Report

**Document ID:** `SEC-AUDIT-20261115-REVISED`  
**Application Target:** RESTROVISION v2.0 Architecture & Multi-Stack Implementation  
**Branch:** `feature/production-hardening`  
**Lead Auditor:** Principal Security Architect & Penetration Tester  
**Date:** 2026-11-15T00:35:00+05:30  
**Overall Code & Architectural Security Posture:** **HARDENED & VERIFIED**  
**External Vulnerability Database Verification:** **PARTIAL (npm PASS, pip PASS, Maven BLOCKED)**  

---

## 1. Executive Summary & Audit Distinctions

This comprehensive security audit assesses the final hardened state of the RESTROVISION v2.0 platform across application layers, authentication mechanisms, network protocols, multi-tenant isolation, cryptographic primitives, and container configurations.

> [!IMPORTANT]
> **Audit Classification Distinction:**
> - **Codebase Architectural Security:** **APPROVED & HARDENED**. All architectural vulnerabilities (unauthenticated WebSocket access, static edge tokens, tenant context leakage, stored XSS, plaintext JWT in `localStorage`, and open persistence context) have been eliminated and verified via 223 automated regression tests.
> - **Dependency Vulnerability Clearance:** **PARTIAL / QUALIFIED**. Frontend (`npm audit`) and Edge AI (`pip-audit`) are certified with **0 vulnerabilities**. However, the Maven OWASP Dependency-Check scan is **BLOCKED** due to the absence of an NVD API key (`$env:NVD_API_KEY`) and unauthenticated NVD rate limits. Therefore, **zero Maven vulnerabilities cannot be claimed** until an authenticated scan is executed in CI/CD.

---

## 2. Multi-Stack Dependency Security Status

### 2.1 Frontend Dependencies (`npm audit`)
- **Execution Command:** `npm audit --omit=dev --json` & `npm audit --json`
- **Packages Audited:** 370 packages total (23 production, 348 development)
- **Vulnerabilities Detected:**
  - Critical: **0**
  - High: **0**
  - Moderate: **0**
  - Low: **0**
  - Informational: **0**
- **Advisories Remediated:** GHSA-67mh-4wv8-2f99 (esbuild dev server vulnerability in Vite 5) was eliminated by upgrading to `vite@^6.4.6` and `@vitejs/plugin-react@^4.3.4`.
- **Status:** **PASS (0 Vulnerabilities)**

### 2.2 Edge AI Dependencies (`pip-audit`)
- **Execution Command:** `python -m pip_audit -r requirements.txt`
- **Runtime:** Python 3.13 / pip-audit 2.10.1
- **Vulnerabilities Detected:** **0** ("No known vulnerabilities found")
- **Packages Scanned:** `fastapi`, `uvicorn`, `pydantic`, `httpx`, `numpy`, `oracledb`, `cryptography`, `opencv-python`.
- **Status:** **PASS (0 Vulnerabilities)**

### 2.3 Backend Dependencies (Maven OWASP Dependency-Check)
- **Profile Configured:** `dependency-audit-strict` in `Backend/pom.xml`
- **Enforcement Rules:** `failBuildOnCVSS = 7.0`, `failOnError = true`
- **Execution Status:** **BLOCKED (Documented)**
  - Remote NVD CVE database download requires `$env:NVD_API_KEY` to prevent rate-limiting rejection by the National Vulnerability Database.
  - In the local execution environment, `$env:NVD_API_KEY` is not set.
  - **Audit Finding:** The security gate is fully configured in the build definition, but actual CVE findings remain **UNVERIFIED / BLOCKED** until executed in an authenticated CI/CD runner. Zero Maven vulnerabilities are NOT claimed.

---

## 3. Production Secret Hardening & Enterprise Database Account Separation

Following security audit findings and operational review, `Backend/src/main/resources/application-oracle.yml` was hardened (Commit `a4b61d3` and subsequent dual-account architecture):

### 3.1 Elimination of Insecure Secret Fallbacks
1. **Elimination of Weak Default Password:** The default fallback `password: ${ORACLE_PASSWORD:restrovision123}` was completely removed.
2. **Mandatory Secrets Fail-Fast:** `username: ${ORACLE_APP_USERNAME}`, `password: ${ORACLE_APP_PASSWORD}`, `user: ${ORACLE_MIGRATION_USERNAME}`, `password: ${ORACLE_MIGRATION_PASSWORD}`, `restrovision.jwt.secret: ${RESTROVISION_JWT_SECRET}`, and `restrovision.edge.hmac-secret: ${RESTROVISION_EDGE_HMAC_SECRET}` have **zero default fallbacks** and zero role fallback. If any secret is omitted at startup, the Spring Boot application fails fast immediately with `IllegalArgumentException`.
3. **Mandatory Secure Cookies:** `restrovision.security.cookie.secure: ${RESTROVISION_COOKIE_SECURE:true}` defaults to `true` on the Oracle profile, guaranteeing that session and refresh tokens are transmitted only over HTTPS.

### 3.2 Dual-Account Least Privilege Architecture
To enforce strict separation of duties and defense-in-depth:
1. **Migration Account (`RESTROVISION_MIGRATOR`):** Schema owner and DDL administrator. Executes Flyway migrations `V1` and `V2` within schema `RESTROVISION_MIGRATOR`. Granted specific DDL privileges (`CREATE TABLE`, `CREATE VIEW`, `CREATE SEQUENCE`, `CREATE PROCEDURE`, `CREATE TRIGGER`, `CREATE SYNONYM`) derived strictly from the actual migration scripts. Unlimited tablespace quota was revoked and replaced with a justified finite quota of **10 GB** on `users` tablespace (sufficient for 2+ years of operation at 5,000 sessions/day while guarding against storage exhaustion).
2. **Runtime Application Account (`RESTROVISION_APP`):** Application connection pool account used by Spring Boot JPA at runtime. Granted strictly **zero DDL privileges** (`NO CREATE TABLE`, `NO DROP`, `NO ALTER`) and **zero tablespace quota** (`QUOTA 0 ON users`). Receives only DML privileges (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) via dedicated role `restrovision_app_role` and synonyms, mitigating SQL injection impact against schema modification. Flyway metadata (`flyway_schema_history`) and internal identity sequences (`ISEQ$$%`) are explicitly excluded from application grants and synonyms.

---

## 4. WebSocket STOMP Security & Fail-Closed RBAC

WebSocket communication is governed by `WebSocketConfig.java` with a custom `ChannelInterceptor`:

### 4.1 Authentication & Session Binding (`CONNECT`)
- Anonymous connections are rejected with `AuthenticationCredentialsNotFoundException`.
- JWT access token is extracted from STOMP `connectHeaders` (Bearer token).
- Cryptographic signature and expiration are validated against `restrovision.jwt.secret`.
- Authenticated `Principal` and user's tenant ID (`restaurantId`) are bound to session attributes.

### 4.2 Subscription Authorization (`SUBSCRIBE`)
Subscriptions to broadcast topics (`/topic/restaurants/{tenantId}/**`) are protected by a strict 5-step fail-closed authorization pipeline:
1. **Authenticated Principal:** `auth != null && auth.isAuthenticated() == true`. Unauthenticated principals are rejected immediately.
2. **Granted Authorities:** `auth.getAuthorities()` must not be empty. Users with zero authorities are rejected.
3. **Explicit Topic Allowlist:** Destination must match an explicit allowed topic pattern:
   - `/topic/restaurants/{id}/tables`
   - `/topic/restaurants/{id}/orders`
   - `/topic/restaurants/{id}/alerts`
   - `/topic/restaurants/{id}/tasks`
4. **Tenant Isolation:** The tenant ID embedded in the destination path must match the principal's tenant ID. Cross-tenant subscriptions are blocked.
5. **Role-Based Access Control (RBAC):** Principal's authorities must intersect the allowed roles for the requested topic:

| Destination Pattern | Allowed Roles | Prohibited Roles |
| :--- | :--- | :--- |
| `/topic/restaurants/{id}/tables` | `ROLE_WAITER`, `ROLE_MANAGER`, `ROLE_OWNER` | `ROLE_KITCHEN`, `ROLE_CLEANING` |
| `/topic/restaurants/{id}/orders` | `ROLE_WAITER`, `ROLE_KITCHEN`, `ROLE_MANAGER`, `ROLE_OWNER` | `ROLE_CLEANING` |
| `/topic/restaurants/{id}/alerts` | `ROLE_WAITER`, `ROLE_KITCHEN`, `ROLE_MANAGER`, `ROLE_OWNER`, `ROLE_CLEANING` | *(None)* |
| `/topic/restaurants/{id}/tasks` | `ROLE_WAITER`, `ROLE_CLEANING`, `ROLE_MANAGER`, `ROLE_OWNER` | `ROLE_KITCHEN` |

### 4.3 Message Dispatch Authorization (`SEND`)
- **Direct Broadcast Blocking:** Client-originated dispatches directly to `/topic/**` are strictly rejected, preventing message spoofing.
- **Path Traversal & Separator Sanitization:** Destinations with backslashes (`\`), URL-encoded slashes (`%2f`, `%2F`), or directory traversal (`..`) are blocked and the socket session is terminated.
- **Allowed Application Destinations:** Only `/app/ping` and `/app/restaurants/{tenantId}/(ping|heartbeat)` are accepted from clients.

---

## 5. Edge AI Cryptographic Security & Ingestion Defense

The Edge AI subsystem connects local camera nodes to the backend via HTTP and WebSocket:

### 5.1 Per-Device JWT Authentication
- Legacy static token string (`edge-token-pune-01`) is rejected with HTTP 401 Unauthorized.
- Each edge device is provisioned with a cryptographic JWT containing device ID, node name, and bound restaurant ID.
- Ingestion filter verifies device existence in the database and enforces status `ACTIVE` / `ONLINE`.
- Tokens belonging to revoked devices or devices marked `OFFLINE` are rejected with HTTP 401.

### 5.2 HMAC-SHA256 Payload Integrity
- Ingestion payloads require an `X-Signature` header computed as:
  $$\text{HMAC-SHA256}(K_{\text{shared}}, \text{payload\_bytes})$$
- Cryptographic verification occurs before any JSON parsing or event processing. Tampered payloads fail with HTTP 401.

### 5.3 Replay Attack & Clock-Skew Defense
`AiEventIngestionService.java` enforces dual-window timestamp validation on event creation timestamps ($T_{\text{event}}$):
1. **Freshness Window ($T_{\text{now}} - T_{\text{event}} \le 7,200\text{s}$):** Events older than 2 hours are rejected as `REJECTED_STALE`, mitigating replay attacks while accommodating edge outbox retries.
2. **Clock-Skew Tolerance ($T_{\text{event}} - T_{\text{now}} \le 600\text{s}$):** Events dated more than 10 minutes into the future are rejected as `REJECTED_CLOCK_SKEW`.
3. **Atomic Deduplication:** In-memory LRU cache backed by database constraint `uq_ai_event` (`event_uuid`) atomically rejects duplicate deliveries.

---

## 6. Web Application Security & Data Protection

### 6.1 Token Security & CSRF Protection
- **HttpOnly Cookies:** JWT access and refresh tokens are stored in `HttpOnly`, `SameSite=Lax` cookies, neutralizing token theft via XSS.
- **Double-Submit CSRF:** Server sets a readable `XSRF-TOKEN` cookie; mutating requests (`POST`, `PUT`, `DELETE`, `PATCH`) must include matching header `X-XSRF-TOKEN`. Tested and verified in `api.test.js`.
- **Explicit Logout:** Calling `/api/v1/auth/logout` sets cookie `Max-Age=0`, invalidating browser session tokens immediately.

### 6.2 Rate Limiting Defense
- **Login Rate Limiter:** Enforces sliding-window limits per IP address (10 attempts / 15 min) and per normalized username (5 attempts / 15 min). Uniform RFC 7807 responses prevent username enumeration.
- **Customer Feedback Flood Limiter:** Imposes maximum comment length of 1,000 characters, IP limit of 10 submissions / 5 min, table cooldown of 15 seconds, and duplicate rejection within 60 seconds.

### 6.3 Cross-Site Scripting (XSS) Sanitization
- Public feedback comments are sanitized via Jsoup using plain-text normalization.
- Strips `<script>`, `<iframe>`, `onload=`, `onerror=`, `javascript:`, and dangerous attributes.
- Preserves natural multilingual characters (Devanagari, Latin, punctuation, emojis). Verified in `RestrovisionApplicationTests`.

### 6.4 Open-Session-In-View (OSIV) Elimination
- `spring.jpa.open-in-view: false` is enforced in `application.yml` and `application-oracle.yml`.
- Database connections close immediately upon service method completion, preventing uncommitted transaction leaks into HTTP rendering threads.
- All lazy relationships required by controllers are explicitly initialized using JPA `@EntityGraph` or DTO projections, verified with 0 `LazyInitializationException` across 10 endpoint tests in `OsivAndLazyLoadingEndpointProductionTest.java`.

### 6.5 Network Boundary & Container Port Isolation
Production container topology (`docker-compose.prod.yml`) enforces strict ingress perimeter isolation:
- **Publicly Ingress Ports (Host):** Only Nginx frontend ports `80:80` (HTTP-to-HTTPS redirect) and `443:443` (TLS HTTPS / WSS) are published to the host network interface.
- **Private Container Network (`restrovision-network`):** Backend port `8080` and Edge AI port `8000` are configured exclusively with `expose:` (not `ports:`), ensuring they cannot be reached directly from external networks or unauthenticated host processes. All traffic must pass through the Nginx reverse proxy.

### 6.6 TLS Termination & HSTS Subdomain Audit Policy
- Nginx terminates TLS 1.2/1.3 with high-strength cipher suites (`HIGH:!aNULL:!MD5`).
- Reverse proxy injects standard security headers: `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `X-XSS-Protection: 1; mode=block`, and `Referrer-Policy: strict-origin-when-cross-origin`.
- **HSTS Subdomain Caution:** `Strict-Transport-Security` is configured as `max-age=31536000` without the `includeSubDomains` directive. The runbook explicitly forbids enabling `includeSubDomains` until an enterprise-wide certificate audit verifies that all organizational subdomains serve valid TLS certificates, preventing catastrophic outages of internal HTTP services.

---

## 7. Security Verification Matrix

| Vulnerability Vector | Severity | Mitigation Technique | Verification Method | Status Category |
| :--- | :---: | :--- | :--- | :---: |
| **STOMP Auth Bypass** | CRITICAL | ChannelInterceptor + JWT on CONNECT | 22 JUnit live STOMP tests | **AUTOMATED TESTED (Pass)** |
| **Cross-Tenant Subscribe**| CRITICAL | Fail-closed tenant & role check | 11 live STOMP integration tests | **AUTOMATED TESTED (Pass)** |
| **Edge Token Spoofing** | CRITICAL | Per-device cryptographic JWT + DB check | 7 JUnit edge security tests | **AUTOMATED TESTED (Pass)** |
| **Tenant Context Bleed** | HIGH | ThreadLocal cleanup in `finally` | 20 recycled worker thread test | **AUTOMATED TESTED (Pass)** |
| **AI Event Replay** | HIGH | Dual-window freshness + DB constraint | Freshness & duplicate tests | **AUTOMATED TESTED (Pass)** |
| **Login Brute Force** | HIGH | Dual sliding-window rate limiting | 5 JUnit brute-force tests | **AUTOMATED TESTED (Pass)** |
| **Feedback Stored XSS** | HIGH | Jsoup plain-text normalization | Multilingual XSS test vectors | **AUTOMATED TESTED (Pass)** |
| **JWT Storage Theft** | HIGH | HttpOnly, SameSite=Lax cookies + CSRF | 16 Vitest API interceptor tests | **AUTOMATED TESTED (Pass)** |
| **Transaction Leak (OSIV)**| HIGH | `spring.jpa.open-in-view: false` | 10 OSIV endpoint tests | **AUTOMATED TESTED (Pass)** |
| **Insecure Oracle Secrets** | HIGH | Fail-fast mandatory env secrets | CLI startup fail-fast test | **STATICALLY VERIFIED (Pass)** |
| **Database Over-Privilege**| HIGH | Dual-account segregation (Migrator vs App)| Privilege script review & DDL test | **IMPLEMENTED & STATICALLY VERIFIED** |
| **Container Port Exposure**| MEDIUM | `expose:` only for 8080/8000; 80/443 public| `docker-compose.prod.yml` inspection | **STATICALLY VERIFIED (Pass)** |
| **npm Dependencies** | HIGH | Toolchain upgrade (Vite 6) | `npm audit` (0 vulnerabilities) | **STATICALLY VERIFIED (Pass)** |
| **Python Dependencies** | HIGH | Package updates (FastAPI, cryptography) | `pip-audit` (0 vulnerabilities) | **STATICALLY VERIFIED (Pass)** |
| **Maven Dependencies** | HIGH | `dependency-audit-strict` (CVSS 7.0) | NVD Remote Scan | **BLOCKED (NVD Key)** |
| **Container Privilege** | MEDIUM | Non-root users (`appuser`, `nginx`) | Dockerfile inspection | **STATICALLY VERIFIED (Pass)** |

---

## 8. Security Auditor Sign-Off

The RESTROVISION v2.0 application codebase is architecturally hardened with zero known security regressions. Complete third-party dependency vulnerability clearance remains conditionally qualified pending authenticated NVD scan execution in CI/CD.

**Architectural Security Verdict:** **APPROVED & CERTIFIED HARDENED**  
**Vulnerability Scan Verdict:** **CONDITIONALLY QUALIFIED (Maven NVD Scan Pending)**
