-- V3: сохранение факторов риска (XAI) в прогнозе.
--
-- ML-сервис возвращает topRiskFactors (объяснение прогноза), но платформа их
-- нигде не хранила и не показывала — на странице риска был только текстовый
-- план действий. Колонка хранит JSON-массив вида
--   [{"factor": "overtime_norm", "impact": 100.0, "description": "..."}]
-- где impact — вклад фактора по шкале 0..100.

ALTER TABLE public.attrition_predictions
    ADD COLUMN IF NOT EXISTS top_risk_factors_json text;
