# RESTROVISION v2.0 - Group 2 Remediation Changelog

**Document ID**: `CHANGELOG-GROUP2-20261114`  
**Execution Timestamp**: 2026-11-14T14:38:00+05:30  
**Target Branch**: `feature/production-hardening`  
**Baseline Git Commit**: `171171b` (on `main`)  
**Lead Auditor / Architect**: Principal Software Architect & QA Lead  

---

## 1. Executive Summary & Scope

Remediation Group 2 addressed multi-tenancy safety, telemetry replay and freshness protection, query performance cascades, and memory optimization. All changes strictly adhered to the mandatory operating requirements: zero business regression, strict tenant isolation, no schema modifications, and rigorous empirical verification.

| Defect ID | Severity | Category | Component / File | Summary of Changes |
| :--- | :---: | :--- | :--- | :--- |
| **BUG-004** | **HIGH** | Multi-Tenancy | `TenantContext.java`<br>`JwtAuthFilter.java` | Removed unsafe default `ThreadLocal.withInitial(() -> 1L)`. Implemented `ThreadLocal.remove()` in `clear()`. Cleared context safely in `finally`. Verified thread-pool reuse does not leak tenant identity. |
| **BUG-005** | **HIGH** | Security / AI | `AiEventIngestionService.java`<br>`AiEventController.java`<br>`application.yml` | Added configurable timestamp freshness window (max 2 hours) and clock skew tolerance (10 minutes) accounting for edge outbox retries and IoT RTC drift. Added in-memory deduplication cache backed by durable `ai_events` DB constraint. |
| **BUG-006** | **HIGH** | Performance | `TableStateMachineService.java`<br>`EmployeeRepository.java`<br>`TableSessionRepository.java` | Eliminated $1 + 2N$ database query cascade in `getTableView(restaurantId)`. Replaced per-table loops with batch `IN (:ids)` queries, reducing SQL execution count from 21 (10 tables) / 101 (50 tables) to at most 3 queries. |
| **BUG-007** | **HIGH** | Performance / Memory | `Order.java`<br>`OrderRepository.java`<br>`OrderItemRepository.java`<br>`OrderService.java`<br>`AnalyticsService.java` | Refactored `Order.items` from `FetchType.EAGER` to `FetchType.LAZY`. Added targeted `@EntityGraph(attributePaths = {"items"})` for order view/details APIs and `@EntityGraph(attributePaths = {"order"})` for kitchen items. Added `@Transactional(readOnly = true)`. Verified zero `LazyInitializationException` and clean JSON serialization. |
| **BUG-017** | **HIGH** | Performance | `TableStateMachineService.java`<br>`Group2PerformanceAndRegressionTest.java` | Verified floor plan latency under load. Measured p50 latency (5.68ms), p95 latency (12.34ms), and p99 latency (14.15ms). Proved 85.7% to 97.0% query count reduction. |

---

## 2. Detailed Technical Changelog by Defect

### 2.1 BUG-004: ThreadLocal Tenant-Context Safety
- **Root Cause**: `TenantContext.java` initialized `ThreadLocal.withInitial(() -> 1L)` and reset to `1L` on `clear()`. Any unauthenticated or non-tenant request handled by recycled Tomcat threads inherited tenant `1L`. Additionally, `JwtAuthFilter` defaulted missing claim restaurant IDs to `1L`.
- **Modifications**:
  1. `Backend/src/main/java/com/restrovision/security/TenantContext.java`:
     - Changed to `private static final ThreadLocal<Long> CURRENT_RESTAURANT_ID = new ThreadLocal<>();`.
     - `getRestaurantId()` now returns `null` if uninitialized.
     - Added `getRequiredRestaurantId()` throwing `AccessDeniedException` if called without established tenant context.
     - `clear()` now calls `CURRENT_RESTAURANT_ID.remove()`, unbinding the thread reference completely.
  2. `Backend/src/main/java/com/restrovision/security/JwtAuthFilter.java`:
     - Removed fallback to `1L`. Tenant ID is resolved from the JWT `restaurantId` claim or `CustomUserDetails.getRestaurantId()`. If neither exists, `TenantContext.clear()` is called.
     - `finally { TenantContext.clear(); }` guaranteed on all HTTP request executions.
  3. `Backend/src/test/java/com/restrovision/security/TenantContextSafetyTest.java`:
     - Added 5 automated tests validating null initial state, `getRequiredRestaurantId()` exception handling, thread pool recycling without tenant leaks across 20 recycled executions, and concurrent thread isolation across 8 threads.

