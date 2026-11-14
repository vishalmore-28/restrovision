"""
Unit and regression tests for Traffic Forecaster (BUG-020).
Tests:
- Minimum sample threshold and fallback (N < 12)
- Monotonic P10 <= P50 <= P90 prediction interval ordering
- High and low outlier clamping (IQR >= 1.0)
- Near-zero IQR handling and MAD=0 fallback
- Missing and empty history handling
- Non-consecutive hourly data gap imputation
- Hyndman-Fan Type 7 quantile verification
"""

import pytest
from datetime import datetime, timezone, timedelta
from app.forecast.traffic import (
    generate_traffic_forecast,
    hyndman_fan_type7,
    SEASONAL_WEIGHTS,
    get_seasonal_weight,
    _calculate_clipping_bounds,
    _compute_rolling_residuals,
    _impute_chronological_gaps,
)

class TestHyndmanFanType7:
    def test_empty_and_single_element(self):
        assert hyndman_fan_type7([], 0.5) == 0.0
        assert hyndman_fan_type7([42.0], 0.5) == 42.0

    def test_standard_quantiles(self):
        # Sample of 5 elements: [10, 20, 30, 40, 50]
        # h = (5 - 1)*0.5 + 1 = 3.0 -> index 3 -> 30.0
        data = [10.0, 20.0, 30.0, 40.0, 50.0]
        assert hyndman_fan_type7(data, 0.5) == 30.0
        # h = 4*0.25 + 1 = 2.0 -> 20.0
        assert hyndman_fan_type7(data, 0.25) == 20.0
        # h = 4*0.75 + 1 = 4.0 -> 40.0
        assert hyndman_fan_type7(data, 0.75) == 40.0

    def test_interpolation_between_points(self):
        # 4 elements: [1.0, 2.0, 3.0, 4.0]
        # For p=0.5: h = (4 - 1)*0.5 + 1 = 2.5
        # i = 2, gamma = 0.5 -> x[1] + 0.5*(x[2] - x[1]) = 2.0 + 0.5*(3.0 - 2.0) = 2.5
        data = [1.0, 2.0, 3.0, 4.0]
        assert hyndman_fan_type7(data, 0.5) == 2.5


class TestMinimumSampleThresholdAndFallback:
    def test_empty_history_triggers_fallback(self):
        forecast = generate_traffic_forecast([], horizon_hours=12)
        assert len(forecast) == 12
        for item in forecast:
            assert item["metadata"]["fallback_applied"] is True
            assert item["metadata"]["sample_count"] == 0
            assert item["metadata"]["reason"] == "INSUFFICIENT_SAMPLE_SIZE"
            assert item["algorithm"] == "Domain Seasonal Prior Fallback"
            assert 0 <= item["p10"] <= item["p50"] <= item["p90"]

    def test_none_history_triggers_fallback(self):
        forecast = generate_traffic_forecast(None, horizon_hours=6)
        assert len(forecast) == 6
        assert all(item["metadata"]["fallback_applied"] is True for item in forecast)

    def test_insufficient_sample_size_below_12(self):
        # Provide exactly 8 clean hourly observations (< 12)
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        history = [
            {"hour": (base_time + timedelta(hours=i)).isoformat(), "guests": 20 + i}
            for i in range(8)
        ]
        forecast = generate_traffic_forecast(history, horizon_hours=12)
        assert len(forecast) == 12
        assert forecast[0]["metadata"]["fallback_applied"] is True
        assert forecast[0]["metadata"]["sample_count"] == 8
        assert forecast[0]["metadata"]["minimum_required"] == 12


