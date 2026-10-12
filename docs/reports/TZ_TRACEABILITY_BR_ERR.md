# Трассируемость ТЗ → код: BR / ERR / SCREEN / Q

**Дата аудита:** 2026-10-13
**Источники требований:**
1. `C:\Users\Argen\Products\_tz_extract\TZ_V1_1.md` — ТЗ v1.1 (экспорт из Word в markdown), разделы 10, 11, 12, 13, 28, 36.
2. `C:\Users\Argen\ПИ-3-24\Семестр 5\ППРППО\StaffSense_TZ_ADDENDUM_V1_1.md` — дополнение № 1 к ТЗ v1.0/1.1 (BR-023…BR-031, ERR-013…ERR-016, SCREEN-028…SCREEN-040, закрытие Q-002/Q-015/Q-018, новые Q-024/Q-025/Q-033…Q-035).

**Проверяемая реализация:** `C:\Users\Argen\Products\StaffSense` (Java 21 / Spring Boot 3.2.5 / Thymeleaf, миграции Flyway V1–V6), ML-сервис `C:\Users\Tima\PythonProjects\StaffSenseML` (FastAPI).

**Легенда статусов:** ✅ реализовано · 🟡 частично · ❌ не реализовано · ⚪ не применимо / закрыто решением заказчика.
Для `Q-*`: ⚪ — вопрос закрыт; 🟡 — вопрос открыт и влияет на текущую реализацию; ❌ — вопрос открыт, но реализации не затрагивает (адресован заказчику).

**Объём выборки ID:** BR-001…BR-031 (22 из ТЗ + 9 из дополнения); ERR-001…ERR-020 из ТЗ + ERR-013…ERR-016 из дополнения (ERR-015/ERR-016 в дополнении переопределены под другие ситуации — коллизия нумерации, см. §4); SCREEN-001…SCREEN-040 (27 из ТЗ + 13 из дополнения); Q-001…Q-023 (ТЗ) + Q-024, Q-025, Q-033…Q-035 (дополнение).

---

## 1. Сводка по статусам

| Группа | Всего | ✅ | 🟡 | ❌ | ⚪ |
|---|---|---|---|---|---|
| BR (бизнес-правила, BR-001…BR-031) | 31 | 26 | 3 | 2 | 0 |
| ERR (ошибки, ERR-001…ERR-020 + 2 строки дополнения) | 22 | 15 | 5 | 2 | 0 |
| SCREEN (экраны, SCREEN-001…SCREEN-040) | 40 | 38 | 0 | 1 | 1 |
| Q (открытые вопросы, 23 из ТЗ + 5 из дополнения) | 28 | — | 14 (открыт, влияет) | 4 (открыт, не влияет) | 10 (закрыт) |
| UC (разделы 10/13, справочно) | 16 | 14 | 0 | 2 | 0 |

**Ключевые выводы.** Полностью не реализованы: BR-002 (флаг/алерт критической перегрузки), BR-005 (реестр AI-скиллов), ERR-012 (доступность опроса), ERR-015 ТЗ (сбой AI-скилла), SCREEN-027 (AI Skill Hub). Частично: BR-001, BR-016, BR-025, ERR-006, ERR-009, ERR-011, ERR-016 ТЗ, ERR-018. Требование BR-021 (пометка пропущенных 1:1), числившееся в ТЗ как «План», **реализовано** (`OneOnOneMissedScheduler` + `OneOnOneService.markOverdueAsMissed`).

> **Обновление после итерации доработок.** BR-007 переведён в ✅: пороги вынесены в
> `RiskThresholdProperties` (`risk.thresholds`, переменные `RISK_THRESHOLD_LOW/HIGH`) и используются
> в `AttritionMlService`, `DataInitializer`, `DatasetGenerator`; Python-сервис читает те же переменные.
> ERR-008 переведён в ✅: `MlRecomputeDispatcher` ловит `TaskRejectedException` и показывает
> «Пересчёт временно недоступен, повторите позже» вместо 500. Покрытие — `RiskThresholdPropertiesTest`,
> `MlRecomputeDispatcherTest`, `ProjectControllerTest`.

---

## 2. Бизнес-правила (BR-*)

