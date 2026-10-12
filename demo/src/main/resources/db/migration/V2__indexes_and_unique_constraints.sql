-- V2: индексы на внешние ключи и ограничения уникальности.
--
-- PostgreSQL не создаёт индексы на FK автоматически, поэтому все выборки по
-- связям (findByUserIdOrderByPeriodEndDesc, findByProjectId, findBySurveyId,
-- findByResponseId, findByEmployeeId) выполнялись последовательным сканированием.
--
-- Уникальные индексы добавлены вместо ALTER TABLE ... ADD CONSTRAINT, потому что
-- PostgreSQL не поддерживает IF NOT EXISTS для констрейнтов: миграция должна
-- безопасно применяться и к существующей базе.

-- ── Индексы внешних ключей ───────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_users_manager_id
    ON public.users (manager_id);

CREATE INDEX IF NOT EXISTS idx_projects_owner_pm_id
    ON public.projects (owner_pm_id);

CREATE INDEX IF NOT EXISTS idx_tasks_project_id
    ON public.tasks (project_id);

CREATE INDEX IF NOT EXISTS idx_tasks_assignee_id
    ON public.tasks (assignee_id);

CREATE INDEX IF NOT EXISTS idx_surveys_created_by_id
    ON public.surveys (created_by_id);

CREATE INDEX IF NOT EXISTS idx_survey_questions_survey_id
    ON public.survey_questions (survey_id);

CREATE INDEX IF NOT EXISTS idx_survey_responses_survey_id
    ON public.survey_responses (survey_id);

CREATE INDEX IF NOT EXISTS idx_survey_answers_response_id
    ON public.survey_answers (response_id);

CREATE INDEX IF NOT EXISTS idx_survey_answers_question_id
    ON public.survey_answers (question_id);

CREATE INDEX IF NOT EXISTS idx_one_on_one_meetings_employee_id
    ON public.one_on_one_meetings (employee_id);

CREATE INDEX IF NOT EXISTS idx_one_on_one_meetings_manager_id
    ON public.one_on_one_meetings (manager_id);

CREATE INDEX IF NOT EXISTS idx_kpi_records_user_id
    ON public.kpi_records (user_id);

CREATE INDEX IF NOT EXISTS idx_workload_records_user_id
    ON public.workload_records (user_id);

CREATE INDEX IF NOT EXISTS idx_attrition_predictions_user_id
    ON public.attrition_predictions (user_id);

-- ── Вспомогательные индексы под частые выборки ───────────────────────────
-- survey_responses.user_id не является FK (NULL для анонимных опросов),
-- но по нему ищется история ответов сотрудника.
CREATE INDEX IF NOT EXISTS idx_survey_responses_user_id
    ON public.survey_responses (user_id);

-- Канбан-доска группирует задачи по статусу.
CREATE INDEX IF NOT EXISTS idx_tasks_status
    ON public.tasks (status);

-- ── Ограничения уникальности ─────────────────────────────────────────────
-- Один ответ на один вопрос в рамках одной анкеты.
CREATE UNIQUE INDEX IF NOT EXISTS uq_survey_answers_response_question
    ON public.survey_answers (response_id, question_id);

-- Один KPI-период на сотрудника.
CREATE UNIQUE INDEX IF NOT EXISTS uq_kpi_records_user_period
    ON public.kpi_records (user_id, period_start, period_end);

-- Один период нагрузки на сотрудника.
CREATE UNIQUE INDEX IF NOT EXISTS uq_workload_records_user_period
    ON public.workload_records (user_id, period_start, period_end);
