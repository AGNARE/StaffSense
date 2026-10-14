# Трассируемость требований ТЗ ↔ реализация: FR и NFR (разделы 7–9)

**Дата аудита:** 13.10.2026
**Источники требований:**
- ТЗ StaffSense v1.1 — экспорт `_tz_extract/TZ_V1_1.md`, разделы **7 «Функциональные требования» (FR-001…FR-039)**, **8 «Нефункциональные требования» (NFR-001…NFR-026)**, **9 «User Stories» (E-01…E-08)**;
- Дополнение № 1 к ТЗ v1.1 (`StaffSense_TZ_ADDENDUM_V1_1.md`) — переформулировка FR-030 в FR-040/FR-041, новые FR-042…FR-048, BR-023…BR-031.

**Объекты проверки:** `C:\Users\Argen\Products\StaffSense` (Java 21 / Spring Boot 3.2.5 / Thymeleaf, commit `d2b9dc4`), `C:\Users\Tima\PythonProjects\StaffSenseML` (Python / FastAPI).

**Методика.** Статус каждого требования определён по реальному коду (файл + класс/метод), а не по колонке «Статус» самого ТЗ: документ содержит устаревшие отметки (например «в текущем коде `@Scheduled` отсутствует», «RestTemplate создан без таймаутов», «drag-and-drop не обнаружен»), которые на текущем коммите уже не соответствуют действительности. Seed/demo-данные (`DataInitializer`, `OnboardingContentInitializer`) реализацией не считались. Где подтверждения нет — статус ❌ и пометка «не найдено в коде».

**Легенда статусов:** ✅ реализовано · 🟡 частично · ❌ не реализовано · ⚪ не применимо / отменено (план `[План]` либо переформулировано дополнением).

---

## 1. Сводка

| Показатель | FR | NFR | Итого |
|---|---|---|---|
| Всего требований в разделах 7–8 | 39 | 26 | **65** |
| ✅ реализовано | 32 | 17 | **49** |
| 🟡 частично | 2 | 3 | **5** |
| ❌ не реализовано | 4 | 2 | **6** |
| ⚪ не применимо / отменено | 1 | 4 | **5** |

> **Обновление после итерации доработок.** FR-007, FR-018, FR-019 и FR-022 переведены
> из 🟡 в ✅: обязательность PM-владельца проекта валидируется формой и Bean Validation,
> веса на экране ML-модели берутся из нового эндпоинта ML-сервиса `/api/v1/model-info`,
> «Радар выгорания» реализован в `BurnoutRadarService` и выведен на `/dashboard`,
> а прохождение опроса проверяет статус, период, охват и однократность участия
> (`SurveyService#requireCanTake` + таблица `survey_participation`, миграция V7).
> Покрытие — `ProjectControllerTest`, `RiskThresholdPropertiesTest`,
> `ModelInsightServiceWeightsTest`, `BurnoutRadarServiceTest`, `SurveyServiceTest`.
> Оставшиеся 🟡: FR-010, FR-036.

Приоритеты заданы только для FR (в разделе 8 колонки приоритета нет — NFR проверяются по категориям и методу проверки).
MUST: 27 FR · SHOULD: 10 FR · COULD: 2 FR.

Разбивка пробелов: **MUST — 1** (FR-010), **SHOULD — 4** (FR-033, FR-034, FR-035, FR-036), **COULD — 1** (FR-039), **NFR (приоритет не задан) — 5** (NFR-001, NFR-002, NFR-019, NFR-020, NFR-021).

---

## 2. Раздел 7 — функциональные требования (FR-001…FR-039)

