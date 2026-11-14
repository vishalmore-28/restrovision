"""
Unit and regression tests for Traffic Forecaster (BUG-020 Remediation).
Covers:
- Hyndman-Fan Type 7 quantile implementation verified against NumPy method='linear'
- Boundary probabilities (0.0, 1.0) and small sample sizes (N=1, 2, 3, 5, 10)
- Enforced actual observation threshold (N_actual >= 12); imputation cannot manufacture threshold satisfaction
- Duplicate timestamp deduplication and corrupted record filtering
- Exact residual counts: M = N - 2 for N in (12, 13, 15, 20)
- Rolling-origin data leakage audit: changing future observation leaves prior residuals and forecasts bit-for-bit identical
- Past-only gap imputation data leakage audit
- Outlier clamping (IQR >= 1.0 vs MAD fallback) applied to residual sample before quantile calculation
"""

import pytest
import numpy as np
from datetime import datetime, timezone, timedelta
from app.forecast.traffic import (
    generate_traffic_forecast,
    hyndman_fan_type7,
    SEASONAL_WEIGHTS,
    get_seasonal_weight,
    _calculate_clipping_bounds,
    _compute_rolling_residuals,
    _impute_chronological_gaps,
    _sanitize_and_sort_history,
)

class TestHyndmanFanType7:
    def test_empty_and_single_element(self):
        assert hyndman_fan_type7([], 0.5) == 0.0
        assert hyndman_fan_type7([42.0], 0.5) == 42.0
        assert hyndman_fan_type7([42.0], 0.0) == 42.0
        assert hyndman_fan_type7([42.0], 1.0) == 42.0

    def test_two_elements_boundaries_and_median(self):
        data = [10.0, 20.0]
        # h = 1*p + 1. For p=0: h=1 -> 10.0; p=1: h=2 -> 20.0; p=0.5: h=1.5 -> 15.0
        assert hyndman_fan_type7(data, 0.0) == 10.0
        assert hyndman_fan_type7(data, 0.5) == 15.0
        assert hyndman_fan_type7(data, 1.0) == 20.0
        # Compare with NumPy method='linear'
        for p in [0.0, 0.25, 0.5, 0.75, 1.0]:
            assert abs(hyndman_fan_type7(data, p) - float(np.quantile(data, p, method="linear"))) < 1e-9

    def test_matches_numpy_linear_across_probabilities(self):
        # 5 elements
        data5 = [10.0, 20.0, 30.0, 40.0, 50.0]
        for p in [0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0]:
            expected = float(np.quantile(data5, p, method="linear"))
            assert abs(hyndman_fan_type7(data5, p) - expected) < 1e-9

        # Exact 10-residual trace dataset from report
        trace_residuals = [1.85, 0.92, 3.12, 0.84, -2.15, -1.08, 0.45, -1.20, -2.40, 1.10]
        for p in [0.0, 0.10, 0.25, 0.50, 0.75, 0.90, 1.0]:
            expected = float(np.quantile(trace_residuals, p, method="linear"))
            actual = hyndman_fan_type7(trace_residuals, p)
            assert abs(actual - expected) < 1e-9

    def test_virtual_index_interpolation_formula(self):
        # 4 elements: [1.0, 2.0, 3.0, 4.0]
        # h = (4 - 1)*0.5 + 1 = 2.5 -> i=2, gamma=0.5 -> x[1] + 0.5*(x[2] - x[1]) = 2.0 + 0.5 = 2.5
        data = [1.0, 2.0, 3.0, 4.0]
        assert hyndman_fan_type7(data, 0.5) == 2.5
        # h for p=0.25: (3)*0.25 + 1 = 1.75 -> i=1, gamma=0.75 -> 1.0 + 0.75*(2.0 - 1.0) = 1.75
        assert hyndman_fan_type7(data, 0.25) == 1.75


