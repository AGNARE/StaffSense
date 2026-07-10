"""
StaffSense ML Service — FastAPI Application
=============================================
Provides REST API for employee attrition prediction using
scikit-learn LogisticRegression trained on the generated dataset.

Endpoint:  POST /api/v1/predict
Startup:   uvicorn app:app --host 0.0.0.0 --port 8001
"""

import logging
import sys
from pathlib import Path

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from model import initialize, get_model, FEATURE_COLUMNS

# ── Logging ──────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("ml-service")

# ── FastAPI ──────────────────────────────────────────────────────────────
app = FastAPI(
    title="StaffSense ML Service",
    version="1.0.0",
    description="Logistic Regression for Employee Attrition Prediction",
)


# ── Pydantic Models ──────────────────────────────────────────────────────

class PredictRequest(BaseModel):
    userId: int | None = None
    salary_ratio: float = 1.0
    workload_index: float = 1.0
    kpi_norm: float = 0.7
    tenure_months_norm: float = 0.5
    satisfaction_norm: float = 0.7
    missed_1on1_ratio: float = 0.0
    overtime_norm: float = 0.0


class RiskFactor(BaseModel):
    factor: str
    impact: float
    description: str


class PredictResponse(BaseModel):
    userId: int | None = None
    riskScore: float
    riskLevel: str
    topRiskFactors: list[RiskFactor]
    aiActionPlan: str
    featuresSnapshot: dict


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    n_features: int
    version: str


# ── Startup ──────────────────────────────────────────────────────────────

@app.on_event("startup")
def startup():
    """Initialize model on service startup."""
    logger.info("=" * 60)
    logger.info("StaffSense ML Service starting...")
    logger.info("=" * 60)

    # Try project root first, then relative to this file
    csv_candidates = [
        Path.cwd() / "dataset_training.csv",
        Path(__file__).parent.parent / "dataset_training.csv",
        Path(__file__).parent / "dataset_training.csv",
    ]

    csv_path = None
    for p in csv_candidates:
        if p.exists():
            csv_path = p
            break

    if csv_path:
        logger.info("Found training CSV: %s", csv_path)
    else:
        logger.warning(
            "No dataset_training.csv found. Checked: %s. "
            "Model will not be trained until CSV is available.",
            [str(p) for p in csv_candidates],
        )

    try:
        result = initialize(csv_path)
        logger.info("Model initialized: %s", result["status"])
        if result.get("metrics"):
            m = result["metrics"]
            logger.info("Training accuracy: %.4f", m["accuracy"])
            logger.info("Model coefficients:")
            for name, coef in m["coefficients"].items():
                logger.info("  %s: %.4f", name, coef)
            logger.info("Intercept: %.4f", m["intercept"])
    except FileNotFoundError as e:
        logger.warning("Model not initialized: %s", e)
    except Exception as e:
        logger.error("Failed to initialize model: %s", e)


# ── Endpoints ────────────────────────────────────────────────────────────

@app.get("/health", response_model=HealthResponse)
def health():
    """Health check endpoint."""
    model = get_model()
    return HealthResponse(
        status="ok",
        model_loaded=model.is_trained,
        n_features=len(model.feature_names),
        version="1.0.0",
    )


@app.post("/api/v1/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    """
    Predict attrition risk for one employee.

    Accepts 7 normalized features, returns risk score (0-100),
    risk level (LOW/MEDIUM/HIGH), top contributing factors, and action plan.

    Formula:
        logit = coef_0*x_0 + ... + coef_6*x_6 + intercept
        probability = 1 / (1 + exp(-logit))
        riskScore = probability * 100

    Request body example:
    ```json
    {
        "userId": 1,
        "salary_ratio": 0.85,
        "workload_index": 1.45,
        "kpi_norm": 0.62,
        "tenure_months_norm": 0.35,
        "satisfaction_norm": 0.48,
        "missed_1on1_ratio": 0.15,
        "overtime_norm": 0.72
    }
    ```
    """
    model = get_model()

    if not model.is_trained:
        # Fallback: use heuristic if model not trained
        logger.warning("Model not trained, using heuristic fallback")
        return _fallback_prediction(request)

    features = {
        "salary_ratio": request.salary_ratio,
        "workload_index": request.workload_index,
        "kpi_norm": request.kpi_norm,
        "tenure_months_norm": request.tenure_months_norm,
        "satisfaction_norm": request.satisfaction_norm,
        "missed_1on1_ratio": request.missed_1on1_ratio,
        "overtime_norm": request.overtime_norm,
    }

    result = model.predict(features)
    result["userId"] = request.userId

    logger.info(
        "Prediction for userId=%s: riskScore=%.1f, level=%s",
        request.userId,
        result["riskScore"],
        result["riskLevel"],
    )

    # Convert RiskFactor dicts to Pydantic models
    result["topRiskFactors"] = [RiskFactor(**rf) for rf in result["topRiskFactors"]]
    return PredictResponse(**result)


@app.post("/api/v1/retrain")
def retrain():
    """
    Retrain the model from the CSV dataset.
    Useful after the dataset has been regenerated.
    """
    csv_candidates = [
        Path.cwd() / "dataset_training.csv",
        Path(__file__).parent.parent / "dataset_training.csv",
    ]

    csv_path = None
    for p in csv_candidates:
        if p.exists():
            csv_path = p
            break

    if not csv_path:
        raise HTTPException(status_code=404, detail="dataset_training.csv not found")

    try:
        metrics = get_model().train(csv_path)
        return {"status": "retrained", "metrics": metrics}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ── Fallback ─────────────────────────────────────────────────────────────

def _fallback_prediction(request: PredictRequest) -> dict:
    """Simple heuristic when ML model is unavailable."""
    score = 50.0
    if request.workload_index > 1.3:
        score += 15
    if request.salary_ratio < 0.9:
        score += 10
    if request.satisfaction_norm < 0.5:
        score += 10
    score = min(score, 95.0)

    level = "HIGH" if score >= 66 else ("MEDIUM" if score >= 33 else "LOW")

    return {
        "userId": request.userId,
        "riskScore": score,
        "riskLevel": level,
        "topRiskFactors": [
            RiskFactor(
                factor="Индекс нагрузки",
                impact=request.workload_index,
                description="Эвристическая оценка (ML-модель недоступна)",
            )
        ],
        "aiActionPlan": "ML-сервис временно недоступен. Запустите обучение через POST /api/v1/retrain.",
        "featuresSnapshot": {
            "salary_ratio": request.salary_ratio,
            "workload_index": request.workload_index,
            "kpi_norm": request.kpi_norm,
            "tenure_months_norm": request.tenure_months_norm,
            "satisfaction_norm": request.satisfaction_norm,
            "missed_1on1_ratio": request.missed_1on1_ratio,
            "overtime_norm": request.overtime_norm,
        },
    }


# ── Main ─────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