| ID | Требование (кратко) | Приоритет | Статус | Доказательство в коде | Что осталось |
|---|---|---|---|---|---|
| FR-001 | Аутентификация по email/паролю, BCrypt, отказ для `active=false` | MUST | ✅ | `SecurityConfig.java#filterChain` (formLogin `/login`), `CustomUserDetailsService.java#loadUserByUsername`, `User.java#isEnabled` | — |
| FR-002 | Выход `/logout`, тайм-аут сессии 30 мин, `/login?expired` | MUST | ✅ | `SecurityConfig.java#filterChain` (logout, `invalidateHttpSession`, `deleteCookies`, `invalidSessionUrl`), `application.yml:5` (`session.timeout: 30m`) | — |
| FR-003 | Rate limit POST `/login`: 5 попыток, +1/60 с, 429 + `Retry-After: 60` | MUST | ✅ | `RateLimitingFilter.java#doFilterInternal` (Bucket4j 5/60 с, Caffeine 1000 записей / 1 ч) | — |
| FR-004 | RBAC по URL и методам; скрытие недоступных ссылок меню | MUST | ✅ | `SecurityConfig.java#filterChain` (правила `/admin`,`/hr`,`/risk`,`/analytics`,`/surveys/manage`,`/employees`), `@PreAuthorize` в `EmployeeController`, `RiskController`, `ProjectController`, `OneOnOneController`; проверки «своя команда» — `TeamAccessService.java#canViewEmployee/canModifyTask/canManageMeeting`; меню по ролям — `templates/fragments/layout.html:130,139` | — |
| FR-005 | `/admin`: список с пагинацией 20 + фильтр по роли, создание, активация/деактивация | MUST | ✅ | `AdminController.java#listUsers/createUser/toggleActive` (запрет деактивации себя) | — |
| FR-006 | `/employees` (10 на стр., фильтр по отделу), профиль, история KPI/нагрузки, прогноз скрыт от EMPLOYEE, CSV | MUST | ✅ | `EmployeeController.java#listEmployees/viewProfile/exportCsv` (`canViewRisk`), `EmployeeService.java#findAllActive/findByDepartment` | — |
| FR-007 | `/projects`: список и создание с названием, описанием, PM-владельцем | MUST | ✅ | `ProjectController.java#listProjects/createProject` (`@Valid` + `BindingResult`), `model/Project.java` (`@NotNull`, `@JoinColumn(nullable = false)`), `config/UserByIdConverter.java`, `templates/projects/new.html` (`required`, сообщение у поля), `db/migration/V1__baseline_schema.sql:75` (`owner_pm_id … NOT NULL`) | — |
| FR-008 | Канбан-доска `/projects/{id}/board`, серверная группировка TODO/IN_PROGRESS/REVIEW/DONE | MUST | ✅ | `ProjectBoardController.java#getProjectBoard` (`tasksByStatus`), `templates/projects/board.html:21-79` | — |
| FR-009 | Создание задачи: форма + REST, старт TODO, `reopenedCount=0` | MUST | ✅ | `ProjectBoardController.java#createTask`, `TaskController.java#createTask`, `TaskService.java#createTask`, `TaskCreateRequest.java` (`@NotBlank` title, `@Positive` estimateHours) | Оценка часов необязательна (`@Positive` без `@NotNull`); подстановки «8.0 ч по умолчанию» из ТЗ в коде нет |
| FR-010 | Смена статуса задачи: `completedAt`, сброс при выходе из DONE, `reopenedCount+1`; целевой drag-and-drop (HTMX) | MUST | 🟡 | `TaskService.java#updateStatus` (правила DONE/reopen), `TaskController.java#updateStatus` (`POST /api/tasks/{id}/status`), `templates/projects/board.html:42,44,111-139` (HTML5 drag-and-drop + `fetch`) | Drag-and-drop реализован, но не на HTMX: после успешного запроса `location.reload()` (замена фрагмента `hx-swap` отсутствует, зависимость HTMX в `pom.xml` не подключена); правила допустимых переходов между статусами не проверяются |
| FR-011 | Индекс нагрузки = Σ estimateHours активных задач / (capacity × недели) | MUST | ✅ | `WorkloadCalculationService.java#calculateAndSave/getCurrentWorkloadIndex` (учёт границ периода, защита от нулевой ёмкости) | — |
| FR-012 | KPI по DONE-задачам: on-time, reopen, нормировка на сумму весов | MUST | ✅ | `KpiCalculationService.java#calculateAndSave/computeKpiScore` (веса читаются из `kpi.weights.*`, `managerRating` (w3=0.3) учитывается, upsert за период), `application.yml:45-49` | — |
| FR-013 | Сбор 7 признаков для ML (значения по умолчанию при отсутствии данных) | MUST | ✅ | `AttritionMlService.java#buildRequest/calculateSalaryRatio/calculateKpiNorm(0.7)/calculateSatisfactionNorm(0.65)/calculateMissed1on1Ratio/calculateOvertimeNorm` | — |
| FR-014 | ML-прогноз: scaler, `predict_proba`, riskScore, riskLevel, топ-3 фактора, aiActionPlan, снимок с `z_score` | MUST | ✅ | `StaffSenseML/main.py#predict_attrition`, `StaffSenseML/services.py#LogisticRegressionPredictor.predict/_build_top_factors/_generate_action_plan` (пороги 33/66, `z_score` в snapshot), `AttritionMlService.java#computeAndSave` | — |
| FR-015 | Fallback-эвристика при ошибке ML: `min(50+15·[w>1.3]+10·[sal<0.9]+10·[sat<0.5], 95)` | MUST | ✅ | `AttritionMlService.java#fallbackPrediction`, вызов в `#computeAndSave` (catch + WARN) | — |
| FR-016 | `/risk`: список (15, UNKNOWN без прогноза), детали с карточками признаков и z-оценкой, `risks.csv` | MUST | ✅ | `RiskController.java#listRisks/viewRiskDetail/exportCsv`, `RiskController.java#FeatureCardBuilder.buildFromSnapshot` (`z_score`, пороги normal/warning/critical), `CsvExportService.java#exportRisksToCsv` | Сортировка по убыванию риска выполняется после выборки страницы (`listRisks`, `Comparator` по DTO) — сортировки на уровне БД нет |
| FR-017 | Пересчёт одного сотрудника (синхронно) и массовый (асинхронно, пул `mlTaskExecutor`) | MUST | ✅ | `RiskController.java#recomputeRisk`, `AnalyticsController.java#recomputeAllML`, `AttritionMlService.java#computeAllEmployeesAsync` (`@Async("mlTaskExecutor")`), `AsyncConfig.java#mlTaskExecutor` | — |
| FR-018 | `/analytics/ml-model`: веса признаков, распределение риска, сравнение, тепловая карта, флаг «вычисляется» | SHOULD | ✅ | `AnalyticsController.java#mlModelAnalytics/addFeatureWeights`, `ModelInsightService.java#getFeatureWeights` (запрос `GET /api/v1/model-info`, кэш `ml.service.model-info-cache-ms`, fallback с пометкой `EXPERT`), `main.py#model_info`, `templates/analytics/ml-model.html` (бейдж источника, нормированная значимость) | — |
| FR-019 | `/dashboard` для не-EMPLOYEE: активные сотрудники, HIGH/MEDIUM/LOW, средний риск, 5 прогнозов, 5 встреч, 5 опросов; целевой «Радар выгорания» | MUST | ✅ | `DashboardController.java#managementDashboard`, `service/metrics/BurnoutRadarService.java` (6 факторов, `TeamAccessService`), `templates/dashboard/main.html` (радар-диаграмма Chart.js, пустое состояние) | — |
| FR-020 | Личный кабинет EMPLOYEE: опросы, свои 1:1, последние KPI и нагрузка, без риска | MUST | ✅ | `DashboardController.java#dashboard/personalDashboard`, `templates/dashboard/personal.html:24,32,42,74` (блоки риска нет) | — |
| FR-021 | Управление опросами: список 10/стр., создание (тип, охват, даты, анонимность, шаблон/свои вопросы), правка DRAFT, активация, закрытие | MUST | ✅ | `SurveyController.java#manageSurveys/createSurvey/updateSurvey/activate/close`, `SurveyService.java#update` (BR-013: только DRAFT), `SurveyService.java#saveTemplateQuestions/saveCustomQuestions`, `SurveyTemplateService.java` | — |
| FR-022 | Прохождение опроса: сохранение ответов, анонимно → `user_id = null`; предусловия — охват/окно дат/однократность | MUST | ✅ | `SurveyService.java#requireCanTake` (статус ACTIVE, окно дат, охват, однократность) вызывается в `SurveyController.java#takeSurvey/submitSurvey`; `submitResponse` принимает только вопросы своего опроса и пишет факт участия в `survey_participation` (`model/SurveyParticipation.java`, миграция `V7__survey_participation.sql`); `hasAlreadyResponded` учитывает таблицу участия | — |
| FR-023 | Результаты опроса: число ответов, средний балл, статистика по LIKERT (среднее/min/max/распределение), свободные ответы, CSV; «Anonymous» | SHOULD | ✅ | `SurveyController.java#surveyResults/exportCsv`, `SurveyService.java#getAnalytics` (пакетная загрузка ответов), `CsvExportService.java#exportSurveyResultsToCsv` | — |
| FR-024 | `/analytics/wellbeing`: динамика среднего LIKERT по датам + тепловая карта по отделам без анонимных | SHOULD | ✅ | `AnalyticsController.java#wellbeingAnalytics` (`likertsByEmployee` только при `resp.getUserId() != null`), `templates/analytics/wellbeing.html` (line + heatmap) | — |
| FR-025 | Встречи 1:1: список 15/стр. с фильтрацией по роли, создание PLANNED, завершение (notes, actionItems), отмена | MUST | ✅ | `OneOnOneController.java#listMeetings/newMeetingForm/createMeeting/completeMeeting/cancelMeeting` (`@PreAuthorize`), `OneOnOneService.java#create/complete/cancel`, `MeetingStatus.java` | Операция переноса (`RESCHEDULED`) по-прежнему не реализована — статус есть в перечислении, перехода в него нет (в ТЗ помечено «требует уточнения») |
| FR-026 | Ежедневная автопометка `missed=true` для просроченных PLANNED-встреч | SHOULD | ✅ | `OneOnOneMissedScheduler.java#markOverdueMeetingsAsMissed` (`@Scheduled(cron=…)`), `SchedulingConfig.java`, `OneOnOneService.java#markOverdueAsMissed`, `application.yml:65` | Отметка ТЗ «План (Фаза 3)» и утверждение «`@Scheduled` отсутствует» устарели: функциональность реализована |
| FR-027 | `/analytics/workload` и `/analytics/kpi`: диаграммы последних значений, пустые состояния | SHOULD | ✅ | `AnalyticsController.java#workloadAnalytics/kpiAnalytics`, `templates/analytics/kpi.html:13` («Нет данных KPI для отображения»), `workload.html:13` | — |
| FR-028 | Экспорт CSV (`employees.csv`, `risks.csv`, `survey-{id}-results.csv`), UTF-8 + BOM, экранирование | SHOULD | ✅ | `CsvExportService.java#exportEmployeesToCsv/exportRisksToCsv/exportSurveyResultsToCsv/writeBom/escapeCsv` (+ защита от CSV-инъекций, `FORMULA_PREFIXES`) | — |
| FR-029 | `GET /api/dataset/training` и `/api/dataset/test` | COULD | ✅ | `DatasetController.java#downloadTraining/downloadTest/serveFile` (404 при отсутствии файла) | — |
| FR-030 | Онбординг кандидата по ИНН (Тундук, mock), портал кандидата, `candidates_onboarding` | MUST | ⚪ | — | **Переформулировано дополнением № 1 (§3.1):** FR-030 раскрыт в FR-040 (верификация по ИНН, инициатор — HR) и FR-041 (личное дело). Роль `CANDIDATE` и сущность `candidates_onboarding` не требуются (закрытие Q-002). Требование в исходной редакции не реализуется и не должно |
| FR-031 | Табель: отработанные часы, удалённая работа, больничные, переработки (`work_schedules`), `overtime_hours` → `overtime_norm` | MUST | ✅ | `TimesheetService.java#saveEntry/deleteEntry/buildRow`, `model/WorkSchedule.java`, `db/migration/V6__timesheet_and_memos.sql`, признак `overtime_norm` — `AttritionMlService.java#calculateOvertimeNorm` | Реализовано в редакции дополнения (FR-045…FR-047, BR-027): часы вносит руководитель по своей команде, сотрудник — только просмотр. Роль EMPLOYEE как редактора своего табеля, указанная в ТЗ v1.1, намеренно не реализована |
| FR-032 | Служебные записки: `PENDING_PM → PENDING_HR → APPROVED`, отклонение на любом этапе → `REJECTED` | MUST | ✅ | `ServiceMemoService.java#submit/approveByPm/approveByHr/reject`, `model/MemoStatus.java`, `model/ServiceMemo.java#isEditable` | Реализовано как FR-048 дополнения; набор статусов расширен (`DRAFT`, `CANCELLED`), добавлены редактирование/отзыв автором и авто-простановка в табель (BR-030). Автоматическая запись в `work_schedules` из «требует уточнения» **реализована** — `TimesheetService.java#applyApprovedMemo` |
| FR-033 | Реестр AI-скиллов (AI Skill Hub, MCP): парсинг SKILL.md, YAML-frontmatter, каталог по IT-ролям | SHOULD | ❌ | не найдено в коде — поиск по `SKILL.md`, `ai_skill`, `AISkillService`, `agent_roles`, `SkillHub` по обоим репозиториям не дал совпадений; сущности/миграции/шаблона нет | Модуль F-15 (эпик E-08) отсутствует полностью: нет сущности, таблицы, сервиса, контроллера, шаблонов и тестов |
| FR-034 | Вызов AI-скилла из карточки задачи, подстановка title/description/tech_stack, вывод ответа в комментариях | SHOULD | ❌ | не найдено в коде — вызывающего кода и HTMX-эндпоинта нет; `model/Task.java` не содержит полей `techStack`/комментариев (только title, description, assignee, status, priority, estimateHours, actualHours, dueDate, createdAt, completedAt, reopenedCount) | Модуль не начат; хранилище ответов ИИ (комментарии задачи) в модели данных отсутствует |
| FR-035 | Динамическая фильтрация канбан-доски (исполнитель, проект, приоритет) без перезагрузки | SHOULD | ❌ | не найдено в коде — в `templates/projects/board.html` и `ProjectBoardController.java#getProjectBoard` фильтров и фрагментного обновления нет | Фильтрация доски не реализована |
| FR-036 | Асинхронный перерасчёт нагрузки и риска по событиям (`@Async` / Event Publisher) | SHOULD | 🟡 | `TaskService.java#updateStatus/recalculateMetrics` — при смене статуса задачи синхронно пересчитываются нагрузка и KPI; `@EnableAsync` и пул есть (`AsyncConfig.java`) | Перерасчёт запускается только синхронно из `TaskService` (блокирует поток запроса), публикации событий (`ApplicationEventPublisher` / `@EventListener`) в коде нет; риск (ML-прогноз) при событиях не пересчитывается — пересчёт только ручной (`RiskController#recomputeRisk`, `AnalyticsController#recomputeAllML`); табель и ответы на опрос перерасчёт не инициируют |
| FR-037 | Обучение: 700 записей, StandardScaler, LogisticRegression (L2, C=1.0, lbfgs, max_iter=1000, balanced, random_state=42), сохранение `model.pkl`/`scaler.pkl`/`weights.json`, вывод метрик | MUST | ✅ | `StaffSenseML/train.py#generate_dataset_csv` (n=700), `#train_with_sklearn` (StandardScaler; LogisticRegression lbfgs/max_iter=1000/class_weight='balanced'/random_state=42; `joblib.dump` модели и scaler; `weights.json`; accuracy + classification_report + confusion_matrix) | Метрики считаются in-sample (`accuracy_score(y, y_pred)` на обучающей выборке), `train_test_split` не используется — отложенная выборка отсутствует (совпадает с оговоркой в ТЗ) |
| FR-038 | `GET /health` → `{"status":"ok","service":"StaffSense ML"}`; Swagger `/docs`, ReDoc `/redoc`; 422/500 | MUST | ✅ | `StaffSenseML/main.py#health` (дополнительно `model_loaded`, `n_features`, `version`), `#predict_attrition` (500 при ошибке), FastAPI-валидация 422; `tests/test_api.py#test_health_reports_service_and_model_state/test_openapi_is_available` | — |
| FR-039 | Администрирование параметров и весов ML-модели (ADMIN) | COULD | ❌ | не найдено в коде — ни экрана/эндпоинта администрирования ML-параметров, ни вызовов `POST /api/v1/retrain` из Java-приложения. Веса модели теперь доступны только для чтения: `GET /api/v1/model-info` + `ModelInsightService#getFeatureWeights` | Требование остаётся в статусе «План (Фаза 3)»: состав параметров и способ применения в ТЗ помечены «требует уточнения». Пороги риска вынесены в `risk.thresholds` (BR-007 ✅), но интерфейса их изменения по-прежнему нет |

