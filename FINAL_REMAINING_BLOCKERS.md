# RESTROVISION v2.0 — Final Remaining Operational Blockers

**Document ID:** `BLOCKERS-20261115-FINAL`  
**Application Target:** RESTROVISION v2.0 Multi-Tenant Enterprise Restaurant Platform  
**Active Working Branch:** `feature/production-hardening`  
**Protected Branch:** `main` (Locked at `171171b08f6bd44f523f224d00c197624246613f`)  
**Audit Date:** 2026-11-15T01:25:00+05:30  
**Overall Release Gate Verdict:** **NOT PRODUCTION READY — RELEASE GATE LOCKED**  

---

## 1. Executive Summary

All source code remediation, architectural hardening, security controls, and multi-stack automated tests (223 / 223 passing) are **100% complete, verified, and locked on `feature/production-hardening`**.

However, the enterprise release gate remains **LOCKED**. Production cutover and deployment cannot proceed until the five (5) operational and environmental blockers documented herein are remediated by the designated infrastructure owners.

---

## 2. Actionable Blocker Register

```
+---------------------------------------------------------------------------------------------------+
| ID | Operational Blocker             | Owner Role        | Urgency  | Current Status              |
+----+---------------------------------+-------------------+----------+-----------------------------+
| B1 | Docker Desktop Daemon Inactive  | Host Admin        | Critical | Stopped (Service Offline)   |
| B2 | Oracle Dual-Account Not Created | Oracle DBA        | Critical | Blocked on SYSDBA Run       |
| B3 | OWASP Maven Scan Unauthenticated| SecOps / DevOps   | High     | Blocked on NVD_API_KEY      |
| B4 | Playwright E2E & Load Testing   | QA Lead / DevOps  | High     | Blocked on Docker (B1)      |
| B5 | Live Isolated Database Restore  | Oracle DBA        | High     | Blocked on Staging PDB (B2) |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. Detailed Blocker Analysis & Resolution Runbooks

### Blocker B1: Docker Desktop Service Inactive (`com.docker.service`)
- **Impact:**
  - `docker compose -f docker-compose.prod.yml` cannot spin up staging containers (`restrovision-nginx`, `restrovision-backend`, `restrovision-frontend`, `restrovision-edge-ai`).
  - Container-level inter-service networking, TLS termination, and live STOMP proxying cannot be validated.
- **Root Cause:**
  - Windows service `com.docker.service` is stopped. Docker named pipe `//./pipe/dockerDesktopLinuxEngine` is inaccessible.
- **Actionable Remediation Runbook (Windows Host Admin):**
  1. Open PowerShell with elevated Administrator privileges:
     ```powershell
     Start-Service com.docker.service
     Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
     ```
  2. Verify Docker Engine status:
     ```powershell
     docker info
     docker version
     ```

---

### Blocker B2: Oracle Dual-Account Schema Provisioning in `FREEPDB1`
- **Impact:**
  - Production Oracle profile (`application-oracle.yml`) cannot initialize Flyway migrations or application connections.
  - Users `RESTROVISION_MIGRATOR` and `RESTROVISION_APP` do not exist in `FREEPDB1`.
- **Root Cause:**
  - The local Oracle 26ai Free instance is active on port 1521, but the required dual-account DDL has not been executed by `SYSDBA`.
