# StaffSense

**HR-Tech platform for employee attrition risk prediction and task management.**

## Overview

StaffSense is an enterprise HR analytics platform designed to predict employee attrition risk
and streamline task management. It combines a modern Java web interface with a Python-based
machine learning microservice.

### Key Features

- :crystal_ball: **Attrition Risk Prediction** — ML-powered employee turnover risk scoring
- :bar_chart: **Analytics Dashboard** — Real-time metrics, KPIs, and trends
- :clipboard: **Task Management** — Kanban board with drag-and-drop
- :memo: **Employee Surveys** — Custom survey creation and analysis
- :busts_in_silhouette: **One-on-One Meetings** — Track and manage employee meetings
- :zap: **Performance Metrics** — KPI calculation and workload analysis

### Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Java 21, Spring Boot 3.2.5 |
| Frontend | Thymeleaf, Chart.js |
| Database | PostgreSQL 16 / H2 |
| Security | Spring Security, BCrypt |
| ML Service | FastAPI, scikit-learn (separate repo) |

## Quick Links

- [Architecture Overview](architecture.md)
- [Quick Start Guide](setup/quickstart.md)
- [API Reference](api/overview.md)
- [Deployment Guide](deployment.md)
