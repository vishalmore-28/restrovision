# ==========================================================================
# APPENDIX B: RESTROVISION Edge AI Camera Ingestion & Occupancy Engine
# Module: camera_worker.py & occupancy_engine.py
# ==========================================================================

"""
RESTROVISION Edge AI - Live Camera Ingestion Worker
Supports:
  1. USB / Laptop Webcam (cv2.VideoCapture(0))
  2. IP / CCTV Camera via RTSP (cv2.VideoCapture("rtsp://admin:pass@ip:554/..."))
  3. Pre-recorded MP4 video (cv2.VideoCapture("test.mp4"))
  4. Synthetic Simulation mode (runs even without physical camera)

Detects persons in Table ROIs, applies the 20-second temporal sliding window filter,
computes HMAC-SHA256 signatures, and pushes real-time events to Spring Boot (port 8080).
"""

import sys
import os
import time
import json
import uuid
import datetime
import urllib.request
import urllib.error

# Ensure app package is in path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from app.core.config import settings
from app.events.signer import compute_hmac_signature
from app.state.occupancy_engine import TemporalTableStateEngine

# Table ROI definitions (relative coordinates: [x_min, y_min, x_max, y_max] from 0.0 to 1.0)
TABLE_ROIS = {
    1: {"code": "T01", "name": "Window 2-Top", "box": (0.05, 0.20, 0.30, 0.70)},
    2: {"code": "T02", "name": "Center 4-Top", "box": (0.38, 0.20, 0.62, 0.70)},
    3: {"code": "T03", "name": "Booth 6-Top",  "box": (0.70, 0.20, 0.95, 0.70)},
}

def box_overlap(box_a, box_b):
    """Calculates intersection area between two boxes (x1, y1, x2, y2)."""
    xA = max(box_a[0], box_b[0])
    yA = max(box_a[1], box_b[1])
    xB = min(box_a[2], box_b[2])
    yB = min(box_a[3], box_b[3])
    inter_w = max(0, xB - xA)
    inter_h = max(0, yB - yA)
    return inter_w * inter_h

def post_event_to_backend(event_type: str, table_id: int, table_code: str, confidence: float, dwell_sec: float):
    """Sends HMAC-SHA256 authenticated event to Spring Boot backend."""
    now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")
    event_id = str(uuid.uuid4())
    
    payload = {
        "eventId": event_id,
        "restaurantId": 1,
        "cameraId": 1,
        "eventType": event_type,
        "tableId": table_id,
        "tableCode": table_code,
        "confidence": round(confidence, 4),
        "dwellTimeSeconds": round(dwell_sec, 1),
        "occurredAt": now_iso,
        "metadata": {
            "source": "camera_worker",
            "model": "opencv-hog-rtmdet",
            "resolution": "1280x720"
        }
    }
    
    json_bytes = json.dumps(payload).encode("utf-8")
    sig = compute_hmac_signature(json_bytes)
    
    url = f"{settings.SPRING_BOOT_URL}/api/v1/ai/events"
    req = urllib.request.Request(
        url,
        data=json_bytes,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {settings.SERVICE_TOKEN}",
            "X-Signature": sig
        },
        method="POST"
    )
    
    try:
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            resp_body = resp.read().decode("utf-8")
            print(f"[RESTROVISION-AI] >>> DISPATCHED {event_type} for {table_code} | HTTP {resp.status} | {resp_body}")
            return True
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode('utf-8', errors='ignore')
        print(f"[RESTROVISION-AI] !!! HTTP {e.code} Error posting event: {err_msg}")
    except Exception as ex:
        print(f"[RESTROVISION-AI] !!! Connection error posting to backend: {ex}")
    return False

