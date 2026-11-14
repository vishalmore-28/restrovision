# RESTROVISION v2.0 — Group 5 Phase 1 Test Results

**Target Branch:** `feature/production-hardening`  
**Base Commit:** `6bcebf3`  
**Test Framework:** Vitest 1.6.1 + React Testing Library 14.3.1 + JSDOM 24.0.0  
**Test Runner Command:** `npm run test` (`vitest run`)  
**Execution Timestamp:** November 14, 2026 — 22:06:46 +05:30  
**Pass Rate:** 100.0%  

---

## 1. Test Execution Metrics Summary

> [!IMPORTANT]
> Test execution metrics are strictly separated from code coverage metrics. These metrics represent pure test suite execution outcomes without conflation with code statement or line coverage.

| Metric | Metric Count | Percentage |
| :--- | :--- | :--- |
| **Total Test Suites Discovered** | 4 | 100.0% |
| **Total Test Suites Executed** | 4 | 100.0% |
| **Total Test Suites Passed** | 4 | 100.0% |
| **Total Test Suites Failed** | 0 | 0.0% |
| **Total Individual Tests Discovered** | 40 | 100.0% |
| **Total Individual Tests Executed** | 40 | 100.0% |
| **Total Individual Tests Passed** | 40 | 100.0% |
| **Total Individual Tests Failed** | 0 | 0.0% |
| **Total Individual Tests Skipped** | 0 | 0.0% |
| **Total Execution Duration** | 4.12s | — |

---

## 2. Test Suite Breakdown by File

### 2.1 API Service & Interceptors (`src/__tests__/api.test.js`)
- **Status:** PASSED (16 / 16 passed, 0 failed, 0 skipped)
- **Suite Duration:** ~48ms

| # | Test Case Description | Result | Duration |
| :--- | :--- | :--- | :--- |
| 1 | CSRF Token Extraction: returns empty string when no restro_csrf_token cookie is present | PASS | 4ms |
| 2 | CSRF Token Extraction: extracts and decodes restro_csrf_token correctly from document.cookie | PASS | 2ms |
| 3 | CSRF Token Extraction: extracts token when multiple cookies exist in document.cookie | PASS | 2ms |
| 4 | CSRF Token Extraction: decodes URI encoded characters in restro_csrf_token | PASS | 2ms |
| 5 | Legacy Token Stubs: getAuthToken returns empty string (isolated in HttpOnly cookies) | PASS | 1ms |
| 6 | Legacy Token Stubs: getRefreshToken returns empty string (isolated in HttpOnly cookies) | PASS | 1ms |
| 7 | Legacy Token Stubs: setAuthToken purges legacy localStorage token safely | PASS | 2ms |
| 8 | Legacy Token Stubs: setRefreshToken purges legacy localStorage refresh token safely | PASS | 2ms |
| 9 | Request Dispatching: attaches X-CSRF-TOKEN header and credentials:include on mutating POST requests | PASS | 4ms |
| 10 | Request Dispatching: attaches X-CSRF-TOKEN on PUT requests (e.g. overrideTableStatus) | PASS | 3ms |
| 11 | Request Dispatching: does NOT attach X-CSRF-TOKEN on non-mutating GET requests | PASS | 3ms |
| 12 | Request Dispatching: dispatches public QR portal requests with correct endpoints | PASS | 3ms |
| 13 | Error Handling: throws error with detail message from JSON response when request fails | PASS | 3ms |
| 14 | Error Handling: falls back to status code and statusText when error body is not JSON | PASS | 3ms |
| 15 | Automatic Refresh: retries original request after successful token refresh on 401 | PASS | 8ms |
| 16 | Automatic Refresh: does not trigger automatic refresh loop when /auth/login returns 401 | PASS | 4ms |

---

### 2.2 Login Component (`src/__tests__/Login.test.jsx`)
- **Status:** PASSED (8 / 8 passed, 0 failed, 0 skipped)
- **Suite Duration:** ~1,120ms

