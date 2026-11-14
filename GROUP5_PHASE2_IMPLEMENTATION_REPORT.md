# RESTROVISION v2.0 — Group 5 Phase 2 Implementation & Verification Report

**Target Branch:** `feature/production-hardening`  
**Base Commit / Starting HEAD:** `dba3bda` (derived from `fd005b9`)  
**Protected Branch:** `main` at `171171b` (Untouched)  
**Authorized Scope:** BUG-020 ONLY (Edge AI ML Implementations & Empirical Residual Quantiles)  
**Date:** November 14, 2026  

---

## 1. Executive Summary & Problem Remediation

In accordance with the authorization for **Group 5 Phase 2 (BUG-020 ONLY)**, all superficial and static mock machine learning placeholders in the Edge AI subsystem have been replaced with fully functional, mathematically rigorous, empirical statistical models and contextual NLP algorithms:

1. **Traffic Forecaster (`edge-ai/app/forecast/traffic.py`):**
   - **Previous State:** Advertised `"model_version": "traffic-lgbm-1.0.0"`, but completely ignored the caller's historical data, returning fixed heuristic ratios ($P10 = 0.75 \times \text{base}$, $P90 = 1.30 \times \text{base}$) from a static dictionary.
   - **Remediated State:** Implements chronological observation ingestion, missing-hour gap imputation via seasonal prior scaling, rolling-origin out-of-sample residual generation strictly starting at $k=3$ (generating $M = N_{\text{valid}} - 2$ residuals), discrete outlier clipping via Hyndman-Fan Type 7 IQR (with robust MAD fallback for near-zero discrete count variance and MAD=0), and guaranteed monotonic prediction intervals ($0 \le P10 \le P50 \le P90$).
   - **Sample Threshold:** Enforces at least 12 valid clean hourly observations ($N_{\text{valid}} \ge 12$, producing $M \ge 10$ out-of-sample residuals). When history is insufficient or empty, transparently falls back to domain seasonal priors with explicit metadata.

2. **Contextual Sentiment NLP (`edge-ai/app/nlp/sentiment.py`):**
   - **Previous State:** Advertised `"model_version": "sentiment-indicbert-1.2.0"`, but performed crude 20-word substring checks without any negation handling (classifying `"not good"` as POSITIVE and `"not bad"` as NEGATIVE).
   - **Remediated State:** Implements contextual valence lexicon scoring with a 3-token lookbehind window, clause boundary scope resets (punctuation `,`, `.`, `;`, `!`, `?` and contrastive conjunctions `but`, `however`), double-negation resolution (`"not not good"` cancels out to positive), domain mitigation for `"not bad"` / `"not terrible"` (mitigated mild positive/neutral $+0.50$), intensifiers/diminishers, and frequency-ranked multi-topic categorization (`FOOD_QUALITY`, `SERVICE_SPEED`, `CLEANLINESS`, `VALUE`, `AMBIENCE`).

3. **Honest Model Attribution & Claims Removal:**
   - Removed all unsupported claims of running Heavy LightGBM or Deep IndicBERT models.
   - Updated model registry and responses to:
     - Traffic: `traffic-seasonal-empirical-1.1.0` (`"Empirical Residual Quantiles (Hyndman-Fan Type 7)"`).
     - Sentiment: `sentiment-lexicon-valence-1.1.0` (`"Contextual Negation Valence Lexicon"`), explicitly disclaiming probabilistic confidence calibration.
     - Demand: `demand-recipe-heuristic-1.1.0` (`"Station BOM & Recipe Heuristic"`).

---

## 2. Mathematical Specification & Algorithmic Design

### 2.1 Out-of-Sample Residual Generation ($k \ge 3$)
Given chronological hourly customer observations $(t_1, y_1), (t_2, y_2), \dots, (t_N, y_N)$:
- At $k=1$, 0 prior observations exist.
- At $k=2$, only 1 prior observation exists (0 degrees of freedom, no variance).
- At $k=3$, at least 2 distinct prior points exist, allowing a windowed moving average and local variance calculation.
For each observation $k \in [3, N]$:
$$w = \min(k-1, 6)$$
$$\bar{y}_{<k} = \frac{1}{w} \sum_{j=1}^{w} y_{k-j}, \quad \bar{S}_{<k} = \frac{1}{w} \sum_{j=1}^{w} S(t_{k-j})$$
$$\hat{y}_k = \bar{y}_{<k} \times \frac{S(t_k)}{\bar{S}_{<k}}$$
$$e_k = y_k - \hat{y}_k$$
This yields an array of $M = N_{\text{valid}} - 2$ clean out-of-sample residuals: $E = [e_3, e_4, \dots, e_N]$.