| ID | Требование (кратко) | Статус | Доказательство в коде | Что осталось |
|---|---|---|---|---|
| BR-001 | Событие (смена статуса задачи, закрытие табеля, публикация ответа) → фоновый пересчёт workload и риска, UI не блокируется | 🟡 | Вызов есть: `service/task/TaskService.java:73,108,117-131` → `WorkloadCalculationService.recalculateCurrentMonth`, `KpiCalculationService.recalculateCurrentMonth`. `@Async`/`@EventListener`/`publishEvent` по проекту — 0 совпадений, кроме `AttritionMlService:151` | Пересчёт синхронный (в потоке запроса, UI блокируется); нет Event Publisher; ответ на опрос и закрытие табеля пересчёт не запускают |
| BR-002 | workload_index > 1.3 → флаг «Критическая перегрузка» на дашборде PM + алерт | ❌ | Есть только константа `WorkloadCalculationService.java:49 OVERLOAD_THRESHOLD = 1.3` (не используется нигде) и комментарий о категориях там же:41. Поиск «Критическая перегрузка» — 0 совпадений; `controller/DashboardController.java` флага перегрузки не формирует | Не найдено в коде: ни флага на дашборде, ни отправки алерта, ни рендеринга категорий |
| BR-003 | Служебная записка: PENDING_PM → виза PM → PENDING_HR → утверждение HR → APPROVED; отклонение на любом этапе → REJECTED | ✅ | `service/ServiceMemoService.java` — `submit:46`, `approveByPm:97`, `approveByHr:113`, `reject:132`; `model/MemoStatus.java` | — |
| BR-004 | Ввод ИНН: проверка 14 цифр и контрольной суммы, вызов MockTundukService, автозаполнение ФИО и паспорта | ✅ | `service/tunduk/InnValidator.java:17-21` (ровно 14 цифр), `service/tunduk/MockTundukClient.java`, автозаполнение `controller/hr/HrHireController.java:58-91`. Контрольная сумма отменена решением Q-015, роль CANDIDATE не нужна (Q-002); инициатор — HR, а не кандидат | — |
| BR-005 | Добавление скилла: парсинг SKILL.md, проверка секций (манифест, промпт, роли) и JSON-схемы | ❌ | Поиск `Skill|SKILL.md|AISkill` по `*.java` — 0 совпадений | Не найдено в коде: AI Skill Hub отсутствует (см. SCREEN-027) |
| BR-006 | Таймаут ML > 2,0 с или 5xx/недоступность → WARN + эвристика 50 (+15 workload>1.3; +10 salary<0.9; +10 satisfaction<0.5), максимум 95 | ✅ | `service/AttritionMlService.java:118-123` (catch → WARN), `:283-315` (формула, `Math.min(score, 95.0)`); таймаут 2,0 с реализован: `config/RestTemplateConfig.java:26-33`, `resources/application.yml:39-43` | — (таймаут 2,0 с, ранее числившийся как нереализованный, реализован) |
| BR-007 | riskScore = P(y=1)×100; LOW < 33; MEDIUM 33 ≤ s < 66; HIGH ≥ 66 | ✅ | `config/RiskThresholdProperties.java` (`@ConfigurationProperties("risk.thresholds")`), `application.yml` (`risk.thresholds.low/medium`, `RISK_THRESHOLD_LOW/HIGH`), `AttritionMlService.java#fallbackPrediction`, `config/DataInitializer.java`, `config/DatasetGenerator.java`, ML `services.py:47-49` (`os.getenv`), `DemoApplication` (`@ConfigurationPropertiesScan`) | — |
| BR-008 | workload_index = Σ estimateHours активных задач / (weeklyCapacityHours × недели периода), не менее 1 недели | ✅ | `service/metrics/WorkloadCalculationService.java:68-107`, фильтр периода `isActiveInPeriod:121-127` (конфликт ТЗ снят), защита от нулевой ёмкости `:86-90` | — |
| BR-009 | kpiScore = (0.5·onTime·100 + 0.2·(100 − min(reopenRate·100,100))) / 0.7, диапазон 0–100; оценка руководителя не учитывается | ✅ | `service/metrics/KpiCalculationService.java:142-165` — веса из конфигурации `kpi.weights.*` (`application.yml:45-49`), ветка без `managerRating` даёт формулу ТЗ; ветка с `managerRating` (w3 = 0.3) — ответ на Q-020 | — (вопрос Q-020 закрыт реализацией) |
| BR-010 | Сохранение прогноза: удалить все прежние прогнозы пользователя, сохранить новый | ✅ | `AttritionMlService.java:317-325` (delete → save), комментарий с решением по Q-007 там же:318-323 | — (история прогнозов по решению не ведётся, см. Q-007) |
| BR-011 | isAnonymous = true → user_id = NULL; в CSV и аналитике «Anonymous» | ✅ | `service/SurveyService.java:159`; `service/CsvExportService.java:173-178`; `controller/AnalyticsController.java:122` (анонимные исключены из карты по сотрудникам) | — |
| BR-012 | Список опросов сотрудника: ACTIVE, today ∈ [startDate, endDate], охват (COMPANY/DEPARTMENT/TEAM/INDIVIDUAL), ответа нет | ✅ | `SurveyService.getVisibleForEmployee:70-90` (`isTargeted:79-86`, `hasAlreadyResponded:88-90`) | Конфликт ТЗ сохраняется: для анонимных опросов повторный ответ не блокируется (`user_id = NULL` не совпадает с искомым `userId`) |
| BR-013 | Жизненный цикл опроса DRAFT → ACTIVE → CLOSED; редактирование — только DRAFT | ✅ | `SurveyService.update:136-153` (проверка DRAFT + `@Transactional`), `activate:178-183`, `close:185-190`; `controller/SurveyController.java:152-157` | Переходы не валидируют исходный статус (ACTIVE → ACTIVE и т. п.); проверка `end ≥ start` добавлена: `SurveyController:104-108, 191-195` |
| BR-014 | DONE: completedAt = now; выход из DONE: completedAt = null, reopenedCount + 1 | ✅ | `controller/task/../service/task/TaskService.java:93-100` | — |
| BR-015 | POST /login: не более 5 попыток на IP, пополнение 1 в 60 с; иначе 429 + Retry-After 60 | ✅ | `config/RateLimitingFilter.java:47-66,80-81` (`classic(5, intervally(1, 60s))`), `Retry-After: 60`, redirect `/login?error=rate_limit`; регистрация `config/SecurityConfig.java:115` | — |
| BR-016 | Пароль хешируется BCrypt; минимальная длина 6 символов указана в форме, на сервере не проверяется | 🟡 | BCrypt с 12 раундами — `SecurityConfig.java:44,120-122`. Серверной проверки длины нет: `controller/AdminController.java:64-88` принимает `@RequestParam String password` без валидации; в шаблоне `templates/admin/user-form.html:35` только `placeholder="Минимум 6 символов"` (нет `minlength`) | Нет серверной политики паролей (архитектурное предложение из ТЗ не реализовано) |
| BR-017 | ADMIN деактивирует учётную запись: себя — отказ, иначе active = false | ✅ | `AdminController.toggleActive:95-113` (сообщение «Вы не можете отключить себя.») | — |
| BR-018 | Нет данных для признака: kpi_norm = 0.7; satisfaction_norm = 0.65; missed_1on1_ratio = 0; overtime_norm = 0; salary_ratio = 1.0 без коллег по грейду | ✅ | `AttritionMlService.java:198, 222, 234, 244, 249, 261` | — |
| BR-019 | Формирование 7 признаков (формулы) | ✅ | `AttritionMlService.java:181-262`; ML `services.py`, `schemas.py` | Окно удовлетворённости — 90 дней (`:232`), окно встреч — все встречи (`:248`); расхождение со спецификацией (3 последних опроса / 6 месяцев) сохраняется, но отражено в ТЗ как конфликт |
| BR-020 | EMPLOYEE: прогнозы и /risk, /employees скрыты и недоступны (403) | ✅ | `SecurityConfig.java:78,81`, `controller/RiskController.java:32` (`@PreAuthorize`), `templates/fragments/layout.html:103-133` (управленческий блок только для HR/PM/ADMIN/EXEC) | — |
| BR-021 | 1:1: PLANNED → COMPLETED \| CANCELLED; PLANNED с scheduledAt < now − 1 день → missed = true | ✅ | `service/OneOnOneService.java:112-159` (`markOverdueAsMissed:141-159`), планировщик `service/OneOnOneMissedScheduler.java:23-32` (`@Scheduled`, `staffsense.oneonone.missed-cron`, `application.yml:62-65`) | Автопометка, ранее числившаяся как «План», реализована |
| BR-022 | salary_ratio — среднее по всем активным сотрудникам того же грейда (динамически, без SalaryBenchmark) | ✅ | `AttritionMlService.calculateSalaryRatio:196-203` (`findByGradeLevelAndActive`) | Учёт отдела не реализован (в ТЗ помечено как расхождение со спецификацией) |
| BR-023 | Повторный приём: ИНН уже есть → новый сотрудник не создаётся, запись реактивируется, данные обновляются | ✅ | `service/hr/HireEmployeeService.java:96-131` (`alreadyExisted`, `reactivated`, `applyCorporateFields`), `model/EmployeeDocuments.java`, миграция `db/migration/V4__tunduk_and_employee_documents.sql` | — |
| BR-024 | Без согласия на обработку ПДн верификация и приём не выполняются; момент согласия фиксируется | ✅ | `HireEmployeeService.java:91-94` (отказ), `:178-181` (`setConsentGivenAt`), `EmployeeDocuments.java:87-88` | — |
| BR-025 | Паспорт и ИНН маскируются (`****1234`) для ролей без прав и в журналах | 🟡 | Маскирование есть: `EmployeeDocuments.getMaskedInn:117-122`, `getMaskedPassport:104-113`; маскирование ИНН в логах — `HireEmployeeService.mask:212-217`. Но методы `getMaskedInn/getMaskedPassport` **не используются нигде** (поиск по `src/main` — только определения) | Роль-зависимый вывод маскированных ПДн не применяется: нет шаблона/ветки, показывающей маску вместо полного значения для роли без прав |
| BR-026 | Аудит обращений к «Тундук»: кто, когда, по какому ИНН, результат | ✅ | `HireEmployeeService.writeLog:184-200` (FOUND/NOT_FOUND/INVALID/UNAVAILABLE), `model/TundukVerificationLog.java`, репозиторий `TundukVerificationLogRepository` | — |
| BR-027 | Табель: PM — своя команда, HR/ADMIN — любая, EMPLOYEE — только чтение | ✅ | `service/TimesheetService.java:52-109` (`canEdit/requireCanEdit/getEditableEmployees`), `controller/TimesheetController.java:56,62` (`canEdit`, `readOnlyRole`) | — |
| BR-028 | Табель за закрытый период не редактируется | ✅ | `TimesheetService.isDateLocked:180-188`, `saveEntry:139-143`, `deleteEntry:170-172`; `model/WorkSchedulePeriod`, `WorkSchedulePeriodRepository` | — |
| BR-029 | Записку подаёт любой сотрудник; маршрут PENDING_PM → PENDING_HR → APPROVED / REJECTED; автор может редактировать и отозвать до финального решения | ✅ | `ServiceMemoService.submit:46`, `update:67`, `cancel:83` (`requireEditable`), `controller/ServiceMemoController.java` | — |
| BR-030 | Утверждённый больничный/отпуск автоматически проставляется в табеле; дни отсутствия уменьшают доступную ёмкость при расчёте нагрузки | ✅ | `TimesheetService.applyApprovedMemo:286-317` (вызов из `ServiceMemoService.approveByHr:126`), `countAbsenceDays:323-327`, использование в `WorkloadCalculationService.java:81-88` (`ABSENCE_HOURS_PER_DAY = 8.0`) | — |
| BR-031 | Справочник сотрудников: только ФИО, должность, отдел, email, руководитель; без оклада, KPI, нагрузки, риска и ПДн | ✅ | `controller/DirectoryController.java:32-72`, `dto/directory/DirectoryEntryDto.java` (6 полей), `SecurityConfig.java:73` (`/directory/**` — authenticated) | — |

