# RESTROVISION Platform — Complete User Manual & System Guide
**Version 2.0 (Production Edition)**  
*AI-Assisted Restaurant Operations, Computer Vision Monitoring, Kitchen Display & Guest Self-Service*

---

## Table of Contents
1. [Platform Overview & Philosophy](#1-platform-overview--philosophy)
2. [Architecture & Data Flow](#2-architecture--data-flow)
3. [User Roles & Default Evaluation Accounts](#3-user-roles--default-evaluation-accounts)
4. [Role & Permission Matrix](#4-role--permission-matrix)
5. [Feature-by-Feature Operational Guide](#5-feature-by-feature-operational-guide)
   - 5.1 [Floor Plan & Table Management](#51-floor-plan--table-management)
   - 5.2 [Kitchen Display System (KDS)](#52-kitchen-display-system-kds)
   - 5.3 [Server & Waiter Dashboard](#53-server--waiter-dashboard)
   - 5.4 [Central Alert Center](#54-central-alert-center)
   - 5.5 [Housekeeping & Cleaning Tasks](#55-housekeeping--cleaning-tasks)
   - 5.6 [CCTV Vision & Edge Analytics](#56-cctv-vision--edge-analytics)
   - 5.7 [Executive Analytics & Sentiment BI](#57-executive-analytics--sentiment-bi)
   - 5.8 [Customer QR Self-Service Portal](#58-customer-qr-self-service-portal)
6. [Edge AI Ingestion & Security Engine](#6-edge-ai-ingestion--security-engine)
7. [API & Swagger Reference](#7-api--swagger-reference)
8. [Deployment & Troubleshooting](#8-deployment--troubleshooting)
9. [Connecting Cameras & Live Vision Ingestion](#9-connecting-cameras--live-vision-ingestion)

---

## 1. Platform Overview & Philosophy

**RESTROVISION** is an enterprise-grade hospitality operations platform combining Computer Vision edge intelligence, real-time STOMP WebSockets, and role-based workflows. It streamlines table turnover, monitors guest wait times, coordinates kitchen production, and maintains dining floor safety.

### Core Architectural Principles

> [!IMPORTANT]
> **AI Emits Evidence, Not Truth**  
> Computer Vision models are advisory sensors. They submit detection observations with confidence scores ($0.0 \dots 1.0$), but staff members always maintain supreme override authority.

* **Strict Confidence Gate ($\ge 0.70$)**: Ingestion pipelines automatically reject low-confidence detections (`REJECTED_BY_RULE`) so transient anomalies never disrupt dining operations.
* **Privacy by Design**: Edge vision models process video frames strictly in local RAM ring buffers. No biometric face identifiers, facial recognition databases, or persistent guest portraits are ever stored to disk or transmitted to the cloud.
* **State Machine Consistency**: Tables, dining sessions, and kitchen orders follow strict, mathematically valid state lifecycles to prevent impossible states (such as double-seating or phantom orders).
* **Multi-Tenant Isolation**: Every database row, API call, and WebSocket channel is hard-scoped by `restaurantId`. Edge node authentication credentials dictate tenant association, preventing spoofing.

---

## 2. Architecture & Data Flow

```mermaid
flowchart TD
    subgraph Edge["On-Premise Restaurant Edge (CCTV)"]
        Cam["IP RTSP Cameras"] --> Node["Edge AI Compute (YOLO/ONNX)"]
        Node --> Signer["HMAC-SHA256 Signer"]
        Signer --> Outbox["SQLite Outbox Queue"]
    end

    subgraph Backend["Spring Boot Core Engine (:8080)"]
        Outbox -- "HTTPS POST /api/v1/ai/events" --> Gateway["AI Ingestion Gateway"]
        Gateway --> AuthFilter["HMAC & Token Security Filter"]
        AuthFilter --> StateEngine["Table State Machine & Scheduler"]
        StateEngine --> DB[("Oracle / H2 DB Engine")]
        StateEngine --> Broker["STOMP WebSocket Broker"]
    end

    subgraph Clients["Staff & Guest Applications"]
        Broker -- "/topic/restaurants/{id}/**" --> UI["React 18 Operations Console (:5173 / :4173)"]
        GuestMobile["Guest Smartphone"] -- "Scan Table QR" --> QRPortal["Public QR Portal (/qr)"]
        QRPortal --> Gateway
    end
```

---

## 3. User Roles & Default Evaluation Accounts

The platform includes five pre-configured operational roles pre-seeded in the database:

| Role | Email | Password | Primary Mission |
| :--- | :--- | :--- | :--- |
| **Owner / Exec** | `owner@restrovision.local` | `Admin@123` | Executive visibility across revenue, floor occupancy, service speed, and sentiment analytics. |
| **Floor Manager** | `manager@restrovision.local` | `Manager@123` | Floor supervision, manual table overrides, alert triage, CCTV camera & zone configuration. |
| **Server / Waiter** | `waiter1@restrovision.local` | `Waiter@123` | Customer seating, table order entry, guest attendance timers, and food pickup delivery. |
| **Head Chef (KDS)** | `chef@restrovision.local` | `Chef@123` | Managing preparation queues across Kitchen Stations, marking ticket prep status and pass readiness. |
| **Housekeeping** | `cleaning@restrovision.local` | `Clean@123` | Receiving busing alerts when guests depart, table sanitization, and resetting tables to `AVAILABLE`. |
| **Guest / Diner** | *Public / Anonymous* | *No password* | Table QR scanning for instant waiter calls, water refills, bill requests, and verified star reviews. |

---

## 4. Role & Permission Matrix

RESTROVISION enforces method-level security (`@PreAuthorize`) across every controller:

| Resource / Action | Owner | Manager | Waiter | Kitchen | Cleaning | Public QR |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **View Floor Plan & Tables** | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| **Seat Guests & Attend Tables** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Override Table to OUT_OF_SERVICE** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Transition CLEANING $\rightarrow$ AVAILABLE** | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ |
| **Create & Update Dining Orders** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Kitchen KDS & Item Status Updates** | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ |
| **View Camera Feeds & CCTV Zones** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Acknowledge & Resolve Alerts** | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| **View Executive Analytics & Feedback** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Request Water, Waiter, or Bill** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Submit Post-Meal Rating (1–5 Stars)** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

---

## 5. Feature-by-Feature Operational Guide

### 5.1 Floor Plan & Table Management
* **Route**: `/` (Floor Plan tab)
* **Real-Time Subscription**: `/topic/restaurants/{id}/tables`

The Floor Plan displays a live graphical grid of dining tables. Each table card indicates its code (`T01`, `T02`), capacity, assigned server, current status, and attentiveness timer.

#### Table Statuses
* 🟢 **`AVAILABLE`**: Clean, sanitized, and ready to receive walk-in guests or reservations.
* 🔵 **`OCCUPIED`**: Guests seated. If unattended, a live timer counts seconds from seating.
* 🟡 **`CLEANING`**: Guests have vacated; table requires busing and sanitization.
* 🟣 **`RESERVED`**: Table held for upcoming confirmed bookings.
* ⚪ **`OUT_OF_SERVICE`**: Maintenance or physical issue (accessible only to Managers).

#### Staff Operations
1. **Seating Guests**: Click **Seat Party** on any `AVAILABLE` table, enter the party size, and assign a server. The table instantly locks into `OCCUPIED` and opens an active `TableSession`.
2. **Attending Guests**: As soon as a waiter greets the table, clicking **Attend** stops the countdown timer and records attendance timestamps for service speed analytics.
3. **Wait-Time Escalation**:
   * **0–119 seconds**: Normal service window (neutral badge).
   * **120–299 seconds (2 min)**: `ATTENTION` level (yellow badge).
   * **300–599 seconds (5 min)**: `ESCALATED` level (orange alert sent to Manager).
   * **600+ seconds (10 min)**: `CRITICAL` emergency alert (red badge, manager escalation).

---

### 5.2 Kitchen Display System (KDS)
* **Route**: Switch to the **Kitchen KDS** tab
* **Real-Time Subscription**: `/topic/restaurants/{id}/orders`

The KDS replaces physical paper slips with digital ticket cards grouped by preparation station:
* **Tandoor & Grill**
* **Curry & Sauté**
* **Cold Appetizers & Salads**
* **Beverage Bar**
* **Dessert Station**

#### Order Ticket Lifecycle
1. **`CONFIRMED`**: Order entered by waitstaff or QR portal. Appears in the kitchen queue.
2. **`IN_PREPARATION`**: Chef clicks **Start Prep**. The timer begins tracking against target prep duration.
3. **`READY`**: Chef clicks **Ready at Pass**. The order moves to the pickup area, and an automatic notification alerts the assigned server to deliver the dishes.
4. **`SERVED`**: Server marks food delivered to table. Ticket archives automatically.

> [!TIP]
> **Delayed Order Flagging**: If ticket preparation time exceeds $1.5 \times$ the menu item's expected prep time, the ticket pulses red to notify the head chef of a station bottleneck.

---

### 5.3 Server & Waiter Dashboard
* **Route**: Switch to the **Waiter** tab
* **Target Users**: Servers, Waitstaff, Runners

Provides servers with a focused view of their assigned section:
* **My Tables**: Quick overview of occupied tables and guest counts.
* **Ready for Pickup**: Real-time alerts when kitchen stations mark orders ready at the pass.
* **Table Service Calls**: Audible/visual banners when guests tap the table QR code to request water, service, or bills.
* **Taking Orders**: One-click menu browser to add items, specify quantities, and dispatch tickets directly to kitchen stations.

---

### 5.4 Central Alert Center
* **Route**: Switch to the **Alerts** tab
* **Real-Time Subscription**: `/topic/restaurants/{id}/alerts`

The Alert Center aggregates operational and safety notices into a prioritized queue:

| Priority | Color | Trigger Sources |
| :--- | :---: | :--- |
| **`CRITICAL`** | 🔴 Red | Slip & fall detection, unattended table $> 10$ minutes. |
| **`HIGH`** | 🟠 Orange | Unattended table $> 5$ minutes, floor spill/hazard, unauthorized zone entry. |
| **`MEDIUM`** | 🟡 Yellow | Customer wave gesture detected, order ready at pass, unattended table $> 2$ minutes. |
| **`LOW`** | 🔵 Blue | Camera reconnected, routine system health updates. |

#### Triaging Alerts
* **Acknowledge**: Lets other staff members know an alert is being actively handled.
* **Resolve**: Closes the alert when action is complete.
* **Mark False Alarm**: Tags the alert as a false positive and records human feedback (`ai_feedback_labels`) to train future edge model weights.

---

### 5.5 Housekeeping & Cleaning Tasks
* **Route**: Switch to the **Cleaning** tab
* **Real-Time Subscription**: `/topic/restaurants/{id}/tasks`

When the Computer Vision model detects that diners have stood up and departed (`TABLE_VACATED`), it automatically creates a housekeeping task:
1. Table code and location are prominently displayed.
2. Cleaning staff tap **Start Cleaning** when wiping down the table.
3. Upon completion, tapping **Mark Completed** automatically resets the table status from `CLEANING` to `AVAILABLE` on the live floor plan.

---

### 5.6 CCTV Vision & Edge Analytics
* **Route**: Switch to the **Cameras & CCTV** tab
* **Target Users**: Floor Managers, Owners

Monitors on-premise edge vision pipelines:
* **Live Stream Feeds**: RTSP stream status, camera hardware name, and target framerate (2.0 FPS for low-overhead inference).
* **Configured Polygon ROI Zones**: Visual boundaries over dining tables and floor corridors.
* **Edge Node Gateway Health**: Status of connected Jetson / edge boxes with active HMAC-SHA256 signature verification badges.
* **Live Ingested Edge Events**: Real-time audit log of the 50 most recent vision detections processed by the Spring Boot backend.

---

### 5.7 Executive Analytics & Sentiment BI
* **Route**: Switch to the **Analytics** tab
* **Target Users**: General Managers, Franchise Owners

Provides honest, multi-tenant performance indicators:
* **Floor Occupancy Rate**: Real percentage of active occupied seats vs total capacity.
* **Median Wait Time to First Attendance**: Calculated strictly from active dining session attendance deltas. Returns `0` when no sessions are recorded (no fabricated fallbacks).
* **Customer Sentiment Index**: Positive vs negative distribution derived from natural language processing on customer reviews.
* **Review Categories**: Auto-categorized guest feedback into Food Quality, Service Speed, Cleanliness, or Ambience.

---

### 5.8 Customer QR Self-Service Portal
* **Route**: Switch to the **Customer QR** tab (or direct URL `/qr`)
* **Guest Credentials**: None required (public endpoint)

Simulates guest table sticker interactions:
1. **Table Selector**: Switch between verified table tokens (`qr-tok-table-01` through `qr-tok-table-10`).
2. **Assistance Buttons**:
   * 🔔 **Call Assigned Server**: Generates an instant high-priority service request.
   * 💧 **Request Water Refill**: Dispatches a water replenishment request.
   * 🧾 **Request Final Bill**: Informs servers that diners are ready to settle the check.
3. **Dining Experience Review**:
   * Interactive 1 to 5 star rating (validated server-side; values outside $[1, 5]$ are rejected).
   * Comment box analyzed by sentiment NLP to extract customer satisfaction metrics.

---

## 6. Edge AI Ingestion & Security Engine

### Unified AI Event Envelope (v1.0)
Edge nodes transmit observations via HTTP `POST` to `/api/v1/ai/events`:

```json
{
  "schemaVersion": "1.0",
  "eventId": "01J9XABC1234567890DEFGHJKM",
  "edgeNodeId": "edge-pune-01",
  "restaurantId": 1,
  "cameraId": 1,
  "zoneId": 1,
  "tableId": 1,
  "eventType": "TABLE_OCCUPIED",
  "confidence": 0.94,
  "occurredAt": "2026-11-13T16:20:00.000Z",
  "detectedAt": "2026-11-13T16:20:00.120Z",
  "modelVersions": {
    "person": "person-det-1.3.0",
    "tablestate": "table-cls-1.1.0"
  },
  "data": {
    "framesUsed": 40,
    "dwellSeconds": 22,
    "estimatedPartySize": 2
  },
  "snapshotRef": "snap/1/01J9XABC1234567890DEFGHJKM.jpg"
}
```

### Mandatory Ingestion Headers
* `Authorization`: `Bearer edge-token-pune-01`
* `X-Signature`: `sha256=<hmac_hex_of_raw_request_bytes>`
* `Content-Type`: `application/json`

---

## 7. API & Swagger Reference

The complete interactive OpenAPI specification is available at:
**`http://localhost:8080/swagger-ui.html`**

### Summary of REST Controllers

| Controller | Base Path | Functionality |
| :--- | :--- | :--- |
| **`AuthController`** | `/api/v1/auth` | User login, JWT token refresh, current user profile `/me`, logout |
| **`TableController`** | `/api/v1/tables` | Table lists, party seating, attendance markers, manual status overrides |
| **`OrderController`** | `/api/v1/orders` | Order creation, lifecycle transitions, delayed order monitoring |
| **`KitchenController`** | `/api/v1/kitchen` | Kitchen queue per station, preparation start, pass ready markers |
| **`AiEventController`** | `/api/v1/ai/events` | Edge AI event ingestion (HMAC protected) and recent event audit |
| **`AlertController`** | `/api/v1/alerts` | Alert retrieval, acknowledgment, resolution, false-alarm tracking |
| **`PublicQrController`** | `/api/v1/public` | Unguessable QR token lookup, waiter calls, customer star feedback |
| **`AnalyticsController`** | `/api/v1/analytics` | Tenant overview metrics and customer review aggregations |
| **`CameraController`** | `/api/v1/cameras` | Camera device list, polygon zones, and maintenance mode toggling |
| **`EmployeeController`**| `/api/v1/employees` | Staff roster and real-time workload score calculations |
| **`TaskController`** | `/api/v1/tasks` | Staff task assignment and completion tracking |
| **`ServiceRequestController`** | `/api/v1/service-requests` | Guest assistance request queue management |

---

## 8. Deployment & Troubleshooting

### Running via Terminal
* **Backend**:
  ```powershell
  java -jar C:\restrovision\backend\target\restrovision-backend-2.0.0.jar
  ```
* **Frontend**:
  ```powershell
  cd C:\restrovision\frontend
  npm run dev
  ```

### Common Questions & Fixes

1. **Port 8080 already in use**:
   ```powershell
   Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess -Force -ErrorAction SilentlyContinue
   ```
2. **Missing `node_modules` after extracting a fresh ZIP**:
   Run `npm install` inside the extracted frontend folder once before running `npm run dev` or `npm run preview`.
3. **Database Reset**:
   The default profile uses an in-memory H2 database operating in Oracle compatibility mode (`MODE=Oracle`). Stopping and restarting the backend automatically seeds a pristine pilot dataset via `DataInitializer.java`.

---

## 9. Connecting Cameras & Live Vision Ingestion

The Edge AI layer supports direct ingestion from physical hardware, network CCTV cameras, or video simulations.

### Camera Connection Modes

| Mode | Source Argument | Example Command | Typical Use Case |
| :--- | :--- | :--- | :--- |
| **Laptop / USB Webcam** | `0` or `1` | `python app/camera/camera_worker.py 0` | Local testing, laptop webcam, USB webcams on POS |
| **RTSP IP Camera** | RTSP URL | `python app/camera/camera_worker.py "rtsp://admin:pass@192.168.1.100:554/stream1"` | Commercial Hikvision, Dahua, Axis CCTV dining floor cameras |
| **MP4 Video File** | File path | `python app/camera/camera_worker.py "cctv_sample.mp4"` | Offline evaluation, pre-recorded restaurant datasets |
| **Synthetic Simulation**| `sim` | `python app/camera/camera_worker.py sim` | Automated testing of the full pipeline without physical camera |

### How It Works Under the Hood

```mermaid
flowchart LR
    Cam["Camera (Webcam / RTSP)"] --> OpenCV["OpenCV Frame Capture"]
    OpenCV --> Detect["Person Detector (HOG / YOLO)"]
    Detect --> ROI["Table ROI Spatial Filter"]
    ROI --> Temp["20s Sliding Window State Engine"]
    Temp --> Signer["HMAC-SHA256 Signer"]
    Signer --> Ingest["POST /api/v1/ai/events (:8080)"]
    Ingest --> UI["Real-time WebSocket Push to UI"]
```

1. **Table Region of Interest (ROI)**: Coordinates in `TABLE_ROIS` define where tables are located in the camera's field of view.
2. **Person Detection**: Persons are detected using OpenCV's built-in HOG descriptor or YOLO models.
3. **Temporal Filtering**: The `TemporalTableStateEngine` tracks person presence over a 20-second sliding window ($\ge 80\%$ positive frames required to transition to `OCCUPIED`), eliminating false triggers from walking staff or patrons passing by.
4. **Automated Event Push**: When occupancy is confirmed, the worker signs the payload with HMAC-SHA256 and dispatches it directly to `http://localhost:8080/api/v1/ai/events`. The Spring Boot backend accepts the event, updates the table state, and broadcasts the transition to the floor plan UI via STOMP WebSockets in milliseconds.

### Quick Start via Batch Launcher
Double-click `start_camera.bat` in `D:\RESTROVISION\` or run:
```powershell
D:\RESTROVISION\start_camera.bat
```
Select `[1]` for Laptop Webcam, `[3]` for IP RTSP Camera, or `[5]` for Simulation Mode.

