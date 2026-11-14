# Phase 3: Static Code Quality Audit Report
**RESTROVISION Operations & Edge Vision Platform**  
**Audit Document**: `PROJECT_AUDIT_REPORT/03_STATIC_CODE_QUALITY_REPORT.md`  
**Date of Audit**: 2026-11-14  
**Audit Status**: COMPLETED  

---

## 1. Executive Summary

Static code analysis was performed across all Java, JavaScript/JSX, Python, and SQL source files.
Key findings include **17 empty catch blocks silently swallowing API errors**, **two monolithic God components exceeding 750+ lines**, **insecure client-side storage of persistent tokens in `localStorage`**, and **missing catch-all exception handlers in the backend REST tier**.

---

## 2. Detailed Static Quality Findings

---

### Finding CODE-001 (HIGH): Widespread Swallowed Exceptions Across Frontend (17 Empty Catch Blocks)

- **Affected Files**:
  - `frontend/src/context/AuthContext.jsx` (Lines 77, 85, 145)
  - `frontend/src/pages/AlertCenter.jsx` (Lines 53, 62, 72)
  - `frontend/src/pages/FloorPlan.jsx` (Lines 89, 100, 127)
  - `frontend/src/pages/KitchenKds.jsx` (Lines 94, 101, 108)
  - `frontend/src/pages/WaiterDashboard.jsx` (Lines 62, 69, 76)
  - `frontend/src/pages/CleaningTasks.jsx` (Lines 44, 51)
  - `frontend/src/pages/CustomerQrPortal.jsx` (Line 57)
  - `frontend/src/services/api.js` (Line 107)
- **Implementation Evidence**:
  Pattern replicated across almost all frontend mutation handlers:
  ```javascript
  // Example from KitchenKds.jsx Line 94:
  const handleStartOrder = async (orderId) => {
    try {
      await api.startKitchenOrder(orderId);
    } catch (_) {} // Empty catch block silently swallows error!
  };
  ```
- **Why It Is Problematic**:
  When a network drop, 401 unauthorized, 403 forbidden, or 500 server error occurs, the exception is completely swallowed. The frontend displays no error toast, alert banner, or notification. The user is misled into believing the operation succeeded while the backend rejected it.
- **Real-World Impact**: Operations staff (chefs marking orders ready, waiters acknowledging calls) will believe their commands were accepted when the backend actually rejected them, leading to lost tickets and service delays.
- **Severity**: **HIGH** (CWE-391: Unchecked Error Condition).
- **Recommended Solution**: Implement an error notification toast system or dispatch errors to an error boundary/state handler so staff receive immediate visual failure notices.
- **Verification Method**: Grep for `catch\s*\([_\w]*\)\s*\{\s*\}` in `frontend/src`.

---

### Finding CODE-002 (MEDIUM): Monolithic God Components (`Analytics.jsx` & `FloorPlan.jsx`)

- **Affected Files**:
  - `frontend/src/pages/Analytics.jsx` (1,015 lines of code)
  - `frontend/src/pages/FloorPlan.jsx` (758 lines of code)
- **Implementation Evidence**:
  - `Analytics.jsx` handles state, network fetching, daily/weekly/monthly dataset fallbacks, P&L calculations, custom SVG stacked bars, hourly traffic distributions, shift comparisons, AI LightGBM forecast rendering, Menu Engineering table formatting, and customer NLP sentiment reviews with multiple filter buttons in a single monolithic file.
  - `FloorPlan.jsx` contains SVG floor layouts, table rendering, party seating modals, manual status overrides, waiter reassignment selectors, and active alert pill rendering in one component.
- **Why It Is Problematic**:
  Violates the Single Responsibility Principle. Components of this scale create severe cognitive overhead, prevent granular unit testing, and drastically increase regression risks when minor styling or logic changes are introduced.
