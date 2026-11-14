# RESTROVISION Group 5 — Final Pre-Authorization Review & Evidence Report

**Document ID:** `EVID-GRP5-20261114-FINAL`  
**Target Branch:** `feature/production-hardening`  
**Base Commit (Group 4 Final Closure):** `58e84e1`  
**Protected Main Branch:** `main` at `171171b` (Untouched)  
**Status:** **STRICT HOLD — IMPLEMENTATION NOT AUTHORIZED (DOCUMENTATION-ONLY)**  
**Audit Timestamp:** 2026-11-14T20:48:00+05:30  

---

## 1. Executive Summary & Verification Scope

This document provides formal empirical evidence, technical specifications, and forensic classifications for the **Final Group 5 Pre-Authorization Review**:

1. **Wording Audit & Status Correction:** Verified and corrected all language across all deliverables. All Group 5 features (BUG-020, BUG-021, BUG-022, BUG-023) are explicitly defined as **proposed designs**, not completed implementations.
2. **Empirical Residual Mathematical Rigor:** Fully defined out-of-sample one-step-ahead residual generation, explained why residuals strictly begin at $e_3$, established sample thresholds ($N_{\text{valid}} \ge 12$, $M \ge 10$), formulated Hyndman-Fan Type 7 quantile interpolation, defined exact discrete IQR/MAD clipping bounds (including $\text{MAD} = 0$), and explained the appropriateness of the $IQR < 1.0$ threshold for discrete integer guest traffic.
3. **Forensic Classification of Untracked Paths:** Inspected and classified all six untracked directory and file paths on disk without modifying, deleting, or ignoring any path.
4. **Git Reference & Closure Commit Reconciliation:** Reconciled starting base commit `f7c4752` with closure commit `58e84e1` via linear Git history.
5. **Clean Working Tree Evidence:** Maintained complete application cleanliness (0 source files, 0 config files, 0 lockfiles modified) and tracked documentation in a clean documentation-only commit.

---

## 2. Mathematical Definition of Empirical Residual Quantile Forecaster

### 2.1 Out-of-Sample Rolling Baseline Forecast Generation
Let historical dining observations be chronologically ordered:
$$(t_1, y_1), (t_2, y_2), \dots, (t_N, y_N)$$
where $t_k \in [0, 23]$ represents the hour of the day and $y_k \in \mathbb{N}_0$ represents the discrete guest count.

To prevent in-sample overfitting and artificial variance underestimation, **each historical forecast $\hat{y}_k$ is strictly generated out-of-sample** using only data prior to observation $k$ ($y_1, \dots, y_{k-1}$):
$$\bar{y}_{<k} = \frac{1}{\min(k-1, w)} \sum_{j=1}^{\min(k-1, w)} y_{k-j} \quad (w = 6\text{ hours})$$
$$\hat{y}_k = \bar{y}_{<k} \times \frac{S(t_k)}{\frac{1}{\min(k-1, w)} \sum_{j=1}^{\min(k-1, w)} S(t_{k-j})}$$
where $S(h)$ is the domain time-of-day seasonal trading profile.
The out-of-sample residual error is:
$$e_k = y_k - \hat{y}_k$$

### 2.2 Why the Residual Series Starts at $e_3$
The out-of-sample residual sequence strictly begins at observation $k = 3$:
1. At index $k = 1$, zero prior historical observations exist; no historical baseline or rolling average can be formed.
2. At index $k = 2$, exactly one prior observation ($y_1$) exists. A single data point has zero degrees of freedom ($N - 1 = 0$), making it mathematically impossible to calculate moving average trends or residual variance.
3. At index $k = 3$, there are at least two distinct preceding observations ($y_1, y_2$). This satisfies the minimal mathematical requirement ($N \ge 2$) to estimate a local sample mean and dispersion.
Therefore, valid out-of-sample residuals exist exclusively for $k \in [3, N_{\text{valid}}]$, producing an array of $M = N_{\text{valid}} - 2$ residuals:
$$E = [e_3, e_4, \dots, e_N]$$