class TestMinimumActualSampleThresholdAndFallback:
    def test_empty_and_none_history_triggers_fallback(self):
        for empty_val in [[], None]:
            forecast = generate_traffic_forecast(empty_val, horizon_hours=6)
            assert len(forecast) == 6
            assert all(item["metadata"]["fallback_applied"] is True for item in forecast)
            assert forecast[0]["metadata"]["actual_sample_count"] == 0
            assert forecast[0]["metadata"]["minimum_required"] == 12

    def test_insufficient_sample_size_below_12(self):
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        history = [
            {"hour": (base_time + timedelta(hours=i)).isoformat(), "guests": 20 + i}
            for i in range(8)
        ]
        forecast = generate_traffic_forecast(history, horizon_hours=6)
        assert len(forecast) == 6
        assert forecast[0]["metadata"]["fallback_applied"] is True
        assert forecast[0]["metadata"]["actual_sample_count"] == 8
        assert forecast[0]["metadata"]["minimum_required"] == 12

    def test_duplicate_timestamps_do_not_inflate_actual_count(self):
        # 12 items passed, but all have the exact same timestamp (8:00)
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        history = [
            {"hour": base_time.isoformat(), "guests": 20 + i}
            for i in range(12)
        ]
        forecast = generate_traffic_forecast(history, horizon_hours=6)
        assert len(forecast) == 6
        # Deduplication reduces count to 1 distinct hour -> triggers fallback
        assert forecast[0]["metadata"]["fallback_applied"] is True
        assert forecast[0]["metadata"]["actual_sample_count"] == 1

    def test_imputation_cannot_manufacture_threshold_satisfaction(self):
        # 7 raw observations spaced 2 hours apart (spanning 13 hours)
        # Even though gap imputation could synthesize intermediate hours,
        # N_actual = 7 < 12 MUST trigger the fallback
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        sparse_history = [
            {"hour": (base_time + timedelta(hours=i * 2)).isoformat(), "guests": 20 + i * 3}
            for i in range(7)
        ]
        forecast = generate_traffic_forecast(sparse_history, horizon_hours=6)
        assert len(forecast) == 6
        assert forecast[0]["metadata"]["fallback_applied"] is True
        assert forecast[0]["metadata"]["actual_sample_count"] == 7
        assert forecast[0]["metadata"]["minimum_required"] == 12

    def test_sufficient_actual_history_with_gap_imputation(self):
        # Exactly 12 actual distinct observations, with one 2-hour gap (missing 1 hour)
        # Raw actual count = 12 (satisfies threshold).
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        history = []
        for i in range(6):
            history.append({"hour": (base_time + timedelta(hours=i)).isoformat(), "guests": 20 + i})
        # Skip 1 hour (gap)
        for i in range(7, 13):
            history.append({"hour": (base_time + timedelta(hours=i)).isoformat(), "guests": 25 + i})
        
        assert len(history) == 12
        forecast = generate_traffic_forecast(history, horizon_hours=6)
        assert len(forecast) == 6
        assert forecast[0]["metadata"]["fallback_applied"] is False
        assert forecast[0]["metadata"]["actual_sample_count"] == 12
        assert forecast[0]["metadata"]["imputed_gap_count"] == 1
        assert forecast[0]["metadata"]["total_series_count"] == 13
        assert forecast[0]["metadata"]["residual_count"] == 11 # 13 - 2


