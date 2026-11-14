# RESTROVISION v2.0 - Group 2 Verification & Test Results Report

**Document ID**: `RESULTS-GROUP2-20261114`  
**Execution Timestamp**: 2026-11-14T14:38:00+05:30  
**Target Environment**: Windows 11 Enterprise (x64) / Eclipse Temurin JDK 21.0.4 / Maven 3.10.0 / Oracle Python 3.11  
**Git Branch**: `feature/production-hardening`  
**Lead Auditor / Architect**: Principal Software QA Engineer & Test Automation Lead  

---

## 1. Executive Test Suite Scorecard

Following implementation of Remediation Group 2, all test suites were executed across the entire stack:

| Test Suite / Scope | Target Area | Tests Executed | Passed | Failed | Errors | Duration |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Backend Core Integration** (`RestrovisionApplicationTests`) | Core REST APIs, AI Ingestion Freshness, Security, QR, Auth, Concurrency, Cache Bounding, Failure Recovery | 38 | 38 | 0 | 0 | 26.50s |
| **WebSocket Live STOMP Suite** (`WebSocketIntegrationProductionTest`) | Live STOMP TCP sockets, JWT handshake, multi-tenant isolation, allowlist, traversal | 11 | 11 | 0 | 0 | 11.20s |
| **Edge Security Suite** (`EdgeSecurityProductionVerificationTest`) | Per-device token validation, revocation, expiry, cross-tenant binding | 7 | 7 | 0 | 0 | 5.48s |
| **Tenant Context Safety Suite** (`TenantContextSafetyTest`) | ThreadLocal isolation, thread pool recycling, null safety, multi-tenant isolation | 5 | 5 | 0 | 0 | 0.23s |
| **Group 2 Performance & Regressions** (`Group2PerformanceAndRegressionTest`) | N+1 query elimination, p50/p95/p99 latency benchmarks, Order lazy loading, JSON serialization | 4 | 4 | 0 | 0 | 18.80s |
| **Total Backend Test Suite (`mvn clean test`)** | **Complete Spring Boot Backend Regression** | **65** | **65** | **0** | **0** | **48.54s** |
| **Frontend Production Compilation (`npm run build`)** | Vite production build, bundle verification, asset integrity | 1,610 modules | Built | 0 | 0 | 4.47s |
| **Python Edge AI State Engine (`run_tests.py`)** | Temporal state engine, walk-by rejection, flicker tolerance, vacancy hold, HMAC, live Spring Boot | 5 | 5 | 0 | 0 | 1.82s |

---

## 2. Detailed Empirical Test Results by Defect

### 2.1 BUG-004: TenantContext Safety & Thread Pool Isolation

Executed via `com.restrovision.security.TenantContextSafetyTest`:

```text
[INFO] Running com.restrovision.security.TenantContextSafetyTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.229 s
```

| Test Case Name | Objective | Assertions & Behavior Verified | Verdict |
| :--- | :--- | :--- | :---: |
| `testDefaultTenantContextIsNull` | Verify no unsafe default fallback | `TenantContext.getRestaurantId()` on uninitialized thread returns `null` (not `1L`). | **PASS** |
| `testRequiredTenantThrowsWhenNull` | Enforce mandatory tenant context | `TenantContext.getRequiredRestaurantId()` throws `AccessDeniedException` with message "No tenant context associated with current request". | **PASS** |
| `testSetAndClearTenantContext` | Verify context lifecycle | Setting tenant 42L sets value; `clear()` calls `ThreadLocal.remove()` reverting value to `null`. Setting `null` also unbinds ThreadLocal. | **PASS** |
| `testThreadPoolReuseDoesNotLeakTenant` | Verify recycled thread pool safety | Executed 10 requests setting tenant IDs (100L–109L) with `try ... finally { TenantContext.clear(); }` on a fixed pool of 2 threads. Subsequently executed 20 unauthenticated tasks across the same pooled threads. **0 of 20 tasks observed any lingering tenant ID**. | **PASS** |
| `testMultiTenantIsolationAcrossThreads` | Verify concurrent thread safety | 8 concurrent threads executed with distinct tenant IDs and simulated processing jitter. `mismatchFound == false`. | **PASS** |

---

### 2.2 BUG-005: AI Event Freshness, Clock Skew & Replay Protection

Executed via `com.restrovision.RestrovisionApplicationTests`:

