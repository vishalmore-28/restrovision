# RESTROVISION v2.0 — Production Cloud Deployment & Free Platform Architecture Guide

> **Target Audience:** DevOps Engineers, Solutions Architects, and Full-Stack Developers.  
> **Applicable Branch:** `feature/production-hardening`  
> **Last Verified:** November 2026

---

## Executive Summary & Verdict

You asked two specific questions regarding deploying RESTROVISION v2.0:
1. **Which free platform is the BEST for the complete deployment?**
2. **Which free platform is the FASTEST?**

### The Direct Verdict

| Evaluation Criteria | Winner | Why It Wins |
|---|---|---|
| **BEST for Complete Stack** | **Oracle Cloud Infrastructure (OCI) Always Free** | Provides **4 OCPU (Arm Ampere A1) + 24 GB of RAM**, 200 GB storage, and **2 Always Free Oracle Autonomous Databases (20 GB each)**. It is the **only** free cloud provider capable of hosting Spring Boot (Java 21), React SPA, FastAPI AI, and Oracle Enterprise DB together with **ZERO cold starts**, 24/7 continuous uptime, and enterprise-grade performance. |
| **FASTEST UI Delivery & Build** | **Vercel / Cloudflare Pages** | Builds and deploys the React 18 SPA in under **40 seconds** from Git push; serves assets on a global edge CDN with sub-15ms TTFB (Time to First Byte). |
| **FASTEST Zero-Config Setup** | **Railway.app** | Auto-detects Maven, Vite, and Python via Nixpacks and starts containers in under 2 minutes. *(Caveat: Railway uses a $5 trial credit model rather than a permanent free tier).* |

---

## 1. Platform Comparison Matrix

RESTROVISION v2.0 is an enterprise multi-tier application:
- **Backend:** Spring Boot 3.3.5 / Java 21 (HikariCP, Flyway, Hibernate 6, WebSocket STOMP) — Minimum stable heap: 512 MB – 1 GB.
- **Frontend:** React 18 + Vite + Tailwind CSS + Lucide Icons (Static build, reverse proxy to `/api` and `/ws`).
- **Edge AI:** Python 3.11 + FastAPI + SQLite WAL Outbox — RAM: 300 MB – 500 MB.
- **Database:** Oracle 19c / 23c / 26ai (or PostgreSQL) — Dual-account Flyway migrations (`RESTROVISION_MIGRATOR` & `RESTROVISION_APP`).

Here is how the leading free cloud providers compare:

| Provider | Free Compute & RAM | Database Included? | Cold Starts? | 24/7 Uptime? | Overall Score |
|---|---|---|---|---|---|
| **Oracle Cloud (OCI) Always Free** | **4 OCPU Ampere A1, 24 GB RAM**, 200 GB Disk | **Yes:** 2x 20 GB Autonomous DBs | **None (0s)** | **Yes (100%)** | **10 / 10 (Champion)** |
| **Render.com** | 512 MB RAM, 0.1 CPU | Postgres (Free for 90 days only) | **Yes (50–90s sleep)** | No (Spins down after 15m) | **6 / 10** |
| **Fly.io** | 3x 256 MB micro-VMs | Postgres via Fly App (1 GB) | Fast wakeup (2–5s) | Up to free allowance | **7.5 / 10** |
| **Vercel** | Edge CDN (Frontend only) | No (Third-party integration) | None (<10ms) | Yes | **9 / 10 (Frontend only)** |
| **Railway.app** | 512 MB RAM, 1 vCPU ($5 credit) | Yes (Postgres/MySQL/Redis) | None while credit lasts | No (Expired after trial) | **6.5 / 10** |
| **Koyeb** | 512 MB RAM (Nano instance) | Postgres (via partners) | Fast wakeup | Yes (1 web service) | **7 / 10** |

---

## 2. Recommended Deployment Architectures

### Architecture Option 1: The OCI Always Free Powerhouse (Recommended)