---

## 3. Раздел 8 — нефункциональные требования (NFR-001…NFR-026)

Приоритеты в разделе 8 ТЗ не заданы (колонки «Приоритет» в таблице нет) — указано «не задан».

| ID | Требование (кратко) | Приоритет | Статус | Доказательство в коде | Что осталось |
|---|---|---|---|---|---|
| NFR-001 | HTMX-фрагменты интерфейса ≤ 150 мс (p95) | не задан | ❌ | не найдено в коде — HTMX в проекте не используется: зависимости в `pom.xml` нет, атрибутов `hx-*` в `templates/**` нет; единственный AJAX-вызов — `board.html:125` (`fetch` → JSON) | Предмет измерения отсутствует: HTMX-фрагментов нет, фрагментного обмена нет. Замеров p95 нет, тест TC-049 (раздел 32 ТЗ) не реализован |
| NFR-002 | Расчёт ML-прогноза `POST /api/v1/predict` ≤ 300 мс | не задан | ❌ | не найдено в коде — в `StaffSenseML/tests/test_api.py`, `test_services.py`, `test_schemas.py` нет ни одного замера времени; тайминги в `services.py#predict` не измеряются | Производительность ML-эндпоинта не подтверждена ни тестом, ни метрикой; TC-049 не реализован |
| NFR-003 | Таймаут HTTP-вызова ML: connect + read = 2,0 с, далее Fallback | не задан | ✅ | `RestTemplateConfig.java#restTemplate` (`setConnectTimeout/setReadTimeout`, `ml.service.*-timeout-ms: 2000`), `application.yml:42-43`, fallback — `AttritionMlService.java#computeAndSave` | — |
| NFR-004 | Пагинация списков: employees 10, risk 15, oneonone 15, surveys 10, admin 20 | не задан | ✅ | `EmployeeController.java:64` (10), `RiskController.java:58` (15), `OneOnOneController.java:48` (15), `SurveyController.java:41` (10), `AdminController.java:33` (20) — все на `PageRequest`/`Pageable` | Для `/risk` сортировка применяется только внутри страницы (`RiskController#listRisks`), сортировки на уровне БД нет |
| NFR-005 | Пароли только в виде BCrypt-хеша | не задан | ✅ | `SecurityConfig.java:44,119-122` (`BCryptPasswordEncoder(BCRYPT_STRENGTH = 12)`), `AdminController.java#createUser`, `HireEmployeeService.java#applyCorporateFields` | — |
| NFR-006 | CSRF включён для всех форм и запросов, кроме `/api/**` | не задан | ✅ | `SecurityConfig.java#filterChain` (`CookieCsrfTokenRepository.withHttpOnlyFalse()`, исключений для `/api/**` нет), `templates/fragments/layout.html:17-18` (meta `_csrf`), `board.html:91-99,123` | Требование выполнено с запасом: ранее существовавшее исключение `/api/**` убрано, AJAX передаёт токен в `X-XSRF-TOKEN` |
| NFR-007 | Ограничение попыток входа: 5 на IP, пополнение 1/60 с | не задан | ✅ | `RateLimitingFilter.java#createNewBucket/resolveClientIp/doFilterInternal` | — |
| NFR-008 | Защита от OWASP Top-10: валидация входа, экранирование вывода (Thymeleaf), параметризованные запросы JPA | не задан | ✅ | `TaskCreateRequest.java`/`HireEmployeeForm.java` (`@NotBlank`, `@Positive`, `@Size`), `TaskController.java` (`@Valid`), шаблоны Thymeleaf (`th:text`, без `th:utext` в формах), репозитории Spring Data JPA (параметризованные запросы), `CsvExportService.java#escapeCsv` (антиинъекция) | Формальный чек-лист OWASP-категорий (какие именно категории проверяются) в ТЗ и репозитории не зафиксирован |
| NFR-009 | Секреты не хранятся в репозитории в открытом виде | не задан | ✅ | `application.yml:16` (`password: ${SPRING_DATASOURCE_PASSWORD:}` — без значения по умолчанию) | — |
| NFR-010 | При недоступности ML-сервиса (порт 8001) платформа работоспособна, расчёт — эвристикой | не задан | ✅ | `AttritionMlService.java#computeAndSave` (catch → WARN → `fallbackPrediction`), `#fallbackPrediction` (score + level + пояснение) | — |
| NFR-011 | Целевая доступность платформы (SLA) | не задан | ⚪ | — | Значение в исходных данных отсутствует («требует уточнения») — требование не проверяемо |
| NFR-012 | Модульный монолит (Controller/Service/Repository/DTO) + отдельно деплоимый ML-микросервис | не задан | ✅ | Структура `demo/src/main/java/com/staffsense/demo/{controller,service,repository,dto,model,config}`; отдельный сервис `C:\Users\Tima\PythonProjects\StaffSenseML` (`main.py`, `services.py`, `schemas.py`) | Целевое число пользователей/сотрудников в ТЗ не задано |
| NFR-013 | Массовый пересчёт асинхронен: пул core 4 / max 8 / очередь 50, без блокировки HTTP-потока | не задан | ✅ | `AsyncConfig.java#mlTaskExecutor` (4/8/50, `@EnableAsync`), `AttritionMlService.java#computeAllEmployeesAsync` (`@Async("mlTaskExecutor")`), вызовы — `AnalyticsController.java:178,205` | — |
| NFR-014 | Операции записи опросов и ответов транзакционны | не задан | ✅ | `SurveyService.java` — `@Transactional` на `submitResponse`, `activate`, `close`, `delete`, `update`, `saveTemplateQuestions`, `saveCustomQuestions` | Ранее нетранзакционный `update()` переведён в транзакцию |
| NFR-015 | Интерфейс на русском; ошибки валидации у полей или в flash; уведомления после каждой записи | не задан | ✅ | Русские шаблоны `templates/**`; ошибки полей — `templates/projects/task-form.html:26` (`th:errors`), прочие — `GlobalControllerAdvice.java#errorView`; flash-сообщения (`success`/`error`/`info`) во всех пишущих контроллерах | Ручной чек-лист по разделу 23 ТЗ в репозитории не зафиксирован |
| NFR-016 | Тёмная и светлая темы оформления | не задан | ✅ | `static/css/style.css:55-240` (`[data-theme="light"]`), `templates/fragments/layout.html:22-52,168-169` (сохранение темы в `localStorage`, кнопка переключения) | — |
| NFR-017 | Соответствие уровню WCAG | не задан | ⚪ | — | Уровень WCAG в исходных данных не определён («требует уточнения»); аудита контрастности/клавиатурной навигации нет — требование не проверяемо |
| NFR-018 | Java 21, Spring Boot 3.2.5, PostgreSQL 16 (H2 в тестах); ML: Python 3.11+ | не задан | ✅ | `pom.xml:8` (3.2.5), `pom.xml:17` (`<java.version>21</java.version>`), `pom.xml:72-78` (postgresql + h2), `demo/src/test/resources/application-test.yml` (H2 `create-drop`), `StaffSenseML/requirements.txt` (диапазоны версий, комментарий: локально 3.14, CI 3.11) | — |
| NFR-019 | Поддерживаемые браузеры и разрешения экрана | не задан | 🟡 | `static/css/style.css:686,836` (`@media (max-width: 768px)`), `.sidebar { transform: translateX(-100%); }` | Перечень браузеров/разрешений в ТЗ не задан. Адаптация под ≤768 px есть, но боковое меню скрывается без кнопки открытия: обработчика показа сайдбара в `static/js/shared-components.js` нет — на мобильных навигация недоступна |
| NFR-020 | Журналирование ключевых событий INFO и сбоев ML WARN/ERROR через SLF4J | не задан | 🟡 | `TaskService.java:54,91` (создание задачи, смена статуса), `AttritionMlService.java:115,121,353` (вызов ML, сбой WARN, сохранение прогноза), `WorkloadCalculationService.java:104`, `KpiCalculationService.java:125`, `AnalyticsController.java:177,203`, `GlobalControllerAdvice.java:160,171` | Аудит действий пользователей (кто создал/изменил сущность) в журнал не пишется; для личных данных журнал обращений к «Тундук» ведётся (`HireEmployeeService.java#writeLog`), это отдельный механизм BR-026 |
| NFR-021 | Эндпоинт `/health` ML доступен; мониторинг платформы (Actuator, метрики) | не задан | 🟡 | `StaffSenseML/main.py#health` (200, статус + состояние модели), `tests/test_api.py#test_health_reports_service_and_model_state` | Для Java-приложения Actuator не подключён: зависимости `spring-boot-starter-actuator` в `pom.xml` нет, метрик/health-эндпоинта платформы нет |
| NFR-022 | Резервное копирование БД PostgreSQL | не задан | ⚪ | — | Периодичность и срок хранения в исходных данных отсутствуют; скриптов/настроек бэкапа в репозитории нет — требование не проверяемо |
| NFR-023 | Параметры восстановления (RPO/RTO) | не задан | ⚪ | — | Значения в исходных данных отсутствуют — требование не проверяемо |
| NFR-024 | Схема БД: `ddl-auto=update` на разработке, миграции зафиксированы перед сдачей | не задан | ✅ | `db/migration/V1__baseline_schema.sql` … `V6__timesheet_and_memos.sql` (V2 — 16 индексов + 3 уникальных, совпадает с дополнением), `application.yml:22` (`ddl-auto: validate`), `application.yml:29-34` (Flyway включён, baseline v1) | Требование перевыполнено относительно ТЗ v1.1: вместо `update` используется Flyway + `validate` (изменение зафиксировано в дополнении, §9) |
| NFR-025 | Анонимность опросов: при `isAnonymous=true` `survey_responses.user_id` не сохраняется | не задан | ✅ | `SurveyService.java:159` — `response.setUserId(survey.isAnonymous() ? null : user.getId())`; агрегация well-being исключает анонимных — `AnalyticsController.java:122`; CSV — `CsvExportService.java:173-178` («Anonymous») | — |
| NFR-026 | Тесты на профиле H2 без PostgreSQL; тесты ML — pytest | не задан | ✅ | `demo/src/test/resources/application-test.yml` (H2, `create-drop`, Flyway off), 10 тест-классов (`KpiCalculationServiceTest`, `HireEmployeeServiceTest`, `InnValidatorTest`, `MockTundukClientTest`, `OnboardingServiceTest`, `TimesheetServiceTest`, `ServiceMemoServiceTest`, `DatasetGeneratorTest`, `ErrorPageRenderingTest`, `DemoApplicationTests`), `StaffSenseML/tests/{test_api,test_services,test_schemas}.py` | Целевое покрытие тестами в ТЗ не задано; набор тестов соответствует разделу 26.2 (тестируются только новые модули и KPI) |

