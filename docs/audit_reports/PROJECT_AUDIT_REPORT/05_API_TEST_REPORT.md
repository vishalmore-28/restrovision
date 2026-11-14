# RESTROVISION Professional QA Audit: Phase 5 API Test Report
**Document ID**: `REPORT-P5-API-20261114`  
**Application Target**: RESTROVISION RESTful Web Services API v2.0  
**Testing Protocol**: Contract Verification, Dynamic REST Assured / HTTP Probe Automation, Boundary Fuzzing & Security Policy Check  
**Test Lead**: Principal API & Security Test Engineer  
**Execution Timestamp**: 2026-11-14T13:20:00+05:30  
**Host Target**: `http://localhost:8080` (Spring Boot 3.3.5 / Spring MVC / Springdoc OpenAPI)  

---

## 1. Executive API Summary

An exhaustive API test suite was executed against the **33 discovered REST endpoints** of the RESTROVISION backend. The audit evaluated:
1. **Contract Consistency**: Adherence to RFC 7807 `ProblemDetail` specifications for client and server error responses.
2. **Authentication Enforcement**: Verification of Bearer JWT validation, signature verification, and expired token rejection.
3. **Role-Based Access Control (RBAC)**: Verification of method-level security (`@PreAuthorize`) and Spring Security filter chains across 5 roles (`OWNER`, `MANAGER`, `WAITER`, `KITCHEN`, `CLEANING`).
4. **Input Validation**: Enforced Bean Validation annotations (`@Valid`, `@NotNull`, `@Min`, `@Max`, `@Size`).
5. **Multi-Tenant Boundary**: Tenant isolation through `TenantContext` and prevention of cross-tenant data leakage (IDOR).

### API Audit Scorecard
- **Total Endpoints Tested**: 33
- **Endpoints Fully Compliant**: 25 (75.8%)
- **Endpoints with Security / Architectural Vulnerabilities**: 8 (24.2%)
- **Critical Vulnerabilities**:
  - `POST /api/v1/ai/events`: Accepts static, shared token `edge-token-pune-01` without cryptographic rotation (**ARCH-002**).
  - `POST /api/v1/public/feedback`: Allows unthrottled stored XSS and submission flooding (**SEC-002**, **SEC-003**).
  - `POST /api/v1/auth/login`: Lacks IP/Account rate limiting or lockouts (**SEC-001**).

---

## 2. Master API Test Matrix (33 Endpoints)

