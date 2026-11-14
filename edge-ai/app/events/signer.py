import hmac
import hashlib
from app.core.config import settings

def compute_hmac_signature(raw_payload) -> str:
    """
    Computes HMAC-SHA256 signature for the raw JSON payload.
    Header format: sha256=<hex_digest>
    """
    if not settings.HMAC_SECRET:
        return ""
    payload_bytes = raw_payload.encode("utf-8") if isinstance(raw_payload, str) else raw_payload
    mac = hmac.new(
        settings.HMAC_SECRET.encode("utf-8"),
        payload_bytes,
        hashlib.sha256
    )
    return f"sha256={mac.hexdigest()}"
