# APPENDIX F: Installation & Local Execution Guide

## System Prerequisites
- Java Development Kit (JDK 17 or higher)
- Apache Maven 3.8+
- Node.js (v18+ or v20+) and npm
- Python 3.11+ or 3.13+ (for Edge AI daemon)
- Optional: Oracle Database 23ai Free (or use default embedded In-Memory H2 Oracle Mode)

## Step 1: Clone Repository
```bash
git clone https://github.com/vishalmore-28/restrovision.git
cd restrovision
```

## Step 2: Build & Start Spring Boot Backend
The application is pre-configured with a turnkey in-memory H2 database in Oracle mode and an automated `DataSeeder.java` that seeds demo users, 12 dining tables, and menus.
```bash
cd Backend
mvn spring-boot:run
```
*Backend initializes at:* `http://localhost:8080` (STOMP WebSocket: `ws://localhost:8080/ws`)

## Step 3: Start React Frontend
```bash
cd ../frontend
npm install
npm run dev
```
*Frontend opens at:* `http://localhost:5173`

## Step 4: Login with Default Credentials
- **Manager / Owner:** `owner@restrovision.local` | Password: `Admin@123`
- **Floor Waiter:** `waiter1@restrovision.local` | Password: `Admin@123`
- **Kitchen Chef:** `kitchen1@restrovision.local` | Password: `Admin@123`
- **Housekeeping:** `cleaner1@restrovision.local` | Password: `Admin@123`
