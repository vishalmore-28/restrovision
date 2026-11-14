"""
RESTROVISION Edge AI — Traffic Forecaster (BUG-020 Remediation)
Implements:
- Chronological observation parsing and chronological gap / missing-hour imputation
- Rolling-origin out-of-sample residual generation strictly starting at k=3 (M >= 10 for N >= 12)
- Hyndman-Fan Type 7 empirical residual quantiles
- Discrete outlier clipping (IQR >= 1.0 vs MAD fallback for near-zero variance, including MAD=0)
- Enforced minimum sample threshold (N >= 12) with transparent domain seasonal prior fallback
- Guaranteed P10 <= P50 <= P90 prediction interval ordering
"""

from typing import List, Dict, Any, Optional, Tuple
from datetime import datetime, timezone, timedelta
import math

# Hourly domain seasonal prior weights (hours 0 through 23)
SEASONAL_WEIGHTS: Dict[int, int] = {
    0: 4, 1: 2, 2: 2, 3: 2, 4: 2, 5: 3,
    6: 5, 7: 8, 8: 12, 9: 15, 10: 18,
    11: 25, 12: 45, 13: 55, 14: 42, 15: 22, 16: 15,
    17: 22, 18: 42, 19: 60, 20: 68, 21: 50, 22: 28, 23: 12
}

def get_seasonal_weight(hour_of_day: int) -> int:
    return SEASONAL_WEIGHTS.get(hour_of_day % 24, 8)

def hyndman_fan_type7(data: List[float], p: float) -> float:
    """
    Computes sample quantile using Hyndman-Fan Type 7 method:
    Virtual index h = (N - 1)*p + 1 (1-based).
    Linear interpolation between sorted values x[floor(h)-1] and x[ceil(h)-1].
    Matches NumPy default (method='weibull'/type 7) and R type 7.
    """
    if not data:
        return 0.0
    n = len(data)
    if n == 1:
        return float(data[0])
    
    # Clamp p to [0, 1]
    p = max(0.0, min(1.0, p))
    s = sorted(data)
    
    h = (n - 1) * p + 1.0
    i = int(h) # 1-based integer index
    gamma = h - i
    
    if i >= n:
        return float(s[-1])
    if i < 1:
        return float(s[0])
    
    return float(s[i - 1] + gamma * (s[i] - s[i - 1]))

def _parse_timestamp(raw_val: Any, default_base: datetime, fallback_offset_hours: int = 0) -> datetime:
    if isinstance(raw_val, datetime):
        return raw_val.astimezone(timezone.utc) if raw_val.tzinfo else raw_val.replace(tzinfo=timezone.utc)
    if isinstance(raw_val, str):
        try:
            # Handle ISO string with or without Z
            cleaned = raw_val.replace("Z", "+00:00")
            dt = datetime.fromisoformat(cleaned)
            return dt.astimezone(timezone.utc) if dt.tzinfo else dt.replace(tzinfo=timezone.utc)
        except Exception:
            pass
    if isinstance(raw_val, (int, float)):
        # Hour of day (0-23)
        return (default_base + timedelta(hours=int(raw_val))).replace(minute=0, second=0, microsecond=0)
    return (default_base + timedelta(hours=fallback_offset_hours)).replace(minute=0, second=0, microsecond=0)

def _sanitize_and_sort_history(history_window: List[Dict[str, Any]]) -> List[Tuple[datetime, int, int]]:
    """
    Extracts valid (timestamp, hour_of_day, guest_count) tuples, sorted chronologically.
    Filters out malformed records or negative guests.
    """
    now_ref = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    parsed_items = []
    
    for idx, item in enumerate(history_window):
        if not isinstance(item, dict):
            continue
        
        # Extract guest count
        guests_raw = item.get("guests", item.get("count", item.get("occupancy", None)))
        if guests_raw is None:
            continue
        try:
            guests = int(round(float(guests_raw)))
            if guests < 0:
                continue
        except (ValueError, TypeError):
            continue
        
        # Extract timestamp / hour
        hour_raw = item.get("hour", item.get("timestamp", idx))
        ts = _parse_timestamp(hour_raw, now_ref, fallback_offset_hours=idx)
        hour_of_day = ts.hour
        parsed_items.append((ts, hour_of_day, guests))
        
    parsed_items.sort(key=lambda x: x[0])
    return parsed_items