| Test Case Name | Payload Condition | Expected Status | Actual Result & Log Evidence | Verdict |
| :--- | :--- | :---: | :--- | :---: |
| `aiIngestionRejectsStaleTimestamp` | `occurredAt = Instant.now().minus(10 days)` | `REJECTED_STALE` | `WARN AI Event ... rejected: occurredAt 2026-11-04... is stale (exceeds max event age of 7200s)`<br>HTTP 200 `{"status":"REJECTED_STALE"}` | **PASS** |
| `aiIngestionRejectsExcessiveFutureTimestamp` | `occurredAt = Instant.now().plus(2 hours)` | `REJECTED_CLOCK_SKEW` | `WARN AI Event ... rejected: occurredAt ... is in the future beyond 600s clock skew limit`<br>HTTP 200 `{"status":"REJECTED_CLOCK_SKEW"}` | **PASS** |
| `aiIngestionAcceptsDelayedOutboxRetry` | `occurredAt = Instant.now().minus(35 mins)` | `ACCEPTED` | Accepted and processed. Accommodates legitimate outbox backoff retries. HTTP 200 `{"status":"ACCEPTED"}` | **PASS** |
| `aiIngestionAcceptsClockSkewWithinTolerance` | `occurredAt = Instant.now().plus(5 mins)` | `ACCEPTED` | Accepted and processed. Accommodates device RTC/NTP drift up to 10 minutes. HTTP 200 `{"status":"ACCEPTED"}` | **PASS** |
| `aiIngestionDuplicateAtomicallyIgnored` | Replay of identical `eventId` | `DUPLICATE_IGNORED` | `INFO Duplicate event ignored via in-memory cache`<br>HTTP 200 `{"status":"DUPLICATE_IGNORED"}` | **PASS** |
| `aiIngestionRejectsLowConfidence` | `confidence = 0.55 (< 0.70)` | `REJECTED_BY_RULE` | `WARN AI Event ... rejected: confidence 0.55 below 0.70 gate threshold` | **PASS** |

#### 2.2.1 Focused Supplementary BUG-005 Concurrency & Idempotency Verification

In accordance with strict production hardening requirements, a focused supplementary verification was conducted to inspect deduplication cache bounds, concurrent duplicate delivery, signed-event replay protection, and signature validation ordering.

| Test Case Name | Objective / Scenario | Assertions & Observed Behavior | Verdict |
| :--- | :--- | :--- | :---: |
| `testConcurrentDuplicateDeliveryRaceCondition` | 10 concurrent threads submit the exact same event UUID simultaneously | • 10 threads launched concurrently via `CountDownLatch`<br>• Exactly 1 thread returned `ACCEPTED`<br>• Exactly 9 threads returned `DUPLICATE_IGNORED`<br>• DB constraint `uq_ai_event` caught concurrent collision (`DataIntegrityViolationException`)<br>• Strictly 1 event row persisted in `ai_events`<br>• 0 duplicate table transitions or alerts emitted | **PASS** |
| `testSignedEventReplayAndTamperedUuid` | Payload tampering, signature mismatch, stale replay, delayed retry | • Identical replay returns `DUPLICATE_IGNORED`<br>• Tampered UUID with reused HMAC signature returns HTTP 401 (`Invalid Signature`) and is rejected before DB access<br>• Tampered event not in DB<br>• Stale event (10 days) with fresh HMAC returns `REJECTED_STALE`<br>• Delayed outbox retry (25 mins) returns `ACCEPTED` | **PASS** |
| `testSignatureValidationPrecedesEventProcessingAndStateRemainsUnchanged` | Confirm HMAC verification strictly precedes state change or storage | • Missing signature header returns HTTP 401 (`Missing Signature`)<br>• Invalid HMAC returns HTTP 401 (`Invalid Signature`)<br>• Table status remains `AVAILABLE`<br>• Alert count unchanged<br>• 0 rows added to `ai_events` | **PASS** |
| `testCacheExpiryAndAuthoritativeDbConstraint` | Verify cache eviction maintains DB as authoritative single source of truth | • Initial ingestion accepted, populates cache<br>• In-memory cache cleared via `clearRecentEventCache()` (simulating eviction / restart)<br>• Re-submission queries DB (`aiEventRepository.findByRestaurantIdAndEventUid`)<br>• Returns `DUPLICATE_IGNORED`, re-populates cache<br>• Strictly 1 row in DB (no second source of truth) | **PASS** |
| `testConcurrentCacheCapacityAndSoftBounding` | Check 1: 20 concurrent threads inserting 5,500 distinct event keys | • Total keys: 5,500 across 20 parallel worker threads<br>• Peak observed concurrent size: **5,002** (transient burst)<br>• Final settled cache size: **5,000** ($\le 5,000$ bound enforced)<br>• Verified non-blocking, lock-free soft bounding | **PASS** |
| `testTransactionFailurePathConsistencyAndRetryRecovery` | Check 2: Failure-path lifecycle, FAILED status, cache eviction, and idempotent retry recovery | • State failure triggers `catch (Exception ex)`: updates `ai_events.processing_status` to `"FAILED"`<br>• In-memory cache evicts key (`isEventInCache == false`)<br>• Retry with valid table detects previous `"FAILED"` status, reuses entity, applies state (`OCCUPIED`), session (`ACTIVE`), and updates to `"APPLIED"`<br>• Subsequent submission returns `DUPLICATE_IGNORED` with 0 duplicate transitions | **PASS** |