---

## 3. Ошибки и исключения (ERR-*)

| ID | Требование (кратко) | Статус | Доказательство в коде | Что осталось |
|---|---|---|---|---|
| ERR-001 | Неверный email/пароль или неактивный пользователь → `/login?error` | ✅ | `SecurityConfig.java:93 failureUrl("/login?error")`; `templates/auth/login.html:73-77` | Текст сообщения отличается от ТЗ (см. §4) |
| ERR-002 | Превышен лимит попыток входа → 429 + Retry-After 60, redirect `/login?error=rate_limit` | ✅ | `config/RateLimitingFilter.java:53-66`; `templates/auth/login.html:75` | — |
| ERR-003 | Сессия истекла → redirect `/login?expired` | ✅ | `SecurityConfig.java:103-105 invalidSessionUrl`; `templates/auth/login.html:81-83` | Текст сообщения не совпадает дословно (см. §4) |
| ERR-004 | Недостаточно прав → 403, страница ошибки | ✅ | `config/GlobalControllerAdvice.java:100-108`; `templates/error.html:19,26` | — |
| ERR-005 | Ресурс не найден → ResourceNotFoundException → 404 | ✅ | `exception/ResourceNotFoundException.java`, `GlobalControllerAdvice.java:90-97`; `IllegalArgumentException` теперь 400 (`:136-143`), а не страница ошибки | Единый `@ControllerAdvice`, числившийся в ТЗ как предложение архитектора, реализован |
| ERR-006 | Ошибка валидации формы (пустое название, оценка ≤ 0) → сообщение у поля / «Проверьте введённые данные» | 🟡 | REST-ветка работает: `dto/task/TaskCreateRequest.java:21,36` (`@NotBlank`, `@Positive`), `controller/task/TaskController.java:33` → 400 с текстом поля (`GlobalControllerAdvice.java:111-123`). Формы без `@Valid`: `controller/ProjectController.java:51` (`@ModelAttribute Project` без валидации), `AdminController.java:64-88` (все `@RequestParam`) | Форма проекта сохраняется с пустым названием; текст «Проверьте введённые данные» в коде отсутствует |
| ERR-007 | Сбой расчёта/сохранения прогноза; ML недоступен → «Ошибка при пересчёте: <текст>» / fallback + WARN | ✅ | `controller/RiskController.java:149-158`; `AttritionMlService.java:120-123` | — |
| ERR-008 | Очередь фонового пересчёта переполнена (50) → «Пересчёт временно недоступен, повторите позже» | ✅ | `config/AsyncConfig.java:19 queueCapacity(50)`, `service/MlRecomputeDispatcher.java` (`TaskRejectedException` → `false` + WARN), `AnalyticsController.java#mlModelAnalytics/recomputeAllML`, `templates/analytics/ml-model.html` (`mlRecomputeUnavailable`), `MlRecomputeDispatcherTest` | — |
| ERR-009 | Email уже существует → «Пользователь с таким email уже существует» | 🟡 | Предварительной проверки в `AdminController.createUser:63-93` нет; нарушение UNIQUE ловится `GlobalControllerAdvice.java:158-166` → 409 «Данные конфликтуют с текущим состоянием базы (дубликат или отсутствующая связь).» Проверка email есть в другом потоке: `HireEmployeeService.java:112-114` («Email уже используется: …») | Текст ТЗ не найден; поведение «проверка до сохранения» (предложение архитектора) не реализовано |
| ERR-010 | Недопустимое значение enum → 400 | ✅ | `GlobalControllerAdvice.java:136-143` (`IllegalArgumentException` → 400); вызовы `Role.valueOf` (`AdminController.java:41,81`), `TargetScope.valueOf` (`SurveyController.java:100`), `QuestionType.valueOf` (`SurveyService.java:120`) | Текст «Недопустимое значение параметра» не задан — отдаётся текст исключения JDK |
| ERR-011 | Некорректный формат даты / попытка самодеактивации | 🟡 | Самодеактивация: `AdminController.java:102-105` — текст совпадает дословно. Даты: `GlobalControllerAdvice.java:148-155` (`DateTimeParseException` → 400 «Некорректный формат даты или времени.») | Текст для дат не совпадает дословно (см. §4) |
| ERR-012 | Опрос недоступен (не ACTIVE, вне периода, не в охвате, пройден) | ❌ | `controller/SurveyController.java:282-313` (`takeSurvey`/`submitSurvey`) — проверки статуса, периода, охвата и факта ответа отсутствуют; `SurveyService.submitResponse:156` принимает ответ без валидации | Не найдено в коде (ТЗ сам помечает это как `[КОНФЛИКТ]`); целевое поведение 403/404 не реализовано |
| ERR-013 (ТЗ) | Некорректный ИНН → «Введите корректный 14-значный ИНН»; запрос не выполняется | ✅ | `InnValidator.java:19-26`, ранний выход `HireEmployeeService.verify:62-66` с записью `INVALID` в журнал | Текст дополнения («…(только цифры)») реализован; текст ТЗ отличается (см. §4). ТЗ-статус «План» устарел |
| ERR-014 (ТЗ) | Данные не найдены / сервис недоступен → ручной ввод, `tunduk_verified = false`, журнал NOT_FOUND/UNAVAILABLE | ✅ | `MockTundukClient.java:43-52` (`UNAVAILABLE_INN = 99999999999999`), `HireEmployeeController.java:81-84` (`NOT_FOUND, UNAVAILABLE` → warning, `tundukVerified(false)`), `HireEmployeeService.writeLog:74` | Текст сообщения не совпадает дословно (см. §4) |
| ERR-015 (ТЗ) | Невалидный SKILL.md или сбой ИИ-оркестратора → «Скилл не может быть выполнен: <причина>» | ❌ | Поиск `Skill|SKILL.md|AISkill` по `*.java` — 0 совпадений; строка сообщения не найдена | Не найдено в коде (AI Skill Hub отсутствует). **Коллизия:** дополнение переиспользует ERR-015 под закрытый период табеля |
| ERR-016 (ТЗ) | Недопустимый переход статуса записки → «Действие недоступно в текущем статусе» | 🟡 | Поведение реализовано: `ServiceMemoService.java:99-101, 115-117, 143-145` (`IllegalStateException` → flash-ошибка в `ServiceMemoController`), статус не меняется | Текст ТЗ не найден; используются свои формулировки (см. §4). **Коллизия:** дополнение переиспользует ERR-016 под ведение табеля вне команды |
| ERR-015 (доп.) | Правка табеля за закрытый период → «Период закрыт — правка табеля запрещена» | ✅ | `TimesheetService.java:139-143, 170-172` | Текст с префиксом «Период за M.YYYY закрыт — …» (см. §4) |
| ERR-016 (доп.) | Ведение табеля вне своей команды → «Табель ведёт руководитель по своей команде» | ✅ | `TimesheetService.java:63-68` (`requireCanEdit`) | Текст с суффиксом «(сотрудник ID=…)» (см. §4) |
| ERR-017 | Файл датасета не найден → 404 | ✅ | `controller/DatasetController.java:31-34` (`ResponseEntity.notFound()`) | Тело ответа пустое — текст «Файл не найден» не найден |
| ERR-018 | Сбой формирования CSV → 500 | 🟡 | `service/CsvExportService.java:108-110, 153-155, 200-202` (`RuntimeException("Failed to export employees CSV", e)` и др.) → 500 через `GlobalControllerAdvice:169-177` | Текст «Не удалось сформировать файл» не найден — сообщения на английском (см. §4) |
| ERR-019 | Некорректное тело запроса к ML → 422; платформа применяет Fallback | ✅ | ML `main.py:100` + Pydantic-схемы `schemas.py` (422 FastAPI); платформа: `AttritionMlService.java:120-123` | — |
| ERR-020 | Внутренняя ошибка ML → «Prediction error: <текст>», 500; Fallback | ✅ | ML `main.py:121-123` (`HTTPException(500, detail=f"Prediction error: {e}")`); платформа `AttritionMlService.java:120-123` | — |