### 2.3 Minimum Valid Sample Requirement After Cleansing
- **Clean Sample Size:** Let $N_{\text{valid}}$ be the count of clean, non-null, chronologically valid hourly observations after removing corrupted records and imputing missing hours.
- **Threshold Rule:** The minimum sample requirement is **$N_{\text{valid}} \ge 12$ clean historical hours**, yielding at least $M \ge 10$ valid out-of-sample residuals.
- **Sparse-Data Fallback ($N_{\text{valid}} < 12$ or missing history):**
  If $N_{\text{valid}} < 12$, empirical quantile estimation is statistically unstable. The forecaster immediately falls back to the **calibrated domain seasonal prior**:
  $$P50 = \text{PriorMean}(H)$$
  $$P10 = \max\left(0, \text{round}(P50 \times 0.70)\right)$$
  $$P90 = \text{round}(P50 \times 1.30)$$
  Metadata returned: `{"fallback_applied": true, "sample_count": N_valid, "minimum_required": 12, "reason": "INSUFFICIENT_SAMPLE_SIZE"}`.

### 2.4 Hyndman-Fan Type 7 Quantile Calculation
Given the clamped and sorted residuals $e_{(1)}^* \le e_{(2)}^* \le \dots \le e_{(M)}^*$:
For any quantile $p \in \{0.10, 0.50, 0.90\}$, the continuous virtual index and linear interpolation weight are computed as:
$$h = (M - 1)p + 1$$
$$i = \lfloor h \rfloor \quad (\text{integer rank})$$
$$\gamma = h - i = (M - 1)p - (i - 1) \quad (\text{fractional weight})$$
$$Q(p) = e_{(i)}^* + \gamma (e_{(i+1)}^* - e_{(i)}^*)$$
Target hour $H$ predictions:
$$P50 = \max(0, \text{round}(\hat{y}_H + Q(0.50)))$$
$$P10 = \max(0, \text{round}(\hat{y}_H + Q(0.10)))$$
$$P90 = \max(P10, \text{round}(\hat{y}_H + Q(0.90)))$$
Monotonicity $0 \le P10 \le P50 \le P90$ is mathematically guaranteed.

### 2.5 Appropriateness of the $IQR < 1.0$ Threshold & Exact MAD Fallback
- **Domain Variable Context:** The forecast variable $y$ represents **hourly dining traffic (guest count)**, which is a **discrete non-negative integer** ($y \in \{0, 1, 2, \dots\}$).
- **Why $IQR < 1.0$ is Appropriate:** During low-volume shifts, late-night hours, or small dining areas, guest counts are frequently identical integers across consecutive hours (e.g. 0, 0, 0 or 2, 2, 2). When more than 50% of the sample residuals fall into the same integer bin, the 25th and 75th percentiles collapse to the same integer, resulting in $IQR = Q(0.75) - Q(0.25) < 1.0$ (often exactly 0.0). If an unadjusted $1.5 \times IQR$ were applied, the clipping bounds would collapse to a zero-width interval, falsely classifying any normal integer variation ($\pm 1$ guest) as an anomalous outlier.
- **Exact Outlier Clipping Formulation:**
  1. Standard IQR Case ($IQR \ge 1.0$):
     $$\text{Clip}_{\text{lower}} = Q(0.25) - 1.5 \times IQR$$
     $$\text{Clip}_{\text{upper}} = Q(0.75) + 1.5 \times IQR$$
  2. Fallback Dispersion Case ($IQR < 1.0$):
     Calculate median residual $\tilde{e} = Q(0.50)$ and $\text{MAD} = \text{median}(|e_i - \tilde{e}|)$.
     - **Subcase 2A ($\text{MAD} > 0$):**
       Scale factor: $S_{\text{eff}} = \max(1.0, 1.4826 \times \text{MAD})$.
       $$\text{Clip}_{\text{lower}} = \tilde{e} - 2.5 \times S_{\text{eff}}$$
       $$\text{Clip}_{\text{upper}} = \tilde{e} + 2.5 \times S_{\text{eff}}$$
     - **Subcase 2B ($\text{MAD} == 0$, $> 50\%$ identical quantized residuals):**
       When $\text{MAD} == 0$, the majority of observations are identical. The fallback scale is fixed to a minimum natural integer dining variance: $S_{\text{eff}} = 2.0$ ($\pm 2$ guests).
       $$\text{Clip}_{\text{lower}} = \tilde{e} - 2.5 \times 2.0 = \tilde{e} - 5.0$$
       $$\text{Clip}_{\text{upper}} = \tilde{e} + 2.5 \times 2.0 = \tilde{e} + 5.0$$
  Each residual is clamped: $e_i^* = \max(\text{Clip}_{\text{lower}}, \min(\text{Clip}_{\text{upper}}, e_i))$.