---

## 4. Раздел 9 — покрытие эпиков E-01…E-08

User Stories US-001…US-039 привязаны к FR через раздел 22 (RTM). Все US опираются на требования, проверенные в разделах 2–3 выше.

| Эпик | US | Статус эпика | Пояснение |
|---|---|---|---|
| E-01 Доступ и безопасность | US-001…US-004 | ✅ | FR-001…FR-005 реализованы; критерии (хеш пароля, блокировка после 5 попыток, запрет деактивации себя, тайм-аут 30 мин) подтверждены кодом |
| E-02 Сотрудники и проекты | US-005…US-009, US-038, US-039 | 🟡 | Всё, кроме US-038 (фильтрация доски — FR-035 ❌) и US-039 (перенос карточки — FR-010: drag-and-drop есть, HTMX-фрагментного обновления нет) |
| E-03 Нагрузка и KPI | US-010, US-011 | ✅ | FR-011, FR-012, FR-027 реализованы; порог >1.3 (`WorkloadCalculationService.OVERLOAD_THRESHOLD`) присутствует |
| E-04 Прогноз риска увольнения | US-012…US-018, US-029, US-037 | 🟡 | FR-013…FR-020, FR-037, FR-038 ✅, FR-026 ✅ (вопреки отметке `[План]`); FR-018 ✅ (веса берутся из `/api/v1/model-info`), FR-019 ✅ («Радар выгорания» реализован); пробел — FR-029/FR-039 (нет администрирования параметров модели) |
| E-05 Опросы и well-being | US-019…US-024 | ✅ | FR-021, FR-023, FR-024 ✅; FR-022 ✅ — предусловия прохождения опроса проверяются на сервере (`SurveyService#requireCanTake`), повторное прохождение блокируется таблицей участия |
| E-06 Встречи 1:1 и экспорт | US-025…US-028 | ✅ | FR-025, FR-028 ✅; FR-026 ✅ (автопометка `missed` реализована, хотя в ТЗ помечена `[План]`) |
| **E-07 Кадровые процессы** `[План]` | US-030…US-034 | ✅ | **Помечен в ТЗ как `[План]`, по факту реализован.** US-030 → FR-040/FR-041 (`HireEmployeeService`, `HrHireController`, `service/tunduk/*`, `hr/hire.html`, `hr/documents.html`); US-031 → FR-045/FR-046 (`TimesheetService`, `TimesheetController`); US-032…US-034 → FR-048 (`ServiceMemoService`, `ServiceMemoController`, `model/MemoStatus`); FR-031/FR-032 покрыты теми же классами. Наблюдения: серверная валидация по BR-027/BR-028 есть, но маскирование ПДн в UI (BR-025) не выполнено — `templates/hr/documents.html:60,71` выводит ИНН и паспорт открыто |
| **E-08 AI Skill Hub** `[План]` | US-035, US-036 | ❌ | **Помечен `[План]` и фактически отсутствует в коде.** FR-033 и FR-034 не реализованы: нет `AISkillService`, сущности/таблицы `ai_skills`, парсинга `SKILL.md`/YAML-frontmatter, каталога по IT-ролям, вызова скилла из карточки задачи и хранения ответа ИИ (в `Task` нет полей комментариев/`tech_stack`). Эпик вне объёма текущей поставки — должен быть явно подтверждён как непокрытый |

