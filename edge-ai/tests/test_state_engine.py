try:
    import pytest
except ImportError:
    pass
from app.state.occupancy_engine import TemporalTableStateEngine
from app.events.signer import compute_hmac_signature

def test_temporal_occupancy_walk_by_rejected():
    engine = TemporalTableStateEngine(min_dwell_sec=15.0, occupancy_confirm_sec=20.0)

    # Person walking past between t=0 and t=6s (dwell < 15s)
    engine.update_frame(table_id=1, person_detected=True, timestamp=1000.0)
    engine.update_frame(table_id=1, person_detected=True, timestamp=1003.0)
    event = engine.update_frame(table_id=1, person_detected=True, timestamp=1006.0)
    assert event is None
    assert engine.table_states[1]["current_state"] == "AVAILABLE"

def test_temporal_occupancy_confirmed_with_missed_frame():
    engine = TemporalTableStateEngine(min_dwell_sec=15.0, occupancy_confirm_sec=20.0, occupancy_ratio_threshold=0.80)

    # Person seated at t=1000s, observed every 2s
    timestamps = [1000.0, 1002.0, 1004.0, 1006.0, 1008.0, 1010.0, 1012.0, 1014.0, 1016.0]
    # Simulate 1 missed detection frame at t=1008s (due to flicker / occlusion)
    detections = [True, True, True, True, False, True, True, True, True]

    event = None
    for ts, det in zip(timestamps, detections):
        ev = engine.update_frame(table_id=2, person_detected=det, timestamp=ts)
        if ev:
            event = ev

    # 8/9 frames are positive (88.8% >= 80%), and dwell = 16s (>= 15s)
    assert event == "TABLE_OCCUPIED"
    assert engine.table_states[2]["current_state"] == "OCCUPIED"

def test_vacancy_hold_time():
    engine = TemporalTableStateEngine(min_dwell_sec=15.0, vacancy_hold_sec=90.0)

    # Seat party
    engine.update_frame(table_id=3, person_detected=True, timestamp=1000.0)
    engine.update_frame(table_id=3, person_detected=True, timestamp=1016.0)
    assert engine.table_states[3]["current_state"] == "OCCUPIED"

    # Person steps away briefly to restroom (30s empty, dwell < 90s)
    event = engine.update_frame(table_id=3, person_detected=False, timestamp=1050.0)
    assert event is None

    # Person returns
    engine.update_frame(table_id=3, person_detected=True, timestamp=1060.0)

    # Person really leaves: observed empty from t=1100s to t=1195s (>90s)
    for t in range(1100, 1195, 5):
        event = engine.update_frame(table_id=3, person_detected=False, timestamp=float(t))

    assert event == "TABLE_VACATED"
    assert engine.table_states[3]["current_state"] == "AVAILABLE"

def test_hmac_signer():
    sig = compute_hmac_signature('{"test":"data"}')
    assert sig.startswith("sha256=")
    assert len(sig) > 20