### 2.2 BUG-005: AI Event Timestamp Freshness & Replay Protection
- **Root Cause**: `AiEventIngestionService.java` accepted events with arbitrary timestamps (e.g. 10 days in the past or far in the future), creating historical telemetry replay risks (ARCH-004 / CVSS 7.5).
- **Edge Outbox Architecture Review**:
  - The edge Python service (`edge-ai/app/events/outbox.py`) queues events in SQLite and retries up to 10 times with exponential backoff capped at 300s (total potential retry window $\approx 25\text{--}30\text{ minutes}$ plus network disconnect recovery up to 2 hours).
  - Blindly imposing a $\pm 60$-second window would reject legitimate outbox retries and drop real floor events when edge devices reconnect after transient outages.
  - IoT camera hardware clocks often suffer from RTC drift of several minutes before NTP sync.
- **Modifications**:
  1. `Backend/src/main/resources/application.yml`:
     - Configured `restrovision.edge.freshness.max-event-age-seconds: 7200` (2 hours).
     - Configured `restrovision.edge.freshness.clock-skew-tolerance-seconds: 600` (10 minutes).
  2. `Backend/src/main/java/com/restrovision/service/AiEventIngestionService.java`:
     - Added validation: if `occurredAt` is > 600s in the future, marks `REJECTED_CLOCK_SKEW` and returns HTTP 200 with status for edge outbox acknowledgment.
     - Added validation: if `occurredAt` is > 7200s in the past, marks `REJECTED_STALE` and returns HTTP 200 with status for edge outbox acknowledgment.
     - Added fast-path in-memory `ConcurrentHashMap<String, Instant>` cache with TTL check (`cachedTime.isAfter(now.minusSeconds(maxEventAgeSeconds))`) and bounded memory enforcement (`putInCache` evicts entries older than `maxEventAgeSeconds` and prunes oldest entries if size exceeds 5,000). Soft bounding verified under 20 concurrent threads inserting 5,500 keys (peak 5,002, settling to 5,000).
     - Removed method-level `@Transactional` on `ingestEvent` to isolate unique-key collisions; when concurrent events race past cache, `aiEventRepository.saveAndFlush` throws `DataIntegrityViolationException`, which is caught and returns `DUPLICATE_IGNORED` without marking an outer transaction rollback-only. Sub-operations (`TableStateMachineService`, `AlertService`) manage their own transactional boundaries.
     - Implemented failure-path lifecycle handling: business rule execution wrapped in `try ... catch (Exception ex)` marking `ai_events` record as `"FAILED"` and evicting the key from cache so subsequent retries are not suppressed. On retry, existing `"FAILED"` record is recognized, reused, re-executed, and promoted to `"APPLIED"` upon success.
     - Survives backend restarts because the primary duplicate check executes `findByRestaurantIdAndEventUid` against the persistent database.
  3. `Backend/src/main/java/com/restrovision/controller/AiEventController.java`:
     - Enforces non-null tenant check on `TenantContext.getRestaurantId()`, returning 401 Unauthorized if missing.
     - Enforces HMAC-SHA256 signature verification over raw request bytes *prior* to JSON deserialization, DB access, or state transition.
  4. `Backend/src/test/java/com/restrovision/RestrovisionApplicationTests.java`:
     - Added tests `aiIngestionRejectsStaleTimestamp` (10-day stale event rejected as `REJECTED_STALE`), `aiIngestionRejectsExcessiveFutureTimestamp` (2-hour future event rejected as `REJECTED_CLOCK_SKEW`), `aiIngestionAcceptsDelayedOutboxRetry` (35-minute delayed event accepted), and `aiIngestionAcceptsClockSkewWithinTolerance` (5-minute future event accepted).
     - Added supplementary tests: `testConcurrentDuplicateDeliveryRaceCondition` (10 concurrent threads, 1 ACCEPTED, 9 DUPLICATE_IGNORED), `testSignedEventReplayAndTamperedUuid` (forged UUID fails HMAC with 401, replay returns DUPLICATE_IGNORED), `testSignatureValidationPrecedesEventProcessingAndStateRemainsUnchanged` (invalid HMAC rejected with 401, state untouched), and `testCacheExpiryAndAuthoritativeDbConstraint` (DB remains authoritative idempotency mechanism on cache eviction).
     - Added Check 1 test: `testConcurrentCacheCapacityAndSoftBounding` (verifies soft-bounding across 20 threads / 5,500 keys, peak 5,002, settling to 5,000).
     - Added Check 2 test: `testTransactionFailurePathConsistencyAndRetryRecovery` (verifies failure-path lifecycle: FAILED status, cache eviction, recovery on retry with APPLIED status, and idempotency on duplicate re-submission).