---

## 5. Пробелы по приоритету

### 5.1 MUST (1)

| ID | Требование | Чего не хватает | Риск |
|---|---|---|---|
| FR-010 | Смена статуса задачи (drag-and-drop) | Drag-and-drop работает через HTML5 DnD + `fetch` + `location.reload()` (`board.html:111-139`); HTMX (`hx-patch`, `hx-swap`) не подключён, обновление не фрагментное; допустимые переходы статусов не валидируются | Средний: функционально требование выполнимо, но заявленный в ТЗ механизм (HTMX) и его приёмка по фрагментам не выполнены |
### 5.2 SHOULD (4)

| ID | Требование | Чего не хватает |
|---|---|---|
| FR-033 | Реестр AI-скиллов (E-08) | Модуль отсутствует полностью (нет сервиса, сущности, таблицы, парсинга SKILL.md, каталога по ролям). Объём оценён в `docs/reports/AI_SKILL_HUB_ASSESSMENT.md` |
| FR-034 | Вызов AI-скилла из карточки задачи (E-08) | Отсутствует; в `Task` нет полей для `tech_stack` и комментариев, куда должен выводиться ответ ИИ |
| FR-035 | Динамическая фильтрация канбан-доски | Фильтров по исполнителю/проекту/приоритету и фрагментного обновления нет |
| FR-036 | Асинхронный перерасчёт по событиям | Нет `ApplicationEventPublisher`/`@EventListener`; перерасчёт нагрузки/KPI синхронный и только при смене статуса задачи, ML-риск при событиях не пересчитывается; табель и ответы на опрос перерасчёт не инициируют |