### 2.2 Hyndman-Fan Type 7 Quantile Formulation
For sorted sample $x_{(1)} \le x_{(2)} \le \dots \le x_{(M)}$ and percentile $p \in [0, 1]$:
$$h = (M - 1)p + 1, \quad i = \lfloor h \rfloor, \quad \gamma = h - i$$
$$Q(p) = x_{(i)} + \gamma(x_{(i+1)} - x_{(i)})$$

### 2.3 Discrete Outlier Clamping (IQR vs MAD Fallback)
1. Compute $Q(0.25)$ and $Q(0.75)$ on $E$ using Hyndman-Fan Type 7:
   $$IQR = Q(0.75) - Q(0.25)$$
2. **If $IQR \ge 1.0$:**
   $$\text{Clip}_{\text{lower}} = Q(0.25) - 1.5 \times IQR, \quad \text{Clip}_{\text{upper}} = Q(0.75) + 1.5 \times IQR$$
3. **If $IQR < 1.0$ (Near-zero discrete dining variance):**
   Compute median $\tilde{e} = Q(0.50)$ and $\text{MAD} = \text{median}(|e_i - \tilde{e}|)$.
   - If $\text{MAD} > 0$: $S_{\text{eff}} = \max(1.0, 1.4826 \times \text{MAD})$, bounds $[\tilde{e} - 2.5 S_{\text{eff}}, \tilde{e} + 2.5 S_{\text{eff}}]$.
   - If $\text{MAD} == 0$ (all residuals identical): $S_{\text{eff}} = 2.0$, bounds $[\tilde{e} - 5.0, \tilde{e} + 5.0]$.
4. Residual clamping: $e_i^* = \max(\text{Clip}_{\text{lower}}, \min(\text{Clip}_{\text{upper}}, e_i))$.

---

## 3. Deterministic Verification & Worked Examples

### 3.1 Worked Quantile Calculation with Intermediate Residuals

**Deterministic Input Fixture (14 hourly observations):**
`[(8:00, 10), (9:00, 14), (10:00, 19), (11:00, 24), (12:00, 44), (13:00, 52), (14:00, 41), (15:00, 23), (16:00, 16), (17:00, 21), (18:00, 40), (19:00, 58), (20:00, 66), (21:00, 48)]`

1. **Rolling Out-of-Sample Residuals ($k=3 \dots 14$, $M=12$ residuals):**
   - $k=3$ (10:00, $y_3=19$, $S(10)=18$): $\bar{y}_{<3}=12.0$, $\bar{S}_{<3}=13.5 \rightarrow \hat{y}_3 = 16.0 \rightarrow e_3 = +3.000$
   - $k=4$ (11:00, $y_4=24$, $S(11)=25$): $\bar{y}_{<4}=14.33$, $\bar{S}_{<4}=15.0 \rightarrow \hat{y}_4 = 23.89 \rightarrow e_4 = +0.111$
   - $k=5$ (12:00, $y_5=44$, $S(12)=45$): $\bar{y}_{<5}=16.75$, $\bar{S}_{<5}=17.5 \rightarrow \hat{y}_5 = 43.07 \rightarrow e_5 = +0.929$
   - $k=6$ (13:00, $y_6=52$, $S(13)=55$): $\bar{y}_{<6}=22.20$, $\bar{S}_{<6}=23.0 \rightarrow \hat{y}_6 = 53.09 \rightarrow e_6 = -1.087$
   - $k=7$ (14:00, $y_7=41$, $S(14)=42$): $\bar{y}_{<7}=27.17$, $\bar{S}_{<7}=28.33 \rightarrow \hat{y}_7 = 40.28 \rightarrow e_7 = +0.718$
   - $k=8 \dots 14$: $e_8=+0.542, e_9=+0.215, e_{10}=-0.884, e_{11}=-1.120, e_{12}=-0.640, e_{13}=+0.125, e_{14}=-1.410$

