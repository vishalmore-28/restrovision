# RESTROVISION v2.0 — Group 5 Phase 1 Coverage Report

**Target Branch:** `feature/production-hardening`  
**Base Commit:** `6bcebf3`  
**Coverage Provider:** v8 Native Bytecode Engine  
**Execution Tool:** `@vitest/coverage-v8@1.6.1` via `npm run test:coverage`  
**Report Date:** November 14, 2026 — 22:06:51 +05:30  

---

## 1. Coverage Philosophy & Reporting Decoupling

> [!IMPORTANT]
> In accordance with Group 5 standards, **test execution pass rate (100.0%, 40/40 tests)** is explicitly decoupled from **code coverage percentages**. Furthermore, coverage is reported as both absolute fractions (`covered / total`) and percentages across all four v8 dimensions: Statements, Branches, Functions, and Lines.
>
> We distinguish:
> 1. **Target Critical-Path Coverage (Phase 1 Scope)**: Target $\ge 80.0\%$ line coverage on core operational paths (Login, Public QR Portal, Decomposed Analytics Dashboard, API Security Interceptors). **Achieved: 98.86% (1,903 / 1,925 lines)**.
> 2. **Overall Project Total**: Includes legacy or untouched operational pages (AlertCenter, FloorPlan, KitchenKds, CamerasZones, CleaningTasks, WaiterDashboard) that are out of scope for Phase 1.

---

## 2. High-Level Coverage Summary

### 2.1 Target Critical-Path Scope (Phase 1 Scope)

| Component / Layer | Statements | Branches | Functions | Lines | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `src/pages/Login.jsx` | 100.0% (313/313) | 86.20% (25/29) | 66.66% (4/6) | 100.0% (313/313) | **EXCEEDED (100%)** |
| `src/pages/CustomerQrPortal.jsx` | 100.0% (346/346) | 85.71% (30/35) | 100.0% (10/10) | 100.0% (346/346) | **EXCEEDED (100%)** |
| `src/pages/Analytics.jsx` | 100.0% (46/46) | 100.0% (1/1) | 100.0% (1/1) | 100.0% (46/46) | **EXCEEDED (100%)** |
| `src/pages/analytics/AnalyticsHeader.jsx` | 100.0% (71/71) | 100.0% (15/15) | 75.00% (3/4) | 100.0% (71/71) | **EXCEEDED (100%)** |
| `src/pages/analytics/DailyOverviewTab.jsx` | 100.0% (210/210) | 100.0% (1/1) | 100.0% (1/1) | 100.0% (210/210) | **EXCEEDED (100%)** |
| `src/pages/analytics/CrowdForecastTab.jsx` | 100.0% (163/163) | 100.0% (8/8) | 100.0% (1/1) | 100.0% (163/163) | **EXCEEDED (100%)** |
| `src/pages/analytics/MenuEngineeringTab.jsx` | 100.0% (99/99) | 57.14% (8/14) | 100.0% (1/1) | 100.0% (99/99) | **EXCEEDED (100%)** |
| `src/pages/analytics/GuestReviewsTab.jsx` | 100.0% (243/243) | 100.0% (25/25) | 80.00% (4/5) | 100.0% (243/243) | **EXCEEDED (100%)** |
| `src/pages/analytics/useAnalyticsData.js` | 98.79% (245/248) | 73.68% (14/19) | 100.0% (1/1) | 98.79% (245/248) | **EXCEEDED (98.8%)** |
| `src/services/api.js` | 89.78% (167/186) | 69.23% (27/39) | 22.64% (12/53) | 89.78% (167/186) | **EXCEEDED (89.8%)** |
| **Combined Target Critical Path** | **98.86% (1903/1925)** | **82.80% (154/186)** | **45.78% (38/83)** | **98.86% (1903/1925)** | **EXCEEDED ($\ge 80\%$)** |

---

### 2.2 Entire Frontend Repository Totals

| Metric | Covered / Total | Percentage | Target Requirement |
| :--- | :--- | :--- | :--- |
| **Statements** | 2,036 / 5,228 | 38.94% | Baseline Established |
| **Branches** | 159 / 200 | 79.50% | High decision coverage |
| **Functions** | 41 / 104 | 39.42% | Baseline Established |
| **Lines** | 2,036 / 5,228 | 38.94% | Baseline Established |

---

## 3. Comprehensive File-by-File Coverage Matrix

Data extracted directly from `frontend/coverage/coverage-summary.json`:

```
-------------------|---------|----------|---------|---------|-------------------
File               | % Stmts | % Branch | % Funcs | % Lines | Uncovered Line #s 
-------------------|---------|----------|---------|---------|-------------------
All files          |   38.94 |     79.5 |   39.42 |   38.94 |                   
 components        |       0 |        0 |       0 |       0 |                   
  Navbar.jsx       |       0 |        0 |       0 |       0 | 1-198             
  Sidebar.jsx      |       0 |        0 |       0 |       0 | 1-147             
 context           |   45.76 |       80 |   33.33 |   45.76 |                   
  AuthContext.jsx  |   37.18 |      100 |       0 |   37.18 | 73-195,198-199    
  ThemeContext.jsx |   91.89 |       80 |      50 |   91.89 | 17,25-26          
 pages             |   21.34 |    78.87 |   65.21 |   21.34 |                   
  AlertCenter.jsx  |       0 |        0 |       0 |       0 | 1-451             
  Analytics.jsx    |     100 |      100 |     100 |     100 |                   
  CamerasZones.jsx |       0 |        0 |       0 |       0 | 1-332             
  ...ningTasks.jsx |       0 |        0 |       0 |       0 | 1-213             
  ...rQrPortal.jsx |     100 |    85.71 |     100 |     100 | 41,44,55,65       
  FloorPlan.jsx    |       0 |        0 |       0 |       0 | 1-788             
  KitchenKds.jsx   |       0 |        0 |       0 |       0 | 1-434             
  Login.jsx        |     100 |     86.2 |   66.66 |     100 | 83-110            
  ...Dashboard.jsx |       0 |        0 |       0 |       0 | 1-380             
 pages/analytics   |    99.7 |    86.58 |   84.61 |    99.7 |                   
  ...icsHeader.jsx |     100 |      100 |      75 |     100 |                   
  ...recastTab.jsx |     100 |      100 |     100 |     100 |                   
  ...erviewTab.jsx |     100 |      100 |     100 |     100 |                   
  ...eviewsTab.jsx |     100 |      100 |      80 |     100 |                   
  ...eeringTab.jsx |     100 |    57.14 |     100 |     100 | 46-51             
  ...lyticsData.js |   98.79 |    73.68 |     100 |   98.79 | 211-212,215       
 services          |   61.93 |       70 |   21.66 |   61.93 |                   
  api.js           |   89.78 |    69.23 |   22.64 |   89.78 | ...,86-96,114-115 
  websocket.js     |   20.16 |      100 |   14.28 |   20.16 | ...94-113,116-121 
-------------------|---------|----------|---------|---------|-------------------
```

---

## 4. Uncovered Lines Analysis on Critical Files

### 4.1 `src/services/api.js` (89.78% Line Coverage)
- **Covered Lines:** 167 / 186
- **Uncovered Sections:** Lines 86–96 (concurrent 401 refresh subscriber queue callback execution) and lines 114–115 (non-JSON 204 No Content response branch).
- **Explanation:** In `api.js`, individual endpoints (`getDelayedOrders`, `createZone`, `assignTask`, etc.) are declared as single-line wrapper lambdas around `request()`. Testing the core `request()` mechanism and representative endpoints validates the security interceptors across the entire module.

### 4.2 `src/pages/analytics/useAnalyticsData.js` (98.79% Line Coverage)
- **Covered Lines:** 245 / 248
- **Uncovered Lines:** 211–212, 215 (live network overview state commit when API is online).
- **Explanation:** In unit tests, `api.getAnalyticsOverview` resolves to `null` or mock fixtures to verify defensive fallback to pre-computed intraday telemetry data.

### 4.3 Untracked Modules (Navbar, Sidebar, AlertCenter, FloorPlan, etc.)
- These components are currently at 0% because Phase 1 was explicitly authorized for **BUG-021** (Login accessibility, Customer QR Portal, Analytics tabs, and CSRF interceptors). Subsequent test phases will expand coverage to remaining views.

---

## 5. Compatibility Requirement for Vite 6 Upgrade (Tracked for BUG-022)

> [!IMPORTANT]
> The current test infrastructure operates on `vitest@1.6.1` and `@vitest/coverage-v8@1.6.1`, which require `"vite": "^5.0.0"`.
> During **BUG-022 (Production Dependency & Vulnerability Auditing)**, when Vite is upgraded to a supported release (e.g. Vite 6.x), Vitest and its coverage provider MUST be upgraded in lockstep to a compatible major release (e.g. `vitest >= 2.1.x` and `@vitest/coverage-v8 >= 2.1.x`) to maintain peer compatibility with Vite 6. This upgrade must be verified against Node `v25.8.2` and lockfile consistency.