- **Actionable Remediation Runbook (Oracle DBA):**
  1. Connect to `FREEPDB1` as `SYSDBA`:
     ```bash
     sqlplus sys/<SYS_PASSWORD>@localhost:1521/FREEPDB1 AS SYSDBA
     ```
  2. Execute the official dual-account provisioning script:
     ```sql
     -- 1. Create Migration Schema (DDL Owner)
     CREATE USER RESTROVISION_MIGRATOR IDENTIFIED BY "${FLYWAY_PASSWORD}"
       DEFAULT TABLESPACE USERS
       QUOTA 2G ON USERS;

     GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX, CREATE VIEW, CREATE PROCEDURE
       TO RESTROVISION_MIGRATOR;

     -- 2. Create Application Runtime Schema (DML Only)
     CREATE USER RESTROVISION_APP IDENTIFIED BY "${APP_RUNTIME_PASSWORD}"
       DEFAULT TABLESPACE USERS
       QUOTA 500M ON USERS;

     GRANT CREATE SESSION, CREATE SYNONYM TO RESTROVISION_APP;

     -- 3. Create Runtime Role
     CREATE ROLE RESTROVISION_APP_ROLE;
     GRANT RESTROVISION_APP_ROLE TO RESTROVISION_APP;
     ```
  3. Verify accounts:
     ```sql
     SELECT username, account_status, default_tablespace FROM dba_users 
     WHERE username IN ('RESTROVISION_MIGRATOR', 'RESTROVISION_APP');
     ```

---

### Blocker B3: Maven OWASP Dependency-Check Unauthenticated Rate Limiting
- **Impact:**
  - Automated CI vulnerability scan against NIST NVD fails or times out due to unauthenticated rate limiting.
  - Zero-vulnerability certification for Java dependencies cannot be mathematically guaranteed.
- **Root Cause:**
  - NIST NVD requires an authenticated API key (`$env:NVD_API_KEY`) for stable, unthrottled database feed synchronization.
- **Actionable Remediation Runbook (SecOps / DevOps):**
  1. Request an API key from the NIST NVD portal: `https://nvd.nist.gov/developers/request-an-api-key`.
  2. Set the environment variable in the CI/CD pipeline or runner:
     ```powershell
     $env:NVD_API_KEY = "your-nist-nvd-api-key-uuid"
     ```
  3. Execute the authenticated OWASP dependency check:
     ```powershell
     cd D:\RESTROVISION\Backend
     mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7 -DnvdApiKey=$env:NVD_API_KEY
     ```

---

### Blocker B4: Live Real-Browser Playwright E2E & 100-VU Concurrent Load Testing
- **Impact:**
  - Production readiness cannot certify end-user browser workflows or 100-VU concurrent transaction handling under realistic network latencies and connection pool contention.
- **Prerequisite:** Blocker B1 (Docker Desktop) must be resolved first.
- **Actionable Remediation Runbook (QA Lead / DevOps):**
  1. Launch staging stack:
     ```bash
     docker compose -f docker-compose.prod.yml up -d
     ```
  2. Run Playwright E2E test suite in headless browser mode:
     ```bash
     cd frontend
     npx playwright test
     ```
  3. Run k6 concurrent load test (100 VUs for 10 minutes):
     ```bash
     k6 run load-tests/restaurant-peak-load.js
     ```

---

### Blocker B5: Live Isolated Database Disaster Recovery Drill
- **Impact:**
  - Point-in-time recovery and schema remapping have not been physically proven on a live staging database.
- **Prerequisite:** Blocker B2 (Oracle Dual-Account) must be provisioned.
- **Actionable Remediation Runbook (Oracle DBA):**
  1. Generate test Data Pump export of `RESTROVISION_MIGRATOR`.
  2. Execute isolated restore into transient schema `RESTROVISION_RECOVERY` using `REMAP_SCHEMA`.
  3. Execute 5-point validation checklist (object parity, index validity, row count parity, Flyway history integrity, cleanup).

---

## 4. Release Gate Final Statement

```
================================================================================
                           RELEASE GATE STATUS
================================================================================
  Target Branch:          feature/production-hardening
  Protected Branch:       main (Untouched at 171171b08f6bd44f523f224d00c197624246613f)
  Automated Code Quality: 223 / 223 Passed (100%)
  Security Hardening:     Zero Known Vulnerabilities (npm/pip)
  Release Decision:       NOT PRODUCTION READY
  Gate Lock Reason:       Five (5) Pending Operational Infrastructure Prerequisites
================================================================================
```

Production deployment, branch merging to `main`, and live traffic routing are strictly forbidden until all 5 blockers are closed and signed off.
