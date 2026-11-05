-- V10: настраиваемые пороги уровней риска (FR-039, BR-007).
--
-- До этого пороги задавались только в application.yml и менялись перезапуском
-- приложения с правкой файла. Требование FR-039 предполагает настройку из
-- интерфейса администратором.
--
-- Таблица однорядная (id = 1): пороги — это глобальная настройка, а не набор
-- записей. Значения из конфигурации остаются значениями по умолчанию: если
-- строки нет, приложение работает на risk.thresholds.* из application.yml.
--
-- Ограничение BR-007 (0 < low < medium < 100) продублировано CHECK-ом, чтобы
-- некорректное значение нельзя было записать в обход приложения.

CREATE TABLE IF NOT EXISTS public.risk_threshold_setting (
    id bigint PRIMARY KEY,
    low_bound double precision NOT NULL,
    medium_bound double precision NOT NULL,
    updated_at timestamp NOT NULL,
    updated_by_id bigint,
    CONSTRAINT rts_bounds_check CHECK (
        low_bound > 0 AND medium_bound > low_bound AND medium_bound < 100
    ),
    CONSTRAINT fk_rts_updated_by FOREIGN KEY (updated_by_id) REFERENCES public.users(id)
);

COMMENT ON TABLE public.risk_threshold_setting IS
    'Пороги уровней риска увольнения (FR-039). Одна строка: low < medium.';
