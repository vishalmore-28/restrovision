# RESTROVISION v2.0 — Remediation Group 5 Test Strategy (Final Pre-Authorization Revision)

**Document ID:** `STRAT-GRP5-20261114-FINAL`  
**Target Branch:** `feature/production-hardening`  
**Base Commit:** `58e84e1`  
**Protected Branch:** `main` untouched at `171171b`  
**Scope:** Verification & Testing Strategy for Defects BUG-021, BUG-020, BUG-022, BUG-023  
**Status:** PROPOSED TEST SPECIFICATION (STRICT HOLD: Implementation Pending Authorization)  

---

## 1. Executive Summary & Verification Core Principles

This test strategy defines the **proposed test specifications and acceptance gates** for Remediation Group 5:

1. **Testing Foundation First (Phase 1):** Vitest + React Testing Library + JSDOM are to be deployed upfront, providing regression safety for subsequent refactoring.
2. **Decoupled Metric Reporting:** Test execution results and code coverage metrics are tracked, evaluated, and reported independently without hardcoded test count expectations.
3. **Rigorous Empirical Residual Quantiles:** Edge AI forecasting utilizes Hyndman-Fan Type 7 quantile interpolation over out-of-sample residuals. Standard deviation approximations are strictly forbidden. Bounded by minimum sample size ($N_{\text{valid}} \ge 12$) with domain prior fallback for sparse/empty history, discrete IQR/MAD outlier clipping, gap interpolation, and contextual negation rules for `"not good"` and `"not bad"`.
4. **Real Vulnerability Scanners & OSIV Testing:** Structural tools (`dependency:tree`, `pip check`) are superseded by actual CVE scanners: **OWASP Dependency-Check** for Maven and **`pip-audit`** for Python. `spring.jpa.open-in-view=false` is verified by testing five endpoints under populated and empty datasets for complete data, zero `LazyInitializationException`, and bounded query counts.
5. **Robust WebSocket Concurrency & Authorization:** Tests cover disconnected pending queues, callback reference stability (`useRef`), StrictMode double-mounting, reconnect resubscription, logout cache purge, and backend STOMP destination-level tenant authorization via `ChannelInterceptor`.

---

## 2. Test Specifications by Phase & Defect

### 2.1 Phase 1 — BUG-021: Proposed Frontend Automated Testing Foundation

#### 2.1.1 Reporting Schema: Execution Metrics vs Code Coverage

**Table 1: Test Execution Metrics (Dynamic Reporting)**
| Metric Name | Reporting Definition | Quality Threshold |
| :--- | :--- | :--- |
| **Discovered Tests** | Total test cases discovered across all test suites by Vitest runner | Actual discovered count (no hardcoding) |
| **Executed Tests** | Total test cases actually executed in the test run | Must equal 100% of discovered tests |
| **Passed Tests** | Total test cases that passed successfully | Must equal 100% of executed tests |
| **Failed Tests** | Total test cases that encountered assertion or runtime failures | **Must be exactly 0** |
| **Skipped Tests** | Total test cases skipped or marked pending | **Must be exactly 0** |
| **Execution Duration** | Total wall-clock time in seconds for test suite execution | Recorded wall-clock time |

**Table 2: Code Coverage Metrics (v8 Provider)**
| Dimension | Reporting Definition | Overall Target | Critical Operational Paths Target |
| :--- | :--- | :---: | :---: |
| **Statements** | Executable AST statements evaluated (Covered / Total) | $\ge 70\%$ | $\ge 80\%$ |
| **Branches** | Conditional branches ($if/else$, ternary, switch) evaluated | $\ge 65\%$ | $\ge 75\%$ |
| **Functions** | Declared functions and methods invoked (Covered / Total) | $\ge 70\%$ | $\ge 80\%$ |
| **Lines** | Physical lines of code executed (Covered / Total) | $\ge 70\%$ | $\ge 80\%$ |

*Critical Operational Paths Defined:* `src/services/websocket.js`, `src/services/api.js`, `src/context/AuthContext.jsx`, `src/pages/CustomerQrPortal.jsx`, `src/pages/FloorPlan.jsx`. High pass rate does not imply adequate coverage, and coverage targets do not waive zero-failure mandates.

