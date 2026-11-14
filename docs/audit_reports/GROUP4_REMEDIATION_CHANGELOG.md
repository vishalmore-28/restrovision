# RESTROVISION v2.0 — Remediation Group 4 Changelog

**Target Branch:** `feature/production-hardening`  
**Base Commit:** `f7c4752`  
**Latest Hardening Commit:** `3562604`  
**Main Branch Status:** Clean & untouched at `171171b`  
**Scope:** Remediation Group 4 (BUG-014, BUG-015, BUG-016, BUG-018, BUG-019)

---

## 1. Executive Summary

Remediation Group 4 addresses five critical stability, security, architectural, accessibility, and responsive UI defects across the RESTROVISION v2.0 stack:
- **BUG-014 (Medium / Security):** Elimination of browser `localStorage` JWT token storage in favor of hardened `HttpOnly`, `SameSite=Lax` cookies with strict anti-CSRF token verification on state-changing requests.
- **BUG-015 (Medium / Architecture):** Modular decomposition of the 1,015-line `Analytics.jsx` God component into cohesive domain tabs and custom hooks.
- **BUG-016 (Medium / Database):** Implementation of JPA optimistic locking (`@Version`) on `TableSession`, `StaffTask`, and `ServiceRequest`, complete with Flyway V2 migration and HTTP 409 Conflict handling.
- **BUG-018 (Medium / Accessibility):** Full resolution of WCAG 2.2 AA accessibility violations across login and customer QR portals (explicit form labels, accessible rating names, ARIA state bindings, focus rings).
- **BUG-019 (Low / UI/UX):** Elimination of mobile horizontal viewport overflow at 375px and 320px screens.

All modifications strictly preserve existing API contracts, WebSocket protocols, and multi-tenant security guarantees.

---

## 2. Detailed File Modifications

### A. BUG-014: Sensitive JWT Storage in LocalStorage & CSRF Protection
- **`Backend/src/main/resources/application.yml`**:
  - Added configurable cookie security flag: `restrovision.security.cookie.secure: ${RESTROVISION_COOKIE_SECURE:false}`.
- **`Backend/src/main/java/com/restrovision/controller/AuthController.java`**:
  - Added `setAuthCookies(HttpServletResponse, String accessToken, String refreshToken, String csrfToken)` setting `HttpOnly`, `SameSite=Lax`, `Path=/` cookies for `restro_access_token` and `restro_refresh_token`.
  - Sets non-HttpOnly readable `restro_csrf_token` cookie for single-page application anti-CSRF synchronization.
  - Added `clearAuthCookies(HttpServletResponse)` on `/api/v1/auth/logout` setting `Max-Age=0`.
  - Updated `/api/v1/auth/refresh` to extract the refresh token from `restro_refresh_token` cookie with backward-compatible request body fallback.
- **`Backend/src/main/java/com/restrovision/security/JwtAuthFilter.java`**:
  - Implemented dual-token resolution: Bearer `Authorization` header takes precedence for REST/Edge API clients; falls back to `restro_access_token` cookie for browser sessions.
  - Implemented state-changing request detection (`POST`, `PUT`, `DELETE`, `PATCH`).
  - Enforced CSRF validation on cookie-authenticated mutations: validates `X-CSRF-TOKEN` or `X-XSRF-TOKEN` header against `restro_csrf_token` cookie.
  - Exempts Bearer-authenticated requests and public endpoints (`/api/v1/auth/login`, `/api/v1/public/**`).
- **`Backend/src/main/java/com/restrovision/security/SecurityConfig.java`**:
  - Configured CORS headers: added `X-CSRF-TOKEN`, `X-XSRF-TOKEN`, `X-Requested-With`, `Set-Cookie`.
  - Permitted unauthenticated access to `/api/v1/auth/logout`.
- **`Backend/src/main/java/com/restrovision/config/WebSocketConfig.java`**:
  - Enhanced STOMP handshake handler to extract `restro_access_token` cookie into session attributes when Bearer header is absent.
  - Validates cookie-supplied JWT during STOMP `CONNECT`.
- **`frontend/src/services/api.js`**:
  - Removed all `localStorage.getItem("token")`, `localStorage.setItem("token")`, and `localStorage.removeItem("token")`.
  - Set `credentials: 'include'` on all `fetch` requests so browser cookies are automatically transmitted.
  - Added automated attachment of `X-CSRF-TOKEN` header extracted from `restro_csrf_token` cookie on all mutating requests.
  - Implemented seamless 401 token refresh queue via cookie authentication.
- **`frontend/src/context/AuthContext.jsx`**:
  - Removed token persistence to `localStorage`. User profile metadata stored exclusively in React state.
  - On mount, validates session via `/api/v1/auth/me` without reading `localStorage`.

### B. BUG-016: Missing Optimistic Locking on Mutable Entities
- **`Backend/src/main/java/com/restrovision/domain/TableSession.java`**:
  - Added `@Version @Column(nullable = false) private Long version = 0L;` with getters and setters.
