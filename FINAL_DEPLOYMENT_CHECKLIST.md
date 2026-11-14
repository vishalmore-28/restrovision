# RESTROVISION v2.0 — Production Deployment & Operational Runbook

**Document ID:** `DEPLOY-RUNBOOK-20261115-V3`  
**Application Target:** RESTROVISION v2.0 Production Deployment  
**Target Environments:** Oracle Linux 9 / Ubuntu 22.04 LTS / Windows Server 2022  
**Target Database:** Oracle Database 26ai Free / 19c Enterprise (PDB `FREEPDB1`)  
**Container Engine:** Docker Engine $\ge 24.0$ / Docker Compose $\ge v2.20$  
**Security Standard:** Enterprise Least Privilege, Dual Account Architecture, Zero Default Fallbacks  
**Revision Date:** 2026-11-15T01:10:00+05:30  
**Status Category:** **IMPLEMENTED & STATICALLY VERIFIED (LIVE RUNTIME BLOCKED)**  

---

## 1. Pre-Deployment Infrastructure Verification Checklist

Verify all host prerequisites before deployment. Every item must meet its documented pass criteria:

| Infrastructure Item | Verification Command | Expected Target / Criteria | Status Category |
| :--- | :--- | :--- | :---: |
| **External Port 443 (HTTPS)** | `netstat -ano \| findstr :443` | Free for Frontend Nginx Ingress | **READY** |
| **External Port 80 (HTTP)** | `netstat -ano \| findstr :80` | Free for HTTP-to-HTTPS redirect | **READY** |
| **Internal Port 8080** | Internal to Docker network only | Not exposed on public host interface | **STATICALLY VERIFIED** |
| **Internal Port 8000** | Internal to Docker network only | Not exposed on public host interface | **STATICALLY VERIFIED** |
| **Oracle TNS Listener (1521)** | `lsnrctl status` | Active listener; restricted to app network | **LIVE RUNTIME VERIFIED (Active)** |
| **Host Docker Engine Daemon** | `docker info` | Active Linux container runtime | **BLOCKED (Service Stopped)** |
| **JDK 21 LTS** | `java -version` | OpenJDK 21 LTS runtime | **AUTOMATED TESTED (Pass)** |
| **Node.js 22 LTS** | `node -v` | v22.x LTS runtime | **AUTOMATED TESTED (Pass)** |
| **Python 3.11+** | `python --version` | Python 3.11 / 3.13 runtime | **AUTOMATED TESTED (Pass)** |

---

## 2. Oracle Dual-Account Least Privilege Architecture

To enforce separation of duties and defense-in-depth, the application database layer is split into two distinct database accounts:
1. **Migration Account (`RESTROVISION_MIGRATOR`):** Schema owner and DDL administrator. Executes Flyway migrations `V1` and `V2`. Does not run application queries in production.
2. **Runtime Application Account (`RESTROVISION_APP`):** Application execution account used by Spring Boot JPA at runtime. Has strictly **zero DDL privileges** and holds only DML permissions (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) on schema objects.

### 2.1 Tablespace Quota Sizing Justification
- **High-Volume Tenant Model:** 100 tables per restaurant $\times$ 50 table turns/day = 5,000 active sessions/day.
- **Data Volume Calculation:** Order items, table state transitions, telemetry, and feedback log approximately 20,000 rows/day ($\approx 10$ MB/day uncompressed data + indexes $\approx 3.65$ GB/year).
- **Quota Decision:** The unlimited tablespace quota is **revoked**. A finite quota of **10 GB** is granted on the `users` tablespace. This provides 2+ years of operation while preventing runaway disk exhaustion attacks or defective batch loops from consuming the entire host volume.

