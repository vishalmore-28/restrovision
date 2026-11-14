# RESTROVISION — Complete Camera Deployment, Edge AI & Architecture Learning Guide

**Version**: 2.0 Production & Educational Edition  
**Target Audience**: Developers, DevOps Engineers, System Integrators & Restaurant Technicians  

---

## Table of Contents
1. [Core Architecture: Why Edge AI Instead of Cloud Video?](#1-core-architecture-why-edge-ai-instead-of-cloud-video)
2. [Hardware & Network Topology](#2-hardware--network-topology)
3. [Connecting the Physical Camera via RTSP](#3-connecting-the-physical-camera-via-rtsp)
4. [Deep Dive: Spatial Regions of Interest (ROI) & Overlap Math](#4-deep-dive-spatial-regions-of-interest-roi--overlap-math)
5. [The Temporal State Engine: Solving Real-World False Positives](#5-the-temporal-state-engine-solving-real-world-false-positives)
6. [Zero-Trust Security: Cryptographic HMAC-SHA256 Signatures](#6-zero-trust-security-cryptographic-hmac-sha256-signatures)
7. [Spring Boot Backend: Ingestion Gateway & WebSocket Broadcasting](#7-spring-boot-backend-ingestion-gateway--websocket-broadcasting)
8. [React Frontend: Real-Time UI & Role-Based Security](#8-react-frontend-real-time-ui--role-based-security)
9. [Step-by-Step Production Deployment Blueprint](#9-step-by-step-production-deployment-blueprint)
10. [Diagnostic Cheat Sheet & Command Reference](#10-diagnostic-cheat-sheet--command-reference)

---

## 1. Core Architecture: Why Edge AI Instead of Cloud Video?

When designing an AI-driven vision system for physical restaurants, the biggest architectural mistake is streaming raw camera video over the public internet to a cloud server.

### Cloud Video Streaming vs. Edge Compute Architecture

| Consideration | Cloud Video Streaming (Antipattern) | On-Premise Edge AI (RESTROVISION Architecture) |
| :--- | :--- | :--- |
| **Internet Bandwidth** | 4 cameras $\times$ 4 Mbps = **16 Mbps continuous upload**. Easily saturates restaurant internet and causes POS dropouts. | **Zero continuous video upload**. Only micro-events ($< 1\text{ KB}$ JSON) sent when state changes occur. |
| **Cloud Hosting Bill** | Thousands of dollars/month in video ingestion and GPU compute hours. | **Zero cloud GPU cost**. Runs on an affordable on-premise mini-PC ($200–$400 one-time cost). |
| **Network Failure Behavior** | If the internet glitches, detection stops completely and video drops. | If the internet goes down, the Edge PC keeps detecting and buffers events in its local SQLite outbox queue until reconnected. |
| **Privacy & GDPR/DPDP** | Continuous video streams of dining patrons transmitted over public networks. | **Privacy-by-Design**: Frames are processed strictly in RAM ring buffers. No faces or guest portraits are ever saved or uploaded. |
| **Latency** | $1.5\text{s} \dots 5.0\text{s}$ streaming and buffering lag. | **Real-time**: Local inference runs at $< 20\text{ms}$ latency. |

```mermaid
flowchart TD
    subgraph OnPremise["Physical Restaurant (Local High-Speed Gigabit LAN)"]
        Cam1["CCTV Dome Camera (Dining Floor)"] -->|RTSP 1080p Stream| Edge["On-Premise Edge Device (Mini-PC / Jetson)"]
        Cam2["CCTV Bullet Camera (Kitchen Pass)"] -->|RTSP 1080p Stream| Edge
        Webcam["Counter USB Webcam"] -->|USB 3.0| Edge
        
        Edge -->|RAM Buffer 2-5 FPS| YOLO["OpenCV / YOLO / RTMDet Detector"]
        YOLO -->|Bounding Boxes| StateMachine["Temporal Sliding Window Engine"]
        StateMachine -->|Confirmed Table Event| Signer["HMAC-SHA256 Signer"]
    end

    subgraph Cloud["Deployed Cloud Infrastructure (AWS / Railway / VPS)"]
        Signer -->|"POST /api/v1/ai/events (JSON < 1 KB)"| SpringBoot["Spring Boot Core Engine (:8080)"]
        SpringBoot --> DB[("Database")]
        SpringBoot --> WebSocket["STOMP / SockJS WebSocket Broker"]
    end

    subgraph Clients["Staff Dashboards (Any Device, Any Location)"]
        WebSocket -->|Push in < 50ms| ManagerUI["Floor Manager Tablet"]
        WebSocket -->|Push in < 50ms| WaiterUI["Waiter Mobile Phone"]
        WebSocket -->|Push in < 50ms| KDSUI["Head Chef Kitchen Screen"]
    end
```

---

## 2. Hardware & Network Topology

### A. Recommended Camera Specs
Any camera supporting the standard **RTSP (Real-Time Streaming Protocol)** or **ONVIF** protocol works seamlessly:
* **Resolution**: 1080p (Full HD, $1920 \times 1080$) is optimal. 4K is unnecessary and increases compute load.
* **Lens / FOV**: $2.8\text{mm}$ wide-angle lens (approx. $105^\circ$ field of view) mounted at ceiling height ($2.5\text{m} \dots 3.2\text{m}$), tilted downward at $35^\circ \dots 45^\circ$.
* **Brands**: Hikvision, Dahua, CP PLUS, Axis, Reolink, TP-Link VIGI, Uniview, UniFi Protect.
* **Power**: Power-over-Ethernet (PoE) 802.3af switch simplifies cabling (single Cat6 cable carries both power and network).

### B. Recommended Edge Compute Hardware
* **Budget Option ($150–$250)**: Intel NUC or Mini PC (Intel N100 / i3 8GB RAM, Windows 11 or Ubuntu Server). Runs OpenCV HOG/MobileNet at 2–4 FPS per stream across 4 cameras simultaneously.
* **Mid-Tier Option ($300–$500)**: Mini PC with AMD Ryzen 5 or Intel i5 (16GB RAM). Runs YOLOv8n / RTMDet via ONNX Runtime with OpenVINO.
* **High-End AI Option ($600+)**: NVIDIA Jetson Orin Nano (TensorRT-FP16 acceleration, sub-10ms inference).

---

## 3. Connecting the Physical Camera via RTSP

### What is RTSP?
RTSP (Real Time Streaming Protocol) is a network control protocol designed for entertainment and surveillance systems. It acts as a standardized video pipe over port `554`.

### Common Commercial RTSP Stream URLs

| Camera Brand | Stream 1 (High Res) | Stream 2 (Sub-Stream / Recommended for AI) |
| :--- | :--- | :--- |
| **Hikvision** | `rtsp://admin:Password@192.168.1.64:554/Streaming/Channels/101` | `rtsp://admin:Password@192.168.1.64:554/Streaming/Channels/102` |
| **Dahua / CP PLUS** | `rtsp://admin:Password@192.168.1.108:554/cam/realmonitor?channel=1&subtype=0` | `rtsp://admin:Password@192.168.1.108:554/cam/realmonitor?channel=1&subtype=1` |
| **Reolink** | `rtsp://admin:Password@192.168.1.50:554/h264Preview_01_main` | `rtsp://admin:Password@192.168.1.50:554/h264Preview_01_sub` |
| **Axis** | `rtsp://root:Password@192.168.1.20/axis-media/media.amp` | `rtsp://root:Password@192.168.1.20/axis-media/media.amp?resolution=640x360` |

> [!TIP]
> **Use the Sub-Stream for AI Inference**  
> IP cameras generate two simultaneous streams:
> 1. **Main Stream (1080p/4K)**: Used for NVR recordings and security monitors.
> 2. **Sub-Stream (640x360 or 720p)**: Ideal for computer vision inference. It reduces CPU/GPU decoding load by over 70% while maintaining detection accuracy.

### Testing Your RTSP Stream
Before running the AI worker, verify that your camera stream is accessible:

1. **Using VLC Media Player**:
   * Open VLC $\rightarrow$ Click `Media` $\rightarrow$ `Open Network Stream` (`Ctrl+N`).
   * Enter your RTSP URL (e.g., `rtsp://admin:Pass123@192.168.1.64:554/Streaming/Channels/102`).
   * If video displays cleanly, your network credentials and RTSP port are valid.

2. **Using Python OpenCV**:
   ```python
   import cv2

   rtsp_url = "rtsp://admin:Pass123@192.168.1.64:554/Streaming/Channels/102"
   cap = cv2.VideoCapture(rtsp_url)

   # Optimize buffer size to eliminate stream lag
   cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

   while True:
       ret, frame = cap.read()
       if not ret:
           print("Failed to grab frame from RTSP stream")
           break
       
       cv2.imshow("Live Feed", frame)
       if cv2.waitKey(1) & 0xFF == ord('q'):
           break

   cap.release()
   cv2.destroyAllWindows()
   ```

---

## 4. Deep Dive: Spatial Regions of Interest (ROI) & Overlap Math

### What is a Normalized ROI?
Different cameras output different pixel dimensions ($1280 \times 720$, $1920 \times 1080$, $640 \times 480$). If you define table boundaries in absolute pixels (e.g., `x=150, y=200`), resizing the stream breaks the mapping.

RESTROVISION uses **Normalized Relative Coordinates**:
$$\text{Coordinates} = [x_{\min}, y_{\min}, x_{\max}, y_{\max}] \quad \text{where } 0.0 \le \text{coord} \le 1.0$$

* `(0.0, 0.0)` is the top-left corner of the camera frame.
* `(1.0, 1.0)` is the bottom-right corner.

To convert normalized coordinates to actual frame pixels in real time:
$$\text{Pixel } X = x_{\text{norm}} \times \text{Frame Width}$$
$$\text{Pixel } Y = y_{\text{norm}} \times \text{Frame Height}$$

### Table ROI Configuration Example (`TABLE_ROIS` in `camera_worker.py`)
```python
TABLE_ROIS = {
    1: {"code": "T-01", "name": "Window 2-Top", "box": (0.05, 0.20, 0.30, 0.70)},
    2: {"code": "T-02", "name": "Center 4-Top", "box": (0.38, 0.20, 0.62, 0.70)},
    3: {"code": "T-03", "name": "Booth 6-Top",  "box": (0.70, 0.20, 0.95, 0.70)},
}
```

### Overlap Calculation: When is a Person "At" a Table?
When the AI detects a person bounding box $B_p = (x_{p1}, y_{p1}, x_{p2}, y_{p2})$ and a table ROI $B_t = (x_{t1}, y_{t1}, x_{t2}, y_{t2})$, the intersection area is calculated as:

$$\text{Intersection Width} = \max(0, \min(x_{p2}, x_{t2}) - \max(x_{p1}, x_{t1}))$$
$$\text{Intersection Height} = \max(0, \min(y_{p2}, y_{t2}) - \max(y_{p1}, y_{t1}))$$
$$\text{Intersection Area} = \text{Intersection Width} \times \text{Intersection Height}$$

If $\frac{\text{Intersection Area}}{\text{Person Bounding Box Area}} \ge 0.25$ (25% spatial overlap), the person is registered as present in that table's dining zone.

---

## 5. The Temporal State Engine: Solving Real-World False Positives

Raw person detections fluctuate frame by frame. Three common challenges occur in restaurant settings:
1. **The Walk-By**: A waiter or dining guest walks past Table 2 to reach the restroom. They are inside the table's bounding box for 2 seconds. *A naive detector would falsely mark the table as OCCUPIED.*
2. **Detection Flicker / Occlusion**: A seated guest reaches for salt or turns around, and for one frame the detector misses their torso. *A naive detector would instantly mark the table as VACATED.*
3. **The Restroom Break**: A dining guest steps away to wash their hands for 90 seconds, leaving their coat and drink at the table. *A naive detector would wipe the dining session and call housekeeping to clean the table.*

### How RESTROVISION Solves This: The `TemporalTableStateEngine`

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE
    
    AVAILABLE --> POTENTIALLY_OCCUPIED: Person detected in ROI
    POTENTIALLY_OCCUPIED --> AVAILABLE: Person leaves before 15s (Walk-by rejected!)
    POTENTIALLY_OCCUPIED --> OCCUPIED: Person present >= 15s (80% frame consistency)
    
    OCCUPIED --> OCCUPIED: Frame flicker / 1-2 missed frames tolerated
    OCCUPIED --> POTENTIALLY_VACATED: Person absent
    POTENTIALLY_VACATED --> OCCUPIED: Person returns within Vacancy Hold Window (120s)
    POTENTIALLY_VACATED --> CLEANING: Person absent > Vacancy Hold Window (Table Confirmed Vacated)
    
    CLEANING --> AVAILABLE: Housekeeping sanitizes & confirms clean
```

#### Key Temporal Parameters:
* **Occupancy Confirmation Window ($15.0\text{s}$)**: Requires consistent person presence ($\ge 80\%$ positive frames) across 15 seconds before triggering `TABLE_OCCUPIED`.
* **Vacancy Hold Window ($120.0\text{s}$)**: Sustains the dining session for 2 minutes when the table becomes empty, ensuring guests stepping away do not have their table cleared.
* **Staff Precedence Override Lock ($120.0\text{s}$)**: When a human floor manager manually sets a table status in the UI, the AI detection is locked out for 120 seconds, ensuring human staff commands take priority over AI vision.

---

## 6. Zero-Trust Security: Cryptographic HMAC-SHA256 Signatures

Because Edge AI workers transmit events across local networks or the internet to your cloud backend, **never trust unauthenticated IoT requests**. An attacker or unauthorized device on the local Wi-Fi could forge JSON events (e.g., falsely marking all tables as occupied).

### How HMAC-SHA256 Signing Works

1. The Edge AI worker and the Spring Boot backend share a secure secret token (e.g., `SERVICE_TOKEN`).
2. When an event is generated, the Edge AI worker serializes the JSON payload into exact bytes.
3. It computes a Hash-based Message Authentication Code using SHA-256:
   $$\text{Signature} = \text{HMAC-SHA256}(\text{Secret Key}, \text{JSON Bytes})$$
4. The worker attaches the signature in the HTTP header:
   ```http
   POST /api/v1/ai/events HTTP/1.1
   Host: api.restrovision.com
   Authorization: Bearer edge-service-secret-token
   X-Signature: sha256=dd57e4c15323def02...
   Content-Type: application/json

   {
     "eventId": "01JEDGE1794602918",
     "restaurantId": 1,
     "cameraId": 1,
     "eventType": "TABLE_OCCUPIED",
     "tableCode": "T-01",
     "confidence": 0.94,
     "dwellTimeSeconds": 15.2,
     "occurredAt": "2026-11-14T02:20:00Z"
   }
   ```
5. **Spring Boot Verification**:
   Before reading the event body, the backend recalculates the HMAC using its copy of the secret.
   * If even a single byte, character, or timestamp was tampered with in transit, the hashes will not match, and Spring Boot rejects the request with `HTTP 401 / 403 Forbidden`.
   * If the hashes match, the event is guaranteed authentic and unaltered.

---

## 7. Spring Boot Backend: Ingestion Gateway & WebSocket Broadcasting

Once the HMAC signature is verified by `AiEventController`, the Spring Boot backend performs three operations:

1. **Database Update**:
   Updates the table row in the database:
   ```sql
   UPDATE tables 
   SET status = 'OCCUPIED', 
       status_source = 'CCTV_AI', 
       updated_at = CURRENT_TIMESTAMP 
   WHERE table_id = 1;
   ```
2. **Audit Event Log**:
   Persists the raw detection envelope into `ai_event_audit` for operational auditing, confidence analytics, and false-alarm machine learning re-training.
3. **STOMP WebSocket Broadcast**:
   Pushes the updated table model over the in-memory WebSocket broker:
   ```java
   messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/tables", updatedTable);
   ```

---

## 8. React Frontend: Real-Time UI & Role-Based Security

### How the Frontend Receives WebSocket Updates
In [`FloorPlan.jsx`](file:///D:/RESTROVISION/frontend/src/pages/FloorPlan.jsx), the component subscribes to the STOMP channel on mount:

```javascript
useEffect(() => {
  // 1. Initial REST fetch for current floor layout
  loadTables();

  // 2. Real-time WebSocket subscription
  const unsub = wsService.subscribe('/topic/restaurants/1/tables', (updatedData) => {
    if (Array.isArray(updatedData)) {
      setTables(updatedData);
    } else {
      // Single table updated
      setTables((prev) =>
        prev.map((t) => (t.tableId === updatedData.tableId ? updatedData : t))
      );
    }
  });

  return () => unsub();
}, []);
```
When the Edge AI worker registers a customer, the table card changes to **Orange (`OCCUPIED`)** in the browser in real time without refreshing the page.

### Role Security (RBAC) Enforcement
Junior personnel only see views relevant to their responsibilities:

```
[Level 5] Restaurant Owner  --> Analytics & KPIs, Floor Plan, KDS, Waiters, Cleaning, CCTV, QR Portal
[Level 4] Floor Manager     --> Floor Plan, Alert Center, Kitchen KDS, Waiters, Cleaning, CCTV, QR Portal
[Level 3] Head Chef (KDS)   --> Kitchen KDS, Cleaning Tasks, Alert Center, CCTV AI Zones
[Level 2] Server / Waiter   --> Waiter Station, Live Floor Plan, Customer QR Demo
[Level 1] Housekeeping      --> Cleaning Tasks, Live Floor Plan
```

---

## 9. Step-by-Step Production Deployment Blueprint

### Step 1: Deploy Backend & Database
1. Build the Spring Boot executable JAR:
   ```powershell
   Set-Location "D:\RESTROVISION\Backend"
   mvn clean package -DskipTests
   ```
2. Run the JAR with production environment variables:
   ```powershell
   java -jar target/restrovision-backend-2.0.0.jar --server.port=8080
   ```

### Step 2: Build & Deploy Frontend
1. Build the production React bundle:
   ```powershell
   Set-Location "D:\RESTROVISION\frontend"
   npm run build
   ```
2. Serve the `dist/` directory via Nginx, Caddy, or your hosting provider (Vercel, Netlify, AWS S3/CloudFront).

### Step 3: Set Up the Edge AI Device in the Restaurant
1. Place a Mini-PC or Jetson on the restaurant's local network (connected via Ethernet to the CCTV router).
2. Configure `.env` in `D:\RESTROVISION\edge-ai\.env`:
   ```properties
   SPRING_BOOT_URL=https://api.your-deployed-restrovision.com
   SERVICE_TOKEN=your-production-hmac-token-secret
   EDGE_NODE_ID=edge-pune-branch-01
   ```
3. Run the camera worker:
   * **Direct RTSP Feed**:
     ```powershell
     python app/camera/camera_worker.py "rtsp://admin:Pass@192.168.1.100:554/stream1"
     ```
   * **Or Run as a Background Windows Service**:
     Install `NSSM` (Non-Sucking Service Manager) to keep the camera worker running automatically on system boot:
     ```powershell
     nssm install RestrovisionCameraWorker "C:\Python311\python.exe" "D:\RESTROVISION\edge-ai\app\camera\camera_worker.py" "rtsp://admin:Pass@192.168.1.100:554/stream1"
     nssm set RestrovisionCameraWorker AppStdout "D:\RESTROVISION\logs\camera_stdout.log"
     nssm set RestrovisionCameraWorker AppStderr "D:\RESTROVISION\logs\camera_stderr.log"
     nssm start RestrovisionCameraWorker
     ```

---

## 10. Diagnostic Cheat Sheet & Command Reference

### Quick Commands

| Action | Command |
| :--- | :--- |
| **Verify Edge AI Unit Tests** | `python edge-ai/run_tests.py` |
| **Run Camera Simulation Mode** | `python edge-ai/app/camera/camera_worker.py sim` |
| **Run Laptop Webcam Mode** | `python edge-ai/app/camera/camera_worker.py 0` |
| **Run Commercial RTSP Camera** | `python edge-ai/app/camera/camera_worker.py "rtsp://admin:pass@IP:554/stream1"` |
| **Inspect AI Outbox Queue** | `python -c "from app.events.outbox import outbox; print(outbox.get_pending_events())"` |
| **Test NLP Sentiment Engine** | `python -c "from app.nlp.sentiment import analyze_sentiment; print(analyze_sentiment('Great service!'))"` |
| **Test Traffic Forecaster** | `python -c "from app.forecast.traffic import generate_traffic_forecast; print(generate_traffic_forecast([], 4))"` |

---

### Common Troubleshooting Scenarios

1. **RTSP Stream Times Out (`Connection refused` or `Host unreachable`)**:
   * Verify the camera and Edge PC are on the same subnet (e.g. `192.168.1.xxx`).
   * Ping the camera IP: `ping 192.168.1.100`.
   * Check if RTSP port `554` is open: `Test-NetConnection -ComputerName 192.168.1.100 -Port 554`.
   * Open the camera's web configuration page in a browser and verify that RTSP is enabled in Network Services.

2. **Edge Events Rejected by Backend (`HTTP 403 Forbidden`)**:
   * The `SERVICE_TOKEN` on the Edge device does not match `application.properties` on the Spring Boot server. Ensure both shared secrets match.
   * Check system clocks. If the Edge device's clock drifts by more than 5 minutes, HMAC timestamp verification may reject the event.

3. **High CPU Usage on Edge Device**:
   * Switch the RTSP URL from the camera's Main Stream ($1080\text{p}$) to its Sub-Stream ($640 \times 360$).
   * Add a frame throttle in `camera_worker.py` (e.g., `time.sleep(0.2)` to process at 5 FPS instead of 30 FPS, which is sufficient for dining occupancy detection).
