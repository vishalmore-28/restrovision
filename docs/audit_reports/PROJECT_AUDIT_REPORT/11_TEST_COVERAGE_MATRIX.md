# RESTROVISION Automated Test Coverage Matrix
**Document ID**: `MATRIX-P11-COV-20261114`  
**Application Target**: RESTROVISION v2.0  
**Audit Lead**: Principal Test Automation Architect  
**Last Updated**: 2026-11-14T13:31:00+05:30  

---

## 1. REST API Endpoint Automated Test Coverage (33 Endpoints)

| # | HTTP Method | Endpoint URI | Target Controller | Automated Test in Suite? | Specific Test Method Name |
| :- | :--- | :--- | :--- | :---: | :--- |
| **01** | `POST` | `/api/v1/auth/login` | `AuthController` | **YES** | `authWrongPasswordReturns401()` |
| **02** | `POST` | `/api/v1/auth/refresh` | `AuthController` | **YES** | `tokenSeparationEnforced()` |
| **03** | `POST` | `/api/v1/auth/logout` | `AuthController` | **NO** | *Untested* |
| **04** | `GET` | `/api/v1/auth/me` | `AuthController` | **YES** | `tokenSeparationEnforced()` |
| **05** | `GET` | `/api/v1/tables` | `TableController` | **YES** | `unauthenticatedAccessDenied()` |
| **06** | `GET` | `/api/v1/tables/{id}` | `TableController` | **NO** | *Untested* |
| **07** | `PUT` | `/api/v1/tables/{id}/status` | `TableController` | **YES** | `tableStateMachineGuards()` |
| **08** | `POST` | `/api/v1/tables/{id}/seat` | `TableController` | **YES** | `tableStateMachineGuards()` |
| **09** | `POST` | `/api/v1/tables/{id}/attend` | `TableController` | **NO** | *Untested* |
| **10** | `PUT` | `/api/v1/tables/{id}/waiter` | `TableController` | **NO** | *Untested* |
| **11** | `GET` | `/api/v1/alerts` | `AlertController` | **NO** | *Untested* |
| **12** | `GET` | `/api/v1/alerts/all` | `AlertController` | **NO** | *Untested* |
| **13** | `PUT` | `/api/v1/alerts/{id}/acknowledge` | `AlertController` | **NO** | *Untested* |
| **14** | `PUT` | `/api/v1/alerts/{id}/resolve` | `AlertController` | **NO** | *Untested* |
| **15** | `PUT` | `/api/v1/alerts/{id}/false-alarm` | `AlertController` | **NO** | *Untested* |
| **16** | `GET` | `/api/v1/orders` | `OrderController` | **NO** | *Untested* |
| **17** | `GET` | `/api/v1/orders/{id}` | `OrderController` | **NO** | *Untested* |
| **18** | `POST` | `/api/v1/orders` | `OrderController` | **YES** | `orderValidationEnforced()` |
| **19** | `PUT` | `/api/v1/orders/{id}/status` | `OrderController` | **NO** | *Untested* |
| **20** | `POST` | `/api/v1/orders/{id}/deliver` | `OrderController` | **NO** | *Untested* |
| **21** | `GET` | `/api/v1/orders/delayed` | `OrderController` | **NO** | *Untested* |
| **22** | `GET` | `/api/v1/kitchen/stations` | `KitchenController` | **NO** | *Untested* |
| **23** | `GET` | `/api/v1/kitchen/queue` | `KitchenController` | **NO** | *Untested* |
| **24** | `PUT` | `/api/v1/kitchen/orders/{id}/start` | `KitchenController` | **NO** | *Untested* |
| **25** | `PUT` | `/api/v1/kitchen/orders/{id}/ready` | `KitchenController` | **NO** | *Untested* |
| **26** | `PUT` | `/api/v1/kitchen/items/{id}/status` | `KitchenController` | **NO** | *Untested* |
| **27** | `GET` | `/api/v1/service-requests` | `ServiceRequestController` | **NO** | *Untested* |
| **28** | `POST` | `/api/v1/service-requests` | `ServiceRequestController` | **NO** | *Untested* |
| **29** | `PUT` | `/api/v1/service-requests/{id}/acknowledge` | `ServiceRequestController` | **NO** | *Untested* |
| **30** | `PUT` | `/api/v1/service-requests/{id}/complete` | `ServiceRequestController` | **NO** | *Untested* |
| **31** | `GET` | `/api/v1/tasks` | `TaskController` | **NO** | *Untested* |
| **32** | `POST` | `/api/v1/tasks` | `TaskController` | **NO** | *Untested* |
| **33** | `POST` | `/api/v1/ai/events` | `AiEventController` | **YES** | `aiIngestionSucceedsWithValidSignature()` |

### Endpoint Coverage Statistics:
- **Total REST Endpoints**: 33
- **Endpoints Covered by Automated Tests**: **10 (30.3%)**
- **Endpoints Lacking Automated Tests**: **23 (69.7%)**

---

## 2. Frontend Component Automated Test Coverage (9 Views)

| View Component | Source File | Unit / Integration Test? | E2E Browser Test? | Coverage |
| :--- | :--- | :---: | :---: | :---: |
| **Login** | `src/pages/Login.jsx` | None | None | **0.0%** |
| **FloorPlan** | `src/pages/FloorPlan.jsx` | None | None | **0.0%** |
| **AlertCenter** | `src/pages/AlertCenter.jsx` | None | None | **0.0%** |
| **KitchenKds** | `src/pages/KitchenKds.jsx` | None | None | **0.0%** |
| **WaiterDashboard** | `src/pages/WaiterDashboard.jsx` | None | None | **0.0%** |
| **CleaningTasks** | `src/pages/CleaningTasks.jsx` | None | None | **0.0%** |
| **CamerasZones** | `src/pages/CamerasZones.jsx` | None | None | **0.0%** |
| **Analytics** | `src/pages/Analytics.jsx` | None | None | **0.0%** |
| **CustomerQrPortal**| `src/pages/CustomerQrPortal.jsx` | None | None | **0.0%** |

### Frontend Coverage Statistics:
- **Total Frontend Views**: 9
- **Automated Test Coverage**: **0.0%**

---

## 3. Python Edge Service Test Coverage

| Module | Source File | Automated Test File | Test Scenarios Covered |
| :--- | :--- | :--- | :--- |
| **Temporal State Engine** | `app/state/occupancy_engine.py` | `tests/test_state_engine.py` | Walk-by rejection, sliding window, vacancy hold |
| **HMAC Signer** | `app/events/signer.py` | `tests/test_state_engine.py` | SHA-256 signature format |
| **SQLite Outbox** | `app/events/outbox.py` | *None* | Untested |
| **Camera Worker** | `app/camera/camera_worker.py` | *None* | Untested |
| **Traffic Forecaster** | `app/forecast/traffic.py` | *None* | Untested |
| **Sentiment Classifier**| `app/nlp/sentiment.py` | *None* | Untested |
| **FastAPI REST API** | `app/main.py` | *None* | Untested |

### Edge Coverage Statistics:
- **Tested Modules**: 2 of 7 (28.6%)
- **Untested Modules**: 5 of 7 (71.4%)
