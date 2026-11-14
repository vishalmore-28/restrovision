# RESTROVISION v2.0 — Final Database Backup & Disaster Recovery Verification

**Document ID:** `DR-VERIF-20261115-FINAL`  
**Application Target:** RESTROVISION v2.0 Multi-Tenant Enterprise Restaurant Platform  
**Active Working Branch:** `feature/production-hardening`  
**Protected Branch:** `main` (Locked at `171171b08f6bd44f523f224d00c197624246613f`)  
**Audit Date:** 2026-11-15T01:20:00+05:30  
**DR Architecture Design:** **VERIFIED & COMPLIANT**  
**Live Restore Verification:** **NOT RUN / PENDING STAGING PDB**  
**Production Destructive Command Policy:** **STRICT PROHIBITION ENFORCED**  

---

## 1. Executive Summary

This document establishes the official enterprise Disaster Recovery (DR) and Backup/Restore strategy for the RESTROVISION v2.0 Oracle database tier (`FREEPDB1` / Oracle Database 26ai). 

The strategy combines **Oracle Data Pump (Logical Export)** for tenant migrations and point-in-time schema recovery, with **Oracle Recovery Manager (RMAN Physical Backup)** for full database disaster recovery.

**Crucially, live database restore testing on staging is classified as NOT RUN / PENDING STAGING PDB** because `FREEPDB1` has not yet been provisioned by the DBA and an isolated recovery target PDB (`TESTPDB`) has not been initialized. Testing restore operations directly against unisolated production or primary schemas is strictly forbidden.

---

## 2. Backup Strategy Architecture

RESTROVISION v2.0 mandates a tiered backup topology to satisfy Recovery Point Objective (RPO) $\le 15\text{ minutes}$ and Recovery Time Objective (RTO) $\le 2\text{ hours}$.

### 2.1 Logical Schema Backup: Oracle Data Pump (`expdp`)
Used for daily scheduled logical snapshots, multi-tenant schema archiving, and pre-deployment checkpoints:
- **Consistent Export Mechanism:** `FLASHBACK_TIME=SYSTIMESTAMP` ensures transactional read consistency across all 33 tables and Flyway metadata.
- **Export Command Specification:**
  ```bash
  expdp system/<PASSWORD>@localhost:1521/FREEPDB1 \
    SCHEMAS=RESTROVISION_MIGRATOR \
    DIRECTORY=DATA_PUMP_DIR \
    DUMPFILE=restrovision_migrator_%U.dmp \
    LOGFILE=expdp_restrovision_%DATE%.log \
    FLASHBACK_TIME=SYSTIMESTAMP \
    COMPRESSION=ALL \
    FILESIZE=4G
  ```
- **Dual-Account Note:** The export target is exclusively `RESTROVISION_MIGRATOR` as it holds all tables, sequences, and indexes. `RESTROVISION_APP` holds only synonyms and runtime grants, which are documented in infrastructure-as-code.

### 2.2 Physical Point-in-Time Backup: Oracle Recovery Manager (RMAN)
Used for disaster recovery against media failure, corruption, or catastrophic data center loss:
- **PDB Archivelog Mode:** Database instance runs in `ARCHIVELOG` mode.
- **RMAN Incremental Strategy:**
  - Weekly Level 0 (Full) physical backup.
  - Daily Level 1 Cumulative incremental backup.
  - Continuous archive log backup every 15 minutes.
- **RMAN Backup Command:**
  ```rman
  CONNECT TARGET /;
  BACKUP AS COMPRESSED BACKUPSET 
    DATABASE 
    PLUS ARCHIVELOG DELETE INPUT;
  ```

---

## 3. Isolated Restore & Verification Procedure

### 3.1 Strict Isolation Principle
> [!CAUTION]
> **NEVER TEST RESTORE ON PRODUCTION OR PRIMARY PDB:**  
> Testing backup restoration directly in the active primary PDB risks catastrophic accidental overwrites, locks, or schema corruption. All restore validations must be executed in an isolated clone PDB (`TESTPDB`) or remapped schema namespace.

### 3.2 Staging Remapped Restore Execution via Data Pump
To verify dump file integrity without affecting active schemas:
1. Create a transient verification schema `RESTROVISION_RECOVERY`:
   ```sql
   CREATE USER RESTROVISION_RECOVERY IDENTIFIED BY "TemporaryPass123#"
     DEFAULT TABLESPACE USERS
     QUOTA 500M ON USERS;
   GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO RESTROVISION_RECOVERY;
   ```
