# RESTROVISION — AI-Assisted Restaurant Operations & Monitoring Platform

> Production Full-Stack Engineering Implementation derived from **RESTROVISION Master Specification v2.0**, **Oracle Core Schema v2.0**, **OpenAPI v1.0**, and **Unified AI Event Envelope v1.0**.

---

## 🏛️ System Architecture Overview

RESTROVISION combines restaurant operations (tables, digital orders, kitchen display, staff tasks, service calls) with computer vision inference on local CCTV cameras without external AI APIs or cloud video streaming.

```
                          RESTAURANT (On-Premises Edge)
                   ┌────────────────────────────────────────┐
                   │  CCTV / IP Cameras (RTSP Streams)      │
                   │                   │                    │
                   │                   ▼                    │
                   │  EDGE AI SERVICE (Python FastAPI)      │
                   │   - M1 Person Detector                 │
                   │   - M3 Table State Classifier          │
                   │   - Temporal State Engine (§6.3)       │
                   │   - Durable SQLite WAL Outbox (§9.3)   │
                   └───────────────────┬────────────────────┘
                                       │ HTTPS (Signed Events + HMAC-SHA256)
                                       │ WebSocket STOMP
                                       ▼
                   ┌────────────────────────────────────────┐
                   │  APPLICATION SERVER (Spring Boot 3.3)  │
                   │   - Multi-Tenant Business Rules (§6)   │
                   │   - Table State Machine (Staff > AI)   │
                   │   - Alert Escalation Engine (§12)      │
                   │   - Waiting Timer Scheduler (§4.2)     │
                   │   - Workload Balancer (§4.4)           │
                   │   - Dual DB: H2 (Dev) & Oracle 19c+    │
                   │   - STOMP WebSocket Broker (/ws)       │
                   └───────────────────┬────────────────────┘
                                       │
                      ┌────────────────┴────────────────┐
                      ▼                                 ▼
         ┌─────────────────────────┐       ┌─────────────────────────┐
         │  REACT 18 FRONTEND      │       │  CUSTOMER QR PORTAL     │
         │   - Floor Plan Grid     │       │   - Self-Service Call   │
         │   - Real-Time Alerts    │       │   - Water & Bill Req    │
         │   - Kitchen KDS Display │       │   - Verified Feedback   │
         │   - Waiter Workload     │       │   - M10 Sentiment NLP   │
         │   - CCTV Zone Simulator │       └─────────────────────────┘
         └─────────────────────────┘
```

---

## 🚀 Key Modules & Feature Implementation

| Specification Area | Implementation Detail | Source File |
|---|---|---|
| **Table State Machine (§6)** | Full 5-state lifecycle (`AVAILABLE`, `OCCUPIED`, `CLEANING`, `RESERVED`, `OUT_OF_SERVICE`). Precedence rule: **Staff beats AI**. Staff override locks AI for 120s. Optimistic locking with `@Version`. | `TableStateMachineService.java` |
| **Unified AI Event Envelope (§7.1)** | Ingests version 1.0 ULID events with HMAC-SHA256 signature verification (`X-Signature`), idempotency key check `(restaurant_id, event_uid)`, duplicate avoidance (`DUPLICATE_IGNORED`), atomic transaction commit. | `AiEventIngestionService.java`, `AiEventController.java` |
| **Alert Escalation Engine (§12)** | Deduplicated alerts (`dedupe_key`), automatic priority elevation, background scheduler for customer wait times (2m Attention, 5m Escalated, 10m Critical). | `AlertService.java`, `WaitingTimerScheduler.java` |
| **Continuous ML Feedback Loop (§8.3.5)** | 1-click "False Alarm" and "Wrong Status" buttons in Alert Center and Floor Plan log labeled hard negatives to `AI_FEEDBACK_LABELS` for retraining without noise. | `AlertController.java`, `AlertCenter.jsx` |
| **Kitchen Display System (KDS) (§4.1 F-05, F-06)** | Station queues (`Tandoor & Grill`, `Curry & Saute`, etc.), prep delay detector pulsing tickets in red when elapsed time > 150% expected duration. | `KitchenKds.jsx`, `OrderService.java` |
| **Waiter Workload Balancer (§4.4)** | `Workload = (occupiedTables * 1.0) + (pendingRequests * 1.5) + (readyOrders * 1.0) + (openTasks * 0.5)`. Detects overloaded servers vs shift median. | `WorkloadService.java`, `WaiterDashboard.jsx` |
| **Cleaning Sanitization Gate (§4.1 F-12, §6.1)** | AI vacancy detection automatically dispatches cleaning tasks; table returns to AVAILABLE **only** upon housekeeping sign-off. | `CleaningTasks.jsx`, `CleaningRecord.java` |
| **Customer QR Portal (§4.1 F-03b, F-20)** | Mobile portal for guests to call server, request bill, and leave star ratings with instant M10 sentiment topic analysis. | `CustomerQrPortal.jsx`, `PublicQrController.java` |
| **Durable SQLite Outbox (§9.3)** | Edge Python microservice stores events locally in SQLite WAL before transmission, with exponential retry backoff and HMAC payload signing. | `outbox.py`, `signer.py` |

---

## 🔑 Default Roles & Pilot Seed Credentials

The system seeds a full restaurant (*The Bombay Grill & Bistro*, ID: 1) with 10 tables, 5 kitchen stations, 15 menu items, 3 cameras, active orders, and personas:

| Role | Email | Password | Persona & Scope |
|---|---|---|---|
| **Owner** | `owner@restrovision.local` | `Admin@123` | Rajesh Singhania (Full Executive & Audit access) |
| **Manager** | `manager@restrovision.local` | `Manager@123` | Ananya Sharma (Floor status overrides, shift & zone configs) |
| **Waiter** | `waiter1@restrovision.local` | `Waiter@123` | Kunal Verma (Assigned tables, guest calls, food pickup) |
| **Waiter 2** | `waiter2@restrovision.local` | `Waiter@123` | Pooja Hegde (Table assignments & delivery) |
| **Kitchen** | `chef@restrovision.local` | `Chef@123` | Chef Vikram Rawat (Station queues, cooking timers) |
| **Cleaning** | `cleaning@restrovision.local` | `Clean@123` | Ramesh Das (Bussing & sanitation verification) |

---

## 🛠️ Quick Start Instructions

### Option 1: Standalone Local Run (Zero External Setup Required)

#### 1. Backend (Spring Boot 3.3 + Java 21/25)
```bash
cd backend
mvn clean package -DskipTests
java -jar target/restrovision-backend-2.0.0.jar
```
* Backend runs on: `http://localhost:8080`
* Interactive OpenAPI / Swagger UI: `http://localhost:8080/swagger-ui.html`
* H2 Console (Oracle Mode): `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:restrovision;MODE=Oracle`)

#### 2. Frontend (React 18 + Vite + Tailwind CSS)
```bash
cd frontend
npm install
npm run dev
```
* Frontend runs on: `http://localhost:5173` (proxies `/api` and `/ws` to `localhost:8080`)

#### 3. Edge AI Service (Python 3.11 + FastAPI)
```bash
cd edge-ai
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```
* Edge API runs on: `http://localhost:8000`
* Edge Docs: `http://localhost:8000/docs`

---

### Option 2: Production Docker Compose Deployment

```bash
# 1. Copy environment template and fill in production secrets
cp .env.example .env

# 2. Build and launch all production containers (Backend, Frontend Nginx, Edge AI, Oracle DB)
docker compose -f docker-compose.prod.yml up -d --build
```
* Frontend: `http://localhost:80` (or your domain via Nginx)
* Backend API: `http://localhost:8080`
* Edge AI Service: `http://localhost:8000`
* Oracle 23c/26ai Database: `localhost:1521` (Service: `freepdb1`)

For full multi-cloud instructions and free hosting platform evaluations, see [Deployment Platforms Guide](file:///D:/RESTROVISION/docs/guides/DEPLOYMENT_PLATFORMS_GUIDE.md).

---

## 📂 Repository Layout

```
RESTROVISION/
├── Backend/                       # Spring Boot 3.3.5 Enterprise Microservice (Java 21)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/restrovision/
│       ├── config/                # Security, WebSocket STOMP, HikariCP, Flyway
│       ├── domain/                # 22 JPA Entities (Hibernate 6 Oracle-hardened)
│       ├── repository/            # Multi-tenant isolated Spring Data Repositories
│       ├── service/               # State machine, AI ingestion, KDS, Workload
│       ├── controller/            # REST controllers matching OpenAPI v1.0
│       └── security/              # JWT provider, tenant filter, HMAC validator
├── frontend/                      # React 18 + Vite + Tailwind CSS SPA
│   ├── package.json
│   ├── vite.config.js
│   ├── Dockerfile
│   └── src/
│       ├── components/            # Floor plan grid, modals, navbar, sidebar
│       ├── context/               # AuthContext & role persona switcher
│       ├── services/              # API fetch client & STOMP WebSocket client
│       └── pages/                 # FloorPlan, AlertCenter, KitchenKds, Waiter, etc.
├── edge-ai/                       # Python 3.11 Edge AI Microservice
│   ├── requirements.txt
│   ├── Dockerfile
│   ├── app/                       # FastAPI, Temporal state engine, Outbox, Forecasters
│   ├── scripts/                   # Camera launcher scripts
│   └── tests/                     # Edge AI pytest test suite (53/53 passing)
├── database/                      # Oracle Production Migrations & DDL
│   ├── 01_RESTROVISION_Oracle_Core_Schema_v2.0.sql
│   ├── 02_RESTROVISION_Oracle_Seed_Data.sql
│   └── 03_RESTROVISION_Role_Permission_Matrix.csv
├── docs/                          # Organized Documentation & Operational Runbooks
│   ├── guides/                    # Deployment, camera, user manuals, and study guides
│   │   ├── DEPLOYMENT_PLATFORMS_GUIDE.md   # Comprehensive free hosting guide (OCI, Vercel, Render)
│   │   ├── CAMERA_DEPLOYMENT_GUIDE.md      # Edge camera hardware and RTSP setup
│   │   ├── USER_MANUAL.md                  # Comprehensive staff & manager operating guide
│   │   └── STUDY_GUIDE.md                  # Complete architectural and technical study guide
│   ├── audit_reports/             # Complete audit trails, verification logs, and reports
│   └── archive/                   # Preserved historical archives and legacy artifacts
├── .env.example                   # Complete production environment variable template
├── docker-compose.yml             # Development container composition
├── docker-compose.prod.yml        # Turnkey production multi-container composition
└── README.md                      # Project documentation and quick-start guide
```

---

## 🔒 Security & Privacy by Design (§14)
* **Zero Identity Tracking**: No facial recognition, no person re-identification across days or cameras.
* **Tenant Isolation**: Every table and method enforces `restaurant_id` filtering server-side.
* **Signed Edge Communication**: Edge requests use short-lived service tokens and HMAC-SHA256 body signatures.
* **Staff-over-AI Sovereignty**: AI only emits advisory observations; Spring Boot business rules govern state transitions with full audit trails.
