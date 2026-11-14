# RESTROVISION v2.0 — Remediation Group 3 Changelog
**Remediation Phase**: Group 3 (Security, Robustness & Code Quality)  
**Target Branch**: `feature/production-hardening`  
**Date**: 2026-11-14  
**Status**: COMPLETED & VERIFIED  

---

## 1. Summary of Changes

Remediation Group 3 addresses 4 critical vulnerabilities and quality defects:
1. **BUG-008**: Authentication login rate limiting (brute force protection, dual-dimension sliding window, account lockout resistance, no enumeration).
2. **BUG-009**: Stored XSS prevention in customer feedback comments (plain-text normalization, tag & protocol stripping, multilingual Unicode and punctuation preservation, zero double-encoding).
3. **BUG-010**: Frontend empty catch block elimination (remediated 17 silent swallow locations, added visible user error feedback and non-disruptive diagnostic logging).
4. **BUG-013**: Customer feedback flooding protection (in-memory rate limiter, 1000-character payload bound, 15s table cooldown, 60s duplicate filter, session ownership and table status validation).

---

## 2. File-by-File Detailed Changelog

### Backend Changes

#### 1. `Backend/src/main/java/com/restrovision/security/LoginRateLimiterService.java` (New File)
- Implemented in-memory sliding-window limiter using thread-safe `ConcurrentHashMap<String, Deque<Instant>>`.
- Dual-dimension tracking:
  - Per-account window (configurable, default: 5 attempts per 60 seconds).
  - Per-IP window (configurable, default: 10 attempts per 60 seconds).
- IP failure counter does **not** clear on successful login to prevent alternation attacks (attacker alternating between 1 valid login and multiple invalid attempts).
- `resolveClientIp(HttpServletRequest)` checks `X-Forwarded-For` **only** when `request.getRemoteAddr()` matches trusted proxy list (`127.0.0.1, ::1`). Untrusted remote addresses cannot spoof client IP.
- Account normalization: `email.trim().toLowerCase(Locale.ROOT)` prevents case-variation bypass.
- Returns `RateLimitResult` containing remaining retry seconds.

#### 2. `Backend/src/main/java/com/restrovision/controller/AuthController.java` (Modified)
- Injected `LoginRateLimiterService`.
- Checks rate limits at entry of `POST /api/v1/auth/login` before attempting authentication.
- Returns RFC 7807 `ProblemDetail` with HTTP 429 Too Many Requests and `Retry-After: <seconds>` header if blocked.
- Records failure on `BadCredentialsException` or any other authentication failure.
- Returns uniform HTTP 429 response structure whether the account exists or not, defeating account enumeration.
- Resets account failure queue upon successful login, while keeping IP counter incremented.

#### 3. `Backend/src/main/java/com/restrovision/security/HtmlSanitizer.java` (New File)
- Implemented static `sanitizeToPlainText(String input)` method.
- Completely removes `<script>`, `<iframe>`, `<object>`, `<embed>`, `<style>`, HTML tags (`<... />`), event handlers (`onload=`, `onerror=`), and dangerous URI schemes (`javascript:`, `vbscript:`, `data:`).
- Preserves quotes (`"`, `'`), ampersands (`&`), newlines (`\n`), and multilingual Unicode (Devanagari / Marathi / Hindi).
- Avoids HTML entity double-encoding (does not convert `"` to `&quot;`), allowing natural database storage and safe React JSX text rendering.

#### 4. `Backend/src/main/java/com/restrovision/security/FeedbackRateLimiterService.java` (New File)
- In-memory process-local limiter enforcing flooding thresholds:
  - 10 submissions per 5 minutes per client IP.
  - 15-second cooldown per table.
  - 60-second duplicate submission rejection (identical comment content for same table).
- Thread-safe bounded memory storage with pruning.

#### 5. `Backend/src/main/java/com/restrovision/controller/PublicQrController.java` (Modified)
- Injected `FeedbackRateLimiterService` and `HtmlSanitizer`.
- In `POST /api/v1/public/feedback`:
  - Enforces rating range `1 <= rating <= 5`.
  - Verifies table existence and rejects tables with status `OUT_OF_SERVICE`.
  - Enforces session ownership: validates that `sessionId` (if provided) matches a session belonging to `tableId`.
  - Enforces 1,000 character maximum payload limit.
  - Passes comment through `HtmlSanitizer.sanitizeToPlainText()`.
  - Checks feedback rate limits (IP limit, table cooldown, duplicate filter), returning RFC 7807 429 `ProblemDetail` if breached.