- **`Backend/src/main/java/com/restrovision/domain/StaffTask.java`**:
  - Added `@Version @Column(nullable = false) private Long version = 0L;` with getters and setters.
- **`Backend/src/main/java/com/restrovision/domain/ServiceRequest.java`**:
  - Added `@Version @Column(nullable = false) private Long version = 0L;` with getters and setters.
- **`Backend/src/main/resources/db/migration/V2__add_optimistic_locking_version.sql`**:
  - Created Flyway migration:
    ```sql
    ALTER TABLE table_sessions ADD version NUMBER DEFAULT 0 NOT NULL;
    ALTER TABLE staff_tasks ADD version NUMBER DEFAULT 0 NOT NULL;
    ALTER TABLE service_requests ADD version NUMBER DEFAULT 0 NOT NULL;
    ```
- **`Backend/src/main/java/com/restrovision/exception/GlobalExceptionHandler.java`**:
  - Added `@ExceptionHandler({OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})` returning HTTP 409 Conflict with RFC 7807 ProblemDetail (`type: "urn:restrovision:error:optimistic-lock-conflict"`).

### C. BUG-015: Modular Decomposition of `Analytics.jsx`
- Monolithic `frontend/src/pages/Analytics.jsx` decomposed from 1,015 lines to 46 lines.
- Extracted into cohesive domain components under `frontend/src/pages/analytics/`:
  - **`useAnalyticsData.js`**: Custom hook encapsulating data loading, fallback data, and date-range filtering.
  - **`AnalyticsHeader.jsx`**: Page title, subtitle, Edge AI model status badge, and timeframe selector.
  - **`DailyOverviewTab.jsx`**: Top KPI summary cards, financial P&L summary, and visual stacked cost breakdown bar.
  - **`CrowdForecastTab.jsx`**: Hourly dining density chart, shift highlights, and LightGBM crowd forecast engine card.
  - **`MenuEngineeringTab.jsx`**: Dish contribution margin matrix and popularity categorizer.
  - **`GuestReviewsTab.jsx`**: Rating breakdown, star rating distribution histogram, and IndicBERT-analyzed guest review feed.

### D. BUG-018: WCAG 2.2 AA Accessibility Violations
- **`frontend/src/pages/Login.jsx`**:
  - Added explicit `<label htmlFor="login-email">` and `<label htmlFor="login-password">` associated with input IDs.
  - Added `aria-required="true"`, `aria-invalid`, `aria-describedby="login-error-alert"`.
  - Added `role="alert"` on error banners.
  - Added `aria-pressed` on role selector buttons and visible focus rings (`focus-visible:ring-2 focus-visible:ring-emerald-500`).
- **`frontend/src/pages/CustomerQrPortal.jsx`**:
  - Added `aria-label="Rate {star} out of 5 stars"` and `aria-pressed` on star rating buttons.
  - Added explicit `<label htmlFor="feedback-comment">` for feedback textarea.
  - Added `role="status"` on submission confirmation and `role="alert"` on submission errors.
  - Added visible focus ring outlines (`focus-visible:ring-2 focus-visible:ring-amber-500`).

### E. BUG-019: Mobile Horizontal Viewport Overflow at 375px
- **`frontend/src/index.css`**:
  - Added global `html, body { max-width: 100%; overflow-x: hidden; box-sizing: border-box; }` and universal box sizing `*, *:before, *:after { box-sizing: border-box; }`.
- **`frontend/src/components/Navbar.jsx`**:
  - Responsive container padding `px-2.5 sm:px-4`, added `truncate`, `min-w-0`, and `box-border overflow-hidden`.
- **`frontend/src/App.jsx`**:
  - Main container padding refined to `p-3 sm:p-4`, `overflow-x-hidden`, and `box-border`.
- **`frontend/src/pages/FloorPlan.jsx`**:
  - Header summary badges wrapped with `flex-wrap gap-2 sm:gap-4`.
- **`frontend/src/pages/CustomerQrPortal.jsx`**:
  - Mockup frame constrained to `max-w-[340px] sm:max-w-sm box-border`, and table selector pill group made scrollable with `overflow-x-auto`.

---

## 3. Git Commit History for Group 4

| Commit SHA | Commit Summary | Scope |
| :--- | :--- | :--- |
| `174ee15` | `fix(security): implement HttpOnly JWT cookies and CSRF protection (BUG-014)` | BUG-014 |
| `622ed0b` | `fix(database): add optimistic locking versioning and 409 Conflict handling (BUG-016)` | BUG-016 |
| `97c6e83` | `refactor(frontend): decompose Analytics God component into modular domain tabs (BUG-015)` | BUG-015 |
| `df5752c` | `fix(accessibility): resolve WCAG 2.2 AA form label and rating button violations (BUG-018)` | BUG-018 |
| `3bd7cc4` | `fix(ui): eliminate mobile horizontal scroll overflow at 375px viewport (BUG-019)` | BUG-019 |
| `3562604` | `test: align integration test fixtures with Flyway V2 and optimistic locking (BUG-016)` | BUG-016 |

`main` branch remains untouched at `171171b`.
