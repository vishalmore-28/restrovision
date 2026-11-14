import os

class Settings:
    SPRING_BOOT_URL: str = os.getenv("SPRING_BOOT_URL", "http://localhost:8080")
    EDGE_NODE_ID: str = os.getenv("EDGE_NODE_ID", "edge-pune-01")
    RESTAURANT_ID: int = int(os.getenv("RESTAURANT_ID", "1"))
    SERVICE_TOKEN: str = os.getenv("SERVICE_TOKEN", os.getenv("RESTROVISION_EDGE_TOKEN", "edge-token-pune-01"))
    HMAC_SECRET: str = os.getenv("RESTROVISION_EDGE_HMAC_SECRET", os.getenv("HMAC_SECRET", "8f4a2b1c9e3d7a5f6e8b0c2d4e6f8a0b1c3d5e7f9a1b3c5d7e9f1a3b5c7d9e1f"))
    OUTBOX_DB_PATH: str = os.getenv("OUTBOX_DB_PATH", "edge_outbox.sqlite")

settings = Settings()