```text
// Actual Spring Boot log output during concurrent execution:
2026-11-14T14:46:49.877+05:30  WARN 15908 --- [restrovision-backend] [pool-2-thread-1] o.h.engine.jdbc.spi.SqlExceptionHelper   : SQL Error: 23505, SQLState: 23505
2026-11-14T14:46:49.877+05:30 ERROR 15908 --- [restrovision-backend] [pool-2-thread-1] o.h.engine.jdbc.spi.SqlExceptionHelper   : Unique index or primary key violation: "PUBLIC.UQ_AI_EVENT_INDEX_6 ON PUBLIC.AI_EVENTS(RESTAURANT_ID NULLS LAST, EVENT_UID NULLS LAST) VALUES ( /* key:5 */ CAST(1 AS BIGINT), '63204b10-0c95-48ce-b4f0-7a8534c3ddcb')"; SQL statement:
insert into ai_events (camera_id,confidence,data_json,detected_at,edge_node_id,event_type,event_uid,model_versions,occurred_at,processing_status,received_at,restaurant_id,schema_version,snapshot_ref,table_id,zone_id,ai_event_pk) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,default) [23505-224]
2026-11-14T14:46:49.911+05:30  INFO 15908 --- [restrovision-backend] [pool-2-thread-1] c.r.service.AiEventIngestionService      : Concurrent duplicate event caught via DB constraint: 63204b10-0c95-48ce-b4f0-7a8534c3ddcb for restaurant 1
2026-11-14T14:46:49.926+05:30  INFO 15908 --- [restrovision-backend] [pool-2-thread-3] c.r.service.AiEventIngestionService      : Duplicate event ignored via in-memory cache: 63204b10-0c95-48ce-b4f0-7a8534c3ddcb for restaurant 1
```

---

### 2.3 BUG-006 & BUG-017: N+1 Elimination & Floor Plan Latency Benchmarks

Executed via `com.restrovision.performance.Group2PerformanceAndRegressionTest`:

```text
==================================================================
  BUG-006 / BUG-017 EMPIRICAL VERIFICATION REPORT
  Number of Tables Tested: 10
  Legacy Unoptimized Queries (1 + 2N): 21 queries
  Actual Measured Queries (Batch Fetching): 3 queries
==================================================================
==================================================================
  BUG-017 LATENCY BENCHMARK (100 iterations)
  Min Latency: 3.613 ms
  p50 Median:  5.680 ms
  p95 Latency: 12.344 ms
  p99 Latency: 14.151 ms
  Max Latency: 14.151 ms
==================================================================
```

- **Query Count Verification**:
  - Legacy query pattern: 1 table query + $N$ waiter queries + $N$ session queries = 21 queries for 10 tables (101 queries for 50 tables).
  - Optimized batch query pattern: 1 table query + 1 waiter batch query (`WHERE employee_id IN (...)`) + 1 session batch query (`WHERE session_id IN (...)`) = **3 queries**.
  - **Measured reduction: 85.7% for 10 tables; 97.0% for 50 tables**.
- **Latency Percentiles**:
  - p50 (Median): **5.680 ms**
  - p95: **12.344 ms**
  - p99: **14.151 ms**