2. Execute Data Pump import with schema remapping:
   ```bash
   impdp system/<PASSWORD>@localhost:1521/FREEPDB1 \
     DIRECTORY=DATA_PUMP_DIR \
     DUMPFILE=restrovision_migrator_01.dmp \
     LOGFILE=impdp_recovery_test.log \
     REMAP_SCHEMA=RESTROVISION_MIGRATOR:RESTROVISION_RECOVERY \
     TRANSFORM=OID:N
   ```

### 3.3 The 5-Point Post-Restore Validation Checklist
Before any restored database image is certified valid, the DBA and QA lead must execute and pass this 5-point checklist:

```sql
-- Point 1: Object Count Parity (Expected: 33 tables, 33 sequences)
SELECT object_type, COUNT(*) 
FROM dba_objects 
WHERE owner = 'RESTROVISION_RECOVERY' 
GROUP BY object_type;

-- Point 2: Constraint & Index Validity (Expected: 0 INVALID or UNUSABLE)
SELECT owner, index_name, status 
FROM dba_indexes 
WHERE owner = 'RESTROVISION_RECOVERY' AND status != 'VALID';

SELECT owner, constraint_name, status 
FROM dba_constraints 
WHERE owner = 'RESTROVISION_RECOVERY' AND status != 'ENABLED';

-- Point 3: Core Entity Row Count Verification
SELECT 'RESTAURANTS' AS tbl, COUNT(*) FROM RESTROVISION_RECOVERY.RESTAURANTS
UNION ALL SELECT 'USERS', COUNT(*) FROM RESTROVISION_RECOVERY.USERS
UNION ALL SELECT 'DINING_TABLES', COUNT(*) FROM RESTROVISION_RECOVERY.DINING_TABLES
UNION ALL SELECT 'ORDERS', COUNT(*) FROM RESTROVISION_RECOVERY.ORDERS
UNION ALL SELECT 'ORDER_ITEMS', COUNT(*) FROM RESTROVISION_RECOVERY.ORDER_ITEMS
UNION ALL SELECT 'EDGE_NODES', COUNT(*) FROM RESTROVISION_RECOVERY.EDGE_NODES;

-- Point 4: Flyway Schema History Integrity Check
SELECT installed_rank, version, description, type, script, checksum, success 
FROM RESTROVISION_RECOVERY.flyway_schema_history 
ORDER BY installed_rank;

-- Point 5: Cleanup Transient Recovery Schema
DROP USER RESTROVISION_RECOVERY CASCADE;
```

---

## 4. Additive Migration Compatibility & Safe Rollback

### 4.1 Zero-Downtime Additive Migration Design
All RESTROVISION v2.0 Flyway SQL migrations (`V1__init_core_schema.sql` and `V2__add_optimistic_locking_version.sql`) are strictly **additive**:
- New tables and sequences do not alter existing schema contracts.
- Columns added in V2 (`created_by`, `updated_by`, `audit_hash`, `tenant_id`) include safe defaults or nullable specifications.
- Foreign keys and check constraints are non-blocking.
- **Rollback Philosophy:** In the event of an application release failure, the application tier is rolled back to the previous container image. Because migrations are additive, the previous application version continues functioning against the updated database schema without schema rollback.

### 4.2 Strict Prohibition Against Destructive Commands
> [!WARNING]
> **PROHIBITION OF `DROP USER ... CASCADE`:**  
> The command `DROP USER RESTROVISION_MIGRATOR CASCADE` or `DROP USER RESTROVISION_APP CASCADE` must NEVER appear in any production deployment, migration, or automated rollback runbook.  
> Dropping schemas destroys all audit trails, historical financial ledgers, and tenant configurations, violating SOX and PCI-DSS compliance.

---

## 5. Current DR Verification Status & Gate Decision

| Verification Item | Target Standard | Result | Gate Status |
| :--- | :--- | :---: | :---: |
| **Data Pump Syntax & Parameters** | Validated against Oracle 26ai specifications | `FLASHBACK_TIME`, `REMAP_SCHEMA` | **SATISFIED** |
| **Additive Migration Verification** | V1 & V2 backwards compatibility | Verified | **SATISFIED** |
| **Destructive Command Elimination** | Zero `DROP CASCADE` in runbooks | Enforced | **SATISFIED** |
| **Live Isolated Restore Execution** | 5-point checklist passed on `TESTPDB` | **Not Run** | **BLOCKED** |
| **RMAN PITR Staging Drill** | Live roll-forward drill | **Not Run** | **BLOCKED** |

### Release Recommendation:
Because live database restore has not yet been demonstrated in an isolated staging PDB, Disaster Recovery readiness remains **BLOCKED / PENDING STAGING PDB**. Live drills must be executed prior to production traffic cutover.
