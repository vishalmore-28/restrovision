# Phase 2: Software Architecture Audit Report
**RESTROVISION Operations & Edge Vision Platform**  
**Audit Document**: `PROJECT_AUDIT_REPORT/02_ARCHITECTURE_AUDIT.md`  
**Date of Audit**: 2026-11-14  
**Audit Status**: COMPLETED  

---

## 1. Executive Architectural Assessment

The RESTROVISION system combines an on-premises Python/FastAPI Edge AI pipeline with a Spring Boot 3.3.5 / Java 21 core backend and a React 18 single-page application. While the core layered domain design exhibits commendable separation between controllers, domain entities, and business services, the architecture suffers from **critical multi-tenant bleed vulnerabilities**, **unauthenticated real-time WebSocket conduits**, **eager-loading database anti-patterns**, and **fragile token lifecycles**.

---

## 2. In-Depth Architectural Findings & Evidence

---

### Finding ARCH-001 (CRITICAL): Unauthenticated WebSocket STOMP Connection & Cross-Tenant Topic Eavesdropping

- **Affected Files**:
  - `Backend/src/main/java/com/restrovision/config/WebSocketConfig.java` (Lines 64–104)
  - `frontend/src/services/websocket.js` (Lines 17–23)
- **Implementation Evidence**:
  In `WebSocketConfig.java`:
  ```java
  // Line 64: Interceptor only extracts token if header is supplied; does NOT reject unauthenticated connects!
  if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      String authHeader = accessor.getFirstNativeHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
          // parses token and sets session attribute 'restaurantId'
      }
      // NO ELSE: Unauthenticated connections proceed without credentials!
  } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      String destination = accessor.getDestination();
      Long clientRestId = accessor.getSessionAttributes() != null
              ? (Long) accessor.getSessionAttributes().get("restaurantId") : null;

      // Line 96: Logic only checks if clientRestId is NOT null!
      if (clientRestId != null && !clientRestId.equals(targetRestId)) {
          throw new AccessDeniedException("Access denied: Cannot subscribe to topics of another restaurant.");
      }
      // If clientRestId is NULL (unauthenticated), THIS CHECK IS ENTIRELY SKIPPED!
  }
  ```
  In `frontend/src/services/websocket.js`:
  ```javascript
  // Line 17: Client connects with zero Authorization headers!
  this.client = new Client({
    brokerURL,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  });
  ```
- **Why It Is Problematic**:
  1. The server allows anonymous, unauthenticated STOMP connections.
  2. The cross-tenant subscription check `clientRestId != null && !clientRestId.equals(targetRestId)` evaluates to `false` when `clientRestId` is `null`.
  3. Consequently, any unauthenticated attacker anywhere on the network can connect to `ws://host/ws` and subscribe to `/topic/restaurants/{tenantId}/alerts`, `/topic/restaurants/{tenantId}/orders`, and `/topic/restaurants/{tenantId}/tables` to exfiltrate live guest orders, revenue figures, and security alerts in real time.
- **Real-World Impact**: Complete breakdown of confidentiality and multi-tenant data isolation over real-time communication channels.
- **Severity**: **CRITICAL** (OWASP A01: Broken Access Control).
- **Recommended Solution**:
  1. In `WebSocketConfig.java`, reject any `StompCommand.CONNECT` that lacks a valid Bearer JWT.
  2. In `frontend/src/services/websocket.js`, inject `connectHeaders: { Authorization: 'Bearer ' + getAuthToken() }`.
  3. Change the subscription guard to `if (clientRestId == null || !clientRestId.equals(targetRestId)) throw new AccessDeniedException(...)`.
- **Verification Method**: Connect an anonymous STOMP client (e.g. via Postman or script) without headers and attempt to subscribe to `/topic/restaurants/1/alerts`. Verify rejection.

---

### Finding ARCH-002 (CRITICAL): Static Shared Edge Token & Hardcoded Tenant Binding

- **Affected File**: `Backend/src/main/java/com/restrovision/security/JwtAuthFilter.java` (Lines 47–54)
- **Implementation Evidence**:
  ```java
  // Line 47: Static edge token comparison
  if (jwt.equals(edgeServiceToken)) {
      TenantContext.setRestaurantId(1L); // Edge node bound to restaurant 1
      List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_AI_SERVICE"));
      UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken("edge-pune-01", null, authorities);
      authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
      SecurityContextHolder.getContext().setAuthentication(authentication);
  }
  ```
- **Why It Is Problematic**:
  1. The authentication mechanism for Edge AI telemetry relies on a single static pre-shared key (`edge-token-pune-01`) passed as a Bearer token.
  2. `TenantContext.setRestaurantId(1L)` is statically hardcoded to `1L`. In a multi-location enterprise deployment (e.g. Pune, Mumbai, Delhi branches), all edge nodes are forced into tenant 1, or edge events from branch 2 will contaminate branch 1.
  3. If this static token is intercepted, the attacker gains permanent `ROLE_AI_SERVICE` privileges with no expiration or revocation mechanism.