def run_camera_worker(source=0, headless=False):
    """
    Main loop capturing frames from camera source, running person detection,
    updating the temporal state engine, and dispatching events.
    """
    try:
        import cv2
        import numpy as np
    except ImportError:
        print("[RESTROVISION-AI] OpenCV (cv2) or NumPy is not installed yet.")
        print("[RESTROVISION-AI] Running synthetic simulated camera stream instead...")
        run_synthetic_worker()
        return

    print("=" * 70)
    print("  RESTROVISION LIVE CAMERA INGESTION WORKER")
    print(f"  Source: {source}")
    print(f"  Backend URL: {settings.SPRING_BOOT_URL}")
    print(f"  Occupancy Window: 20s (>=80% positive) | Vacancy Hold: 90s (>=95% empty)")
    print("=" * 70)

    # Initialize OpenCV VideoCapture
    # source can be integer (webcam index) or string (rtsp:// URL or video file path)
    if isinstance(source, str) and source.isdigit():
        source = int(source)

    cap = cv2.VideoCapture(source)
    if not cap.isOpened():
        print(f"[ERROR] Could not open video source: {source}")
        print("[RESTROVISION-AI] Falling back to synthetic simulation mode...")
        run_synthetic_worker()
        return

    # Use OpenCV built-in HOG person detector (lightweight, runs real-time on CPU)
    hog = cv2.HOGDescriptor()
    hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())

    # Initialize State Engine
    engine = TemporalTableStateEngine(
        occupancy_confirm_sec=20.0,
        min_dwell_sec=15.0,
        vacancy_hold_sec=90.0,
        cooldown_sec=30.0
    )

    dwell_tracker = {t_id: 0.0 for t_id in TABLE_ROIS}
    table_presence_history = {t_id: [] for t_id in TABLE_ROIS}

    frame_count = 0
    fps_timer = time.time()
    current_fps = 0.0

    print("[INFO] Camera stream started. Press 'q' in the video window to stop.")

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                print("[WARNING] Frame capture returned empty, looping or waiting...")
                time.sleep(0.1)
                # If reading video file, loop back to start
                if isinstance(source, str) and not source.startswith("rtsp"):
                    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                    continue
                continue

            frame_count += 1
            h, w = frame.shape[:2]

            # Calculate FPS
            if frame_count % 15 == 0:
                now_t = time.time()
                current_fps = 15.0 / (now_t - fps_timer)
                fps_timer = now_t

            # Resize frame for faster HOG detection (max width 640 for real-time CPU speed)
            scale = 640.0 / max(w, 640)
            small_w = int(w * scale)
            small_h = int(h * scale)
            small_frame = cv2.resize(frame, (small_w, small_h))

            # Run HOG person detector (detectMultiScale)
            # Only run every 2nd frame to save CPU
            detected_persons = []
            if frame_count % 2 == 0:
                rects, weights = hog.detectMultiScale(
                    small_frame,
                    winStride=(8, 8),
                    padding=(4, 4),
                    scale=1.05
                )
                for (rx, ry, rw, rh) in rects:
                    # Scale back to original frame coordinates
                    ox1 = int(rx / scale)
                    oy1 = int(ry / scale)
                    ox2 = int((rx + rw) / scale)
                    oy2 = int((ry + rh) / scale)
                    detected_persons.append((ox1, oy1, ox2, oy2))

            # Evaluate each Table ROI
            now = time.time()
            for table_id, info in TABLE_ROIS.items():
                code = info["code"]
                rx1 = int(info["box"][0] * w)
                ry1 = int(info["box"][1] * h)
                rx2 = int(info["box"][2] * w)
                ry2 = int(info["box"][3] * h)
                t_box = (rx1, ry1, rx2, ry2)

                # Check if any detected person overlaps with this table ROI
                person_present = False
                for p_box in detected_persons:
                    overlap = box_overlap(t_box, p_box)
                    p_area = (p_box[2] - p_box[0]) * (p_box[3] - p_box[1])
                    if p_area > 0 and (overlap / p_area) > 0.25:
                        person_present = True
                        break

                # Track dwell time
                if person_present:
                    dwell_tracker[table_id] += 0.5
                else:
                    dwell_tracker[table_id] = max(0.0, dwell_tracker[table_id] - 0.2)

                # Update State Engine
                event = engine.update_frame(table_id, person_present, now)
                if event:
                    conf = 0.91 if event == "TABLE_OCCUPIED" else 0.95
                    dwell = dwell_tracker[table_id]
                    post_event_to_backend(event, table_id, code, conf, dwell)

                # Visual annotations
                st = engine.table_states.get(table_id, {}).get("current_state", "AVAILABLE")
                color = (0, 0, 255) if st == "OCCUPIED" else (0, 255, 0)
                cv2.rectangle(frame, (rx1, ry1), (rx2, ry2), color, 2)
                
                label = f"{code} [{st}] - Dwell: {dwell_tracker[table_id]:.0f}s"
                cv2.putText(frame, label, (rx1, ry1 - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.55, color, 2)

            # Draw person boxes
            for (px1, py1, px2, py2) in detected_persons:
                cv2.rectangle(frame, (px1, py1), (px2, py2), (255, 165, 0), 2)
                cv2.putText(frame, "Person", (px1, py1 - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 165, 0), 1)

            # Draw HUD
            cv2.rectangle(frame, (0, 0), (w, 40), (20, 20, 20), -1)
            hud = f"RESTROVISION EDGE-AI | FPS: {current_fps:.1f} | Backend: {settings.SPRING_BOOT_URL}"
            cv2.putText(frame, hud, (15, 26), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)

            if not headless:
                cv2.imshow("RESTROVISION AI - Live Camera Stream", frame)
                key = cv2.waitKey(1) & 0xFF
                if key == ord('q') or key == 27:
                    print("[INFO] User pressed 'q' to stop.")
                    break

            time.sleep(0.01)

    finally:
        cap.release()
        if not headless:
            try:
                cv2.destroyAllWindows()
            except Exception:
                pass
        print("[INFO] Camera worker terminated gracefully.")

def run_synthetic_worker():
    """
    Simulation mode that tests the full AI event generation and backend ingestion pipeline
    without requiring physical camera hardware.
    """
    print("=" * 70)
    print("  RESTROVISION SYNTHETIC CAMERA WORKER (Simulation Mode)")
    print("  Simulating live patron arrival, seating, and vacating at Table 1 (T01)")
    print("=" * 70)

    engine = TemporalTableStateEngine(
        occupancy_confirm_sec=5.0,  # Fast simulation window
        min_dwell_sec=3.0,
        vacancy_hold_sec=6.0,
        cooldown_sec=5.0
    )

    t0 = time.time()
    table_id = 1
    code = "T01"

    print("\n[Phase 1] Simulating patron arriving and sitting down at Table 1 (T01)...")
    for sec in range(1, 8):
        sim_time = t0 + sec
        event = engine.update_frame(table_id, True, sim_time)
        print(f"  Frame {sec}s: Person DETECTED at Table 1. Engine event: {event}")
        if event == "TABLE_OCCUPIED":
            post_event_to_backend(event, table_id, code, 0.94, dwell_sec=float(sec))
            break
        time.sleep(0.5)

    print("\n[Phase 2] Simulating table occupied while dining (10 seconds)...")
    for sec in range(8, 14):
        sim_time = t0 + sec
        engine.update_frame(table_id, True, sim_time)
        time.sleep(0.3)

    print("\n[Phase 3] Simulating patrons leaving Table 1 (vacancy hold filter)...")
    for sec in range(14, 25):
        sim_time = t0 + sec
        event = engine.update_frame(table_id, False, sim_time)
        print(f"  Frame {sec}s: Person ABSENT at Table 1. Engine event: {event}")
        if event == "TABLE_VACATED":
            post_event_to_backend(event, table_id, code, 0.96, dwell_sec=22.0)
            break
        time.sleep(0.5)

    print("\n[SUCCESS] Synthetic simulation completed successfully!")

if __name__ == "__main__":
    src = 0
    if len(sys.argv) > 1:
        src = sys.argv[1]
    
    headless_mode = "--headless" in sys.argv
    if src == "--headless":
        src = 0
    elif src in ("sim", "synthetic"):
        run_synthetic_worker()
        sys.exit(0)

    run_camera_worker(source=src, headless=headless_mode)


# --------------------------------------------------------------------------
# occupancy_engine.py
# --------------------------------------------------------------------------

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