2. **Residual Series $E$ ($M=12$):**
   `[+3.000, +0.111, +0.929, -1.087, +0.718, +0.542, +0.215, -0.884, -1.120, -0.640, +0.125, -1.410]`
   Sorted: `[-1.410, -1.120, -1.087, -0.884, -0.640, +0.111, +0.125, +0.215, +0.542, +0.718, +0.929, +3.000]`

3. **Hyndman-Fan Type 7 Quantiles:**
   - $Q(0.25)$: $h = 11 \times 0.25 + 1 = 3.75 \rightarrow -1.087 + 0.75(-0.884 - (-1.087)) = -0.935$
   - $Q(0.75)$: $h = 11 \times 0.75 + 1 = 9.25 \rightarrow +0.542 + 0.25(0.718 - 0.542) = +0.586$
   - $IQR = 0.586 - (-0.935) = 1.521 \ge 1.0$ (Scale Mode: `IQR`)
   - $\text{Clip}_{\text{lower}} = -0.935 - 1.5(1.521) = -3.217$
   - $\text{Clip}_{\text{upper}} = +0.586 + 1.5(1.521) = +2.868$
   - Residual clamping: $+3.000$ clamped to $+2.868$.
   - $Q(0.10) = -1.117$
   - $Q(0.50) = +0.118$
   - $Q(0.90) = +0.910$

4. **Forecast Projection for Target Hour 22:00 ($S(22)=28$):**
   - Baseline $\hat{y}_{22} = 27.4$
   - $P50 = \text{round}(27.4 + 0.118) = 28$
   - $P10 = \text{round}(27.4 - 1.117) = 26$
   - $P90 = \text{round}(27.4 + 0.910) = 28 \rightarrow \max(P50, 28) = 28$
   - Strict ordering guaranteed: $P10 (26) \le P50 (28) \le P90 (28)$.

---

### 3.2 Before / After Forecast Output Comparison

#### Before (Superficial Mock with Static Dictionary):
```json
{
  "hour": "2026-11-14T22:00:00+00:00",
  "p10": 18,
  "p50": 25,
  "p90": 32,
  "unit": "customers",
  "model_version": "traffic-lgbm-1.0.0"
}
```
*(Notice: Hardcoded $0.75 \times 25$ and $1.30 \times 25$, input historical data completely ignored).*

#### After (Empirical Residual Quantiles):
```json
{
  "hour": "2026-11-14T22:00:00+00:00",
  "p10": 26,
  "p50": 28,
  "p90": 28,
  "unit": "customers",
  "model_version": "traffic-seasonal-empirical-1.1.0",
  "algorithm": "Empirical Residual Quantiles (Hyndman-Fan Type 7)",
  "metadata": {
    "fallback_applied": false,
    "sample_count": 14,
    "residual_count": 12,
    "iqr": 1.5208,
    "scale_mode": "IQR",
    "clipping_bounds": [-3.2166, 2.8677],
    "q10_residual": -1.1167,
    "q50_residual": 0.1180,
    "q90_residual": 0.9099,
    "disclaimer": "Empirical residual quantile intervals. Disclaims parametric Gaussian calibration."
  }
}
```

---

### 3.3 Sentiment Classification Traces

| Input Feedback Text | Negation / Lexicon Resolution | Valence Score | Sentiment | Primary Category |
| :--- | :--- | :--- | :--- | :--- |
| `"The food was not good."` | `"good"` (+0.9) preceded by `"not"` $\rightarrow -0.90$ | `-0.669` | **NEGATIVE** | `FOOD_QUALITY` |
| `"The dal makhani was not bad."` | `"bad"` (-1.0) preceded by `"not"` mitigated to $+0.50$ | `+0.447` | **POSITIVE** | `FOOD_QUALITY` |
| `"The biryani was not not good."` | Double negation cancels $\rightarrow +0.90$ | `+0.669` | **POSITIVE** | `FOOD_QUALITY` |
| `"Food was not good, but service was fast and attentive."` | `not good` (-0.9); comma/but resets scope; `fast` (+0.8) + `attentive` (+0.9) | `+0.768` | **POSITIVE** | `SERVICE_SPEED` |
| `"The washroom and floor were clean and sanitized."` | `clean` (+0.9) + `sanitized` (+0.9) | `+0.874` | **POSITIVE** | `CLEANLINESS` |
| `"Extremely expensive bill, not worth the cost."` | `expensive` $\times 1.5$ (-1.05) + `not worth` (-0.80) | `-0.880` | **NEGATIVE** | `VALUE` |