- **Real-World Impact**: High maintenance costs, brittle pull requests, and slow development velocity.
- **Severity**: **MEDIUM** (Maintainability & Clean Architecture).
- **Recommended Solution**: Decompose into focused subcomponents:
  - `Analytics.jsx` $\rightarrow$ `KpiRibbon.jsx`, `ProfitLossBreakdown.jsx`, `CrowdCurveChart.jsx`, `MenuEngineeringTable.jsx`, `SentimentStream.jsx`.
  - `FloorPlan.jsx` $\rightarrow$ `TableCard.jsx`, `SeatPartyModal.jsx`, `WaiterAssignModal.jsx`.
- **Verification Method**: Line count inspection (`wc -l`).

---

### Finding CODE-003 (MEDIUM): Sensitive JWT Tokens Stored in Browser `localStorage`

- **Affected Files**:
  - `frontend/src/services/api.js` (Lines 4, 9, 16, 21)
  - `frontend/src/context/AuthContext.jsx` (Lines 76, 142)
- **Implementation Evidence**:
  ```javascript
  localStorage.setItem('restrovision_token', token);
  localStorage.setItem('restrovision_refresh_token', refreshToken);
  ```
- **Why It Is Problematic**:
  `localStorage` is completely accessible to any JavaScript executing in the browser context. In the event of a Cross-Site Scripting (XSS) flaw in any third-party dependency, both access tokens and long-lived 7-day refresh tokens can be extracted silently.
- **Real-World Impact**: Persistent session hijacking without user knowledge.
- **Severity**: **MEDIUM** (OWASP A07: Identification and Authentication Failures).
- **Recommended Solution**: Transition refresh tokens to `HttpOnly`, `Secure`, `SameSite=Strict` cookies, keeping access tokens in memory (React context/state).
- **Verification Method**: Inspect `api.js` lines 4-25.

---

### Finding CODE-004 (MEDIUM): Missing Fallback Catch-All Exception Handler in Backend

- **Affected File**: `Backend/src/main/java/com/restrovision/controller/GlobalExceptionHandler.java` (Lines 1–69)
- **Implementation Evidence**:
  `GlobalExceptionHandler.java` handles `BadCredentialsException`, `AccessDeniedException`, `IllegalArgumentException`, `IllegalStateException`, `MethodArgumentNotValidException`, and `DataIntegrityViolationException`.
  **It does NOT include `@ExceptionHandler(Exception.class)`**.
- **Why It Is Problematic**:
  Any unforeseen runtime exception (such as `NullPointerException`, `OptimisticLockException`, or database connection drops) bypasses the standardized RFC 7807 `ProblemDetail` handler and is caught by Spring's default Whitelabel Error Controller, potentially exposing server internals, class names, or returning non-JSON HTML error pages to REST clients.
- **Real-World Impact**: Client parsing crashes and potential information disclosure during unexpected backend errors.
- **Severity**: **MEDIUM** (CWE-209: Generation of Error Message Containing Sensitive Information).
- **Recommended Solution**: Add a general `@ExceptionHandler(Exception.class)` that logs the full stack trace internally and returns a clean, sanitized `ProblemDetail` with status 500.
- **Verification Method**: Inspect `GlobalExceptionHandler.java`.

---

### Finding CODE-005 (LOW): Silent Empty Catch in Backend WebSocket Configuration

- **Affected File**: `Backend/src/main/java/com/restrovision/config/WebSocketConfig.java` (Line 101)
- **Implementation Evidence**:
  ```java
  try {
      Long targetRestId = Long.parseLong(parts[3]);
      // ...
  } catch (NumberFormatException ignored) {}
  ```
- **Why It Is Problematic**:
  If a malformed topic URI is passed (e.g. `/topic/restaurants/abc/tables`), the parsing error is silently swallowed and execution continues rather than rejecting the malformed subscription.
- **Real-World Impact**: Inconsistent subscription handling for invalid inputs.
- **Severity**: **LOW** (Code Hygiene).
- **Recommended Solution**: Log a debug warning and explicitly reject the frame.
- **Verification Method**: Inspect `WebSocketConfig.java` line 101.