#### 2.1.2 Baseline Test Suites (To Be Implemented)
| Test Suite | Test ID | Target Scenario | Verification Details & Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **`Login.test.jsx`** | **TC-21-01** | Accessible form control labels | `getByLabelText(/username/i)` and `getByLabelText(/password/i)` resolve correctly via `htmlFor`. |
| | **TC-21-02** | Role switcher buttons | Clicking "Manager", "Chef", or "Waiter" pre-populates valid credentials. |
| | **TC-21-03** | 401 error announcement | Mocked 401 response renders accessible `role="alert"` with sanitized error text. |
| | **TC-21-04** | Pending state disabling | Form submit disables submit button and shows loading spinner. |
| **`CustomerQrPortal.test.jsx`** | **TC-21-05** | Rating button accessible names | Rating buttons have `aria-label="Rate {n} out of 5 stars"` and update `aria-pressed`. |
| | **TC-21-06** | Character limit counter | Typing updates character counter; input is capped or rejected at 500 characters. |
| | **TC-21-07** | Submission cooldown timer | Successful submission starts cooldown timer, disables submit button with remaining seconds text. |
| | **TC-21-08** | Screen reader feedback status | Submission completion renders `role="status"` live region with confirmation. |
| **`Analytics.test.jsx`** | **TC-21-09** | Decomposed tab switching | Switching between Overview, Menu Engineering, Guest Reviews, and Crowd Forecast renders correct tab. |
| | **TC-21-10** | Sentiment & category badges | Review items render sentiment badges (`POSITIVE`, `NEUTRAL`, `NEGATIVE`) and topic categories. |
| | **TC-21-11** | Forecast interval cards | Crowd Forecast renders P10, P50, and P90 volume cards cleanly without crashing on empty data. |
| **`api.test.js`** | **TC-21-12** | Automatic CSRF token header | Mutating requests (POST, PUT, DELETE) read `XSRF-TOKEN` cookie and bind `X-XSRF-TOKEN` header. |
| | **TC-21-13** | Credential inclusion | All Axios requests include `withCredentials: true`. |
| | **TC-21-14** | Bearer token fallback | Requests without cookies fall back to Bearer token header if available. |

---

### 2.2 Phase 2 — BUG-020: Proposed Empirical Residual Forecaster & Contextual Valence NLP

#### 2.2.1 Edge AI Traffic Forecaster Specifications (`edge-ai/tests/test_forecast.py`)
- **Mathematical Specification:**
  - Given observations $(t_1, y_1), \dots, (t_N, y_N)$ sorted chronologically.
  - Out-of-sample residuals: For $k \ge 3$, expected baseline $\hat{y}_k$ is computed strictly from prior data ($y_1, \dots, y_{k-1}$):
    $$\hat{y}_k = \left(\frac{1}{\min(k-1, 6)} \sum_{j=1}^{\min(k-1, 6)} y_{k-j}\right) \times \frac{S(t_k)}{\frac{1}{\min(k-1, 6)} \sum_{j=1}^{\min(k-1, 6)} S(t_{k-j})}$$
    $$e_k = y_k - \hat{y}_k$$
    Valid residual array $E = [e_3, \dots, e_N]$ ($M = N_{\text{valid}} - 2$).
  - Residual series starts at $e_3$ because $k=3$ is the earliest point with $\ge 2$ prior historical points, providing the minimal degrees of freedom needed for rolling averages and dispersion.
  - If $N_{\text{valid}} < 12$ (or missing/empty history): Domain seasonal prior fallback: $P50 = \text{PriorMean}(H)$, $P10 = \max(0, \text{round}(P50 \times 0.70))$, $P90 = \text{round}(P50 \times 1.30)$. Metadata returns `"fallback_applied": true`, `"sample_count": N_valid`, `"minimum_required": 12`, `"reason": "INSUFFICIENT_SAMPLE_SIZE"`.
  - If $N_{\text{valid}} \ge 12$:
    - Residual IQR: $IQR = Q(0.75) - Q(0.25)$ (Hyndman-Fan Type 7).
    - *Why $IQR < 1.0$ is appropriate:* Guest traffic is a discrete non-negative integer. During off-peak periods, identical guest counts across multiple hours cause $> 50\%$ of residuals to fall into the same integer bin, collapsing $IQR < 1.0$. Applying $1.5 \times IQR$ directly would collapse clipping bounds to zero, falsely classifying normal $\pm 1$ guest variations as outliers.
    - Clipping Bounds:
      - If $IQR \ge 1.0$: $[\text{Clip}_{\text{lower}}, \text{Clip}_{\text{upper}}] = [Q(0.25) - 1.5 IQR, Q(0.75) + 1.5 IQR]$.
      - If $IQR < 1.0$ and $\text{MAD} > 0$: $[\tilde{e} - 2.5 S_{\text{eff}}, \tilde{e} + 2.5 S_{\text{eff}}]$ where $S_{\text{eff}} = \max(1.0, 1.4826 \times \text{MAD})$.
      - If $IQR < 1.0$ and $\text{MAD} == 0$: $[\tilde{e} - 5.0, \tilde{e} + 5.0]$ using minimum integer sensitivity $S_{\text{eff}} = 2.0$.
    - Empirical Quantiles: $Q(0.10)$, $Q(0.50)$, $Q(0.90)$ calculated via Hyndman-Fan Type 7 from sorted $e^*$.
    - Predictions: $P50 = \max(0, \text{round}(\hat{y}_H + Q(0.50)))$, $P10 = \max(0, \text{round}(\hat{y}_H + Q(0.10)))$, $P90 = \max(P10, \text{round}(\hat{y}_H + Q(0.90)))$.
    - Strictly disclaims Gaussian or standard-deviation approximations.