---

## 4. Pytest Execution & Coverage Metrics

### 4.1 Pytest Execution Metrics
Command: `pytest -v --cov=app --cov-report=term-missing`  
Python Runtime: `3.13.11` (Oracle DB Home Python)

```
============================= test session starts =============================
platform win32 -- Python 3.13.11, pytest-9.2.0, pluggy-1.7.0
plugins: anyio-4.16.0, cov-7.1.0
collected 43 items

tests/test_api_endpoints.py::TestApiHealthAndModels::test_health_live PASSED [  2%]
tests/test_api_endpoints.py::TestApiHealthAndModels::test_health_ready PASSED [  4%]
tests/test_api_endpoints.py::TestApiHealthAndModels::test_models_registry_honest_names PASSED [  6%]
tests/test_api_endpoints.py::TestForecastEndpoints::test_traffic_forecast_endpoint_empty_history PASSED [  9%]
tests/test_api_endpoints.py::TestForecastEndpoints::test_traffic_forecast_endpoint_full_history PASSED [ 11%]
tests/test_api_endpoints.py::TestForecastEndpoints::test_demand_forecast_endpoint PASSED [ 13%]
tests/test_api_endpoints.py::TestSentimentEndpoint::test_sentiment_endpoint_positive PASSED [ 16%]
tests/test_api_endpoints.py::TestSentimentEndpoint::test_sentiment_endpoint_negation_not_good PASSED [ 18%]
tests/test_api_endpoints.py::TestSentimentEndpoint::test_sentiment_endpoint_negation_not_bad PASSED [ 20%]
tests/test_forecast.py::TestHyndmanFanType7::test_empty_and_single_element PASSED [ 23%]
tests/test_forecast.py::TestHyndmanFanType7::test_standard_quantiles PASSED [ 25%]
tests/test_forecast.py::TestHyndmanFanType7::test_interpolation_between_points PASSED [ 27%]
tests/test_forecast.py::TestMinimumSampleThresholdAndFallback::test_empty_history_triggers_fallback PASSED [ 30%]
tests/test_forecast.py::TestMinimumSampleThresholdAndFallback::test_none_history_triggers_fallback PASSED [ 32%]
tests/test_forecast.py::TestMinimumSampleThresholdAndFallback::test_insufficient_sample_size_below_12 PASSED [ 34%]
tests/test_forecast.py::TestRollingResidualsAndQuantiles::test_residual_series_starts_at_k3 PASSED [ 37%]
tests/test_forecast.py::TestRollingResidualsAndQuantiles::test_monotonic_prediction_intervals PASSED [ 39%]
tests/test_forecast.py::TestOutlierHandlingAndMADFallback::test_iqr_clipping_bounds_on_extreme_outliers PASSED [ 41%]
tests/test_forecast.py::TestOutlierHandlingAndMADFallback::test_near_zero_iqr_and_mad_zero_fallback PASSED [ 44%]
tests/test_forecast.py::TestOutlierHandlingAndMADFallback::test_mad_positive_fallback_when_iqr_less_than_one PASSED [ 46%]
tests/test_forecast.py::TestMissingHistoryAndGaps::test_non_consecutive_hourly_data_gap_imputation PASSED [ 48%]
tests/test_forecast.py::TestMissingHistoryAndGaps::test_corrupted_and_negative_counts_filtered PASSED [ 51%]
tests/test_sentiment.py::TestContextualNegation::test_not_good_is_negative PASSED [ 53%]
tests/test_sentiment.py::TestContextualNegation::test_not_bad_is_mitigated_neutral_or_positive PASSED [ 55%]
tests/test_sentiment.py::TestContextualNegation::test_not_clean_is_negative PASSED [ 58%]
tests/test_sentiment.py::TestContextualNegation::test_not_terrible_is_mitigated PASSED [ 60%]
tests/test_sentiment.py::TestContextualNegation::test_double_negation_cancellation PASSED [ 62%]
tests/test_sentiment.py::TestContextualNegation::test_double_negation_not_without PASSED [ 65%]
tests/test_sentiment.py::TestNegationScopeAndBoundary::test_boundary_resets_negation_scope PASSED [ 67%]
tests/test_sentiment.py::TestNegationScopeAndBoundary::test_negation_does_not_leak_beyond_three_tokens PASSED [ 69%]
tests/test_sentiment.py::TestIntensifiersAndDiminishers::test_intensifiers_increase_magnitude PASSED [ 72%]
tests/test_sentiment.py::TestIntensifiersAndDiminishers::test_diminishers_decrease_magnitude PASSED [ 74%]
tests/test_sentiment.py::TestMultiTopicCategorization::test_food_quality_category PASSED [ 76%]
tests/test_sentiment.py::TestMultiTopicCategorization::test_service_speed_category PASSED [ 79%]
tests/test_sentiment.py::TestMultiTopicCategorization::test_cleanliness_category PASSED [ 81%]
tests/test_sentiment.py::TestMultiTopicCategorization::test_value_category PASSED [ 83%]
tests/test_sentiment.py::TestMultiTopicCategorization::test_ambience_category PASSED [ 86%]
tests/test_sentiment.py::TestEmptyAndEdgeInputs::test_empty_and_whitespace PASSED [ 88%]
tests/test_sentiment.py::TestEmptyAndEdgeInputs::test_honest_model_metadata PASSED [ 90%]
tests/test_state_engine.py::test_temporal_occupancy_walk_by_rejected PASSED [ 93%]
tests/test_state_engine.py::test_temporal_occupancy_confirmed_with_missed_frame PASSED [ 95%]
tests/test_state_engine.py::test_vacancy_hold_time PASSED                [ 97%]
tests/test_state_engine.py::test_hmac_signer PASSED                      [100%]

======================== 43 passed in 1.25s ========================
```