#### 6. `Backend/src/main/resources/application.yml` (Modified)
- Added configuration properties:
  ```yaml
  restrovision:
    security:
      rate-limit:
        window-seconds: 60
        max-attempts-per-ip: 10
        max-attempts-per-account: 5
        trusted-proxies: 127.0.0.1, ::1
      feedback:
        max-comment-length: 1000
        ip-window-seconds: 300
        max-per-ip: 10
        table-cooldown-seconds: 15
        duplicate-window-seconds: 60
  ```

#### 7. `Backend/src/test/java/com/restrovision/RestrovisionApplicationTests.java` (Modified)
- Added 10 empirical tests:
  - `07o`: Login rate limiting by account (5 attempts -> 6th triggers 429).
  - `07p`: Login rate limiting uniform response without account enumeration.
  - `07q`: Successful login resets account counter, preserves IP counter.
  - `07q2`: Forwarded IP spoofing protection (untrusted remote addresses ignored).
  - `07r`: Stored XSS neutralization & plain-text normalization.
  - `07s`: Formatting, quotes, newlines, and Devanagari multilingual Unicode preservation.
  - `07t`: Feedback flooding payload length bound (1000 chars).
  - `07u`: Feedback flooding rejection for OUT_OF_SERVICE table.
  - `07v`: Feedback flooding session ID ownership validation.
  - `07w`: Feedback flooding table cooldown and duplicate rejection with 429.

---

### Frontend Changes

#### 8. `frontend/src/pages/AlertCenter.jsx` (Modified)
- Added `errorMsg` state and display banner.
- Wrapped `api.acknowledgeAlert`, `api.resolveAlert`, and `api.markFalseAlarm` in error handling that logs to `console.error` and presents inline error banners.
- Replaced polling `.catch(() => {})` with `console.warn`.

#### 9. `frontend/src/pages/FloorPlan.jsx` (Modified)
- Added `actionError` state and display banner.
- Wrapped `handleAttend`, `handleSeatParty`, and `handleOverrideStatus` in error handling displaying `flashError` banner and logging to `console.error`.
- Replaced polling `.catch(() => {})` with `console.warn`.

#### 10. `frontend/src/pages/KitchenKds.jsx` (Modified)
- Added `kdsError` state and display banner.
- Wrapped `handleStartOrder`, `handleMarkReady`, and `handleUpdateItemStatus` in error handling displaying `flashError` banner.
- Replaced polling `.catch(() => {})` with `console.warn`.

#### 11. `frontend/src/pages/WaiterDashboard.jsx` (Modified)
- Added `actionError` state and display banner.
- Wrapped `handleAcknowledgeRequest`, `handleCompleteRequest`, and `handleDeliverOrder` in error handling displaying `flashError` banner.
- Replaced polling `.catch(() => {})` with `console.warn`.

#### 12. `frontend/src/pages/CleaningTasks.jsx` (Modified)
- Added `errorMsg` state and display banner.
- Wrapped `handleStart` and `handleComplete` in error handling displaying `errorMsg` banner.
- Replaced polling `.catch(() => {})` with `console.warn`.

#### 13. `frontend/src/pages/CamerasZones.jsx` (Modified)
- Replaced `.catch(() => {})` on camera, zone, and event polling with `console.warn`.

#### 14. `frontend/src/pages/CustomerQrPortal.jsx` (Modified)
- Added `feedbackError` state and customer-facing error message banner.
- Replaced silent catch in `handleCall` with contextual error message.
- Replaced silent catch in `handleFeedbackSubmit` with `feedbackError` banner to surface 400 and 429 validation errors to patrons.

#### 15. `frontend/src/components/Navbar.jsx` (Modified)
- Replaced `.catch(() => {})` on alert polling with `console.warn`.

#### 16. `frontend/src/context/AuthContext.jsx` (Modified)
- Added diagnostic logging for session storage parsing and logout cleanup.
- Added sanitized error logging in `login` and `switchUserQuick` without exposing tokens or passwords.

#### 17. `frontend/src/services/api.js` (Modified)
- Replaced empty catch on non-JSON response body parsing with `console.warn`.