| Test ID | Test Case Name | Input Conditions | Expected Result & Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **TC-20-01** | `test_forecast_insufficient_samples_fallback` | Array of 11 hourly observations ($N = 11 < 12$). | Falls back to domain seasonal prior. Response contains `"fallback_applied": true`, `"sample_count": 11`, `"minimum_required": 12`, `"reason": "INSUFFICIENT_SAMPLE_SIZE"`. |
| **TC-20-02** | `test_forecast_empty_history_fallback` | Empty history array (`[]`). | Returns domain seasonal prior cleanly with `"fallback_applied": true` and `"sample_count": 0`. |
| **TC-20-03** | `test_forecast_empirical_quantile_ordering` | Valid history of 24 hourly observations ($N \ge 12$). | Quantiles computed via Hyndman-Fan Type 7. Asserts strictly: $0 \le P10 \le P50 \le P90$. |
| **TC-20-04** | `test_forecast_high_outlier_spike_clipping` | 18 hours of normal traffic (15–25 guests) with 1 extreme anomalous spike (500 guests at 14:00). | Upper clipping bound clamps the spike residual. P50 and P90 remain realistic ($\le 45$ guests) rather than exploding to $> 200$. |
| **TC-20-05** | `test_forecast_low_outlier_clipping` | 18 hours of peak traffic (80–100 guests) with 1 anomalous sensor dropout (0 guests at 19:00). | Lower clipping bound clamps negative residual. P10 remains bounded and non-negative. |
| **TC-20-06** | `test_forecast_near_zero_iqr_handling` | 15 hours of identical traffic counts (exactly 20 guests every hour, $IQR = 0$). | Forecaster detects near-zero IQR, falls back to MAD dispersion bound ($S_{\text{eff}} = 2.0$ when $\text{MAD}=0$), and computes valid $P10 < P50 < P90$ without division-by-zero. |
| **TC-20-07** | `test_forecast_hourly_gap_imputation` | Non-consecutive hourly timestamps: observations for 12:00, 13:00, gap, and 18:00, 19:00, 20:00, 21:00, 22:00, 23:00. | Chronological validator detects gap, imputes intermediate expected volume using seasonal prior ratio scaling, and computes valid empirical intervals. |
| **TC-20-08** | `test_forecast_disclaims_gaussian_calibration` | Valid prediction request. | Response JSON contains `"algorithm": "Empirical Residual Quantiles (Hyndman-Fan Type 7)"`. Metadata disclaims Gaussian calibration or parametric coverage guarantees. Zero mention of `lgbm`. |