- **Real-World Impact**: Inability to scale to multiple restaurant locations and vulnerability to credential theft leading to AI event spoofing.
- **Severity**: **CRITICAL** (OWASP A07: Identification and Authentication Failures).
- **Recommended Solution**: Issue short-lived, mTLS-backed or scoped JWTs for each edge node carrying a verified `edgeNodeId` and `restaurantId` claim.
- **Verification Method**: Inspect `JwtAuthFilter.java` lines 47-54.

---

### Finding ARCH-003 (HIGH): Flawed Multi-Tenant Context ThreadLocal Initialization & Cleanup

- **Affected File**: `Backend/src/main/java/com/restrovision/security/TenantContext.java` (Lines 4, 17)
- **Implementation Evidence**:
  ```java
  public class TenantContext {
      // Line 4: ThreadLocal defaults to Tenant 1
      private static final ThreadLocal<Long> CURRENT_RESTAURANT_ID = ThreadLocal.withInitial(() -> 1L);

      public static void clear() {
          // Line 17: Resetting sets value back to 1L instead of removing!
          CURRENT_RESTAURANT_ID.set(1L);
      }
  }
  ```
- **Why It Is Problematic**:
  1. In a multi-tenant enterprise system, `TenantContext` must never have a default tenant. If a request is unauthenticated or fails tenant extraction, `getRestaurantId()` must return `null` or throw an exception. Defaulting to `1L` causes unauthenticated endpoints (like public feedback) or misconfigured requests to execute against Restaurant 1.
  2. Tomcat reuses pooled worker threads across HTTP requests. Calling `set(1L)` in `clear()` leaves a stale reference on the thread. Standard practice demands `ThreadLocal.remove()` to prevent memory leaks and thread pollution.
- **Real-World Impact**: Silent cross-tenant data leakage where unauthenticated operations pollute the first restaurant's database records.
- **Severity**: **HIGH** (Multi-Tenancy Isolation Failure).
- **Recommended Solution**: Change to `private static final ThreadLocal<Long> CURRENT_RESTAURANT_ID = new ThreadLocal<>()`, implement strict null checks in `getRestaurantId()`, and replace `clear()` with `CURRENT_RESTAURANT_ID.remove()`.
- **Verification Method**: Check `TenantContext.java` lines 4 and 17.

---

### Finding ARCH-004 (HIGH): Missing Timestamp Skew & Replay Protection in AI Event Ingestion

- **Affected Files**:
  - `Backend/src/main/java/com/restrovision/controller/AiEventController.java` (Lines 40–80)
  - `Backend/src/main/java/com/restrovision/service/AiEventIngestionService.java` (Lines 75, 104–126)
- **Implementation Evidence**:
  ```java
  // AiEventIngestionService.java Line 75:
  aiEvent.setOccurredAt(envelope.getOccurredAt() != null ? envelope.getOccurredAt() : Instant.now());
  ```
- **Why It Is Problematic**:
  While `AiEventIngestionService` enforces deduplication by `event_uid`, it performs zero validation on the `occurredAt` timestamp.
  An adversary with valid edge credentials (or a compromised edge node) can submit events timestamped days in the past or hours in the future. Furthermore, an intercepted payload can be replayed indefinitely by merely altering the `eventId` UUID string while keeping the rest of the payload intact.
- **Real-World Impact**: State machine corruption (e.g. false occupancy or erroneous turnover alerts) via replayed historical telemetry.
- **Severity**: **HIGH** (CWE-294: Authentication Bypass by Capture-replay).
- **Recommended Solution**: Enforce a strict timestamp tolerance window (e.g. $|T_{\text{occurred}} - T_{\text{received}}| \le 60\text{ seconds}$) and include the timestamp in the HMAC signature.
- **Verification Method**: Submit a validly signed event with an `occurredAt` timestamp set to 7 days in the past; verify whether the backend accepts and processes it.

---

### Finding ARCH-005 (HIGH): $N+1$ Database Query Anti-Pattern in Table Overview

- **Affected File**: `Backend/src/main/java/com/restrovision/service/TableStateMachineService.java` (Lines 59–85)
- **Implementation Evidence**:
  ```java
  public List<TableViewDto> getTableView(Long restaurantId) {
      List<RestaurantTable> tables = tableRepository.findByRestaurantIdOrderByTableCodeAsc(restaurantId);

      return tables.stream().map(t -> {
          // Query 1 per table:
          if (t.getAssignedWaiterId() != null) {
              employeeRepository.findByRestaurantIdAndEmployeeId(restaurantId, t.getAssignedWaiterId())
                      .ifPresent(e -> dto.setAssignedWaiterName(e.getDisplayName()));
          }
          // Query 2 per table:
          if (t.getCurrentSessionId() != null) {
              sessionRepository.findByRestaurantIdAndSessionId(restaurantId, t.getCurrentSessionId())
                      .ifPresent(s -> { ... });
          }
          return dto;
      }).collect(Collectors.toList());
  }
  ```