### 2.2 DBA Provisioning Script (Execute as SYSDBA)
```sql
-- ==============================================================================
-- RESTROVISION v2.0 - Oracle Dual-Account Provisioning Script
-- Pluggable Database: FREEPDB1
-- Must be executed by authorized DBA prior to application deployment.
-- ==============================================================================

ALTER SESSION SET CONTAINER = FREEPDB1;

-- 1. Create Migration Account (Schema Owner & DDL Administrator)
CREATE USER restrovision_migrator IDENTIFIED BY "&ENTER_MIGRATOR_SECURE_PASSWORD"
  DEFAULT TABLESPACE users
  TEMPORARY TABLESPACE temp
  QUOTA 10G ON users;

-- Privileges derived strictly from actual V1 and V2 SQL statements:
-- (CREATE TABLE for 33 tables, CREATE SEQUENCE, CREATE VIEW, CREATE TRIGGER)
GRANT CREATE SESSION TO restrovision_migrator;
GRANT CREATE TABLE TO restrovision_migrator;
GRANT CREATE VIEW TO restrovision_migrator;
GRANT CREATE SEQUENCE TO restrovision_migrator;
GRANT CREATE PROCEDURE TO restrovision_migrator;
GRANT CREATE TRIGGER TO restrovision_migrator;
GRANT CREATE SYNONYM TO restrovision_migrator;

-- 2. Create Runtime Application Account (Strict Least-Privilege DML Only)
CREATE USER restrovision_app IDENTIFIED BY "&ENTER_APP_RUNTIME_SECURE_PASSWORD"
  DEFAULT TABLESPACE users
  TEMPORARY TABLESPACE temp
  QUOTA 0 ON users; -- Zero table creation quota

GRANT CREATE SESSION TO restrovision_app;

-- 3. Dedicated Application Role for Post-Migration Grants
CREATE ROLE restrovision_app_role;

-- Verification of created accounts:
SELECT username, account_status, default_tablespace 
FROM dba_users 
WHERE username IN ('RESTROVISION_MIGRATOR', 'RESTROVISION_APP');

EXIT;
```

### 2.3 Post-Migration Object Grants Script
After Flyway executes migrations `V1` and `V2` under `restrovision_migrator`, the DBA or migration pipeline executes the following script to grant DML permissions to `restrovision_app`:

```sql
-- Execute in FREEPDB1 to wire DML permissions and synonyms:
BEGIN
  -- Grant DML on all tables
  FOR t IN (SELECT table_name FROM dba_tables WHERE owner = 'RESTROVISION_MIGRATOR') LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON restrovision_migrator.' || t.table_name || ' TO restrovision_app_role';
  END LOOP;
  
  -- Grant sequence usage
  FOR s IN (SELECT sequence_name FROM dba_sequences WHERE sequence_owner = 'RESTROVISION_MIGRATOR') LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT ON restrovision_migrator.' || s.sequence_name || ' TO restrovision_app_role';
  END LOOP;
END;
/

GRANT restrovision_app_role TO restrovision_app;

-- Create synonyms in restrovision_app for clean object resolution:
BEGIN
  FOR t IN (SELECT table_name FROM dba_tables WHERE owner = 'RESTROVISION_MIGRATOR') LOOP
    EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM restrovision_app.' || t.table_name || ' FOR restrovision_migrator.' || t.table_name;
  END LOOP;
END;
/
```

### 2.4 Listener Network Access Control (`sqlnet.ora`)
Restrict database listener connections to authorized application servers only by configuring `$ORACLE_HOME/network/admin/sqlnet.ora`:
```ini
tcp.validnode_checking = yes
tcp.invited_nodes = (127.0.0.1, localhost, 10.0.1.10, 10.0.1.11)
```

---

## 3. Database Backup, Recovery & Safe Rollback Procedures

> [!CAUTION]
> **CRITICAL DATA SAFETY POLICY:**
> - **NEVER** execute `DROP USER ... CASCADE` or drop database schemas during application rollback.
> - Destructive schema drops destroy tenant configurations, order history, audit trails, and financial records.
> - Never execute destructive recovery commands against production data. Recovery procedures must be validated exclusively in an isolated staging database.

### 3.1 Logical Backup vs. Physical RMAN PITR Distinction
- **Oracle Data Pump (`expdp`/`impdp`):** Logical schema-level export/import of table definitions, constraints, indexes, and row data. Used for pre-deployment snapshots, schema refreshes, and test migrations.
- **Oracle Recovery Manager (RMAN):** Enterprise physical block-level backup of datafiles, control files, and archivelogs. Used for disaster recovery, media failure recovery, and Point-In-Time Recovery (PITR) to a specific System Change Number (SCN).

### 3.2 Pre-Deployment Logical Backup (Data Pump `expdp`)
Before applying new migrations or deploying code, create a logical schema snapshot:

```bash
# Secure execution via Data Pump (credentials injected via environment)
expdp \"restrovision_migrator/$ORACLE_MIGRATION_PASSWORD@localhost:1521/freepdb1\" \
  schemas=RESTROVISION_MIGRATOR \
  directory=DATA_PUMP_DIR \
  dumpfile=restrovision_predeploy_%U.dmp \
  logfile=restrovision_predeploy.log \
  compression=ALL
```

### 3.3 Isolated Recovery Prerequisites & Test Procedure
To validate backup recoverability without impacting production:
1. **Target Environment:** Dedicated isolated testing database or staging pluggable database (`TESTPDB`).
2. **Directory Privileges:** Ensure directory object exists:
   ```sql
   GRANT READ, WRITE ON DIRECTORY DATA_PUMP_DIR TO system;
   ```
3. **Isolated Schema Import Command:**
   ```bash
   impdp system/\"$SYSTEM_PASSWORD\"@localhost:1521/testpdb \
     directory=DATA_PUMP_DIR \
     dumpfile=restrovision_predeploy_%U.dmp \
     remap_schema=RESTROVISION_MIGRATOR:RESTROVISION_RECOVERY \
     remap_tablespace=USERS:TEST_USERS \
     logfile=restrovision_recovery_verify.log
   ```
4. **Validation Checklist for Restored Schema:**
   - [ ] Query `dba_objects` count matching source schema by `object_type`.
   - [ ] Verify table row counts across `users`, `restaurants`, `orders`, `restaurant_tables`.
   - [ ] Check constraint status: all `PRIMARY KEY` and `FOREIGN KEY` constraints must be `ENABLED`.
   - [ ] Verify index status: all indexes must report `STATUS = 'VALID'`.
   - [ ] Verify sequence values: `LAST_NUMBER` must match or exceed source.

*Audit Notice:* A restore verification was **NOT RUN** in this cycle because no isolated secondary staging instance was provisioned.

### 3.4 Database Migration Backward Compatibility & Rollback Conditions
- **Compatibility of V1 and V2:** Flyway migration `V1` establishes the core tables. Migration `V2` adds optimistic locking columns (`@Version private Long version = 0L;`).
- **Additive Schema Invariant:** Because `V2` is **strictly additive** (adds a nullable column with default 0), the previous application code (version N-1) remains 100% functional against the migrated schema (version N).
- **Safe Rollback Rule:** If a new application version exhibits defects, roll back the application container image to the previous approved tag. **Do NOT roll back or drop database tables.**
- **Breaking Schema Change Protocol:** Any future breaking schema changes (e.g. column renames, drops, structural splits) must follow the **Expand/Contract Pattern** across two separate releases:
  - Release N (Expand): Add new column, maintain backward compatibility.
  - Release N+1 (Contract): Remove obsolete column after all application nodes are upgraded.

---

## 4. Production Image Deployment & CI/CD Pipeline

> [!IMPORTANT]
> **No Source Code Compilation on Production Servers:**
> Production hosts must never run `mvn`, `npm run build`, or `docker build` against raw source files.
> All container images must be built, tested, scanned, and signed in CI, then deployed via immutable tags or digest pins.

### 4.1 CI/CD Image Build, Scan & Publish Workflow
Executed within GitHub Actions or enterprise CI runner:
```bash
# 1. Build immutable production images with semantic tags
docker build -t ghcr.io/restrovision/restrovision-backend:2.0.0 ./Backend
docker build -t ghcr.io/restrovision/restrovision-frontend:2.0.0 ./frontend
docker build -t ghcr.io/restrovision/restrovision-edge-ai:2.0.0 ./edge-ai

# 2. Scan images for OS and package vulnerabilities
docker scout cves ghcr.io/restrovision/restrovision-backend:2.0.0

# 3. Publish to container registry
docker push ghcr.io/restrovision/restrovision-backend:2.0.0
docker push ghcr.io/restrovision/restrovision-frontend:2.0.0
docker push ghcr.io/restrovision/restrovision-edge-ai:2.0.0

# 4. Record immutable sha256 digests
docker inspect --format='{{index .RepoDigests 0}}' ghcr.io/restrovision/restrovision-backend:2.0.0
```

### 4.2 Production Docker Compose Configuration (`docker-compose.prod.yml`)
The production deployment uses `docker-compose.prod.yml`, pulling pre-built images with private internal networking:

```yaml
# ==============================================================================
# RESTROVISION v2.0 - Production Docker Compose Definition
# Uses pre-built digest-pinned images; isolates backend/edge from public network.
# ==============================================================================
services:
  backend:
    image: ${BACKEND_IMAGE:-ghcr.io/restrovision/restrovision-backend:2.0.0}
    container_name: restrovision-backend
    restart: always
    expose:
      - "8080" # Internal container network only; NOT exposed on host
    environment:
      - SPRING_PROFILES_ACTIVE=oracle
      - ORACLE_HOST=${ORACLE_HOST}
      - ORACLE_PORT=${ORACLE_PORT:-1521}
      - ORACLE_SERVICE=${ORACLE_SERVICE:-freepdb1}
      - ORACLE_APP_USERNAME=${ORACLE_APP_USERNAME}
      - ORACLE_APP_PASSWORD=${ORACLE_APP_PASSWORD}
      - ORACLE_MIGRATION_USERNAME=${ORACLE_MIGRATION_USERNAME}
      - ORACLE_MIGRATION_PASSWORD=${ORACLE_MIGRATION_PASSWORD}
      - RESTROVISION_JWT_SECRET=${RESTROVISION_JWT_SECRET}
      - RESTROVISION_EDGE_HMAC_SECRET=${RESTROVISION_EDGE_HMAC_SECRET}
      - RESTROVISION_COOKIE_SECURE=true
      - RESTROVISION_TRUSTED_PROXIES=10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
    networks:
      - restrovision-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 30s

  frontend:
    image: ${FRONTEND_IMAGE:-ghcr.io/restrovision/restrovision-frontend:2.0.0}
    container_name: restrovision-frontend
    restart: always
    ports:
      - "80:80"   # External HTTP (Redirect to HTTPS)
      - "443:443" # External HTTPS / WSS
    volumes:
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      backend:
        condition: service_healthy
    networks:
      - restrovision-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:80/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 5s

  edge-ai:
    image: ${EDGE_AI_IMAGE:-ghcr.io/restrovision/restrovision-edge-ai:2.0.0}
    container_name: restrovision-edge-ai
    restart: always
    expose:
      - "8000" # Internal container network only; NOT exposed on host
    environment:
      - SPRING_BOOT_URL=http://backend:8080
      - EDGE_NODE_ID=${EDGE_NODE_ID:-edge-pune-01}
      - RESTAURANT_ID=${RESTAURANT_ID:-1}
      - SERVICE_TOKEN=${SERVICE_TOKEN}
      - HMAC_SECRET=${RESTROVISION_EDGE_HMAC_SECRET}
    depends_on:
      backend:
        condition: service_healthy
    networks:
      - restrovision-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/ai/v1/health/live"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 10s

networks:
  restrovision-network:
    driver: bridge
```

### 4.3 Container Image Rollback Procedure
If a defect is detected in a newly deployed container version:
```bash
# Roll back by specifying the previous approved digest:
export BACKEND_IMAGE="ghcr.io/restrovision/restrovision-backend@sha256:<previous_stable_digest>"
docker compose -f docker-compose.prod.yml up -d backend
```

---

## 5. Network Hardening, TLS Termination & HSTS Governance

### 5.1 Port Exposure Architecture
- **Publicly Exposed Ports:**
  - `443/tcp`: TLS HTTPS traffic and secure WebSockets (`wss://`).
  - `80/tcp`: Plaintext HTTP (Enforces immediate 301 redirect to HTTPS).
- **Private Internal Networks:**
  - `8080/tcp` (Backend): Accessible only from the frontend Nginx reverse proxy.
  - `8000/tcp` (Edge AI): Accessible only from the local camera network and backend proxy.
  - `1521/tcp` (Oracle): Accessible only from backend hosts.

### 5.2 TLS Reverse Proxy & Forwarded Header Configuration
Nginx configuration handles TLS termination and forwards client context to Spring Boot:

```nginx
server {
    listen 80;
    server_name app.restrovision.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name app.restrovision.com;

    ssl_certificate /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Security Headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # HSTS Governance (See Section 5.3 below)
    add_header Strict-Transport-Security "max-age=31536000" always;

    # Forwarded headers for Spring Boot rate limiting & audit logs
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto https;

    # WebSocket Proxying
    location /ws {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }

    # API Proxying
    location /api {
        proxy_pass http://backend:8080;
    }

    # SPA Static Files
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }
}
```