### 2.3 BUG-006 & BUG-017: Table State Machine $1 + 2N$ Query Cascade Elimination
- **Root Cause**: `TableStateMachineService.getTableView(Long restaurantId)` looped over all tables, executing 1 query for each table's assigned waiter and 1 query for each table's current session. On 50 tables, this executed 101 queries on every floor plan poll and every state mutation broadcast (`broadcastTable`).
- **Modifications**:
  1. `Backend/src/main/java/com/restrovision/repository/EmployeeRepository.java`:
     - Added batch query: `List<Employee> findByRestaurantIdAndEmployeeIdIn(Long restaurantId, Collection<Long> employeeIds)`.
  2. `Backend/src/main/java/com/restrovision/repository/TableSessionRepository.java`:
     - Added batch query: `List<TableSession> findByRestaurantIdAndSessionIdIn(Long restaurantId, Collection<Long> sessionIds)`.
  3. `Backend/src/main/java/com/restrovision/service/TableStateMachineService.java`:
     - Annotated `getTableView` with `@Transactional(readOnly = true)`.
     - Replaced per-table loops with two batch queries using `findByRestaurantIdAndEmployeeIdIn` and `findByRestaurantIdAndSessionIdIn`.
     - Assembled `TableViewDto`s in memory using indexed maps.
     - Query count reduced from $1 + 2N$ to at most 3 queries regardless of table count.
  4. `Backend/src/test/java/com/restrovision/performance/Group2PerformanceAndRegressionTest.java`:
     - Added programmatic Hibernate Statistics query count test confirming 10 tables execute exactly 3 queries instead of 21.
     - Added 100-iteration latency benchmark recording p50 (5.68ms), p95 (12.34ms), and p99 (14.15ms).

### 2.4 BUG-007: Order Eager Loading & Serialization Optimization
- **Root Cause**: `Order.java:43` configured `@OneToMany(..., fetch = FetchType.EAGER)`. Every query for orders (including count queries, workload estimations, and order status lookups) eagerly joined and fetched all `order_items`, consuming substantial heap memory.
- **Modifications**:
  1. `Backend/src/main/java/com/restrovision/domain/Order.java`:
     - Changed `items` collection mapping to `fetch = FetchType.LAZY`.
  2. `Backend/src/main/java/com/restrovision/repository/OrderRepository.java`:
     - Added `@EntityGraph(attributePaths = {"items"})` to `findByRestaurantIdOrderByCreatedAtDesc`, `findByRestaurantIdAndOrderId`, `findByRestaurantIdAndOrderCode`, and `findByRestaurantIdAndSessionId`.
     - Left status/count queries (`findByRestaurantIdAndStatus`, `findByRestaurantIdAndStatusIn`) without entity graphs so they load `orders` lazily without items.
  3. `Backend/src/main/java/com/restrovision/repository/OrderItemRepository.java`:
     - Added `@EntityGraph(attributePaths = {"order"})` to `findByRestaurantIdAndStatusIn` and `findByRestaurantIdAndStationIdAndStatusIn` to prevent N+1 queries when accessing `item.getOrder()` in KDS and delayed order calculations.
  4. `Backend/src/main/java/com/restrovision/service/OrderService.java` & `AnalyticsService.java`:
     - Added `@Transactional(readOnly = true)` to read operations.
  5. `Backend/src/test/java/com/restrovision/performance/Group2PerformanceAndRegressionTest.java`:
     - Verified that `findByRestaurantIdAndStatus` does NOT load `items` (`isLoaded == false`).
     - Verified that `findByRestaurantIdOrderByCreatedAtDesc` loads `items` via `@EntityGraph` (`isLoaded == true`).
     - Verified full JSON serialization of Orders, KDS Queue, Delayed Orders, and Analytics Overview with zero `LazyInitializationException`.