- **Why It Is Problematic**:
  Fetching table states executes $1 + 2N$ separate queries against the database (where $N$ is the table count). On a 50-table floor plan, every refresh triggers **101 SQL queries**. This severely degrades database throughput and elevates latency during peak shift operations.
- **Real-World Impact**: Database connection pool exhaustion and sluggish UI updates on the live floor plan.
- **Severity**: **HIGH** (Performance & Scalability Defect).
- **Recommended Solution**: Use a single JPQL query with `LEFT JOIN FETCH` or batch-fetch active sessions and employees into indexed in-memory lookup maps before building DTOs.
- **Verification Method**: Enable `spring.jpa.show-sql: true` and execute `GET /api/v1/tables`. Count database queries.

---

### Finding ARCH-006 (HIGH): Eager Collection Fetching on Order Items (`FetchType.EAGER`)

- **Affected File**: `Backend/src/main/java/com/restrovision/domain/Order.java` (Line 43)
- **Implementation Evidence**:
  ```java
  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private List<OrderItem> items = new ArrayList<>();
  ```
- **Why It Is Problematic**:
  `FetchType.EAGER` forces Hibernate to execute Cartesian product joins and pull all child `OrderItem` records into heap memory every time an `Order` is queried—even when the caller only checks order status, counts orders, or executes financial aggregations.
- **Real-World Impact**: High memory consumption, excessive garbage collection pressure, and risk of `OutOfMemoryError` on large historical order queries.
- **Severity**: **HIGH** (JPA Anti-Pattern).
- **Recommended Solution**: Convert to `FetchType.LAZY` and use entity graphs or explicit join fetch queries in repository methods that specifically require order items.
- **Verification Method**: Inspect `Order.java` line 43.

---

### Finding ARCH-007 (HIGH): Missing Optimistic Locking Versioning on Alert Entity

- **Affected File**: `Backend/src/main/java/com/restrovision/domain/Alert.java` (Lines 10–50)
- **Implementation Evidence**:
  Unlike `RestaurantTable` and `Order` (which correctly include `@Version Long version`), `Alert.java` lacks an `@Version` property.
- **Why It Is Problematic**:
  Alerts are subjected to high concurrent writes: staff acknowledge or resolve alerts on mobile terminals while background schedulers (`WaitingTimerScheduler`) simultaneously evaluate escalation rules. Without optimistic locking, two simultaneous transactions will execute blind overwrites (last-write-wins), silently discarding staff acknowledgements or escalation transitions.
- **Real-World Impact**: Inconsistent alert states and false escalation of already-acknowledged emergency notifications.
- **Severity**: **HIGH** (Data Concurrency & Integrity Failure).
- **Recommended Solution**: Add `@Version private Long version;` to `Alert.java`.
- **Verification Method**: Inspect `Alert.java` field declarations.

---

### Finding ARCH-008 (MEDIUM): Complete Absence of React ErrorBoundary

- **Affected Files**: `frontend/src/main.jsx` & `frontend/src/App.jsx`
- **Implementation Evidence**:
  No `componentDidCatch` or `ErrorBoundary` wrapper exists anywhere in the frontend tree.
- **Why It Is Problematic**:
  In React 18, an unhandled rendering error in any child component causes the entire React component tree to unmount. A minor unexpected property in an incoming STOMP alert or an order payload crashes the entire application to an unrecoverable blank white screen.
- **Real-World Impact**: Complete POS/KDS terminal lockout requiring a manual browser refresh and state loss during busy service.
- **Severity**: **MEDIUM** (Operational Reliability Hazard).
- **Recommended Solution**: Wrap core views in a top-level `ErrorBoundary` with a user-friendly fallback and recovery action.
- **Verification Method**: Grep for `ErrorBoundary` in `frontend/src`.

---

### Finding ARCH-009 (MEDIUM): Stateless Logout Without Token Invalidation

- **Affected File**: `Backend/src/main/java/com/restrovision/controller/AuthController.java` (Lines 101–104)
- **Implementation Evidence**:
  ```java
  @PostMapping("/logout")
  public ResponseEntity<Map<String, String>> logout() {
      return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
  }
  ```
- **Why It Is Problematic**:
  The logout endpoint performs no server-side invalidation. Refresh tokens in `localStorage` or stolen access tokens remain 100% valid until their expiration window (15 minutes for access tokens, 7 days for refresh tokens).
- **Real-World Impact**: Inability to revoke compromised sessions immediately upon staff departure.
- **Severity**: **MEDIUM** (Session Management Defect).
- **Recommended Solution**: Invalidate the associated refresh token record in the database and place active access tokens in a distributed Redis/in-memory token blacklist.
- **Verification Method**: Call `/api/v1/auth/logout`, then immediately submit the previous token to `/api/v1/auth/me`. The request still succeeds with 200 OK.