| Metric | Count | Percentage |
| :--- | :--- | :--- |
| **Discovered** | 43 | 100.0% |
| **Executed** | 43 | 100.0% |
| **Passed** | 43 | 100.0% |
| **Failed** | 0 | 0.0% |
| **Skipped** | 0 | 0.0% |
| **Pass Rate** | — | **100.0%** |

---

### 4.2 Pytest Code Coverage Matrix

```
Name                            Stmts   Miss  Cover   Missing
-------------------------------------------------------------
app\core\config.py                  9      0   100%
app\events\outbox.py               48     22    54%   37-45, 48-58, 61-64, 68-78
app\events\signer.py                9      1    89%   11
app\forecast\demand.py              9      0   100%
app\forecast\traffic.py           159      8    95%   49, 51, 57, 68, 81, 91-92, 143
app\main.py                        78     27    65%   66, 77, 81-85, 94, 116-147
app\nlp\sentiment.py               68      0   100%
app\state\occupancy_engine.py      53      1    98%   49
-------------------------------------------------------------
TOTAL                             433     59    86%
```

---

## 5. Regression Verifications

### 5.1 Backend Test Suite (Maven)
- **Execution Command:** `mvn test` in `Backend/`
- **Results:**
  ```
  [INFO] Results:
  [INFO] Tests run: 92, Failures: 0, Errors: 0, Skipped: 0
  [INFO] BUILD SUCCESS
  [INFO] Total time: 42.475 s
  ```
- **Outcome:** 92 / 92 tests passed. 0 regressions.

### 5.2 Legacy Edge AI Ingestion Script
- **Execution Command:** `python run_tests.py` in `edge-ai/`
- **Results:** 5 / 5 checks passed (Walk-by rejection, 80% sliding window frame flicker tolerance, vacancy hold time, HMAC-SHA256 signer, Gateway readiness).

### 5.3 Frontend Test Suite (BUG-021 Verification)
- **Execution Command:** `npm run test` in `frontend/`
- **Results:** 4 test files, 40 / 40 tests passed, 0 failed, 0 skipped.
- **Outcome:** BUG-021 remains 100% green and operational.

---

## 6. Strict Stop Gate Enforcement

In strict compliance with user instructions:
- **Phase 2 (BUG-020)** is completed and verified.
- **Phase 3 (BUG-022: Dependency Auditing & Security)** has NOT been started.
- **Phase 4 (BUG-023: WebSocket Deduplication)** has NOT been started.
- Protected branch `main` at `171171b` remains completely untouched.
- No deployment actions have been initiated.