---

## 4. Расхождения в текстах сообщений ERR

Сверка дословная: слева — текст ТЗ, справа — фактический текст в коде.

| ID | Текст в ТЗ | Текст в коде (дословно) | Оценка |
|---|---|---|---|
| ERR-001 | «Неверные учётные данные» | «Неверный Email или пароль.» (`templates/auth/login.html:76`) | Формулировка иная; смысл сохранён |
| ERR-002 | «Слишком много попыток. Пожалуйста, попробуйте позже.» | «Слишком много попыток. Пожалуйста, попробуйте позже.» (`RateLimitingFilter.java:65`, `login.html:75`) | ✅ совпадает дословно |
| ERR-003 | «Сессия истекла, войдите снова» | «Ваша сессия истекла. Пожалуйста, войдите снова.» (`templates/auth/login.html:82`) | Расширенная формулировка |
| ERR-004 | «Доступ запрещён» | «Доступ запрещён» / «У вас нет прав для просмотра этой страницы.» (`GlobalControllerAdvice.java:106-107`, `templates/error.html:19,26`) | ✅ заголовок совпадает |
| ERR-005 | «Запрошенный объект не найден» | «Ресурс не найден» (JSON, `GlobalControllerAdvice.java:93`) / «Страница не найдена» + «Запрашиваемый ресурс не существует или был удалён.» (`error.html:18,25`) | Формулировка иная |
| ERR-006 | «Проверьте введённые данные» / сообщение у поля | «Название задачи обязательно» / «Оценка трудозатрат должна быть положительной» и др. (`dto/task/TaskCreateRequest.java:21,36`, вывод `GlobalControllerAdvice.java:113-115`) | Текст ТЗ отсутствует; есть тексты полей Bean Validation |
| ERR-007 | «Ошибка при пересчёте: <текст>» | «Ошибка при пересчёте: » + `e.getMessage()` (`RiskController.java:156`) | ✅ шаблон совпадает |
| ERR-008 | «Пересчёт временно недоступен, повторите позже» | `MlRecomputeDispatcher.UNAVAILABLE_MESSAGE` = «Пересчёт временно недоступен, повторите позже.» (`service/MlRecomputeDispatcher.java`, вывод `templates/analytics/ml-model.html`) | ✅ совпадает дословно |
| ERR-009 | «Пользователь с таким email уже существует» | «Данные конфликтуют с текущим состоянием базы (дубликат или отсутствующая связь).» (`GlobalControllerAdvice.java:161`); в другом потоке — «Email уже используется: …» (`HireEmployeeService.java:113`) | Расхождение: пользователь не узнаёт, что причина — дубликат email |
| ERR-010 | «Недопустимое значение параметра» | текст исключения JDK, напр. `No enum constant com.staffsense.demo.model.Role.X` (`Role.valueOf`) | Расхождение: технический текст вместо пользовательского |
| ERR-011 | «Некорректная дата» / «Вы не можете отключить себя.» | «Некорректный формат даты или времени.» (`GlobalControllerAdvice.java:150`) / «Вы не можете отключить себя.» (`AdminController.java:103`) | Второе ✅ дословно; первое — иная формулировка |
| ERR-013 | «Введите корректный 14-значный ИНН» | «Введите корректный 14-значный ИНН (только цифры)» (`InnValidator.java:25`) | Текст дополнения совпадает дословно; текст ТЗ — без «(только цифры)» |
| ERR-014 | «Данные не найдены, заполните анкету вручную» | «Гражданин с ИНН <ИНН> не найден в госсистеме. Заполните данные вручную.» / «Сервис «Тундук» недоступен: эмуляция сбоя по тестовому ИНН. Заполните данные вручную.» (`TundukLookupResult.java:16-24`, `HrHireController.java:83`) | Расхождение: текст формируется динамически и содержит ИНН |
| ERR-015 (ТЗ) | «Скилл не может быть выполнен: <причина>» | не найдено | ❌ расхождение: сообщения нет |
| ERR-016 (ТЗ) | «Действие недоступно в текущем статусе» | «Записка не находится на визе руководителя» (`ServiceMemoService.java:100`), «Записка не находится на утверждении HR» (`:116`), «Записка уже не может быть отклонена» (`:144`), «Записка уже получила решение — изменение невозможно» (`:214`) | Расхождение: четыре собственные формулировки вместо единой из ТЗ |
| ERR-015 (доп.) | «Период закрыт — правка табеля запрещена» | «Период за 10.2026 закрыт — правка табеля запрещена» (`TimesheetService.java:140-142`), «Период закрыт — правка табеля запрещена» (`:171`) | ✅ подстрока совпадает; в `saveEntry` добавлен префикс с месяцем |
| ERR-016 (доп.) | «Табель ведёт руководитель по своей команде» | «Табель ведёт руководитель по своей команде (сотрудник ID=42)» (`TimesheetService.java:65-66`) | ✅ подстрока совпадает; добавлен суффикс с ID |
| ERR-017 | «Файл не найден» | не найдено (пустое тело 404, `DatasetController.java:33`) | Расхождение: пользовательского текста нет |
| ERR-018 | «Не удалось сформировать файл» | «Failed to export employees CSV» / «Failed to export risks CSV» / «Failed to export survey results CSV» (`CsvExportService.java:109,154,201`) | Расхождение: сообщения на английском, текст ТЗ отсутствует |
| ERR-019 | «Ошибка валидации (422)» | стандартный ответ FastAPI/Pydantic `{"detail":[{"loc":…,"msg":…}]}` | Расхождение: кастомного текста нет |
| ERR-020 | «Prediction error: <текст>» | `Prediction error: {e}` (`main.py:123`) | ✅ совпадает дословно |

