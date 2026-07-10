# StaffSense ML Service

Сервис машинного обучения для прогнозирования риска увольнения сотрудников.

## Архитектура

```
Python FastAPI (port 8001)     Java Spring Boot (port 9091)
┌──────────────────────┐       ┌─────────────────────────┐
│  LogisticRegression  │◄─────►│  AttritionMlService     │
│  (scikit-learn)      │ POST  │  calls /api/v1/predict  │
│                      │       │                         │
│  Обучается на:       │       │  Использует 7 признаков │
│  dataset_training.csv│       │  из БД сотрудника       │
└──────────────────────┘       └─────────────────────────┘
```

## Быстрый старт

```bash
cd ml-service
pip install -r requirements.txt
python run.py
```

После запуска:
- Swagger UI: http://localhost:8001/docs
- Health: http://localhost:8001/health

## API Endpoints

### GET /health
Проверка состояния сервиса.

### POST /api/v1/predict
Прогноз риска увольнения для одного сотрудника.

**Входные параметры (все normalized 0-1):**

| Параметр | Описание | Формула |
|----------|----------|---------|
| salary_ratio | Уровень зарплаты | salary / market_average |
| workload_index | Индекс нагрузки | total_hours / weekly_capacity |
| kpi_norm | KPI нормализованный | last_kpi / 100 |
| tenure_months_norm | Стаж | months / 60 |
| satisfaction_norm | Удовлетворённость | avg_likert / 5 |
| missed_1on1_ratio | Пропуски 1:1 | missed / total |
| overtime_norm | Переработки | overtime / (capacity * 0.2) |

**Формула модели:**
```
logit = coef_0*x_0 + coef_1*x_1 + ... + coef_6*x_6 + intercept
probability = 1 / (1 + exp(-logit))
riskScore = probability * 100
```

**Уровни риска:**
- `LOW`: riskScore < 33
- `MEDIUM`: 33 <= riskScore < 66
- `HIGH`: riskScore >= 66

**Пример запроса:**
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

**Пример ответа:**
```json
{
  "userId": 1,
  "riskScore": 78.3,
  "riskLevel": "HIGH",
  "topRiskFactors": [
    {"factor": "Индекс нагрузки", "impact": 0.85, "description": "..."}
  ],
  "aiActionPlan": "ВЫСОКИЙ РИСК УВОЛЬНЕНИЯ...",
  "featuresSnapshot": {"salary_ratio": 0.85, ...}
}
```

### POST /api/v1/retrain
Переобучить модель из CSV. Нужно если dataset_training.csv был обновлён.

## Зависимости

- Python 3.10+
- scikit-learn 1.5.0
- FastAPI 0.111.0
- pandas 2.2.2
- numpy 1.26.4

## Связь с Java-приложением

Сервис вызывается из `AttritionMlService.java` через `RestTemplate`.
URL настраивается в `application.yml`:

```yaml
ml:
  service:
    url: http://localhost:8001/api/v1/predict
```

Если Python-сервис не запущен, Java-приложение использует встроенную эвристику (fallback).