### 5.3 COULD (1)

| ID | Требование | Чего не хватает |
|---|---|---|
| FR-039 | Администрирование параметров ML-модели | Нет экрана/эндпоинта для ADMIN, нет вызовов `POST /api/v1/retrain` из Java; веса модели доступны только на чтение (`/api/v1/model-info`). Пороги риска вынесены в конфигурацию (BR-007), но менять их через UI нельзя |

### 5.4 NFR — приоритет в ТЗ не задан (5)

| ID | Требование | Чего не хватает |
|---|---|---|
| NFR-001 | HTMX-фрагменты ≤ 150 мс (p95) | HTMX в проекте не используется вовсе; предмета измерения нет, p95 не замерялся |
| NFR-002 | ML-прогноз ≤ 300 мс | Замеров нет ни в ML-сервисе, ни в тестах; требование не подтверждено |
| NFR-019 | Поддерживаемые браузеры и разрешения | Перечень в ТЗ не задан; адаптация ≤768 px есть, но сайдбар скрывается без кнопки открытия — навигация на мобильных недоступна |
| NFR-020 | Журналирование событий | Аудит действий пользователей (кто создал/изменил) в журнал не пишется |
| NFR-021 | Мониторинг платформы | Java Actuator не подключён, метрик/health-эндпоинта платформы нет (ML `/health` работает) |