#### 2.2.2 Contextual Valence NLP Engine Specifications (`edge-ai/tests/test_sentiment.py`)
| Test ID | Test Case Name | Input Text | Expected Result & Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **TC-20-09** | `test_sentiment_contextual_negation_not_good` | `"The food was not good and the soup was cold."` | Negation token `"not"` inverts positive token `"good"` to negative weight. Classification is `NEGATIVE`; heuristic valence score $\le -0.30$; category contains `FOOD_QUALITY`. |
| **TC-20-10** | `test_sentiment_contextual_negation_not_bad` | `"The waiter service was not bad at all."` | Negation token `"not"` mitigates negative token `"bad"` to positive/neutral (+0.5). Classification is `NEUTRAL` or `POSITIVE`; heuristic valence score $\ge 0.0$; category contains `SERVICE_SPEED`. |
| **TC-20-11** | `test_sentiment_negation_distance_limit` | `"The service was not at the hotel standard but the biryani was really good."` | Distance between `"not"` and `"good"` is 10 tokens (> 3-token lookbehind limit). Token `"good"` is NOT inverted. Correctly tags positive sentiment for food. |
| **TC-20-12** | `test_sentiment_double_negation_handling` | `"The ambience is not unpleasant."` | Double negation of negative token `"unpleasant"` produces non-negative valence. |
| **TC-20-13** | `test_sentiment_intensifier_scaling` | Comment A: `"Good naan"`. Comment B: `"Extremely good naan"`. | Intensifier `"extremely"` applies $1.5\times$ multiplier to adjacent token. Absolute score of Comment B strictly exceeds Comment A. |
| **TC-20-14** | `test_sentiment_regional_lexicon_keywords` | `"Khana ekdum bakwas tha, waiter sun hi nahi raha tha."` | Hinglish keywords (`bakwas`, `nahi`) scored with negative valence. Classified as `NEGATIVE`. |
| **TC-20-15** | `test_sentiment_model_metadata_honesty` | Any valid sentiment request. | Metadata reports `"model_version": "sentiment-lexicon-valence-1.1.0"`. Disclaims probabilistic confidence calibration. Zero mention of `indicbert`. |

#### 2.2.3 Backend Dynamic Analytics (`AnalyticsServiceTest.java`)
| Test ID | Test Case Name | Preconditions / Input | Expected Execution & Output | Acceptance Threshold |
| :--- | :--- | :--- | :--- | :--- |
| **TC-20-16** | `testDynamicForecastCalculatedFromDatabaseSessions` | Database populated with completed table sessions across lunch and dinner. | `AnalyticsService.getAnalyticsOverview()` computes guest turnover and occupancy dynamically from repository records rather than static 5,200/480/148 constants. | Values vary dynamically based on test fixture data. |

---

### 2.3 Phase 3 — BUG-022: Multi-Stack Production Vulnerability Auditing & OSIV Testing

#### 2.3.1 Multi-Stack Vulnerability Scanning Commands & Targets
| Tier | Tool / Target | Execution Command | Database Update & Scan Scope | Pass/Fail Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend (npm)** | npm audit | `npm audit` / `npm audit --omit=dev` | Scans `frontend/package-lock.json` against GitHub Advisory Database. Lockfile verified with `vite@^6.4.6` and `esbuild@^0.25.0`. | 0 high or critical vulnerabilities; GHSA-67mh-4wv8-2f99 and GHSA-587h-7wmj-h5pm resolved. |
| **Backend (Maven)** | OWASP Dependency-Check | `mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7.0 -Dformats=HTML,JSON -Dout=target/dependency-check-report` | Downloads/updates NVD CVE feeds; scans Spring Boot 3.3.5, JJWT 0.12.6, Flyway 10.10.0, OJDBC 23.5.0. | Fails on any vulnerability with CVSS $\ge 7.0$. Generates HTML/JSON reports in `target/`. |
| **Edge AI (Python)** | PyPA pip-audit | `pip-audit -r edge-ai/requirements.txt --desc on -f json -o pip-audit-report.json --strict` | Queries OSV/PyPA database against pinned dependencies (`fastapi==0.110.0`, `uvicorn==0.28.0`, `pydantic==2.6.4`, `numpy==1.26.4`). | Fails on any known unpatched CVE. Generates JSON report. |