```
                            INTERNET (HTTPS / WSS)
                                      │
                                      ▼
                        OCI Virtual Cloud Network (VCN)
                  ┌────────────────────────────────────────┐
                  │  OCI Compute (Ampere A1, 24 GB RAM)   │
                  │                                        │
                  │   ┌────────────────────────────────┐   │
                  │   │   Nginx Reverse Proxy & SSL    │   │
                  │   │   (Certbot Let's Encrypt Free) │   │
                  │   └──────┬──────────────────┬──────┘   │
                  │          │ /                │ /api,/ws │
                  │          ▼                  ▼          │
                  │     ┌─────────┐       ┌────────────┐   │
                  │     │ React   │       │ Spring     │   │
                  │     │ SPA     │       │ Boot 3.3   │   │
                  │     └─────────┘       └─────┬──────┘   │
                  │                             │          │
                  │          ┌──────────────────┴──┐       │
                  │          ▼                     ▼       │
                  │     ┌─────────┐          ┌─────────┐   │
                  │     │ Edge AI │          │ Local   │   │
                  │     │ FastAPI │          │ Oracle  │   │
                  │     └─────────┘          │ 23c/26ai│   │
                  │                          └─────────┘   │
                  └────────────────────────────────────────┘
```

#### Why Option 1 is Unbeatable:
1. **Massive Memory**: 24 GB of RAM allows running Spring Boot with a generous 2 GB heap, the Python Edge AI service, React frontend Nginx container, and Oracle 23c Free container simultaneously on a single VM with zero memory pressure.
2. **True 24/7 Availability**: The server never spins down, sleeps, or disconnects. Alerts, background waiting timers, and WebSocket connections remain alive continuously.
3. **Zero Financial Cost**: OCI Always Free resources do not expire after 30 days.

---

### Architecture Option 2: The Decoupled Cloud Native Hybrid

If you prefer managed zero-maintenance serverless platforms:
- **Frontend:** Hosted on **Vercel** (Global edge CDN, instant Git push deploys, custom domain, automatic SSL).
- **Backend & Edge AI:** Hosted on **Render** (via Dockerfile) or **Fly.io**.
- **Database:** Hosted on **Supabase** or **Neon** (Free managed PostgreSQL) or **OCI Autonomous Database** (Free Oracle ATP).

---

## 3. Step-by-Step Deployment Guide: OCI Always Free