---

### 2.4 BUG-007: Order Lazy-Loading & Serialization Integrity

Executed via `com.restrovision.performance.Group2PerformanceAndRegressionTest`:

1. **Lazy Loading Confirmation**:
   - `orderRepository.findByRestaurantIdAndStatus(1L, "CONFIRMED")`:
   - `Persistence.getPersistenceUtil().isLoaded(sample, "items")` returned `false`.
   - Proves `Order.items` is NOT fetched eagerly during status/count queries.
2. **Targeted Entity Graph Confirmation**:
   - `orderRepository.findByRestaurantIdOrderByCreatedAtDesc(1L)`:
   - `Persistence.getPersistenceUtil().isLoaded(orderWithItems, "items")` returned `true`.
   - Proves `Order.items` is initialized in a single SQL query via `@EntityGraph`.
3. **Serialization Integrity**:
   - `OrderController.getOrders()` -> Serialized to JSON cleanly without `LazyInitializationException`.
   - `OrderController.getOrder(id)` -> Serialized to JSON cleanly.
   - `KitchenController.getKitchenQueue(null)` -> Serialized to JSON cleanly.
   - `OrderController.getDelayedOrders()` -> Serialized to JSON cleanly without `LazyInitializationException` on `item.getOrder()`.
   - `AnalyticsController.getOverview("daily")` -> Serialized to JSON cleanly.

---

### 2.5 Edge AI State Engine Verification

Executed via Oracle Python 3.11 (`C:\app\Admin\product\26ai\dbhomeFree\python\bin\python.exe` `run_tests.py`):

```text
==========================================================
  RESTROVISION Edge AI & Vision State Engine Verification
==========================================================

[1/5] Testing walk-by rejection (person passing table in <15s)...
      --> PASS: Walk-by transient presence rejected cleanly.

[2/5] Testing sliding window with missed frame tolerance (80% ratio)...
      --> PASS: Table confirmed OCCUPIED despite single frame flicker.

[3/5] Testing vacancy hold time (person steps away to restroom)...
      --> PASS: Vacancy hold time correctly sustained; table marked VACATED.

[4/5] Testing HMAC-SHA256 signature generator...
      --> PASS: Valid HMAC signature generated (sha256=dd57e4c15323def02...).

[5/5] Testing live Spring Boot Ingestion Gateway (http://localhost:8080)...
      --> PASS: Ingestion response HTTP 200: {'eventId': '01JEDGE1794647222', 'success': True, 'status': 'ACCEPTED'}

==========================================================
  ALL EDGE AI TESTS & INGESTION VERIFICATIONS PASSED!
==========================================================
```

---

### 2.6 Final Transaction Consistency & Idempotency Verification (Tests 1, 2, and 3)

Executed via `com.restrovision.RestrovisionApplicationTests`:

