# RESTROVISION Master Defect & Vulnerability Register
**Document ID**: `REPORT-P14-DEFECTS-20261114`  
**Application Target**: RESTROVISION v2.0  
**Audit Lead**: Principal Software QA Engineer & Security Auditor  
**Final Status**: Reconciled & Verified (23 Total Defects)  
**Distribution**: Critical: 3 | High: 12 | Medium: 7 | Low: 1  

---

## 1. Complete Reconciled Defect Master Table

| Defect ID | Severity | Category | File Reference & Lines | Title & Description | Status |
| :--- | :---: | :--- | :--- | :--- | :---: |
| **BUG-001** | **CRITICAL** | Security | `WebSocketConfig.java:55-80` | Anonymous STOMP access & cross-tenant topic eavesdropping | **CONFIRMED** |
| **BUG-002** | **CRITICAL** | Security | `JwtAuthFilter.java:57-67` | Static shared edge token & hardcoded tenant ID (`1L`) | **CONFIRMED** |
| **BUG-003** | **CRITICAL** | DevOps | `Backend/Dockerfile` | Dockerfile missing from disk; compose build crashes | **CONFIRMED** |
| **BUG-004** | **HIGH** | Multi-Tenancy| `TenantContext.java:12-25` | ThreadLocal defaults and resets to `1L` (Tenant bleed) | **STATICALLY VERIFIED** |
| **BUG-005** | **HIGH** | Security | `AiEventIngestionService.java:45-70` | Zero timestamp freshness validation (Event replay) | **CONFIRMED** |
| **BUG-006** | **HIGH** | Performance | `TableStateMachineService.java:59-86`| $1 + 2N$ database query cascade on table broadcasts | **STATICALLY VERIFIED** |
| **BUG-007** | **HIGH** | Performance | `Order.java:43` | `FetchType.EAGER` on `items` collection (Heap memory hazard) | **STATICALLY VERIFIED** |
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

## 2. False Positive Reconciliation & Independent Audit Verification

During preliminary discovery, finding **ARCH-007** claimed that `Alert.java` lacked optimistic locking.
- **Independent Verification**: Re-examination of [Alert.java#L62](file:///D:/RESTROVISION/Backend/src/main/java/com/restrovision/domain/Alert.java#L62) confirmed:
  ```java
  @Version
  @Column(nullable = false)
  private Long version = 0L;
  ```
- **Reconciliation Result**: The finding on `Alert.java` was resolved as a **FALSE POSITIVE**. Concurrency vulnerability was re-assigned to entities that genuinely lack `@Version`: `TableSession.java`, `StaffTask.java`, and `ServiceRequest.java` (**BUG-016**).
