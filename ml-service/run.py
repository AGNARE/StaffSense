#!/usr/bin/env python
"""
StaffSense ML Service — Quick start:
    pip install -r requirements.txt
    python run.py

This starts a FastAPI server on http://localhost:8001
with a scikit-learn LogisticRegression model trained on dataset_training.csv.

API:
    POST /api/v1/predict   — Predict attrition risk
    GET  /health            — Health check
    POST /api/v1/retrain    — Retrain model from CSV

Example:
    curl -X POST http://localhost:8001/api/v1/predict \
      -H "Content-Type: application/json" \
      -d '{"userId":1,"salary_ratio":0.85,"workload_index":1.45,"kpi_norm":0.62,
           "tenure_months_norm":0.35,"satisfaction_norm":0.48,
           "missed_1on1_ratio":0.15,"overtime_norm":0.72}'
"""

import uvicorn
from app import app

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001, log_level="info")