def _impute_chronological_gaps(clean_items: List[Tuple[datetime, int, int]]) -> List[Tuple[datetime, int, int]]:
    """
    Detects non-consecutive hours (timestamp delta > 1h) and imputes intermediate
    missing hours using seasonal prior ratio scaling rather than zero-filling.
    """
    if len(clean_items) < 2:
        return clean_items
    
    full_sequence: List[Tuple[datetime, int, int]] = [clean_items[0]]
    for curr in clean_items[1:]:
        prev_ts, prev_h, prev_g = full_sequence[-1]
        curr_ts, curr_h, curr_g = curr
        
        # Calculate step in hours
        delta_hours = int(round((curr_ts - prev_ts).total_seconds() / 3600.0))
        if delta_hours > 1 and delta_hours <= 48:
            # Impute up to 48 missing intermediate hours
            prev_weight = max(1, get_seasonal_weight(prev_h))
            for step in range(1, delta_hours):
                imp_ts = prev_ts + timedelta(hours=step)
                imp_h = imp_ts.hour
                imp_weight = get_seasonal_weight(imp_h)
                imputed_guests = max(0, int(round(prev_g * (imp_weight / prev_weight))))
                full_sequence.append((imp_ts, imp_h, imputed_guests))
        
        full_sequence.append(curr)
        
    return full_sequence

def _compute_rolling_residuals(observations: List[Tuple[datetime, int, int]]) -> List[float]:
    """
    Generates rolling-origin out-of-sample residuals strictly starting at k=3 (1-based, index 2).
    At k=1: 0 prior points.
    At k=2: 1 prior point (zero degrees of freedom).
    At k=3: at least 2 prior points (w=min(k-1, 6)=2) to compute rolling baseline and dispersion.
    Yields M = N_valid - 2 residuals: [e3, e4, ..., eN].
    """
    residuals: List[float] = []
    n = len(observations)
    if n < 3:
        return residuals
    
    for k in range(2, n): # k starts at index 2 (observation 3)
        window_size = min(k, 6) # k prior observations available
        prior_obs = observations[k - window_size:k]
        
        mean_y = sum(obs[2] for obs in prior_obs) / float(window_size)
        mean_s = sum(get_seasonal_weight(obs[1]) for obs in prior_obs) / float(window_size)
        
        curr_h = observations[k][1]
        curr_s = get_seasonal_weight(curr_h)
        curr_y = observations[k][2]
        
        scaling_ratio = (curr_s / mean_s) if mean_s > 0 else 1.0
        expected_y = mean_y * scaling_ratio
        
        residual = curr_y - expected_y
        residuals.append(residual)
        
    return residuals

def _calculate_clipping_bounds(residuals: List[float]) -> Tuple[float, float, float, str]:
    """
    Computes discrete outlier clipping bounds using Hyndman-Fan Type 7 IQR.
    If IQR < 1.0 (discrete count binning / low-count collapse), falls back to MAD.
    If MAD == 0 (identical residuals), falls back to natural minimum integer dining variance.
    Returns (clip_lower, clip_upper, iqr, scale_mode).
    """
    q25 = hyndman_fan_type7(residuals, 0.25)
    q75 = hyndman_fan_type7(residuals, 0.75)
    iqr = q75 - q25
    
    if iqr >= 1.0:
        clip_lower = q25 - 1.5 * iqr
        clip_upper = q75 + 1.5 * iqr
        return clip_lower, clip_upper, iqr, "IQR"
    
    # Near-zero IQR fallback to MAD
    median_e = hyndman_fan_type7(residuals, 0.50)
    abs_deviations = [abs(e - median_e) for e in residuals]
    mad = hyndman_fan_type7(abs_deviations, 0.50)
    
    if mad > 0:
        s_eff = max(1.0, 1.4826 * mad)
        clip_lower = median_e - 2.5 * s_eff
        clip_upper = median_e + 2.5 * s_eff
        return clip_lower, clip_upper, iqr, "MAD_POSITIVE"
    else:
        # MAD == 0: all residuals are identical (zero dispersion).
        # Use scale S_eff = 2.0 (minimum natural integer dining variance)
        s_eff = 2.0
        clip_lower = median_e - 5.0
        clip_upper = median_e + 5.0
        return clip_lower, clip_upper, iqr, "MAD_ZERO"

