# RESTROVISION v2.0 — Production Deployment & Operational Runbook

**Document ID:** `DEPLOY-CHECKLIST-20261115-FINAL`  
**Application Target:** RESTROVISION v2.0 Production Deployment  
**Target Environment:** Oracle Linux 9 / Ubuntu 22.04 LTS / Windows Server 2022  
**Target Database:** Oracle Database 26ai Free (PDB `FREEPDB1`)  
**Container Engine:** Docker Engine $\ge 24.0$ / Docker Compose $\ge v2.20$  
**Date:** 2026-11-15T00:20:00+05:30  

---

## 1. Pre-Deployment Infrastructure Checklist

Before initiating the deployment sequence, the Infrastructure / Operations team must verify:

| Check | Item Description | Verification Command / Target | Pass Criteria |
| :---: | :--- | :--- | :--- |
| [ ] | **Host Port 8080 Availability** | `netstat -ano \| findstr 8080` | Free (Backend API / Actuator) |
| [ ] | **Host Port 80 / 5173 Availability**| `netstat -ano \| findstr :80` | Free (Frontend Nginx SPA) |
| [ ] | **Host Port 8000 Availability** | `netstat -ano \| findstr 8000` | Free (Edge AI Service) |
| [ ] | **Host Port 1521 Availability** | `netstat -ano \| findstr 1521` | Oracle TNS Listener listening |
| [ ] | **Docker Engine Active** | `docker info` | Active, Linux container mode |
| [ ] | **JDK 21 Installed** | `java -version` | OpenJDK 21 LTS |
| [ ] | **Node.js 22 Installed** | `node -v` | v22.x LTS |
| [ ] | **Python 3.11+ Installed** | `python --version` | Python 3.11 or 3.13 |

---

## 2. Oracle Database 26ai Free DBA Provisioning Guide

The application requires a dedicated schema user `restrovision` in pluggable database `FREEPDB1`. The Oracle DBA must execute the following commands before starting the backend with the Oracle profile.

### Step 2.1: Connect as SYSDBA via SQL*Plus
```bash
sqlplus / as sysdba
```

### Step 2.2: Switch to Pluggable Database `FREEPDB1`
```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
SHOW CON_NAME;
-- Expected output: CON_NAME = FREEPDB1
```

### Step 2.3: Create Application Schema User & Tablespace Quota
```sql
-- 1. Create restrovision schema user
CREATE USER restrovision IDENTIFIED BY "restrovision123"
  DEFAULT TABLESPACE users
  TEMPORARY TABLESPACE temp
  QUOTA UNLIMITED ON users;

-- 2. Grant necessary operational privileges
GRANT CREATE SESSION TO restrovision;
GRANT CREATE TABLE TO restrovision;
GRANT CREATE VIEW TO restrovision;
GRANT CREATE SEQUENCE TO restrovision;
GRANT CREATE PROCEDURE TO restrovision;
GRANT CREATE TRIGGER TO restrovision;
GRANT CREATE SYNONYM TO restrovision;

-- 3. Verify user status
SELECT username, account_status, default_tablespace 
FROM dba_users 
WHERE username = 'RESTROVISION';

EXIT;
```

### Step 2.4: Validate TNS Connection String
```bash
sqlplus restrovision/restrovision123@localhost:1521/freepdb1
-- Expected output: Connected to: Oracle Database 26ai Free ...
```

---

## 3. Flyway Database Migration Sequence

Once the DBA has provisioned user `restrovision`, execute the versioned migrations.

### Option A: Execute Migrations via Maven CLI
```powershell
Set-Location "D:\RESTROVISION\Backend"
mvn flyway:migrate -Poracle -Dflyway.user=restrovision -Dflyway.password=restrovision123
```

### Option B: Automatic Migration on Container Startup
When the container starts with profile `oracle`, Spring Boot executes Flyway automatically:
- **Migration V1:** `V1__init_core_schema.sql` (Creates all 33 core tables, indexes, constraints)
- **Migration V2:** `V2__add_optimistic_locking_version.sql` (Adds `@Version` optimistic locking columns to `table_sessions`, `staff_tasks`, and `service_requests`)

### Verification of Applied Migrations:
```sql
SELECT version, description, type, installed_on, success 
FROM restrovision.flyway_schema_history 
ORDER BY installed_rank;
```
Expected output:
- `1` | `init core schema` | `SQL` | `SUCCESS` (1)
- `2` | `add optimistic locking version` | `SQL` | `SUCCESS` (1)

---

## 4. Production Secrets & Configuration Matrix

Populate the following environment variables in production (`/etc/environment`, Docker secrets, or cloud secret manager):