---

## 6. Не применимо / отменено (⚪)

| ID | Причина | Обоснование |
|---|---|---|
| FR-030 «Онбординг кандидата по ИНН (Тундук, mock)» | **Переформулировано дополнением № 1 (§3.1, §7 Q-002)** | FR-030 раскрыт в **FR-040** (верификация гражданина по ИНН; инициатор — HR) и **FR-041** (личное дело сотрудника). Роль `CANDIDATE` и сущность `candidates_onboarding` признаны ненужными. Реализация выполнена в новой редакции: `HireEmployeeService.java#verify/hire`, `service/tunduk/{TundukClient,MockTundukClient,InnValidator}`, `HrHireController.java`, `model/{EmployeeDocuments,TundukVerificationLog}`, миграция `V4__tunduk_and_employee_documents.sql`. Требование в исходной формулировке (портал кандидата) не подлежит реализации |
| NFR-011 «Целевая доступность платформы (SLA)» | Значение отсутствует в исходных данных | В ТЗ прямо указано «[ТРЕБУЕТ УТОЧНЕНИЯ]: в исходных данных значение отсутствует». Проверять нечего |
| NFR-017 «Соответствие уровню WCAG» | Уровень не определён | В ТЗ «[ТРЕБУЕТ УТОЧНЕНИЯ]: контрастность и клавиатурная навигация в исходных данных не специфицированы». Требование не проверяемо до уточнения |
| NFR-022 «Резервное копирование БД PostgreSQL» | Периодичность и срок хранения не заданы | В ТЗ «[ТРЕБУЕТ УТОЧНЕНИЯ]». Скриптов/настроек бэкапа в репозитории нет, критериев приёмки нет |
| NFR-023 «Параметры восстановления (RPO/RTO)» | Значения отсутствуют в исходных данных | В ТЗ «[ТРЕБУЕТ УТОЧНЕНИЯ]». Требование не проверяемо |