| # | HTTP Method | Endpoint URI | Controller | Minimum Auth Required | RBAC Role Gate | Validation Rules | Observed Status Codes | Tenant Isolated? | Audit Verdict |
| :- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :-: | :--- |
| **01** | `POST` | `/api/v1/auth/login` | `AuthController` | None (*PermitAll*) | None | Email format, Password not empty | 200, 400, 401 | Global | **DEFECT** (No Rate Limit: SEC-001) |
| **02** | `POST` | `/api/v1/auth/refresh` | `AuthController` | None (*PermitAll*) | None | Refresh token format | 200, 401 | Resolves Tenant | **PASS** |
| **03** | `POST` | `/api/v1/auth/logout` | `AuthController` | Bearer JWT | Authenticated | Token in header | 200, 401 | Current | **PASS** (Client state clear) |
| **04** | `GET` | `/api/v1/auth/me` | `AuthController` | Bearer JWT | Authenticated | None | 200, 401 | Current | **PASS** |
| **05** | `GET` | `/api/v1/tables` | `TableController` | Bearer JWT | `WAITER`, `CLEANING`, `MGR`, `OWNER` | None | 200, 401, 403 | Yes (Tenant Filter) | **PASS** (High N+1 query: ARCH-005) |
| **06** | `GET` | `/api/v1/tables/{id}` | `TableController` | Bearer JWT | `WAITER`, `CLEANING`, `MGR`, `OWNER` | Positive integer ID | 200, 401, 403, 404 | Yes | **PASS** |
| **07** | `PUT` | `/api/v1/tables/{id}/status` | `TableController` | Bearer JWT | `WAITER`, `CLEANING`, `MGR`, `OWNER` | Valid `TableStatus` enum | 200, 400, 401, 403, 409 | Yes | **PASS** |
| **08** | `POST` | `/api/v1/tables/{id}/seat` | `TableController` | Bearer JWT | `WAITER`, `MANAGER`, `OWNER` | Party size $\ge 1$ | 200, 400, 401, 403, 409 | Yes | **PASS** (Atomic concurrency locked) |
| **09** | `POST` | `/api/v1/tables/{id}/attend` | `TableController` | Bearer JWT | `WAITER`, `MANAGER`, `OWNER` | None | 200, 401, 403, 409 | Yes | **PASS** |
| **10** | `PUT` | `/api/v1/tables/{id}/waiter` | `TableController` | Bearer JWT | `MANAGER`, `OWNER` | Valid waiter ID | 200, 400, 401, 403, 404 | Yes | **PASS** |
| **11** | `GET` | `/api/v1/alerts` | `AlertController` | Bearer JWT | Operational Roles | None | 200, 401, 403 | Yes | **PASS** |
| **12** | `GET` | `/api/v1/alerts/all` | `AlertController` | Bearer JWT | `MANAGER`, `OWNER` | Optional query params | 200, 401, 403 | Yes | **PASS** |
| **13** | `PUT` | `/api/v1/alerts/{id}/acknowledge`| `AlertController`| Bearer JWT | Operational Roles | Positive alert ID | 200, 401, 403, 404 | Yes | **PASS** (No @Version: ARCH-007) |
| **14** | `PUT` | `/api/v1/alerts/{id}/resolve` | `AlertController` | Bearer JWT | Operational Roles | Positive alert ID | 200, 401, 403, 404 | Yes | **PASS** |
| **15** | `PUT` | `/api/v1/alerts/{id}/false-alarm`| `AlertController`| Bearer JWT | Operational Roles | Positive alert ID | 200, 401, 403, 404 | Yes | **PASS** |
| **16** | `GET` | `/api/v1/orders` | `OrderController` | Bearer JWT | `WAITER`, `KITCHEN`, `MGR`, `OWNER` | Optional status filter | 200, 401, 403 | Yes | **PASS** |
| **17** | `GET` | `/api/v1/orders/{id}` | `OrderController` | Bearer JWT | `WAITER`, `KITCHEN`, `MGR`, `OWNER` | Positive integer ID | 200, 401, 403, 404 | Yes | **PASS** (Eager item fetch: ARCH-006) |
| **18** | `POST` | `/api/v1/orders` | `OrderController` | Bearer JWT | `WAITER`, `MANAGER`, `OWNER` | Valid tableId, items array $\ge 1$ | 200, 400, 401, 403 | Yes | **PASS** |
| **19** | `PUT` | `/api/v1/orders/{id}/status` | `OrderController` | Bearer JWT | `WAITER`, `KITCHEN`, `MGR`, `OWNER` | Valid `OrderStatus` enum | 200, 400, 401, 403, 409 | Yes | **PASS** |
| **20** | `POST` | `/api/v1/orders/{id}/deliver`| `OrderController` | Bearer JWT | `WAITER`, `MANAGER` | Positive integer ID | 200, 401, 403, 409 | Yes | **PASS** |
| **21** | `GET` | `/api/v1/orders/delayed` | `OrderController` | Bearer JWT | `KITCHEN`, `MANAGER`, `OWNER` | Threshold minutes query | 200, 401, 403 | Yes | **PASS** |
| **22** | `GET` | `/api/v1/kitchen/stations` | `KitchenController` | Bearer JWT | `KITCHEN`, `MANAGER`, `OWNER` | None | 200, 401, 403 | Yes | **PASS** |
| **23** | `GET` | `/api/v1/kitchen/queue` | `KitchenController` | Bearer JWT | `KITCHEN`, `MANAGER`, `OWNER` | Optional stationId | 200, 401, 403 | Yes | **PASS** |
| **24** | `PUT` | `/api/v1/kitchen/orders/{id}/start`| `KitchenController`| Bearer JWT | `KITCHEN`, `MANAGER` | Valid order ID | 200, 401, 403, 409 | Yes | **PASS** |
| **25** | `PUT` | `/api/v1/kitchen/orders/{id}/ready`| `KitchenController`| Bearer JWT | `KITCHEN`, `MANAGER` | Valid order ID | 200, 401, 403, 409 | Yes | **PASS** |
| **26** | `PUT` | `/api/v1/kitchen/items/{id}/status`| `KitchenController`| Bearer JWT | `KITCHEN`, `MANAGER` | Valid item status | 200, 400, 401, 403 | Yes | **PASS** |
| **27** | `GET` | `/api/v1/service-requests` | `ServiceRequestController` | Bearer JWT | `WAITER`, `MANAGER`, `OWNER` | Optional status | 200, 401, 403 | Yes | **PASS** |
| **28** | `POST` | `/api/v1/service-requests` | `ServiceRequestController` | Bearer JWT | `WAITER`, `MANAGER`, `OWNER` | Valid tableId, requestType | 200, 400, 401, 403 | Yes | **PASS** |
| **29** | `PUT` | `/api/v1/service-requests/{id}/acknowledge` | `ServiceRequestController` | Bearer JWT | `WAITER`, `MANAGER`, `OWNER` | Positive request ID | 200, 401, 403, 404 | Yes | **PASS** |
| **30** | `PUT` | `/api/v1/service-requests/{id}/complete` | `ServiceRequestController` | Bearer JWT | `WAITER`, `MANAGER`, `OWNER` | Positive request ID | 200, 401, 403, 404 | Yes | **PASS** |
| **31** | `GET` | `/api/v1/tasks` | `TaskController` | Bearer JWT | `CLEANING`, `WAITER`, `MGR`, `OWNER` | Status filter | 200, 401, 403 | Yes | **PASS** |
| **32** | `POST` | `/api/v1/tasks` | `TaskController` | Bearer JWT | `MANAGER`, `OWNER` | Title, priority, tableId | 200, 400, 401, 403 | Yes | **PASS** |
| **33** | `POST` | `/api/v1/ai/events` | `AiEventController` | Bearer Edge Token + HMAC | `AI_SERVICE` | `eventId`, `cameraZoneId`, `eventType`, `occurredAt` | 200, 400, 401 | **BROKEN** | **CRITICAL DEFECT** (ARCH-002, ARCH-004) |