### Step 1: Create Your OCI Free Account & Provision Instance
1. Sign up at [cloud.oracle.com](https://cloud.oracle.com). Choose your home region carefully (e.g., US-East, Frankfurt, Mumbai).
2. Navigate to **Compute > Instances > Create Instance**.
3. Under **Image and shape**:
   - Image: `Ubuntu 22.04 LTS` (or `Oracle Linux 8`).
   - Shape: Click **Change Shape** -> Select **Ampere** (Arm processor) -> Choose **VM.Standard.A1.Flex**.
   - Allocation: Allocate **4 OCPUs** and **24 GB Memory** (Always Free Eligible).
4. Under **Networking**:
   - Assign a public IPv4 address.
5. Under **Add SSH keys**:
   - Download the private key or paste your public SSH key.
6. Click **Create**. Your VM will be active within 60 seconds.

### Step 2: Configure Ingress Rules (Firewall)
1. Go to **Networking > Virtual Cloud Networks > [Your VCN] > Security Lists > Default Security List**.
2. Add Ingress Rules:
   - Port `80` (HTTP) — Source `0.0.0.0/0`
   - Port `443` (HTTPS) — Source `0.0.0.0/0`
   - Port `8080` (Backend API, optional if using Nginx reverse proxy)
3. Open ports inside the VM firewall:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

### Step 3: Install Docker & Docker Compose
Connect to your VM via SSH:
```bash
ssh -i your_key.pem ubuntu@<YOUR_OCI_PUBLIC_IP>
```
Run the setup script:
```bash
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# Install Docker Engine & Docker Compose Plugin
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
```
Log out and back in for group membership to take effect.

### Step 4: Clone & Configure RESTROVISION v2.0
```bash
git clone https://github.com/your-org/restrovision.git
cd restrovision

# Initialize production environment configuration
cp .env.example .env
nano .env
```

Generate secure 64-character hex keys for your production deployment:
```bash
# Generate JWT Secret
openssl rand -hex 32

# Generate Edge HMAC Secret
openssl rand -hex 32
```
Update `.env` with these keys and set your database passwords:
```ini
SPRING_PROFILES_ACTIVE=oracle
ORACLE_HOST=oracle-db
ORACLE_PORT=1521
ORACLE_SERVICE=freepdb1
ORACLE_APP_USERNAME=restrovision_app
ORACLE_APP_PASSWORD=YourStrongAppPassword#2026
ORACLE_MIGRATION_USERNAME=restrovision_migrator
ORACLE_MIGRATION_PASSWORD=YourStrongMigratorPassword#2026
RESTROVISION_JWT_SECRET=<your-64-char-hex>
RESTROVISION_EDGE_HMAC_SECRET=<your-64-char-hex>
RESTROVISION_COOKIE_SECURE=true
```

### Step 5: Launch the Production Stack
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
Verify all containers are healthy:
```bash
docker compose -f docker-compose.prod.yml ps
docker logs restrovision-backend-prod -f
```

### Step 6: Free SSL Certificate with Let's Encrypt & Certbot
Point your custom domain (e.g. `app.yourrestaurant.com`) to your OCI Public IP, then install Certbot:
```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d app.yourrestaurant.com
```

---

## 4. Step-by-Step Deployment Guide: Vercel (Frontend) + Render (Backend)

If you prefer a zero-server management approach:

### Step 1: Deploy Frontend on Vercel
1. Push your repository to GitHub.
2. Sign in to [Vercel](https://vercel.com) with GitHub.
3. Click **Add New... > Project** and import `restrovision`.
4. Configure Project Settings:
   - **Framework Preset:** Vite
   - **Root Directory:** `frontend`
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist`
5. Add Environment Variable:
   - `VITE_API_BASE_URL` = `https://your-backend-service.onrender.com`
6. Click **Deploy**. Your frontend is live on edge CDN in ~40 seconds!

### Step 2: Deploy Backend on Render
1. Sign in to [Render.com](https://render.com).
2. Click **New + > Web Service**.
3. Connect your repository.
4. Select **Docker** or **Java**:
   - Dockerfile path: `backend/Dockerfile`
   - Plan: Free (512 MB)
5. Under **Environment Variables**, paste the required variables from `.env.example`:
   - `RESTROVISION_JWT_SECRET`
   - `RESTROVISION_EDGE_HMAC_SECRET`
   - `SPRING_PROFILES_ACTIVE` = `prod` (or `oracle` / `postgres`)
   - Database credentials.
6. Click **Create Web Service**.

> **Note on Render Free Tier Limitation:**  
> Free web services on Render spin down after 15 minutes of inactivity. When a customer or staff member visits the site after idle time, the initial request takes 50–90 seconds to respond while the container starts up. For zero-cold-start performance without paying, use the **OCI Always Free** guide in Section 3.

---

## 5. Production Health & Readiness Verification

Once deployed, execute the following smoke tests against your live URL:

```bash
# 1. Verify Spring Boot Actuator Health
curl -s -X GET https://<your-domain>/actuator/health | jq .
# Expected output: {"status":"UP"}

# 2. Verify Liveness and Readiness Probes
curl -s -X GET https://<your-domain>/actuator/health/liveness | jq .
curl -s -X GET https://<your-domain>/actuator/health/readiness | jq .
# Expected output: {"status":"UP"}

# 3. Test Public QR Endpoint
curl -s -X GET https://<your-domain>/api/public/qr/table-status/1/1 | jq .

# 4. Test Authenticated Login
curl -s -X POST https://<your-domain>/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"owner@restrovision.local","password":"Admin@123"}' | jq .
```

---

## 6. Summary of Architectural Recommendations

1. **For Production Restaurant Deployment:**  
   Deploy using **OCI Always Free** via `docker-compose.prod.yml`. The 24 GB RAM, zero cold starts, and persistent Oracle database provide real commercial viability at $0.00/month hosting cost.
2. **For Instant Client Demos & Portfolio Showcases:**  
   Deploy the frontend on **Vercel** (instant speed, beautiful custom domain, CDN caching) paired with a backend container on **Render** or **Railway**.