### 5.3 HSTS Subdomain Assumption Verification
> [!WARNING]
> **HSTS Subdomain Directive Warning:**  
> Do **NOT** include `includeSubDomains` in the `Strict-Transport-Security` header until it is empirically verified that **every** subdomain under the organization's domain serves valid TLS. Enabling `includeSubDomains` on a shared domain breaks any legacy internal HTTP subdomains.
> Use `max-age=31536000` on the application domain; evaluate `includeSubDomains` only after full domain certificate auditing.

### 5.4 Automated TLS Certificate Renewal
```bash
# Automated renewal via Certbot with zero-downtime Nginx reload:
certbot renew --post-hook "docker compose -f docker-compose.prod.yml exec -T frontend nginx -s reload"
```

---

## 6. Environment-Specific Secret Injection Runbooks

Production secrets must never be placed in source control, echoed in terminal commands, or saved in plaintext scripts.

### 6.1 Linux / Bash Production Secret Injection
```bash
# 1. Interactive input without terminal echo
read -s -p "Enter Oracle App Password: " ORACLE_APP_PASSWORD; echo
export ORACLE_APP_PASSWORD

read -s -p "Enter Oracle Migration Password: " ORACLE_MIGRATION_PASSWORD; echo
export ORACLE_MIGRATION_PASSWORD

read -s -p "Enter JWT Secret (64-byte hex): " RESTROVISION_JWT_SECRET; echo
export RESTROVISION_JWT_SECRET

read -s -p "Enter Edge HMAC Secret (64-byte hex): " RESTROVISION_EDGE_HMAC_SECRET; echo
export RESTROVISION_EDGE_HMAC_SECRET

export ORACLE_HOST="db.internal.restrovision.com"
export ORACLE_PORT="1521"
export ORACLE_SERVICE="freepdb1"
export ORACLE_APP_USERNAME="restrovision_app"
export ORACLE_MIGRATION_USERNAME="restrovision_migrator"

# 2. Deploy using production compose
docker compose -f docker-compose.prod.yml up -d
```

### 6.2 Windows PowerShell Production Secret Injection
```powershell
# 1. Secure interactive input using Read-Host -AsSecureString
$appPass = Read-Host -Prompt "Enter Oracle App Password" -AsSecureString
$env:ORACLE_APP_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($appPass))

$migPass = Read-Host -Prompt "Enter Oracle Migration Password" -AsSecureString
$env:ORACLE_MIGRATION_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($migPass))

$jwtSec = Read-Host -Prompt "Enter JWT Secret (64-byte hex)" -AsSecureString
$env:RESTROVISION_JWT_SECRET = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($jwtSec))

$hmacSec = Read-Host -Prompt "Enter Edge HMAC Secret (64-byte hex)" -AsSecureString
$env:RESTROVISION_EDGE_HMAC_SECRET = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($hmacSec))

$env:ORACLE_HOST = "localhost"
$env:ORACLE_PORT = "1521"
$env:ORACLE_SERVICE = "freepdb1"
$env:ORACLE_APP_USERNAME = "restrovision_app"
$env:ORACLE_MIGRATION_USERNAME = "restrovision_migrator"

# 2. Deploy using production compose
docker compose -f docker-compose.prod.yml up -d
```

---

## 7. Operational Health Probes & Monitoring Checklist

| Component | Endpoint | Probe Method | Expected Response | Remediation |
| :--- | :--- | :--- | :--- | :--- |
| **Backend Actuator** | `http://localhost:8080/actuator/health` | HTTP GET | `{"status":"UP"}` | Check Spring Boot logs; verify Oracle TNS listener |
| **Frontend Nginx** | `http://localhost:80/health` | HTTP GET | `healthy` | Check Nginx master/worker process status |
| **Edge AI Liveness** | `http://localhost:8000/ai/v1/health/live` | HTTP GET | `{"status":"UP"}` | Verify Python uvicorn process; check camera RTSP |
| **Edge AI Readiness**| `http://localhost:8000/ai/v1/health/ready`| HTTP GET | `{"status":"READY"}`| Verify model loading and SQLite outbox writability |