**Дополнительно.** Пометка ТЗ «[КОНФЛИКТ] Текст сообщений пользователю в коде для ERR-003, 004, 005, 006, 009, 010, 012 не стандартизирован (глобальный обработчик GlobalControllerAdvice в выгрузке недоступен)» **устарела**: `config/GlobalControllerAdvice.java` в репозитории присутствует и обрабатывает 400/403/404/409/500, но единого каталога сообщений (предложение архитектора) нет — тексты по-прежнему разбросаны между advice, контроллерами и шаблонами.

---

## 5. Экраны (SCREEN-*, разделы 12 и 23)

| ID | Требование (кратко) | Статус | Доказательство в коде | Что осталось |
|---|---|---|---|---|
| SCREEN-001 | Вход, `/login` | ✅ | `controller/AuthController.java` (`@GetMapping("/login")`), `templates/auth/login.html` | — |
| SCREEN-002 | Управленческий дашборд, `/dashboard` | ✅ | `controller/DashboardController.java:40-83`, `templates/dashboard/main.html` | — |
| SCREEN-003 | Личный кабинет, `/dashboard/personal` | ✅ | `DashboardController.java:50-54,85-97`, `templates/dashboard/personal.html` | — |
| SCREEN-004 | Пользователи (админ), `/admin` | ✅ | `AdminController.java:31-52`, `templates/admin/users.html` | — |
| SCREEN-005 | Создание пользователя, `/admin/users/new` | ✅ | `AdminController.java:54-93`, `templates/admin/user-form.html` | — |
| SCREEN-006 | Сотрудники, `/employees` | ✅ | `controller/EmployeeController.java:61-90`, `templates/employees/list.html` | — |
| SCREEN-007 | Профиль сотрудника, `/employees/{id}` | ✅ | `EmployeeController.java:98-119`, `templates/employees/profile.html` | — |
| SCREEN-008 | Проекты, `/projects` | ✅ | `controller/ProjectController.java:28-35`, `templates/projects/list.html` | — |
| SCREEN-009 | Создание проекта, `/projects/new` | ✅ | `ProjectController.java:37-62`, `templates/projects/new.html` | — |
| SCREEN-010 | Канбан-доска, `/projects/{id}/board` | ✅ | `controller/task/ProjectBoardController.java`, `templates/projects/board.html` | — |
| SCREEN-011 | Создание задачи, `/projects/{id}/tasks/new` | ✅ | `ProjectBoardController` (`@GetMapping/PostMapping("/projects/{id}/tasks/new")`), `templates/projects/task-form.html` | — |
| SCREEN-012 | Риск увольнения (список), `/risk` | ✅ | `RiskController.java:56-95`, `templates/risk/list.html` | — |
| SCREEN-013 | Риск увольнения (детали), `/risk/{userId}` | ✅ | `RiskController.java:97-140`, `templates/risk/details.html` (7 карточек + `prediction.aiActionPlan`:54 + `riskFactors`:98-105) | — |
| SCREEN-014 | Аналитика нагрузки, `/analytics/workload` | ✅ | `controller/AnalyticsController.java:55-74`, `templates/analytics/workload.html` | Категории BR-002 (<0.6 / 0.6–1.0 / 1.0–1.3 / >1.3) в шаблоне не отрисованы |
| SCREEN-015 | Аналитика KPI, `/analytics/kpi` | ✅ | `AnalyticsController.java:76-93`, `templates/analytics/kpi.html` | — |
| SCREEN-016 | Well-being, `/analytics/wellbeing` | ✅ | `AnalyticsController.java:95-167`, `templates/analytics/wellbeing.html` | — |
| SCREEN-017 | ML-модель, `/analytics/ml-model` | ✅ | `AnalyticsController.java:169-222` (+ `recompute`), `templates/analytics/ml-model.html` | — |
| SCREEN-018 | Управление опросами, `/surveys/manage` | ✅ | `SurveyController.java:39-55`, `templates/surveys/manage.html` | — |
| SCREEN-019 | Создание/правка опроса, `/surveys/manage/new`, `/{id}/edit` | ✅ | `SurveyController.java:57-220`, `templates/surveys/new.html`, `templates/surveys/edit.html` | — |
| SCREEN-020 | Результаты опроса, `/surveys/manage/{id}/results` | ✅ | `SurveyController.java:251-266`, `templates/surveys/results.html` | — |
| SCREEN-021 | Прохождение опроса, `/surveys/take/{id}` | ✅ | `SurveyController.java:282-313`, `templates/surveys/take.html` | Нет проверки доступности опроса (ERR-012) |
| SCREEN-022 | Встречи 1:1, `/oneonone` | ✅ | `controller/OneOnOneController.java:46-67`, `templates/oneonone/list.html` | — |
| SCREEN-023 | Назначение встречи, `/oneonone/new` | ✅ | `OneOnOneController.java:70-96`, `templates/oneonone/new.html` | — |
| SCREEN-024 | Страница ошибки, `error` | ✅ | `templates/error.html`, `GlobalControllerAdvice.errorView:200-214`, тест `src/test/.../ErrorPageRenderingTest.java` | — |
| SCREEN-025 | [План] Портал кандидата | ⚪ | Роль `CANDIDATE` и сущность `candidates_onboarding` не требуются (дополнение §3.1, Q-002); функциональность реализована как SCREEN-028/SCREEN-029 | Закрыт решением заказчика |
| SCREEN-026 | [План] Табель и служебные записки | ✅ | Реализовано в переформулированном виде: `/timesheet` (`TimesheetController`, `templates/timesheet/index.html`) и `/memos` (`ServiceMemoController`, `templates/memos/*`) | Частично замещён SCREEN-036…SCREEN-040 |
| SCREEN-027 | [План] AI Skill Hub — каталог и вызов скиллов | ❌ | Поиск `Skill|SKILL.md|AISkill` — 0 совпадений; шаблонов каталога скиллов нет | Не найдено в коде |
| SCREEN-028 | Приём сотрудника (верификация по ИНН), `/hr/hire` | ✅ | `controller/hr/HrHireController.java:47-132`, `templates/hr/hire.html`; `SecurityConfig.java:67` (`/hr/**` — ADMIN, HR) | — |
| SCREEN-029 | Личное дело сотрудника, `/hr/documents/{userId}` | ✅ | `HrHireController.java:136-145`, `templates/hr/documents.html`, миграция `V4__tunduk_and_employee_documents.sql` | — |
| SCREEN-030 | Портал онбординга (главная), `/onboarding` | ✅ | `controller/OnboardingController.java:33-41`, `templates/onboarding/index.html` | — |
| SCREEN-031 | Раздел портала, `/onboarding/section/{section}` | ✅ | `OnboardingController.java:44-52`, `templates/onboarding/section.html` | — |
| SCREEN-032 | Статья, `/onboarding/article/{id}` | ✅ | `OnboardingController.java:55-62`, `templates/onboarding/article.html` | — |
| SCREEN-033 | Чек-лист новичка, `/onboarding/checklist` | ✅ | `OnboardingController.java:65-85`, `service/OnboardingService.java:81-132`, `templates/onboarding/checklist.html`, миграция `V5__onboarding_portal.sql` | — |
| SCREEN-034 | Управление порталом, `/onboarding/manage` | ✅ | `controller/OnboardingManageController.java`, `templates/onboarding/manage.html`, `templates/onboarding/form.html` | — |
| SCREEN-035 | Справочник сотрудников, `/directory` | ✅ | `DirectoryController.java`, `templates/directory/index.html` | — |
| SCREEN-036 | Табель сотрудника, `/timesheet` | ✅ | `controller/TimesheetController.java:37-106`, `templates/timesheet/index.html` | — |
| SCREEN-037 | Отчёт по табелю, `/timesheet/report` | ✅ | `TimesheetController.java:136-163`, `templates/timesheet/report.html` | — |
| SCREEN-038 | Список служебных записок, `/memos` | ✅ | `ServiceMemoController.java:32-40`, `templates/memos/list.html` | — |
| SCREEN-039 | Подача записки, `/memos/new` | ✅ | `ServiceMemoController.java:42-67`, `templates/memos/form.html` | — |
| SCREEN-040 | Карточка записки и согласование, `/memos/{id}` | ✅ | `ServiceMemoController.java:69-174`, `templates/memos/view.html`, `templates/memos/edit.html` | — |