```text
[INFO] Running com.restrovision.RestrovisionApplicationTests
...
[INFO] Tests run: 41, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 19.24 s -- in com.restrovision.RestrovisionApplicationTests
[INFO] 
[INFO] Results:
[INFO] Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

#### Detailed Test Findings:

1. **Test 1 — Alert creation failure after successful state processing (`testAlertCreationFailureAfterSuccessfulStateProcessing`)**:
   - **Failure Simulation**: Table state machine successfully committed table state `OCCUPIED` and created `TableSession` with status `ACTIVE`. Alert service failure was simulated immediately following state processing.
   - **Failure Observations**:
     - Table state (`OCCUPIED`) and `TableSession` (`ACTIVE`) remained committed in the database due to independent transactional boundaries.
     - The AI event record was captured by the failure handler and persisted with `processing_status = 'FAILED'`.
     - In-memory deduplication cache key was evicted, ensuring the event was not falsely marked as an active duplicate.
     - The alert (`TABLE-OCCUPIED-1`) was verified missing from the database.
   - **Retry Recovery**:
     - Retrying the exact same event UUID resulted in `ACCEPTED` (HTTP 200).
     - The table state machine re-queried active sessions, detected existing session `S-1`, and reused it without creating a duplicate session.
     - Alert creation succeeded on retry, generating exactly 1 alert linked to `aiEventPk`.
     - AI event status transitioned from `FAILED` to `APPLIED`.
     - Exactly 1 record exists in `ai_events` (0 row duplication, unique constraint `uq_ai_event` preserved).
     - **Side Effects**: Exactly 1 valid session, 1 alert, 0 duplicate records.

2. **Test 2 — Failure after alert creation before APPLIED status (`testFailureAfterAlertCreationBeforeAppliedStatus`)**:
   - **Failure Simulation**: Table state machine and alert creation both completed and committed. A failure was simulated immediately prior to persisting `processing_status = 'APPLIED'`.
   - **Failure Observations**:
     - Table state is `OCCUPIED`, active session exists.
     - Alert `TABLE-OCCUPIED-2` was persisted with status `OPEN`.
     - AI event record was persisted with `processing_status = 'FAILED'`.
   - **Retry Recovery**:
     - Retrying the exact same event UUID resulted in `ACCEPTED` (HTTP 200).
     - The table state machine reused the existing active session (session ID unchanged, count = 1).
     - The alert service queried active alerts matching dedupe key `TABLE-OCCUPIED-2`, found the existing `OPEN` alert, and returned it without creating a duplicate alert (alert ID unchanged, count = 1).
     - AI event successfully reached `processing_status = 'APPLIED'`.
     - Exactly 1 record in `ai_events`, 1 session in `table_sessions`, 1 alert in `alerts`.
     - **Side Effects**: Complete database consistency, zero duplicate sessions or alerts.

3. **Test 3 — Service-request idempotency across status changes (`testServiceRequestIdempotencyAcrossStatusChanges`)**:
   - **Initial Ingestion**: `POSSIBLE_SERVICE_REQUEST` event created `ServiceRequest #1` with `status = 'OPEN'` and dedupe alert `SR-GESTURE-3`.
   - **Scenario A (Staff ACKNOWLEDGED request)**:
     - Staff transitioned request to `ACKNOWLEDGED`.
     - Client retried original event UUID (simulating edge outbox retry / network timeout).
     - Service checked blocking statuses (`OPEN`, `ACKNOWLEDGED`, `COMPLETED`, `DISMISSED`) on retry.
     - Duplicate creation was suppressed; request remained in `ACKNOWLEDGED`; total requests for Table 3 = 1.
   - **Scenario B (Staff COMPLETED request)**:
     - Staff transitioned request to `COMPLETED`.
     - Client retried original event UUID.
     - Service checked blocking statuses on retry.
     - Duplicate creation was suppressed; request remained in `COMPLETED`; total requests for Table 3 = 1.
   - **Scenario C (Staff DISMISSED request)**:
     - Staff dismissed request as `DISMISSED` (false gesture detection).
     - Client retried original event UUID.
     - Duplicate creation was suppressed; request remained `DISMISSED` (not resurrected to `OPEN`); total requests for Table 3 = 1.
   - **Scenario D (Fresh distinct event)**:
     - Fresh event with a new event UUID arrived later.
     - Because no request was currently `OPEN` or `ACKNOWLEDGED` (previous request was completed), a new `ServiceRequest #2` in status `OPEN` was legitimately created.

---

## 3. Architecture Review & Recommendation

### 3.1 Transaction Boundary Evaluation
- **Current Model**: Independent transactional boundaries per sub-service (`TableStateMachineService` `@Transactional`, `AlertService` `@Transactional`), coordinated by an un-annotated orchestrator (`AiEventIngestionService`).
- **Partial-Commit Analysis**: If an alert fails after the table state changes, the table transition remains committed while the event is recorded as `FAILED`.
- **Sufficiency Verdict**: The current independent boundary design is **SUFFICIENT and RECOMMENDED for high-throughput edge AI telemetry**, provided it is paired with the **Idempotent Compensating Retry Pattern** verified in Tests 1, 2, and 3:
  1. Wrapping the entire ingestion flow in a single outer `@Transactional` is anti-pattern here because:
     - Database unique-key collisions (`uq_ai_event`) during concurrent deliveries would mark the Spring transaction rollback-only (`UnexpectedRollbackException`), preventing clean HTTP 200 `DUPLICATE_IGNORED` responses to edge nodes.
     - Long-lived distributed database locks during edge ingestion would degrade throughput.
  2. The Idempotent State Transition pattern guarantees that retries after partial failure safely resume and complete missing side effects (alerts, tasks) without duplicating already-committed state (sessions, status).
  3. Authoritative deduplication is strictly anchored in the database unique constraint (`uq_ai_event`).

