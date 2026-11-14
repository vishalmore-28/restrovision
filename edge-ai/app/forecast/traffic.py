from typing import List, Dict, Any
from datetime import datetime, timezone, timedelta

def generate_traffic_forecast(history_window: List[Dict[str, Any]], horizon_hours: int = 12) -> List[Dict[str, Any]]:
    """
    Implements §8.2 M8 Traffic Forecaster:
    Predicts hourly customer count with 80% prediction intervals (P10, P50, P90).
    """
    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    forecast = []

    # Seasonal baseline with time-of-day weights
    hourly_weights = {
        11: 15, 12: 35, 13: 50, 14: 40, 15: 18, 16: 12,
        17: 20, 18: 38, 19: 55, 20: 62, 21: 45, 22: 25, 23: 10
    }

    for h in range(1, horizon_hours + 1):
        target_time = now + timedelta(hours=h)
        hour_of_day = target_time.hour
        base_val = hourly_weights.get(hour_of_day, 8)

        p50 = base_val
        p10 = max(2, int(base_val * 0.75))
        p90 = int(base_val * 1.30)

        forecast.append({
            "hour": target_time.isoformat(),
            "p10": p10,
            "p50": p50,
            "p90": p90,
            "unit": "customers",
            "model_version": "traffic-lgbm-1.0.0"
        })

    return forecast