---

## 6. Раздел 10 «USE CASES» и раздел 13 «USER FLOWS» (справочно)

| ID | Требование (кратко) | Статус | Доказательство в коде |
|---|---|---|---|
| UC-001 | Вход в систему | ✅ | `SecurityConfig.filterChain`, `RateLimitingFilter`, `AuthController` |
| UC-002 | Управление пользователями | ✅ | `AdminController.java` |
| UC-003 | Ведение проектов и задач | ✅ | `ProjectController`, `ProjectBoardController`, `TaskController` |
| UC-004 | Смена статуса задачи | ✅ | `TaskService.updateStatus:82-110` |
| UC-005 | Прохождение опроса | ✅ | `SurveyController.takeSurvey/submitSurvey`, `SurveyService.submitResponse` |
| UC-006 | Управление опросами | ✅ | `SurveyController` (manage/new/edit/results/activate/close/export) |
| UC-007 | Планирование и ведение встреч 1:1 | ✅ | `OneOnOneController`, `OneOnOneService`, `OneOnOneMissedScheduler` |
| UC-008 | Расчёт риска увольнения | ✅ | `AttritionMlService.computeAndSave`, `RiskController.recomputeRisk` |
| UC-009 | Просмотр рисков и плана удержания | ✅ | `RiskController.listRisks/viewRiskDetail`, `templates/risk/details.html` |
| UC-010 | Просмотр аналитики | ✅ | `AnalyticsController` |
| UC-011 | Экспорт CSV | ✅ | `CsvExportService` (BOM `:51-53`, экранирование `:63-75`) |
| UC-012 | Онбординг кандидата по ИНН [План] | ✅ | Реализован в HR-контуре: `HrHireController`, `HireEmployeeService` (см. BR-023, BR-024, BR-026, SCREEN-028) |
| UC-013 | Служебная записка [План] | ✅ | `ServiceMemoController`, `ServiceMemoService` (см. BR-003, BR-029, BR-030) |
| UC-014 | Вызов AI-скилла [План] | ❌ | Не найдено в коде (нет `AISkillService`, нет каталога скиллов) |
| UC-015 | Табель учёта времени [План] | ✅ | `TimesheetController`, `TimesheetService` |
| UC-016 | Управление реестром AI-скиллов [План] | ❌ | Не найдено в коде |
| FLOW-1 | Рисунок 1. Вход и маршрутизация по ролям | ✅ | `SecurityConfig.defaultSuccessUrl("/dashboard")` + `DashboardController:44-47` (EMPLOYEE → личный кабинет) |
| FLOW-2 | Рисунок 2. Работа HR с риском увольнения | ✅ | `RiskController.listRisks/viewRiskDetail`, `aiActionPlan` в `templates/risk/details.html:54` |