*Structural Tool Distinction:* `mvn dependency:tree` and `pip check` are structural dependency analyzers, NOT vulnerability scans.

#### 2.3.2 `spring.jpa.open-in-view=false` Lazy-Loading Integration Tests
Setting `spring.jpa.open-in-view: false` disables the `OpenEntityManagerInViewInterceptor`, ensuring persistence contexts close at the service boundary.
To prevent `LazyInitializationException` and bounded SQL query performance, services must execute fetch planning (`@EntityGraph`, `JOIN FETCH`) and DTO mapping within transaction boundaries.

Each of the five endpoints is to be tested under both **populated data** and **empty-data (boundary)** conditions:
| Test ID | Endpoint Under Test | Test Scenario | Verified Assertions |
| :--- | :--- | :--- | :--- |
| **TC-22-01A** | `GET /api/v1/orders` | Populated (10 orders with items) | HTTP 200 OK; full items array serialized; 0 `LazyInitializationException`; bounded query count ($\le 2$ SQL queries). |
| **TC-22-01B** | `GET /api/v1/orders` | Empty dataset (0 orders) | HTTP 200 OK; empty JSON array `[]`; 0 errors; 1 query. |
| **TC-22-02A** | `GET /api/v1/orders/{id}` | Populated single order | HTTP 200 OK; order details and items serialized; 0 `LazyInitializationException`. |
| **TC-22-02B** | `GET /api/v1/orders/{id}` | Non-existent ID | HTTP 404 Not Found ProblemDetail; zero exception leaks. |
| **TC-22-03A** | `GET /api/v1/orders/kds-queue` | Populated KDS queue | HTTP 200 OK; active kitchen items serialized; 0 `LazyInitializationException`; bounded queries. |
| **TC-22-03B** | `GET /api/v1/orders/kds-queue` | Empty KDS queue | HTTP 200 OK; empty array `[]`; 0 errors. |
| **TC-22-04A** | `GET /api/v1/analytics/overview` | Populated analytics history | HTTP 200 OK; KPIs and aggregated revenue computed; 0 `LazyInitializationException`. |
| **TC-22-04B** | `GET /api/v1/analytics/overview` | Zero session history | HTTP 200 OK; safe zero-value defaults; zero division-by-zero or lazy errors. |
| **TC-22-05A** | `GET /api/v1/tables` | Populated floor plan (10 tables) | HTTP 200 OK; table view with session and tasks serialized; 0 `LazyInitializationException`; $\le 3$ queries (N+1 eliminated). |
| **TC-22-05B** | `GET /api/v1/tables` | Empty restaurant (0 tables) | HTTP 200 OK; empty array `[]`; 0 errors. |

---

### 2.4 Phase 4 — BUG-023: Proposed WebSocket Architecture & State Synchronization