---

## 3. Forensic Inspection & Classification of Untracked Paths

Inspection of the six untracked paths present in `D:\RESTROVISION`:

```powershell
Get-ChildItem -Path ".agents", ".hallmark", "documant" -Force
Get-Item "skills-lock.json", "RESTROVISION_v2.0_FullStack.zip", "complete zip.zip"
```

| Path | Size / Type | Date Modified | Content Summary | Forensic Classification | Recommended Treatment |
| :--- | :---: | :---: | :--- | :--- | :--- |
| **`.agents/`** | Directory | 2026-11-14 19:29 | Subdirectory `skills/modern-web-guidance` | **Generated Content (Tooling)** | Auto-generated by the Antigravity skill manager. Retain untouched; do not modify or delete. |
| **`skills-lock.json`** | 302 B (JSON) | 2026-11-14 19:29 | Lockfile pinning `modern-web-guidance` from GitHub | **Generated Content (Tooling)** | Auto-generated companion to `.agents/`. Retain untouched; do not modify or delete. |
| **`.hallmark/`** | Directory | 2026-11-14 00:41 | Files: `before.json` (7.7 KB), `before.txt` (14.8 KB), `.gitignore` | **Generated Content (Evaluation)** | Pre-remediation audit and benchmark evaluation harness snapshot. Retain untouched; do not modify or delete. |
| **`RESTROVISION_v2.0_FullStack.zip`** | 24,202 B (Zip) | 2026-11-13 23:57 | Zip archive created prior to remediation work | **Intentional Project Content (Backup Archive)** | Project backup snapshot from start of engagement. Retain untouched. |
| **`complete zip.zip`** | 203.7 MB (Zip) | 2026-11-13 23:42 | Monolithic project backup archive | **Intentional Project Content (Full Backup Archive)** | Full-stack backup snapshot. Retain untouched. |
| **`documant/`** | Directory | 2026-11-13 22:18 | Subfolder `docs/` and 5 zip archives of backend, frontend, and edge-ai | **Intentional Project Content (Legacy Backup Folder)** | Legacy directory containing modular backups and notes. Retain untouched. |

*Protocol Compliance:* Zero paths have been modified, deleted, or ignored. All remain in their original un-staged state.

---

## 4. Git History & Closure Commit Reconciliation

### 4.1 Branch Pointers & Head Verification
```powershell
git branch -v
git rev-parse HEAD
git rev-parse main
```
- **Active Branch:** `feature/production-hardening`
- **Protected Main Branch:** `main` at `171171b` (Untouched)
- **Current HEAD Commit:** `5d909a3` (Documentation-only commit)
- **Group 4 Closure Base Commit:** `58e84e1`

### 4.2 Linear History Reconciliation (`f7c4752` to `58e84e1`)
```powershell
git log --oneline f7c4752..58e84e1
```
```
58e84e1 docs: add Group 4 independent verification audit report
5a32f49 docs: add Group 4 remediation changelog, test results, and final acceptance report
3562604 test: align integration test fixtures with Flyway V2 and optimistic locking (BUG-016)
3bd7cc4 fix(ui): eliminate mobile horizontal scroll overflow at 375px viewport (BUG-019)
df5752c fix(accessibility): resolve WCAG 2.2 AA form label and rating button violations (BUG-018)
97c6e83 refactor(frontend): decompose Analytics God component into modular domain tabs (BUG-015)
622ed0b fix(database): add optimistic locking versioning and 409 Conflict handling (BUG-016)
174ee15 fix(security): implement HttpOnly JWT cookies and CSRF protection (BUG-014)
```
- `f7c4752` represents the **Group 4 Base / Starting Commit** authorized at the end of Group 3.
- `58e84e1` represents the **Group 4 Final Closure Commit** containing the independent verification audit accepting Group 4 for closure.

---

## 5. Working Tree Cleanliness & Documentation-Only Commit Details

### 5.1 Status and Diff Verification
```powershell
git status --short
# Output:
# ?? .agents/
# ?? .hallmark/
# ?? RESTROVISION_v2.0_FullStack.zip
# ?? "complete zip.zip"
# ?? documant/
# ?? skills-lock.json

git diff --stat
# Output: (empty - 0 tracked files modified)
```

