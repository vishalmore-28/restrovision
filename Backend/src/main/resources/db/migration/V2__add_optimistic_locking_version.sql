-- Flyway Migration V2: Add optimistic locking version columns
-- (BUG-016: TableSession, StaffTask, ServiceRequest)

ALTER TABLE table_sessions ADD version NUMBER DEFAULT 0 NOT NULL;
ALTER TABLE staff_tasks ADD version NUMBER DEFAULT 0 NOT NULL;
ALTER TABLE service_requests ADD version NUMBER DEFAULT 0 NOT NULL;