#### 2.4.1 Frontend Client Concurrency & Lifecycle Tests (`frontend/src/__tests__/websocket.test.js`)
| Test ID | Test Scenario | Execution Details & Lifecycle | Expected Result & Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **TC-23-01** | **Disconnected Pending Subscription Queue** | Component mounts and calls `subscribe(topic, cb)` while `client.connected === false`. Later, `onConnect` fires. | Subscription is queued in `pendingQueue`. `client.subscribe()` is NOT called prematurely. When `onConnect` fires, pending queue flushes and invokes `client.subscribe()` exactly once. |
| **TC-23-02** | **Early Unmount from Pending Queue** | Component subscribes while disconnected, but unmounts BEFORE `onConnect` fires. | Unsubscribe cleans up the callback from `pendingQueue`. When `onConnect` subsequently fires, zero broker subscriptions are opened (no zombie subscriptions). |
| **TC-23-03** | **Multiple Consumers on Same Topic** | Component A and Component B subscribe to `/topic/restaurants/1/alerts`. | STOMP mock records **exactly 1 `client.subscribe()` call**. When an alert frame arrives, both Component A and Component B callbacks are invoked. |
| **TC-23-04** | **Partial vs Complete Unmount Cleanup** | Components A and B are subscribed. Component A unmounts. Later, Component B unmounts. | After Component A unmounts, broker subscription **remains active** (`callbacks.size == 1`). After Component B unmounts, STOMP broker `subscription.unsubscribe()` is invoked and topic is removed from registry. |
| **TC-23-05** | **Callback Identity Stability (`useRef`)** | Component re-renders with a newly allocated arrow function callback `() => handleAlert(data)`. | Hook `useWebSocketSubscription` updates `callbackRef.current` without calling `wsService.subscribe()` again. STOMP broker subscription count remains exactly 1. Incoming frames execute the latest callback. |
| **TC-23-06** | **React 18 StrictMode Double-Mounting** | Simulate StrictMode: Component mounts -> unmounts -> remounts immediately. | Reference-counted registry decrements and cleanly re-registers. Broker subscription count does **not** double. The remounted component successfully receives subsequent STOMP messages. |
| **TC-23-07** | **Automatic Reconnect Resubscription** | Active subscriptions exist in registry. STOMP connection drops (`onWebSocketClose`) and reconnects (`onConnect`). | `WebSocketService` iterates active topics in `topicRegistry` and cleanly re-subscribes each topic on the new STOMP client without creating duplicate topic entries or dropping registered callbacks. |
| **TC-23-08** | **Explicit Logout Teardown & Purge** | User logs out. `wsService.disconnect()` is called. | All active STOMP broker subscriptions are immediately unsubscribed. STOMP client is deactivated (`client.deactivate()`). `topicRegistry` and pending subscription queues are completely cleared. |
| **TC-23-09** | **Tenant Switching Isolation** | User logs out of Tenant 1 (`restaurantId: 1`) and logs into Tenant 2 (`restaurantId: 2`). | Subscriptions to `/topic/restaurants/1/...` are completely dismantled during logout. New subscriptions to `/topic/restaurants/2/...` are established on login. Zero cross-tenant topic leaks. |
| **TC-23-10** | **Zero Hardcoded Restaurant IDs** | Static analysis / RTL render across all 7 operational pages (`FloorPlan`, `AlertCenter`, `KitchenKds`, `WaiterDashboard`, `CleaningTasks`, `CamerasZones`, `Navbar`). | All subscription topics dynamically interpolate `user.restaurantId` from `useAuth()`. Zero hardcoded `/topic/restaurants/1/...` occurrences. |

#### 2.4.2 Backend STOMP Channel Authorization Tests (`WebSocketIntegrationProductionTest.java`)
| Test ID | Test Scenario | Execution Details | Expected Result & Assertion |
| :--- | :--- | :--- | :--- |
| **TC-23-11** | `testStrictCrossTenantSubscriptionRejected` | Client authenticated with JWT for Restaurant 1 sends `SUBSCRIBE` frame for `/topic/restaurants/2/alerts`. | Backend `ChannelInterceptor` intercepts frame, evaluates `TenantContext` against path `{id}`, and rejects subscription with `AccessDeniedException`. Client receives STOMP ERROR frame. |
| **TC-23-12** | `testClientDirectTopicPublishRejected` | Client sends `SEND` frame directly to `/topic/restaurants/1/alerts` (bypassing `@MessageMapping`). | Backend interceptor rejects frame; clients are forbidden from broadcasting directly to broker topics. |
| **TC-23-13** | `testCrossTenantAppSendRejected` | Client for Restaurant 1 sends `SEND` frame to `/app/restaurants/2/ping`. | Backend interceptor rejects with `AccessDeniedException`. |
| **TC-23-14** | `testValidTenantSubscriptionAccepted` | Client for Restaurant 1 sends `SUBSCRIBE` frame for `/topic/restaurants/1/alerts`. | Interceptor validates tenant match; subscription succeeds; client receives test broadcast frame. |

---

## 3. Test Execution Matrix & Tooling