class TestRollingResidualsAndQuantiles:
    def test_residual_series_starts_at_k3(self):
        # 12 observations produces exactly M = 12 - 2 = 10 residuals
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        obs = [
            (base_time + timedelta(hours=i), (8 + i) % 24, 25 + (i % 5))
            for i in range(12)
        ]
        residuals = _compute_rolling_residuals(obs)
        assert len(residuals) == 10  # M = N - 2 = 10

    def test_monotonic_prediction_intervals(self):
        # 16 observations: sufficient history
        base_time = datetime(2026, 11, 14, 6, 0, 0, tzinfo=timezone.utc)
        history = [
            {"hour": (base_time + timedelta(hours=i)).isoformat(), "guests": 15 + (i * 2)}
            for i in range(16)
        ]
        forecast = generate_traffic_forecast(history, horizon_hours=12)
        assert len(forecast) == 12
        for item in forecast:
            assert item["metadata"]["fallback_applied"] is False
            assert item["metadata"]["sample_count"] == 16
            assert item["metadata"]["residual_count"] == 14
            # Strict ordering: 0 <= P10 <= P50 <= P90
            assert 0 <= item["p10"] <= item["p50"] <= item["p90"]
            assert item["unit"] == "customers"


class TestOutlierHandlingAndMADFallback:
    def test_iqr_clipping_bounds_on_extreme_outliers(self):
        # Residuals with spread IQR >= 1.0 and an extreme outlier (+200.0)
        residuals = [0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 200.0]
        clip_lower, clip_upper, iqr, mode = _calculate_clipping_bounds(residuals)
        assert mode == "IQR"
        assert iqr >= 1.0
        # 200.0 must be significantly above clip_upper
        assert 200.0 > clip_upper
        clamped = [max(clip_lower, min(clip_upper, r)) for r in residuals]
        assert max(clamped) <= clip_upper

    def test_near_zero_iqr_and_mad_zero_fallback(self):
        # Discrete integer guest counts where all residuals are identical: MAD = 0
        residuals = [3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0]
        clip_lower, clip_upper, iqr, mode = _calculate_clipping_bounds(residuals)
        assert iqr == 0.0
        assert mode == "MAD_ZERO"
        # Scale S_eff = 2.0, bounds: median - 5.0 to median + 5.0 -> -2.0 to 8.0
        assert clip_lower == -2.0
        assert clip_upper == 8.0

    def test_mad_positive_fallback_when_iqr_less_than_one(self):
        # Residues with spread where IQR < 1.0 but MAD > 0
        residuals = [2.0, 2.2, 2.4, 2.6, 2.8, 2.3, 2.5, 2.7, 2.1, 2.9]
        clip_lower, clip_upper, iqr, mode = _calculate_clipping_bounds(residuals)
        assert iqr < 1.0
        assert mode == "MAD_POSITIVE"
        assert clip_lower < 2.0 < clip_upper


class TestMissingHistoryAndGaps:
    def test_non_consecutive_hourly_data_gap_imputation(self):
        # 6 observations spaced 2 hours apart (12-hour span: 8:00, 10:00, 12:00, 14:00, 16:00, 18:00)
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        sparse_history = [
            {"hour": (base_time + timedelta(hours=i * 2)).isoformat(), "guests": 20 + i * 3}
            for i in range(7)
        ]
        # Raw items = 7. After gap imputation of 1 hour between each, length becomes 13 (>= 12)
        forecast = generate_traffic_forecast(sparse_history, horizon_hours=6)
        assert len(forecast) == 6
        # Confirms imputation elevated sample count across 12-hour span to satisfy threshold
        assert forecast[0]["metadata"]["sample_count"] >= 12
        assert forecast[0]["metadata"]["fallback_applied"] is False

    def test_corrupted_and_negative_counts_filtered(self):
        base_time = datetime(2026, 11, 14, 8, 0, 0, tzinfo=timezone.utc)
        history = [
            {"hour": (base_time + timedelta(hours=i)).isoformat(), "guests": 25}
            for i in range(12)
        ]
        # Insert corrupted records
        history.append({"hour": "invalid-timestamp", "guests": 50})
        history.append({"hour": (base_time + timedelta(hours=15)).isoformat(), "guests": -10})
        history.append({"hour": None, "guests": None})
        
        forecast = generate_traffic_forecast(history, horizon_hours=6)
        assert len(forecast) == 6
        assert forecast[0]["metadata"]["fallback_applied"] is False