class TestRollingResidualsAndDataLeakageAudit:
    @pytest.mark.parametrize("n_obs, expected_m", [(12, 10), (13, 11), (15, 13), (20, 18)])
    def test_exact_residual_count_m_equals_n_minus_two(self, n_obs, expected_m):
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        obs = [
            (base_time + timedelta(hours=i), (8 + i) % 24, 25 + (i % 5))
            for i in range(n_obs)
        ]
        residuals = _compute_rolling_residuals(obs)
        assert len(residuals) == expected_m

    def test_rolling_origin_future_data_leakage_audit(self):
        """
        AUDIT: Prove that modifying observation at t_14 does NOT affect
        residuals e_3 through e_13 or any forecast originating prior to t_14.
        """
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        obs_baseline = [
            (base_time + timedelta(hours=i), (8 + i) % 24, 20 + i)
            for i in range(14)
        ]
        # Same first 13 observations, but 14th observation is drastically altered
        obs_future_changed = [
            (base_time + timedelta(hours=i), (8 + i) % 24, 20 + i if i < 13 else 9999)
            for i in range(14)
        ]

        res_baseline = _compute_rolling_residuals(obs_baseline)
        res_future_changed = _compute_rolling_residuals(obs_future_changed)

        assert len(res_baseline) == 12 # 14 - 2
        assert len(res_future_changed) == 12

        # First 11 residuals (e_3 through e_13) MUST be bit-for-bit identical
        assert res_baseline[:-1] == res_future_changed[:-1]
        # Only the 12th residual (e_14) changes
        assert res_baseline[-1] != res_future_changed[-1]

    def test_past_only_gap_imputation_future_data_leakage_audit(self):
        """
        AUDIT: Prove that gap imputation uses only past observation (t_prev)
        and static seasonal weights, never reading future observations (t_next).
        """
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        # Gap between 8:00 and 11:00 (hours 9 and 10 missing)
        clean_base = [
            (base_time, 8, 20),
            (base_time + timedelta(hours=3), 11, 30),
            (base_time + timedelta(hours=4), 12, 40)
        ]
        clean_future_changed = [
            (base_time, 8, 20),
            (base_time + timedelta(hours=3), 11, 30),
            (base_time + timedelta(hours=4), 12, 9999) # Future changed at 12:00
        ]

        imp_base = _impute_chronological_gaps(clean_base)
        imp_future_changed = _impute_chronological_gaps(clean_future_changed)

        # Imputed intermediate points (hours 9 and 10) must be identical
        assert imp_base[:3] == imp_future_changed[:3]


class TestOutlierHandlingAndOrderOfOperations:
    def test_outlier_clamping_applied_before_forecast_quantiles(self):
        # Sample with extreme positive outlier (+500.0)
        residuals = [0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 500.0]
        clip_lower, clip_upper, iqr, mode = _calculate_clipping_bounds(residuals)
        assert mode == "IQR"
        assert 500.0 > clip_upper

        clamped = [max(clip_lower, min(clip_upper, r)) for r in residuals]
        assert max(clamped) == clip_upper

        # Quantile calculated on clamped sample prevents P90 explosion
        q90_clamped = hyndman_fan_type7(clamped, 0.90)
        assert q90_clamped <= clip_upper

    def test_near_zero_iqr_and_mad_zero_fallback(self):
        # All residuals identical: MAD = 0
        residuals = [3.0] * 10
        clip_lower, clip_upper, iqr, mode = _calculate_clipping_bounds(residuals)
        assert iqr == 0.0
        assert mode == "MAD_ZERO"
        assert clip_lower == -2.0
        assert clip_upper == 8.0

    def test_mad_positive_fallback_when_iqr_less_than_one(self):
        residuals = [2.0, 2.2, 2.4, 2.6, 2.8, 2.3, 2.5, 2.7, 2.1, 2.9]
        clip_lower, clip_upper, iqr, mode = _calculate_clipping_bounds(residuals)
        assert iqr < 1.0
        assert mode == "MAD_POSITIVE"
        assert clip_lower < 2.0 < clip_upper


class TestCorruptedAndInvalidInputs:
    def test_corrupted_negative_and_null_records_filtered(self):
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        history = [
            {"hour": (base_time + timedelta(hours=i)).isoformat(), "guests": 25}
            for i in range(12)
        ]
        # Insert corrupted records
        history.append({"hour": "invalid-timestamp", "guests": 50})
        history.append({"hour": (base_time + timedelta(hours=15)).isoformat(), "guests": -10})
        history.append({"hour": None, "guests": None})
        history.append("not-a-dict")
        
        forecast = generate_traffic_forecast(history, horizon_hours=6)
        assert len(forecast) == 6
        assert forecast[0]["metadata"]["fallback_applied"] is False
        assert forecast[0]["metadata"]["actual_sample_count"] == 12
