# RESTROVISION v2.0 — Production Deployment & Operational Runbook

**Document ID:** `DEPLOY-CHECKLIST-20261115-REVISED`  
**Application Target:** RESTROVISION v2.0 Production Deployment  
**Target Environment:** Oracle Linux 9 / Ubuntu 22.04 LTS / Windows Server 2022  
**Target Database:** Oracle Database 26ai Free / 19c Enterprise (PDB `FREEPDB1`)  
**Container Engine:** Docker Engine $\ge 24.0$ / Docker Compose $\ge v2.20$  
**Security Standard:** Zero Default Fallbacks, Least Privilege DBA Policy, HTTPS/WSS Only  
**Revision Date:** 2026-11-15T00:35:00+05:30  

---

## 1. Pre-Deployment Infrastructure Verification Checklist

Before initiating production deployment, verify all host prerequisites:

| Check | Infrastructure Item | Verification Command / Target | Pass Criteria | Status |
| :---: | :--- | :--- | :--- | :---: |
| [ ] | **Host Port 8080** | `netstat -ano \| findstr 8080` | Port free for Backend Spring Boot | Ready |
| [ ] | **Host Port 80 / 443** | `netstat -ano \| findstr :80` | Port free for Frontend Nginx SPA / TLS | Ready |
| [ ] | **Host Port 8000** | `netstat -ano \| findstr 8000` | Port free for Edge AI Service | Ready |
| [ ] | **Host Port 1521** | `netstat -ano \| findstr 1521` | Oracle TNS Listener active | **ACTIVE** |
| [ ] | **Docker Engine Daemon** | `docker info` | Active Linux container daemon | **BLOCKED (Service Stopped)** |
| [ ] | **JDK 21 LTS** | `java -version` | OpenJDK 21 LTS | **PASS** |
| [ ] | **Node.js 22 LTS** | `node -v` | v22.x LTS | **PASS** |
| [ ] | **Python 3.11+** | `python --version` | Python 3.11 / 3.13 | **PASS** |

---

## 2. Oracle Database 26ai Least-Privilege DBA Provisioning Guide

The application schema user `restrovision` must be provisioned in pluggable database `FREEPDB1` by the authorized DBA. 

> [!CAUTION]
> **Production Security Mandates:**
> - **NEVER** use weak or default passwords (such as `restrovision123`).
> - Passwords must be at least 16 characters containing uppercase, lowercase, numbers, and symbols.
> - Never hardcode or echo plaintext passwords in shell histories or scripts.
> - Grant only explicit least-privilege permissions. Do **NOT** grant `DBA`, `SYSDBA`, or `ALL PRIVILEGES`.

### Step 2.1: DBA Authentication & PDB Selection
```bash
sqlplus / as sysdba
```
```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
SHOW CON_NAME;
-- Expected output: CON_NAME = FREEPDB1
```

### Step 2.2: Least-Privilege Application User Creation
Execute the provisioning script, supplying a securely generated production secret:

```sql
-- 1. Create dedicated application schema user with strict quota
CREATE USER restrovision IDENTIFIED BY "&ENTER_STRONG_SECURE_PASSWORD"
  DEFAULT TABLESPACE users
  TEMPORARY TABLESPACE temp
  QUOTA UNLIMITED ON users;

-- 2. Grant minimum required application privileges (Least Privilege Principle)
GRANT CREATE SESSION TO restrovision;
GRANT CREATE TABLE TO restrovision;
GRANT CREATE VIEW TO restrovision;
GRANT CREATE SEQUENCE TO restrovision;
GRANT CREATE PROCEDURE TO restrovision;
GRANT CREATE TRIGGER TO restrovision;
GRANT CREATE SYNONYM TO restrovision;

-- 3. Verify user status and lock expiry
SELECT username, account_status, default_tablespace 
FROM dba_users 
WHERE username = 'RESTROVISION';

EXIT;
```

---

## 3. Flyway Database Migration Sequence

Once the DBA has provisioned `restrovision`, execute the versioned migrations.

### Step 3.1: Environment Variable Export (No Plaintext in Commands)
```bash
# Securely export credentials in the deployment shell session
export ORACLE_HOST="localhost"
export ORACLE_PORT="1521"
export ORACLE_SERVICE="freepdb1"
export ORACLE_USERNAME="restrovision"
read -s -p "Enter Oracle DB Password: " ORACLE_PASSWORD
export ORACLE_PASSWORD
echo ""
```

### Step 3.2: Automated Migration via Spring Boot Startup
When the application starts with profile `oracle`, Flyway executes migrations automatically:
- **Migration V1:** `V1__init_core_schema.sql` (Creates 33 core tables, indexes, constraints)
- **Migration V2:** `V2__add_optimistic_locking_version.sql` (Adds `@Version` optimistic locking columns)

### Step 3.3: Verification of Applied Migrations
Query the migration history table:
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

All production secrets must be supplied via environment variables or secret manager. The `oracle` profile in `application-oracle.yml` has **zero default fallbacks** and will fail fast if any required secret is omitted.