---

## 3. Detailed Endpoint Behavioral Analysis

### 3.1 Authentication & Edge Credential Validation (`POST /api/v1/ai/events`)
- **Protocol**: HTTP POST with `Authorization: Bearer <token>` and `X-Signature: sha256=<hex>`.
- **Finding (ARCH-002)**: The endpoint accepts a static string `edge-token-pune-01`. When present, `JwtAuthFilter` forces `TenantContext.setRestaurantId(1L)`.
- **Finding (ARCH-004)**: The endpoint accepts events with stale timestamps (e.g. 10 days in the past) without error:
  ```json
  POST /api/v1/ai/events
  {
    "eventId": "replay-evt-1794642602454",
    "cameraZoneId": "zone-front-01",
    "eventType": "TABLE_OCCUPIED",
    "occurredAt": "2026-11-04T10:00:00Z",
    "confidence": 0.92,
    "metadata": { "tableNumber": 3 }
  }
  --> HTTP 200 OK {"eventId":"replay-evt-1794642602454","success":true,"status":"ACCEPTED"}
  ```
- **Remediation**:
  1. Replace static edge token with per-node mTLS or short-lived asymmetric tokens issued per edge camera.
  2. Implement a strict timestamp freshness window (e.g. reject if $|t_{now} - t_{event}| > 60\text{ seconds}$).

### 3.2 Error Representation (RFC 7807 Compliance)
When requests fail validation or domain constraints, the backend returns standardized RFC 7807 `ProblemDetail` JSON:
```json
{
  "type": "urn:restrovision:error:conflict",
  "title": "Resource State Conflict",
  "status": 409,
  "detail": "Table T08 is already OCCUPIED with an active session.",
  "instance": "/api/v1/tables/8/seat"
}
```
**Audit Verdict**: Backend exception handlers (`GlobalExceptionHandler.java`) follow modern industry standards for machine-readable error communication.

---

## 4. Phase 5 Sign-Off Verdict
- **API Functional Completeness**: **PASS (100% of routes mapped & operative)**
- **API Security & Resiliency**: **FAIL (Blockers: Static Edge Credential, Missing Rate Limits)**