### 5.2 Documentation-Only Commit Details
- **Commit Hash:** `5d909a3` (HEAD of `feature/production-hardening`)
- **Parent Commit:** `58e84e1`
- **Subject:** `docs: finalize Group 5 implementation plan, test strategy, and review evidence (Revision 4)`
- **Committed Files (Documentation Only):**
  - `GROUP5_IMPLEMENTATION_PLAN.md`
  - `GROUP5_TEST_STRATEGY.md`
  - `UPDATED_BUG_TRACKER.md`
  - `GROUP5_REVISION4_REVIEW_EVIDENCE.md`
- **Application Files in Commit:** **0**

---

## 6. BUG-022 Dependency & Lockfile Audit Evidence

### 6.1 Host Node & Vite 6 Compatibility
```powershell
node -v # v25.8.2
npm -v  # 11.11.1
```
- `vite@6.4.6` engines: `"node": "^18.0.0 || ^20.0.0 || >=22.0.0"`. Satisfied by Node 25.8.2.
- `@vitejs/plugin-react@4.3.4` peers: `"vite": "^4.2.0 || ^5.0.0 || ^6.0.0"`. Explicitly supports Vite 6.

### 6.2 Installed Lockfile Dependency Tree
Execution of `npm list vite esbuild --all` in `frontend/`:
```
restrovision-frontend@2.0.0 D:\RESTROVISION\frontend
+-- @vitejs/plugin-react@4.7.0
| `-- vite@5.4.21 deduped
`-- vite@5.4.21
  `-- esbuild@0.21.5
```
- **Current Vulnerability:** `vite@5.4.21` (affected by GHSA-587h-7wmj-h5pm) and `esbuild@0.21.5` (affected by GHSA-67mh-4wv8-2f99).
- **Target Resolution:** Upgrading to `vite@^6.4.6` bundles `esbuild@^0.25.0`, satisfying `esbuild >= 0.25.0` and eliminating both advisories.

### 6.3 Real Vulnerability Scanners & OSIV Mechanism
1. **Maven Vulnerability Scanner:** OWASP Dependency-Check (`mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7.0`), downloading NVD feeds, failing on CVSS $\ge 7.0$.
2. **Python Vulnerability Scanner:** `pip-audit -r edge-ai/requirements.txt --strict` scanning PyPA/OSV databases.
3. **`spring.jpa.open-in-view=false` Mechanism:** Disables `OpenEntityManagerInViewInterceptor`. Persistence context closes at transaction boundary. Does not release physical connections immediately by itself, and does not eliminate N+1 queries by itself (throws `LazyInitializationException` if un-fetched lazy proxies are serialized). Mandates service-layer fetch planning (`@EntityGraph`, `JOIN FETCH`) and DTO mapping.
4. **Five Endpoint Test Suites (Populated vs Empty):** `/orders`, `/orders/{id}`, `/orders/kds-queue`, `/analytics/overview`, `/tables` tested under populated and empty datasets, asserting HTTP 200, complete data, 0 `LazyInitializationException`, and bounded query counts ($\le 3$).

---

## 7. BUG-023 Proposed WebSocket State Synchronization & Authorization

1. **Disconnected Pending Subscription Queue:** Subscriptions initiated before STOMP `CONNECT` queue in `pendingQueue`. Early unmount cancels pending callbacks before connection. `onConnect` executes an exactly-once atomic flush.
2. **Callback Identity Management (`useRef`):** `useWebSocketSubscription` encapsulates handlers via `useRef`, allowing component re-renders to update handler state without broker re-subscribing.
3. **Backend STOMP Channel Authorization Integration Tests:** Tests in `WebSocketIntegrationProductionTest.java` running against the actual `ChannelInterceptor` verify that same-tenant subscriptions succeed while cross-tenant subscriptions and publishes are rejected with `AccessDeniedException`.

---

## 8. Final Strict Hold Certification

- **Application Source Files Modified:** 0
- **Application Configuration Files Modified:** 0
- **Dependencies or Lockfiles Modified:** 0
- **Application Behavior Changes:** 0
- **Protected Main Branch (`171171b`):** Untouched
- **Current Feature Branch HEAD (`58e84e1` / `5d909a3`):** Verified clean application state

**0 application source files modified; 0 application configuration files modified; 0 dependencies or lockfiles modified; 0 application behavior changes; implementation remains unauthorized.**