---

## 7. Открытые вопросы (Q-*)

**Закрыты дополнением (§7):** Q-002, Q-015, Q-018. **Закрыты фактически реализацией** (формально в дополнении не закрыты): Q-005, Q-006, Q-007, Q-008, Q-009, Q-010, Q-020.

| ID | Вопрос (кратко) | Статус | Чем закрыт / влияние на реализацию |
|---|---|---|---|
| Q-001 | Эталонные имена ролей: ADMIN/EXEC/HR/PM/EMPLOYEE или PROJECT_MANAGER/HR_SPECIALIST | 🟡 | Влияет: `model/Role.java`, `SecurityConfig` (`hasRole(...)`), CHECK-констрейнт `users_role_check` (`V1__baseline_schema.sql`). Переименование = миграция данных |
| Q-002 | Нужна ли роль CANDIDATE / отдельная сущность | ⚪ | Закрыт дополнением §7: роль не нужна; в коде `Role` без CANDIDATE, портал кандидата не создан (SCREEN-025 ⚪) |
| Q-003 | Контракт `POST /api/v1/predict`: нормализованные или сырые признаки | 🟡 | Влияет: реализованы нормализованные 7 признаков (`dto/PythonPredictRequest.java`, ML `schemas.py`). Контракт не согласован формально — риск переделки при смене решения |
| Q-004 | Модель scikit-learn или свой градиентный спуск; какие веса итоговые | ✅ | ML использует `model.pkl`+`scaler.pkl` (`services.py`), fallback-веса остаются только как резерв. Источник весов для UI теперь один: `GET /api/v1/model-info` отдаёт `coef_`/`intercept_` действующей модели (`services.py#coefficients`), а `ModelInsightService` показывает их либо помечает график как экспертную оценку |
| Q-005 | Эталонная схема БД (11 или 15 таблиц), нужны ли Flyway-миграции | ⚪ | Фактически закрыт: принят Flyway, `db/migration/V1…V6`, `spring.jpa.hibernate.ddl-auto=validate` (`application.yml:22,29-34`) |
| Q-006 | Нужна ли фильтрация PM по «своей команде»; как определяется команда | ⚪ | Фактически закрыт: команда = `users.manager_id` (`service/TeamAccessService.java:46-53`), применено в `EmployeeController`, `RiskController`, `TimesheetService`, `ServiceMemoService` |
| Q-007 | Нужна ли история прогнозов вместо одного актуального | ⚪ | Фактически закрыт в пользу одного прогноза: `AttritionMlService.java:317-325` + комментарий с решением заказчика (`:318-323`); выборка последнего — `findFirstByUserIdOrderByComputedAtDesc` |
| Q-008 | Разрешено ли редактировать опрос после активации; проверять end ≥ start | ⚪ | Фактически закрыт: редактирование только DRAFT (`SurveyService.update:141-145`), `end ≥ start` проверяется (`SurveyController:104-108, 191-195`) |
| Q-009 | Тайм-аут вызова ML 2,0 с и политика повторов | ⚪ | Фактически закрыт: connect + read = 2000 мс (`RestTemplateConfig.java:26-33`, `application.yml:42-43`); политика повторов не реализована (Fallback вместо retry) |
| Q-010 | Раунды BCrypt: 12 или 10 | ⚪ | Фактически закрыт: `BCRYPT_STRENGTH = 12` (`SecurityConfig.java:44,120-122`) |
| Q-011 | Какие роли имеют доступ к `/analytics/**` и созданию 1:1 и задач | 🟡 | Влияет на матрицу доступа: реализовано `/analytics/**` — ADMIN/EXEC/HR/PM (`SecurityConfig.java:79`), 1:1 и задачи — ADMIN/HR/PM (`OneOnOneController:71,86`, `TaskController:32`). Решение не подтверждено заказчиком |
| Q-012 | Целевая нагрузка и объём данных | ❌ | Реализации не затрагивает: вопрос к заказчику (NFR-011/012, тестирование производительности не проводилось) |
| Q-013 | Доступность, RPO/RTO, резервное копирование | ❌ | Адресован заказчику/DevOps; в коде отражения нет |
| Q-014 | Среда production и способ развёртывания | ❌ | Адресован заказчику; в репозитории только локальный запуск и CI |
| Q-015 | Реальная интеграция с «Тундук», алгоритм контрольной суммы ИНН, тестовые данные | ⚪ | Закрыт дополнением §7: проверяется только формат 14 цифр (`InnValidator.java`), тестовые ИНН вымышленные и детерминированы (`MockTundukClient.buildDirectory:63-120`), служебный ИНН недоступности `99999999999999` |
| Q-016 | Источник tech_stack для AI-промпта и выбор ИИ-провайдера | 🟡 | Влияет на плановые FR-033/FR-034: реализации нет, вопрос блокирует модуль AI Skill Hub (связан со SCREEN-027, BR-005) |
| Q-017 | Каналы уведомлений (email, внутренние, мессенджер) | 🟡 | Влияет на раздел 27 и BR-002: подсистема уведомлений/алертов в коде отсутствует, поэтому BR-002 не может быть закрыт без решения по каналу (достаточно внутреннего флага) |
| Q-018 | Правовые требования к ПДн, сроки хранения, согласие | ⚪ | Закрыт дополнением §7: согласие фиксируется (BR-024), доступ ограничен ADMIN/HR (`SecurityConfig.java:67`), ведётся журнал (BR-026). Сроки хранения — за внутренним регламентом |
| Q-019 | Источник реальных данных для обучения и критерии приёмки качества модели | 🟡 | Влияет на ML: обучение на синтетическом `dataset_training.csv`, метрика считается in-sample (`main.py:144-145`); критерии приёмки не заданы |
| Q-020 | Должна ли участвовать оценка руководителя (w3 = 0.3) в KPI и кто её вводит | ⚪ | Фактически закрыт: вес читается из `kpi.weights.manager-rating` (`application.yml:49`), учитывается при заданном `managerRating` (`KpiCalculationService:147-156`). Интерфейс ввода оценки отсутствует |
| Q-021 | Спринты, эпики, backlog, роль Team Lead, перенос встреч (RESCHEDULED) | 🟡 | Влияет на модель данных: `MeetingStatus` содержит `RESCHEDULED`, и CHECK-констрейнт БД его допускает (`V1__baseline_schema.sql`), но переход не реализован (`OneOnOneService`); Team Lead в `Role` отсутствует |
| Q-022 | Требования WCAG, поддерживаемые браузеры, мобильная версия | 🟡 | Влияет на шаблоны Thymeleaf и CSS: адаптивность/доступность не проверялись, целевые требования не заданы |
| Q-023 | Стандарт оформления ТЗ для нормоконтроля | ❌ | К коду не относится (вопрос к руководителю) |
| Q-024 | Сроки хранения личных дел и порядок уничтожения ПДн после увольнения | 🟡 | Влияет на `EmployeeDocuments`: логики удаления/архивации/ретенции нет; деактивация пользователя личное дело не затрагивает |
| Q-025 | Ограничивать ли срок доступа HR к личным делам уволенных | 🟡 | Влияет на `/hr/documents/{userId}`: проверки `user.active` нет (`HrHireController:136-145`) — дело уволенного доступно бессрочно |
| Q-033 | Нужна ли интеграция с реальной госсистемой «Тундук / Кызмат» и протокол обмена | 🟡 | Влияет на порт `service/tunduk/TundukClient.java`: сейчас единственная реализация — `MockTundukClient`; при реальной интеграции меняется реализация порта, контракт сохранён |
| Q-034 | Нужно ли файловое хранилище фотографий (сейчас заглушка) | 🟡 | Влияет на `MockTundukClient.PHOTO_PLACEHOLDER = "/img/tunduk-photo.svg"` и `EmployeeDocuments.photoUrl`: загрузки/хранения файлов нет |
| Q-035 | Учитывать ли утверждённые отпуска при планировании загрузки проектов | 🟡 | Влияет на `WorkloadCalculationService.java:81-88`: отпуска уменьшают ёмкость, но не влияют на планирование задач/сроков |

