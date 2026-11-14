import sqlite3
import json
import logging
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any, List
from app.core.config import settings
from app.events.signer import compute_hmac_signature

logger = logging.getLogger(__name__)

class SQLiteOutbox:
    def __init__(self, db_path: str = settings.OUTBOX_DB_PATH):
        self.db_path = db_path
        self._init_db()

    def _get_conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.execute("PRAGMA journal_mode=WAL")
        return conn

    def _init_db(self):
        with self._get_conn() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS outbox (
                    event_id     TEXT PRIMARY KEY,
                    payload      TEXT NOT NULL,
                    created_at   TEXT NOT NULL,
                    attempts     INTEGER NOT NULL DEFAULT 0,
                    next_attempt TEXT NOT NULL,
                    status       TEXT NOT NULL CHECK (status IN ('PENDING','SENT','DEAD'))
                )
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_outbox_due ON outbox(status, next_attempt)")
            conn.commit()

    def enqueue(self, event_id: str, payload_dict: Dict[str, Any]):
        now_str = datetime.now(timezone.utc).isoformat()
        payload_json = json.dumps(payload_dict)
        with self._get_conn() as conn:
            conn.execute("""
                INSERT OR IGNORE INTO outbox (event_id, payload, created_at, attempts, next_attempt, status)
                VALUES (?, ?, ?, 0, ?, 'PENDING')
            """, (event_id, payload_json, now_str, now_str))
            conn.commit()
        logger.info(f"Event {event_id} written to SQLite outbox.")

    def get_pending_events(self, limit: int = 10) -> List[Dict[str, Any]]:
        now_str = datetime.now(timezone.utc).isoformat()
        with self._get_conn() as conn:
            cursor = conn.execute("""
                SELECT event_id, payload, attempts
                FROM outbox
                WHERE status = 'PENDING' AND next_attempt <= ?
                ORDER BY created_at ASC
                LIMIT ?
            """, (now_str, limit))
            rows = cursor.fetchall()
            return [{"event_id": r[0], "payload": json.loads(r[1]), "raw_json": r[1], "attempts": r[2]} for r in rows]

    def mark_sent(self, event_id: str):
        with self._get_conn() as conn:
            conn.execute("UPDATE outbox SET status = 'SENT' WHERE event_id = ?", (event_id,))
            conn.commit()
        logger.info(f"Event {event_id} marked SENT.")

    def mark_retry(self, event_id: str, attempts: int):
        # Exponential backoff with jitter capped at 300s
        delay_seconds = min(300, 2 ** attempts)
        next_attempt = (datetime.now(timezone.utc) + timedelta(seconds=delay_seconds)).isoformat()
        status = 'DEAD' if attempts >= 10 else 'PENDING'
        with self._get_conn() as conn:
            conn.execute("""
                UPDATE outbox
                SET attempts = ?, next_attempt = ?, status = ?
                WHERE event_id = ?
            """, (attempts, next_attempt, status, event_id))
            conn.commit()
        logger.warning(f"Event {event_id} failed dispatch (attempt {attempts}). Next retry at {next_attempt}.")

outbox = SQLiteOutbox()
