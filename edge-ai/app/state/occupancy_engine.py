import time
from typing import Dict, List, Optional, Tuple

class TemporalTableStateEngine:
    """
    Implements §6.3 Temporal State Engine rules:
    - Occupancy confirm window: 20 s with >= 80% frames positive
    - Minimum dwell for 'seated': 15 s stationary in zone
    - Vacancy hold time: 90 s with >= 95% frames empty
    - Event cooldown per table: 30 s
    """
    def __init__(self,
                 occupancy_confirm_sec: float = 20.0,
                 min_dwell_sec: float = 15.0,
                 vacancy_hold_sec: float = 90.0,
                 cooldown_sec: float = 30.0,
                 occupancy_ratio_threshold: float = 0.80,
                 vacancy_ratio_threshold: float = 0.95):
        self.occupancy_confirm_sec = occupancy_confirm_sec
        self.min_dwell_sec = min_dwell_sec
        self.vacancy_hold_sec = vacancy_hold_sec
        self.cooldown_sec = cooldown_sec
        self.occupancy_ratio_threshold = occupancy_ratio_threshold
        self.vacancy_ratio_threshold = vacancy_ratio_threshold

        # table_id -> state record
        self.table_states: Dict[int, Dict] = {}

    def update_frame(self, table_id: int, person_detected: bool, timestamp: Optional[float] = None) -> Optional[str]:
        """
        Feeds a camera frame observation into the temporal sliding window filter.
        Returns 'TABLE_OCCUPIED', 'TABLE_VACATED', or None.
        """
        now = timestamp if timestamp is not None else time.time()

        if table_id not in self.table_states:
            self.table_states[table_id] = {
                "current_state": "AVAILABLE",
                "last_event_time": 0.0,
                "history": [],  # List of (timestamp, person_detected)
            }

        st = self.table_states[table_id]
        history: List[Tuple[float, bool]] = st["history"]
        history.append((now, person_detected))

        # Check cooldown
        if (now - st["last_event_time"]) < self.cooldown_sec:
            return None

        if st["current_state"] == "AVAILABLE":
            # Prune history older than occupancy_confirm_sec (20s)
            cutoff = now - self.occupancy_confirm_sec
            st["history"] = [h for h in history if h[0] >= cutoff]
            cur_history = st["history"]

            if len(cur_history) >= 2:
                earliest_time = cur_history[0][0]
                dwell_duration = now - earliest_time

                # Check minimum dwell time (15s)
                if dwell_duration >= self.min_dwell_sec:
                    total_frames = len(cur_history)
                    pos_frames = sum(1 for h in cur_history if h[1])
                    ratio = pos_frames / total_frames

                    # Check >= 80% frames positive
                    if ratio >= self.occupancy_ratio_threshold:
                        st["current_state"] = "OCCUPIED"
                        st["last_event_time"] = now
                        st["history"].clear()
                        return "TABLE_OCCUPIED"

        elif st["current_state"] == "OCCUPIED":
            # Prune history older than vacancy_hold_sec (90s)
            cutoff = now - self.vacancy_hold_sec
            st["history"] = [h for h in history if h[0] >= cutoff]
            cur_history = st["history"]

            if len(cur_history) >= 2:
                earliest_time = cur_history[0][0]
                hold_duration = now - earliest_time

                # Check vacancy hold time (90s)
                if hold_duration >= self.vacancy_hold_sec:
                    total_frames = len(cur_history)
                    empty_frames = sum(1 for h in cur_history if not h[1])
                    ratio = empty_frames / total_frames

                    # Check >= 95% frames empty
                    if ratio >= self.vacancy_ratio_threshold:
                        st["current_state"] = "AVAILABLE"
                        st["last_event_time"] = now
                        st["history"].clear()
                        return "TABLE_VACATED"

        return None