| Environment Variable | Production Requirement | Purpose | Fallback Behavior |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `oracle` | Activates Oracle production datasource | Defaults to `default` (H2) |
| `ORACLE_HOST` | Hostname / IP of Oracle server | Database connection endpoint | Defaults to `localhost` |
| `ORACLE_PORT` | `1521` | Oracle TNS Listener port | Defaults to `1521` |
| `ORACLE_SERVICE` | `freepdb1` | Pluggable database service name | Defaults to `freepdb1` |
| `ORACLE_USERNAME` | `restrovision` | Application database schema user | **FAIL FAST** (No fallback) |
| `ORACLE_PASSWORD` | Strong password ($> 16$ chars) | Application database password | **FAIL FAST** (No fallback) |
| `RESTROVISION_JWT_SECRET` | 64-byte hex string (256-bit key) | Signs & verifies user and device JWTs | **FAIL FAST** (No fallback) |
| `RESTROVISION_HMAC_SECRET`| 64-byte hex string (256-bit key) | Validates edge-to-cloud AI ingestion | **FAIL FAST** (No fallback) |
| `RESTROVISION_COOKIE_SECURE`| `true` | Enforces `Secure` flag on HttpOnly cookies | Defaults to `true` on Oracle profile |
| `RESTROVISION_TRUSTED_PROXIES`| CIDR blocks of upstream proxies | Rate limiter IP resolution | Defaults to `127.0.0.1,::1` |

---

## 5. Container Orchestration & Immutable Image Deployment

### Step 5.1: Immutable Tagging Policy
Production container images must use immutable version tags (never rely solely on `:latest`):
- `restrovision-backend:2.0.0`
- `restrovision-frontend:2.0.0`
- `restrovision-edge-ai:2.0.0`

### Step 5.2: Production Multi-Stage Build & Execution
```bash
# Export required production secrets into environment
export RESTROVISION_JWT_SECRET="<SECURE_256BIT_HEX_KEY>"
export RESTROVISION_HMAC_SECRET="<SECURE_256BIT_HEX_KEY>"
export ORACLE_PASSWORD="<SECURE_DB_PASSWORD>"

# Launch full stack
docker compose -f docker-compose.yml up --build -d
```

### Step 5.3: Container Health Check Verification
```bash
docker compose ps
```
Verify every service attains `healthy` status:
- `restrovision-backend`: `Up (healthy)` via `/actuator/health` probe
- `restrovision-frontend`: `Up (healthy)` via `/health` probe
- `restrovision-edge-ai`: `Up (healthy)` via `/ai/v1/health/live` probe

---

## 6. TLS Termination & Production Reverse Proxy Configuration

Production deployments must terminate TLS (HTTPS / WSS) in front of Nginx:

1. **SSL/TLS Certificates:** Install valid CA-signed certificates (`fullchain.pem`, `privkey.pem`).
2. **Security Headers Enforced:**
   ```nginx
   add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
   add_header X-Frame-Options "SAMEORIGIN" always;
   add_header X-Content-Type-Options "nosniff" always;
   add_header X-XSS-Protection "1; mode=block" always;
   add_header Referrer-Policy "strict-origin-when-cross-origin" always;
   ```
3. **WebSocket Persistent Upgrades:**
   ```nginx
   location /ws {
       proxy_pass http://backend:8080;
       proxy_http_version 1.1;
       proxy_set_header Upgrade $http_upgrade;
       proxy_set_header Connection "Upgrade";
       proxy_read_timeout 86400s;
       proxy_send_timeout 86400s;
   }
   ```

---

## 7. Database Backup, Recovery & Safe Rollback Procedures

> [!CAUTION]
> **CRITICAL DATA SAFETY RULE:**  
> **NEVER** execute `DROP USER ... CASCADE` or drop database schemas during normal application rollback.  
> Destructive schema drops destroy all historical restaurant data, tenant configs, order history, and audit logs.  
> Never test destructive recovery commands against live or production databases.

### 7.1 Pre-Deployment Database Backup (Oracle Data Pump `expdp`)
Before deploying schema migrations or code updates, take a complete logical backup:

```bash
# Execute schema backup to Oracle directory DATA_PUMP_DIR
expdp restrovision/\"$ORACLE_PASSWORD\"@localhost:1521/freepdb1 \
  schemas=RESTROVISION \
  directory=DATA_PUMP_DIR \
  dumpfile=restrovision_predeploy_%U.dmp \
  logfile=restrovision_predeploy.log \
  compression=ALL
```

### 7.2 Point-In-Time Restore Procedure (In Isolated Recovery Environment)
To verify or perform a schema restore without impacting production:

```bash
# Test restore into an isolated staging database or alternate schema:
impdp system/\"$SYSTEM_PASSWORD\"@localhost:1521/freepdb1 \
  directory=DATA_PUMP_DIR \
  dumpfile=restrovision_predeploy_%U.dmp \
  remap_schema=RESTROVISION:RESTROVISION_RECOVERY \
  logfile=restrovision_restore_test.log
```

### 7.3 Application Container Rollback
If a container update exhibits defects:
1. Revert Git reference to the previous stable release commit:
   ```bash
   git checkout <PREVIOUS_STABLE_COMMIT_SHA>
   docker compose up --build -d
   ```
2. Verify container health probes:
   ```bash
   curl -f http://localhost:8080/actuator/health
   curl -f http://localhost:80/health
   curl -f http://localhost:8000/ai/v1/health/live
   ```
3. Because Flyway migrations `V1` and `V2` are backwards-compatible (adding nullable `@Version` columns with default 0), previous code versions continue to function safely without requiring destructive database schema rollback.

---

## 8. Operational Health Probes & Monitoring Checklist

| Component | Endpoint | Expected Response | Remediation If Failing |
| :--- | :--- | :--- | :--- |
| **Backend Actuator** | `GET http://localhost:8080/actuator/health` | `{"status":"UP"}` | Inspect Spring Boot logs; check Oracle DB connectivity |
| **Frontend Nginx** | `GET http://localhost:80/health` | `healthy` | Verify Nginx worker process; check static file permissions |
| **Edge AI Liveness** | `GET http://localhost:8000/ai/v1/health/live` | `{"status":"UP"}` | Check Python uvicorn process; inspect camera feeds |
| **Edge AI Readiness**| `GET http://localhost:8000/ai/v1/health/ready`| `{"status":"READY"}`| Verify model loading and SQLite outbox writability |