def generate_traffic_forecast(history_window: Optional[List[Dict[str, Any]]] = None, horizon_hours: int = 12) -> List[Dict[str, Any]]:
    """
    Generates hourly customer footfall forecast with empirical residual quantiles (P10, P50, P90).
    Enforces minimum valid sample threshold N >= 12 (yielding M >= 10 out-of-sample residuals).
    When N < 12, triggers transparent domain seasonal prior fallback with explicit metadata.
    """
    now_ref = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    history_raw = history_window or []
    
    # 1. Clean, parse and sort historical observations
    clean_items = _sanitize_and_sort_history(history_raw)
    imputed_items = _impute_chronological_gaps(clean_items)
    n_valid = len(imputed_items)
    
    # Reference start for horizon forecasting
    last_ts = imputed_items[-1][0] if n_valid > 0 else now_ref
    
    # 2. Fallback check: Minimum valid sample requirement is N >= 12
    if n_valid < 12:
        forecast = []
        for h in range(1, horizon_hours + 1):
            target_time = last_ts + timedelta(hours=h)
            hour_of_day = target_time.hour
            base_val = get_seasonal_weight(hour_of_day)
            
            p50 = base_val
            p10 = max(0, int(round(base_val * 0.70)))
            p90 = int(round(base_val * 1.30))
            
            # Guarantee monotonicity
            p10 = max(0, p10)
            p50 = max(p10, p50)
            p90 = max(p50, p90)
            
            forecast.append({
                "hour": target_time.isoformat(),
                "p10": p10,
                "p50": p50,
                "p90": p90,
                "unit": "customers",
                "model_version": "traffic-seasonal-empirical-1.1.0",
                "algorithm": "Domain Seasonal Prior Fallback",
                "metadata": {
                    "fallback_applied": True,
                    "sample_count": n_valid,
                    "minimum_required": 12,
                    "reason": "INSUFFICIENT_SAMPLE_SIZE",
                    "disclaimer": "Predictions generated from calibrated hourly prior weights. Uncalibrated confidence intervals."
                }
            })
        return forecast

    # 3. Rolling-origin out-of-sample residual series starting at k=3
    residuals = _compute_rolling_residuals(imputed_items)
    m_residuals = len(residuals) # M = n_valid - 2
    
    # 4. Outlier detection and clamping bounds
    clip_lower, clip_upper, iqr_val, scale_mode = _calculate_clipping_bounds(residuals)
    clamped_residuals = [max(clip_lower, min(clip_upper, r)) for r in residuals]
    
    # 5. Hyndman-Fan Type 7 quantiles on clamped residuals
    q10_res = hyndman_fan_type7(clamped_residuals, 0.10)
    q50_res = hyndman_fan_type7(clamped_residuals, 0.50)
    q90_res = hyndman_fan_type7(clamped_residuals, 0.90)
    
    # 6. Baseline rolling average from most recent window
    recent_window = min(n_valid, 6)
    recent_obs = imputed_items[-recent_window:]
    recent_mean_y = sum(obs[2] for obs in recent_obs) / float(recent_window)
    recent_mean_s = sum(get_seasonal_weight(obs[1]) for obs in recent_obs) / float(recent_window)
    
    forecast = []
    for h in range(1, horizon_hours + 1):
        target_time = last_ts + timedelta(hours=h)
        hour_of_day = target_time.hour
        target_s = get_seasonal_weight(hour_of_day)
        
        ratio = (target_s / recent_mean_s) if recent_mean_s > 0 else 1.0
        expected_y = recent_mean_y * ratio
        
        # Apply empirical quantiles
        p50 = max(0, int(round(expected_y + q50_res)))
        p10 = max(0, int(round(expected_y + q10_res)))
        p90 = max(0, int(round(expected_y + q90_res)))
        
        # Mathematically enforce monotonicity: P10 <= P50 <= P90
        p10 = max(0, p10)
        p50 = max(p10, p50)
        p90 = max(p50, p90)
        
        forecast.append({
            "hour": target_time.isoformat(),
            "p10": p10,
            "p50": p50,
            "p90": p90,
            "unit": "customers",
            "model_version": "traffic-seasonal-empirical-1.1.0",
            "algorithm": "Empirical Residual Quantiles (Hyndman-Fan Type 7)",
            "metadata": {
                "fallback_applied": False,
                "sample_count": n_valid,
                "residual_count": m_residuals,
                "iqr": round(iqr_val, 4),
                "scale_mode": scale_mode,
                "clipping_bounds": [round(clip_lower, 4), round(clip_upper, 4)],
                "q10_residual": round(q10_res, 4),
                "q50_residual": round(q50_res, 4),
                "q90_residual": round(q90_res, 4),
                "disclaimer": "Empirical residual quantile intervals. Disclaims parametric Gaussian calibration."
            }
        })
        
    return forecast