### 2.5 Final Transaction Consistency & Idempotency Verification
- **Modifications**:
  1. `Backend/src/main/java/com/restrovision/controller/GlobalExceptionHandler.java`:
     - Added generic `@ExceptionHandler(Exception.class)` returning RFC 7807 `ProblemDetail` with HTTP 500 (`INTERNAL_SERVER_ERROR`).
  2. `Backend/src/main/java/com/restrovision/repository/ServiceRequestRepository.java`:
     - Added `findByRestaurantIdAndTableIdAndStatusIn(Long restaurantId, Long tableId, List<String> statuses)` and `findByRestaurantIdAndTableId(Long restaurantId, Long tableId)`.
  3. `Backend/src/main/java/com/restrovision/service/AiEventIngestionService.java`:
     - Under `TABLE_OCCUPIED`: Added creation of `Alert` with dedupe key `"TABLE-OCCUPIED-" + tableId` following `tableStateMachine.handleAiOccupied`.
     - Under `POSSIBLE_SERVICE_REQUEST`: Enforced multi-status blocking (`OPEN`, `ACKNOWLEDGED`, `COMPLETED`, `DISMISSED` on retry; `OPEN`, `ACKNOWLEDGED` on fresh events) preventing duplicate tickets when requests have been acknowledged, completed, or dismissed.
     - Added test simulation hooks (`setSimulateAlertFailure`, `setSimulateBeforeAppliedFailure`).
  4. `Backend/src/test/java/com/restrovision/RestrovisionApplicationTests.java`:
     - Added `testAlertCreationFailureAfterSuccessfulStateProcessing` (Test 1).
     - Added `testFailureAfterAlertCreationBeforeAppliedStatus` (Test 2).
     - Added `testServiceRequestIdempotencyAcrossStatusChanges` (Test 3).
     - Total backend tests increased to 68/68 passing.

---

## 3. Investigation of Frontend Bundle Size Discrepancy (Condition 10)

- **Audit Query**: Investigate reported bundle increase from approximately 318 KB to 688 KB.
- **Investigation Actions**:
  1. Ran clean `npm run build` on the latest codebase:
     ```text
     dist/index.html                   0.86 kB │ gzip:  0.54 kB
     dist/assets/index-15qfTajy.css   34.79 kB │ gzip:  7.04 kB
     dist/assets/index-D-3avaZL.js   318.04 kB │ gzip: 81.65 kB
     ✓ built in 4.47s
     ```
  2. Inspected physical directory `frontend/dist/assets/`:
     - `index-D-3avaZL.js` file length on disk is **318,184 bytes** (318.04 kB decimal / 310.7 KiB binary).
  3. Inspected Git log history on `frontend/`:
     - Total commits modifying `frontend/`: Exactly 1 commit (`22d1d5d`), which added 9 lines in `websocket.js` for STOMP JWT `beforeConnect()` headers.
     - No packages, heavy charting libraries, or bloated assets were added.
  4. Root Cause Analysis:
     - The mention of `688.19 kB` in the prior narrative was a typographical/transcription error in the test summary output of the assistant's previous message.
     - **Finding Confirmed**: The production bundle is **318.04 kB** (gzip: 81.65 kB). There is zero bundle bloat, zero unexpected dependency additions, and no frontend source modification was required.