| Test Level | Tooling | Target Directory | Execution Command | Target Duration |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend Unit & RTL** | Vitest, JSDOM, React Testing Library | `frontend/` | `npm run test` | ~15 seconds |
| **Frontend Coverage** | Vitest v8 Coverage Provider | `frontend/` | `npm run test:coverage` | ~18 seconds |
| **Edge AI ML & NLP** | Pytest | `edge-ai/` | `pytest tests/ -v` | ~5 seconds |
| **Maven CVE Audit** | OWASP Dependency-Check | `Backend/` | `mvn org.owasp:dependency-check-maven:check` | ~30 seconds |
| **Python CVE Audit** | PyPA pip-audit | `edge-ai/` | `pip-audit -r requirements.txt --strict` | ~10 seconds |
| **npm CVE Audit** | npm audit | `frontend/` | `npm audit` / `npm audit --omit=dev` | ~5 seconds |
| **Backend Integration** | JUnit 5, Spring Boot Test, Surefire | `Backend/` | `mvn clean test` | ~55 seconds |
| **Frontend Production Build** | Vite, Rollup, PostCSS | `frontend/` | `npm run build` | ~8 seconds |

---

## 4. Acceptance Criteria & Pass/Fail Gates

| Checkpoint | Mandatory Acceptance Criteria | Blocking Condition |
| :--- | :--- | :--- |
| **Gate 1: BUG-021** (Phase 1) | - Vitest + RTL + JSDOM installed and executing cleanly.<br>- Baseline tests for Login, QR Portal, Analytics, and API pass with 100% pass rate (0 failed, 0 skipped).<br>- Execution status and code coverage reported in independent tables.<br>- Critical operational services achieve $\ge 80\%$ line coverage. | Any test failure, skipped test, runner crash, or critical path coverage $< 80\%$. |
| **Gate 2: BUG-020** (Phase 2) | - All Edge AI tests in `test_forecast.py` and `test_sentiment.py` pass.<br>- Minimum sample threshold $N_{\text{valid}} \ge 12$ enforced; domain prior fallback for $N < 12$ verified.<br>- Outlier clipping, near-zero IQR, and gap interpolation verified.<br>- Contextual negation verified: `"not good"` $\rightarrow$ NEGATIVE, `"not bad"` $\rightarrow$ NEUTRAL/POSITIVE.<br>- Model names `traffic-seasonal-empirical-1.1.0` and `sentiment-lexicon-valence-1.1.0` verified without Gaussian claims. | Any failed test, negation inversion error, or presence of false `lgbm` / `indicbert` claims. |
| **Gate 3: BUG-022** (Phase 3) | - Upgraded to `vite@^6.4.6` and `@vitejs/plugin-react@^4.3.4`; lockfile verifies `esbuild >= 0.25.0`; `npm audit` reports 0 high/critical CVEs.<br>- Real vulnerability scans (OWASP Dependency-Check & pip-audit) report 0 vulnerabilities with CVSS $\ge 7.0$.<br>- `spring.jpa.open-in-view: false` configured; all 5 endpoints pass populated and empty dataset tests with zero `LazyInitializationException` and bounded queries.<br>- Complete `DEPENDENCY_AUDIT_REPORT.md` generated. | Any high/critical npm/Maven/Python CVE remaining, or any `LazyInitializationException` in backend tests. |
| **Gate 4: BUG-023** (Phase 4) | - All frontend WebSocket tests pass (disconnected queue, callback identity via `useRef`, StrictMode, reconnects, logout teardown, tenant switching, zero hardcoding).<br>- All backend STOMP destination authorization tests pass on actual `ChannelInterceptor`.<br>- Exactly 1 broker subscription per unique topic; broker `unsubscribe()` called when last subscriber unmounts. | Disconnected subscription drop, callback reference thrashing, broker leak, or cross-tenant topic collision. |
| **Gate 5: Full Regression** (Phase 5) | - Full `mvn clean test` passes with 100% pass rate.<br>- Clean `npm run test` with 100% pass rate.<br>- Clean `npm run build` (0 errors, 0 warnings).<br>- Working tree clean on `feature/production-hardening`.<br>- `main` untouched at `171171b`. | Any regression in backend test suite, frontend tests, or production bundle. |

---

## 5. Strict Stop Gate & Hold Notice

**STRICT HOLD IN EFFECT:** No source code, configuration, dependencies, package manifests, lockfiles, migrations, or application behavior have been modified. Execution of tests and implementation will begin only upon formal review and authorization.