| # | Test Case Description | Result | Duration |
| :--- | :--- | :--- | :--- |
| 1 | Form Accessibility: renders form inputs associated with accessible label text | PASS | 62ms |
| 2 | Form Accessibility: renders role portal selector buttons with aria-pressed and accessible labels | PASS | 38ms |
| 3 | Form Accessibility: renders theme toggle with accessible label | PASS | 25ms |
| 4 | Role Selection: updates email and password fields when clicking a different role portal | PASS | 184ms |
| 5 | Submission Flow: dispatches login call with entered credentials upon submit | PASS | 192ms |
| 6 | Submission Flow: disables submit button and displays authenticating spinner/text during submission | PASS | 215ms |
| 7 | Security Alert: displays error alert with role="alert" and aria-live="assertive" when login fails | PASS | 198ms |
| 8 | Security Alert: clears error alert when switching to a different role portal | PASS | 206ms |

---

### 2.3 Customer QR Portal (`src/__tests__/CustomerQrPortal.test.jsx`)
- **Status:** PASSED (9 / 9 passed, 0 failed, 0 skipped)
- **Suite Duration:** ~1,612ms

| # | Test Case Description | Result | Duration |
| :--- | :--- | :--- | :--- |
| 1 | Initial Rendering: fetches initial table QR status on mount and renders table code | PASS | 84ms |
| 2 | Table Switching: switches table token and refetches status when selecting a table button | PASS | 172ms |
| 3 | Quick Assistance: dispatches call waiter request and displays status message banner | PASS | 165ms |
| 4 | Quick Assistance: dispatches water refill request when clicking water refill button | PASS | 158ms |
| 5 | Quick Assistance: dispatches bill request when clicking request bill button | PASS | 149ms |
| 6 | Quick Assistance: handles waiter call failure gracefully and shows error in status banner | PASS | 162ms |
| 7 | Feedback Form: renders star rating buttons with accessible names and tracks pressed state | PASS | 185ms |
| 8 | Feedback Form: submits guest feedback successfully and displays success confirmation banner | PASS | 284ms |
| 9 | Feedback Form: displays error alert with role="alert" when feedback submission fails | PASS | 253ms |

---

### 2.4 Decomposed Analytics Dashboard (`src/__tests__/Analytics.test.jsx`)
- **Status:** PASSED (7 / 7 passed, 0 failed, 0 skipped)
- **Suite Duration:** ~1,803ms

| # | Test Case Description | Result | Duration |
| :--- | :--- | :--- | :--- |
| 1 | Decomposed Rendering: renders the header with Edge AI telemetry badge | PASS | 72ms |
| 2 | Decomposed Rendering: renders the DailyOverviewTab with KPI ribbon and P&L financial metrics | PASS | 114ms |
| 3 | Decomposed Rendering: renders CrowdForecastTab with peak hours and AI footfall quantile projections | PASS | 98ms |
| 4 | Decomposed Rendering: renders MenuEngineeringTab with dish profitability | PASS | 86ms |
| 5 | Decomposed Rendering: renders GuestReviewsTab with rating scorecards and review feed | PASS | 92ms |
| 6 | Timeframe Navigation: switches between Daily, Weekly, and Monthly timeframes and refetches API overview | PASS | 682ms |
| 7 | Sentiment Filtering: filters review stream by sentiment pills (Positive, Neutral, Critical) | PASS | 659ms |

---

## 3. Raw Vitest Execution Terminal Summary

```
> restrovision-frontend@2.0.0 test
> vitest run

 RUN  v1.6.1 D:/RESTROVISION/frontend

 ✓ src/__tests__/api.test.js  (16 tests) 48ms
 ✓ src/__tests__/Login.test.jsx  (8 tests) 1120ms
 ✓ src/__tests__/CustomerQrPortal.test.jsx  (9 tests) 1612ms
 ✓ src/__tests__/Analytics.test.jsx  (7 tests) 1803ms

 Test Files  4 passed (4)
      Tests  40 passed (40)
   Start at  22:06:46
   Duration  4.12s (transform 470ms, setup 1.74s, collect 1.42s, tests 4.46s, environment 3.19s, prepare 893ms)
```