---

## 7. Ключевые расхождения ТЗ ↔ код (в обе стороны)

1. **ТЗ недооценивает реализацию** (отметки устарели): FR-026 (`План` → фактически ✅, `OneOnOneMissedScheduler`), NFR-003 (заявлено «таймаутов нет» → фактически ✅, `RestTemplateConfig`), NFR-006 (заявлено исключение `/api/**` → фактически CSRF включён везде), NFR-014 (`update()` назван нетранзакционным → фактически `@Transactional`), NFR-024 (заявлено `ddl-auto=update` → фактически Flyway + `validate`), FR-010 (заявлено «drag-and-drop не обнаружен» → фактически реализован), FR-021 (заявлено, что параметры охвата не сохраняются → фактически сохраняются и валидируются), FR-012 (заявлено, что `managerRating` не учитывается → фактически учитывается), FR-007 (заявлено «проект создаётся без `owner_pm`» → в БД `NOT NULL`).
2. **Код обгоняет ТЗ по безопасности**: добавлен `TeamAccessService` (проверки «своя команда», владелец задачи/встречи, IDOR закрыт), ограничен доступ PM к `/employees`, `/risk`, CSV, `/analytics/**` закрыт для EMPLOYEE, добавлена защита от CSV-инъекций.
3. **ТЗ переоценивает реализацию** (единственный случай полного отсутствия): эпик **E-08 AI Skill Hub** — FR-033 и FR-034 не имеют ни одной строки кода, при этом связаны в RTM с BG-05, US-035/US-036, UC-014/UC-016 и тест-кейсами TC-043/TC-044.
4. **Дефекты реализации, а не пробелы ТЗ**: FR-018 (веса графика ≠ обученная модель) и FR-022 (обход предусловий опроса по прямому URL) — **оба закрыты**; остаётся отсутствие маскирования ПДн в UI (BR-025, `hr/documents.html`).
5. **Приёмка производительности невозможна как заявлено**: NFR-001 опирается на HTMX, которого в проекте нет; NFR-002 не измеряется. Тест-кейсы TC-049/TC-050 из раздела 32 не реализованы.