**Замечание к дополнению.** §1 дополнения заявляет «новые открытые вопросы Q-024…Q-032», однако в таблице §8 приведены только Q-024, Q-025, Q-033, Q-034, Q-035. Вопросы Q-026…Q-032 в документе отсутствуют — нумерация в дополнении неполная. Дополнение также переиспользует ID `ERR-015`/`ERR-016`, уже занятые в ТЗ §28 под другие ситуации (см. §3 и §4).

---

## 8. Открытые вопросы, влияющие на реализацию (по приоритету)

| Приоритет | ID | Что именно блокирует / чем влияет |
|---|---|---|
| Высокий | Q-001 | Имена ролей зашиты в `Role`, `SecurityConfig`, CHECK-констрейнт `users_role_check` и данные; смена решения = миграция + правка всех `@PreAuthorize`/URL-правил |
| Высокий | Q-017 | Без решения по каналам уведомлений BR-002 (алерт о перегрузке) не закрывается; подсистемы уведомлений в коде нет |
| Высокий | Q-016 | Блокирует AI Skill Hub: без провайдера/промпта нельзя закрыть BR-005, UC-014, UC-016, SCREEN-027 |
| Высокий | Q-004 | Три несогласованных источника весов (model.pkl, fallback, статические веса `ModelInsightService`) искажают страницу ML-модели и защиту проекта |
| Средний | Q-003 | Контракт `/api/v1/predict` не согласован: принятая нормализация на стороне платформы при смене решения потребует переноса логики и правки `PythonPredictRequest`/`services.py` |
| Средний | Q-019 | Нет критериев приёмки качества модели и реального датасета: метрики считаются in-sample (`main.py`), KPI-04 не подтверждён |
| Средний | Q-024 / Q-025 | Личные дела ПДн хранятся бессрочно и доступны HR для уволенных (`HrHireController.documents` не проверяет `active`) — правовой риск, требуется решение по ретенции |
| Средний | Q-033 / Q-034 | «Тундук» и фото — заглушки (`MockTundukClient`), при переходе на реальную систему меняется реализация порта `TundukClient` и появляется хранилище |
| Средний | Q-011 | Фактическая матрица доступа к `/analytics/**`, 1:1 и задачам реализована, но не подтверждена заказчиком; риск переделки правил безопасности |
| Низкий | Q-021 | `RESCHEDULED` уже допускается CHECK-констрейнтом БД, но не реализован; Team Lead отсутствует в `Role` — при положительном решении потребуется миграция enum |
| Низкий | Q-022 | Требования WCAG/мобильной версии не заданы — шаблоны могут потребовать переработки вёрстки |
| Низкий | Q-035 | Отпуска влияют на нагрузку, но не на планирование задач; при положительном решении — доработка планирования |
| Не влияет | Q-012, Q-013, Q-014, Q-023 | Вопросы к заказчику по нагрузке, доступности/RPO, production-среде и оформлению ТЗ; текущего кода не касаются |

---

## 9. Ограничения аудита

1. Проверялись только файлы репозиториев `StaffSense` (ветка рабочего каталога) и `StaffSenseML`; незакоммиченные изменения и внешние окружения не анализировались.
2. Статусы «✅ реализовано» означают наличие подтверждающего кода, но **не** подтверждают поведение в рантайме (автотесты покрывают лишь часть сценариев: `HireEmployeeServiceTest`, `InnValidatorTest`, `MockTundukClientTest`, `OnboardingServiceTest`, `TimesheetServiceTest`, `ServiceMemoServiceTest`, `KpiCalculationServiceTest`, `DatasetGeneratorTest`, `ErrorPageRenderingTest`).
3. Там, где подтверждение отсутствует, статус установлен ❌ с пометкой «не найдено в коде» — без предположений.
