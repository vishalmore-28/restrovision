"""
API Contract and Integration tests for Edge AI FastAPI service (BUG-020).
Tests:
- POST /ai/v1/forecast/traffic with fallback and empirical residual forecasting
- POST /ai/v1/forecast/demand with recipe heuristics
- POST /ai/v1/sentiment with contextual valence NLP
- GET /ai/v1/models registry with honest names
- GET /ai/v1/health/live and ready endpoints
"""

import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

class TestApiHealthAndModels:
    def test_health_live(self):
        resp = client.get("/ai/v1/health/live")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "UP"
        assert data["service"] == "restrovision-ai"

    def test_health_ready(self):
        resp = client.get("/ai/v1/health/ready")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "READY"
        assert data["modelsLoaded"] >= 5

    def test_models_registry_honest_names(self):
        resp = client.get("/ai/v1/models")
        assert resp.status_code == 200
        models = resp.json()
        model_names = {m["modelName"]: m for m in models}
        
        # Verify honest model naming
        assert "traffic-forecaster" in model_names
        assert model_names["traffic-forecaster"]["version"] == "traffic-seasonal-empirical-1.1.0"
        assert "Empirical Residual Quantiles" in model_names["traffic-forecaster"]["framework"]

        assert "sentiment-nlp" in model_names
        assert model_names["sentiment-nlp"]["version"] == "sentiment-lexicon-valence-1.1.0"
        assert "Contextual Valence" in model_names["sentiment-nlp"]["framework"]

        assert "demand-forecaster" in model_names
        assert model_names["demand-forecaster"]["version"] == "demand-recipe-heuristic-1.1.0"


class TestForecastEndpoints:
    def test_traffic_forecast_endpoint_empty_history(self):
        payload = {"historyWindow": [], "horizonHours": 6}
        resp = client.post("/ai/v1/forecast/traffic", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert len(data) == 6
        assert data[0]["metadata"]["fallback_applied"] is True
        assert data[0]["p10"] <= data[0]["p50"] <= data[0]["p90"]

    def test_traffic_forecast_endpoint_full_history(self):
        history = [
            {"hour": f"2026-11-14T{h:02d}:00:00Z", "guests": 20 + (h % 6) * 3}
            for h in range(14)
        ]
        payload = {"historyWindow": history, "horizonHours": 4}
        resp = client.post("/ai/v1/forecast/traffic", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert len(data) == 4
        assert data[0]["metadata"]["fallback_applied"] is False
        assert data[0]["metadata"]["sample_count"] == 14
        assert data[0]["p10"] <= data[0]["p50"] <= data[0]["p90"]

    def test_demand_forecast_endpoint(self):
        payload = {
            "items": [
                {"itemId": 1, "name": "Paneer Tikka Angara"},
                {"itemId": 2, "name": "Butter Chicken Delhi 6"}
            ]
        }
        resp = client.post("/ai/v1/forecast/demand", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert len(data) == 2
        assert data[0]["predictedOrders"] == 24
        assert data[0]["recommendedPrepBatch"] == 10
        assert data[0]["model_version"] == "demand-recipe-heuristic-1.1.0"


class TestSentimentEndpoint:
    def test_sentiment_endpoint_positive(self):
        payload = {"comment": "Water refill was fast and staff was very polite!"}
        resp = client.post("/ai/v1/sentiment", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["sentiment"] == "POSITIVE"
        assert data["category"] == "SERVICE_SPEED"
        assert data["model_version"] == "sentiment-lexicon-valence-1.1.0"

    def test_sentiment_endpoint_negation_not_good(self):
        payload = {"comment": "Food was not good and dal makhani was cold."}
        resp = client.post("/ai/v1/sentiment", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["sentiment"] == "NEGATIVE"
        assert data["category"] == "FOOD_QUALITY"

    def test_sentiment_endpoint_negation_not_bad(self):
        payload = {"comment": "The dining atmosphere was not bad."}
        resp = client.post("/ai/v1/sentiment", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data["sentiment"] in ("NEUTRAL", "POSITIVE")
        assert data["valence_score"] >= 0