| Environment Variable | Recommended Production Value | Description | Default Fallback |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `oracle` | Activates Oracle production datasource profile | `default` (H2) |
| `ORACLE_HOST` | `oracle-db.internal` | Oracle database hostname / IP | `localhost` |
| `ORACLE_PORT` | `1521` | Oracle TNS listener port | `1521` |
| `ORACLE_SERVICE` | `freepdb1` | Oracle PDB service name | `freepdb1` |
| `ORACLE_USERNAME` | `restrovision` | Application database user | `restrovision` |
| `ORACLE_PASSWORD` | *[Strong Secret $> 16$ chars]* | Application database password | `restrovision123` |
| `RESTROVISION_JWT_SECRET`| *[64-byte hex key]* | Signs and verifies user and device JWT tokens | Built-in test secret |
| `RESTROVISION_EDGE_HMAC_SECRET` | *[64-byte hex key]* | Signs and validates edge-to-cloud AI ingestion payloads | Built-in test secret |
| `RESTROVISION_COOKIE_SECURE` | `true` | Enforces `Secure` flag on HttpOnly cookies under HTTPS | `false` |
| `RESTROVISION_TRUSTED_PROXIES` | `10.0.0.0/8,172.16.0.0/12` | CIDR blocks of upstream load balancers / Nginx | `127.0.0.1,::1` |

---

## 5. Container Orchestration Deployment Procedure

### Step 5.1: Build & Launch Multi-Container Stack
```bash
# Navigate to repository root
cd /opt/restrovision

# Build multi-stage images and start in background
docker compose up --build -d
```

### Step 5.2: Verify Container Health & Status
```bash
docker compose ps
```
Expected output:
```
NAME                    IMAGE                   COMMAND                  SERVICE    STATUS
restrovision-backend    restrovision-backend    "java -XX:+UseContai…"   backend    Up (healthy)
restrovision-frontend   restrovision-frontend   "nginx -g 'daemon of…"   frontend   Up (healthy)
restrovision-edge-ai    restrovision-edge-ai    "uvicorn app.main:ap…"   edge-ai    Up (healthy)
```

### Step 5.3: Execute Live Health Probes
```bash
# 1. Backend Health & Readiness
curl -f http://localhost:8080/actuator/health
# Expected: {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"},"ping":{"status":"UP"}}}

# 2. Frontend Nginx Health Probe
curl -f http://localhost:80/health
# Expected: healthy

# 3. Edge AI Live Probe
curl -f http://localhost:8000/ai/v1/health/live
# Expected: {"status":"UP","service":"restrovision-ai","node":"edge-pune-01"}

# 4. Edge AI Ready Probe
curl -f http://localhost:8000/ai/v1/health/ready
# Expected: {"status":"READY","modelsLoaded":6,"camerasActive":3,"outboxWritable":true}
```

---

## 6. TLS / Reverse Proxy & Ingress Termination

Production deployments must terminate TLS (HTTPS / WSS) in front of Nginx:

1. **Certificate Files:** Obtain valid CA certificate (`fullchain.pem`) and private key (`privkey.pem`).
2. **Reverse Proxy Configuration:** Forward `Host`, `X-Real-IP`, `X-Forwarded-For`, and `X-Forwarded-Proto: https`.
3. **WebSocket Upgrade Headers:**
   ```nginx
   proxy_http_version 1.1;
   proxy_set_header Upgrade $http_upgrade;
   proxy_set_header Connection "Upgrade";
   proxy_read_timeout 86400s;
   ```
4. **HSTS Header:** Add `add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;`.

---

## 7. Rollback Strategy & Disaster Recovery Runbook

### Scenario A: Container Deployment Failure / Bad Code Push
If a container fails to achieve `healthy` status:
```bash
# 1. Revert container instances to previous stable tag
docker compose down
git checkout HEAD~1
docker compose up --build -d

# 2. Validate health probes
curl -f http://localhost:8080/actuator/health
```

### Scenario B: Database Migration Rollback
Flyway versioned migrations are non-destructive and additive.
- `V1`: Core schema tables
- `V2`: Adds nullable `@Version` columns with default 0

If rollback is required:
```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
-- To drop tables cleanly if initial installation failed
DROP USER restrovision CASCADE;
-- Recreate user and reapply tested migration scripts
```

### Scenario C: Camera Edge Node Disconnection
The Edge AI service contains a durable SQLite Write-Ahead-Log outbox (`app.events.outbox`). If the backend network becomes unavailable:
1. Edge node accumulates inferred table and gesture events in `edge_outbox.sqlite`.
2. Outbox background worker retries delivery with exponential backoff (max 10 attempts).
3. Backend accepts historical events within the 7,200s (2-hour) freshness window upon network restoration.
4. Duplicate events are atomically rejected via `uq_ai_event` unique constraint without data corruption.
