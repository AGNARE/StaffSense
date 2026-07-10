# TECH SPEC: StaffSense
## Полная техническая спецификация для генерации кода (Java Spring Boot + Thymeleaf SSR)

> Версия: 2.0 (расширенная, для передачи локальному ИИ-агенту)
> Назначение документа: единый источник правды (single source of truth) для генерации кода, БД, бизнес-логики и UI.
> Как читать: документ детерминированный — формулы, типы данных, маршруты и роли описаны явно, чтобы агент не «придумывал» архитектуру сам.

---

## 0. Инструкция для ИИ-агента (вставь это первым сообщением)

```
Ты — Java-разработчик. Ниже идёт полная спецификация веб-приложения StaffSense
(Spring Boot 3.x, Java 17+, Thymeleaf SSR, PostgreSQL, Spring Security, Spring Data JPA).
Генерируй код строго по разделам этой спецификации, не меняя названия полей,
эндпоинтов и enum-значений без явного запроса. Если что-то в спецификации
противоречиво — останавливайся и спрашивай, а не придумывай самостоятельно.
Работай по одному модулю за раз в порядке раздела 14 (Поэтапный план).
```

---

## 1. Обзор системы

StaffSense — корпоративная веб-платформа SSR на Spring Boot + Thymeleaf, объединяющая:
1. Управление задачами/проектами (упрощённый Jira/Trello).
2. Расчёт нагрузки и KPI сотрудников.
3. Формализованный прогноз вероятности увольнения (классификация, объяснимая модель).
4. Психологический well-being трек: pulse-опросы + 1:1 встречи.
5. Аналитические страницы с графиками.

Тип рендера: **Server-Side Rendering**. Никакого SPA/REST-фронтенда — данные подставляются в HTML на сервере через Thymeleaf, JS используется только для отрисовки графиков (Chart.js) и мелких UI-интеракций.

---

## 2. Стек и версии (фиксированные)

| Компонент | Версия / выбор |
|---|---|
| Java | 17 (LTS) |
| Spring Boot | 3.2.x |
| Build | Maven |
| Шаблонизатор | Thymeleaf 3.x |
| БД | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Безопасность | Spring Security 6 (form login, session-based, БЕЗ JWT — SSR не нуждается в токенах) |
| Графики | Chart.js (CDN) |
| ML | Собственная реализация логистической регрессии в Java (без внешних ML-библиотек) — см. раздел 6.3 |
| Валидация | Jakarta Bean Validation (`@NotNull`, `@Min`, и т.д.) |
| Тесты | JUnit 5 + Mockito (минимально — на сервисный слой) |
| Логирование | SLF4J + Logback (стандартно из Spring Boot) |

**Допущение:** ML без сторонних библиотек (Smile/Weka) — чтобы не ловить проблемы совместимости версий в учебной среде и чтобы агент мог сгенерировать прозрачный, читаемый код формулы (важно для защиты по ОФС — там должна быть явная математика, а не «чёрный ящик импортированной либы»).

---

## 3. Глоссарий

| Термин | Значение |
|---|---|
| Workload Index | Индекс нагрузки сотрудника за период (0 = нет задач, 1 = ровно норма, >1 = перегруз) |
| Risk Score | Вероятность увольнения сотрудника (0–100%), результат логистической регрессии |
| Pulse Survey | Короткий периодический опрос по самочувствию/удовлетворённости |
| 1:1 (one-on-one) | Личная встреча сотрудника с руководителем/HR |
| Target Scope | Область назначения опроса: компания / отдел / команда / конкретный сотрудник |
| Capacity | Нормативная ёмкость сотрудника (часов в неделю), используется для расчёта Workload Index |

---

## 4. Доменная модель — сущности (полная спецификация полей)

### 4.1 `User` (сотрудник/пользователь системы)

| Поле | Тип | Ограничения | Комментарий |
|---|---|---|---|
| id | Long | PK, autogenerate | |
| fullName | String | NOT NULL, max 150 | |
| email | String | NOT NULL, UNIQUE | используется как логин |
| passwordHash | String | NOT NULL | BCrypt |
| role | Enum: ADMIN, EXEC, HR, PM, EMPLOYEE | NOT NULL | |
| department | String | NOT NULL | для группировки аналитики |
| position | String | NOT NULL | должность |
| managerId | Long (FK → User.id, nullable) | | прямой руководитель (для 1:1, для team-scope) |
| salary | BigDecimal | NOT NULL, > 0 | базовая зарплата |
| gradeLevel | Enum: JUNIOR, MIDDLE, SENIOR, LEAD | NOT NULL | нужно для salary_ratio (см. 6.3) |
| weeklyCapacityHours | Integer | NOT NULL, default 40 | нормативная ёмкость |
| hireDate | LocalDate | NOT NULL | для расчёта tenure |
| active | Boolean | default true | мягкое удаление |

**Допущение:** `salary_ratio` (зарплата относительно грейда/рынка) считается как `salary / averageSalaryForGrade(department, gradeLevel)`. Нужно либо хранить справочник средних зарплат по грейдам (таблица `SalaryBenchmark`), либо считать динамически как среднее по всем активным сотрудникам того же грейда. **Рекомендую второй вариант (динамический), он не требует ручного справочника.**

### 4.2 `Project`

| Поле | Тип | Ограничения |
|---|---|---|
| id | Long | PK |
| name | String | NOT NULL |
| description | String | nullable |
| ownerPmId | Long (FK → User) | NOT NULL, role должен быть PM |
| createdAt | LocalDateTime | auto |

### 4.3 `Task`

| Поле | Тип | Ограничения |
|---|---|---|
| id | Long | PK |
| projectId | Long (FK → Project) | NOT NULL |
| title | String | NOT NULL |
| description | String | nullable |
| assigneeId | Long (FK → User, nullable) | задача может быть не назначена |
| status | Enum: TODO, IN_PROGRESS, REVIEW, DONE | default TODO |
| priority | Enum: LOW, MEDIUM, HIGH, CRITICAL | default MEDIUM |
| estimateHours | Double | NOT NULL, > 0 | используется в расчёте Workload Index |
| actualHours | Double | nullable | если ведётся факт |
| dueDate | LocalDate | nullable |
| createdAt | LocalDateTime | auto |
| completedAt | LocalDateTime | nullable, заполняется при переходе в DONE |
| reopenedCount | Integer | default 0 | инкрементируется при возврате из DONE/REVIEW в IN_PROGRESS — используется в KPI |

### 4.4 `KpiRecord` (снимок KPI за период)

| Поле | Тип | Ограничения |
|---|---|---|
| id | Long | PK |
| userId | Long (FK → User) | NOT NULL |
| periodStart | LocalDate | NOT NULL |
| periodEnd | LocalDate | NOT NULL |
| onTimeCompletionRate | Double | 0..1 |
| reopenRate | Double | 0..1 |
| managerRating | Double | 1..5, nullable (если PM не оценил) |
| kpiScore | Double | 0..100, расчётное поле (см. 6.2) |
| computedAt | LocalDateTime | auto |

### 4.5 `WorkloadRecord` (снимок нагрузки за период)

| Поле | Тип | Ограничения |
|---|---|---|
| id | Long | PK |
| userId | Long (FK → User) | NOT NULL |
| periodStart / periodEnd | LocalDate | NOT NULL |
| totalEstimatedHours | Double | сумма оценок активных задач |
| capacityHours | Double | = weeklyCapacityHours * количество недель в периоде |
| workloadIndex | Double | расчётное (см. 6.1) |
| overtimeHours | Double | nullable, если ведётся учёт переработок |

### 4.6 `Survey`

| Поле | Тип | Ограничения |
|---|---|---|
| id | Long | PK |
| title | String | NOT NULL |
| createdById | Long (FK → User) | роль HR/PM/ADMIN |
| targetScope | Enum: COMPANY, DEPARTMENT, TEAM, INDIVIDUAL | NOT NULL |
| targetDepartment | String | nullable, заполняется если scope=DEPARTMENT |
| targetManagerId | Long, nullable | заполняется если scope=TEAM (все, у кого managerId = X) |
| targetUserId | Long, nullable | если scope=INDIVIDUAL |
| startDate | LocalDate | NOT NULL |
| endDate | LocalDate | NOT NULL, >= startDate |
| isAnonymous | Boolean | NOT NULL |
| status | Enum: DRAFT, ACTIVE, CLOSED | default DRAFT |

### 4.7 `SurveyQuestion`

| Поле | Тип |
|---|---|
| id | Long PK |
| surveyId | Long (FK → Survey) |
| text | String |
| type | Enum: LIKERT_5, FREE_TEXT |
| orderIndex | Integer |

### 4.8 `SurveyResponse`

| Поле | Тип |
|---|---|
| id | Long PK |
| surveyId | Long (FK → Survey) |
| userId | Long, **nullable если isAnonymous = true** (хранить null, а не реальный id, чтобы анонимность была настоящей на уровне данных) |
| submittedAt | LocalDateTime |

### 4.9 `SurveyAnswer`

| Поле | Тип |
|---|---|
| id | Long PK |
| responseId | Long (FK → SurveyResponse) |
| questionId | Long (FK → SurveyQuestion) |
| likertValue | Integer, nullable (1..5) |
| textValue | String, nullable |

### 4.10 `OneOnOneMeeting`

| Поле | Тип | Ограничения |
|---|---|---|
| id | Long | PK |
| employeeId | Long (FK → User) | NOT NULL |
| managerId | Long (FK → User) | NOT NULL |
| scheduledAt | LocalDateTime | NOT NULL |
| status | Enum: PLANNED, COMPLETED, CANCELLED, RESCHEDULED | default PLANNED |
| notes | String | nullable, заполняется после встречи |
| actionItems | String (JSON-массив строк или просто текст с разделителями) | nullable |
| missed | Boolean | default false, ставится true если PLANNED просрочена без перевода в COMPLETED |

### 4.11 `AttritionPrediction` (история расчётов риска)

| Поле | Тип |
|---|---|
| id | Long PK |
| userId | Long (FK → User) |
| computedAt | LocalDateTime |
| riskScore | Double (0..100) |
| riskLevel | Enum: LOW, MEDIUM, HIGH |
| featuresSnapshotJson | String (JSON со значениями всех признаков на момент расчёта — для прозрачности/объяснимости) |

---

## 5. Связи между сущностями (краткая ER-сводка)

```
User (1) ───< (N) Task            [assignee]
User (1) ───< (N) Project         [ownerPm]
Project (1) ───< (N) Task
User (1) ───< (N) KpiRecord
User (1) ───< (N) WorkloadRecord
User (1) ───< (N) OneOnOneMeeting [as employee]
User (1) ───< (N) OneOnOneMeeting [as manager]
Survey (1) ───< (N) SurveyQuestion
Survey (1) ───< (N) SurveyResponse
SurveyResponse (1) ───< (N) SurveyAnswer
User (1) ───< (N) AttritionPrediction
User (self-reference) managerId → User.id
```

---

## 6. Бизнес-логика (формальная)

### 6.1 Workload Index (нагрузка)

```
totalEstimatedHours(user, period) = SUM(task.estimateHours)
    WHERE task.assigneeId = user.id
      AND task.status IN (TODO, IN_PROGRESS, REVIEW)
      AND task.createdAt <= period.end
      AND (task.completedAt IS NULL OR task.completedAt >= period.start)

capacityHours(user, period) = user.weeklyCapacityHours * weeksInPeriod(period)

workloadIndex = totalEstimatedHours / capacityHours
```

Интерпретация:
| Диапазон | Категория |
|---|---|
| < 0.6 | Недогруз |
| 0.6 – 1.0 | Нормальная нагрузка |
| 1.0 – 1.3 | Повышенная нагрузка |
| > 1.3 | Перегруз (риск-фактор) |

### 6.2 KPI Score

```
onTimeCompletionRate = (кол-во задач DONE с completedAt <= dueDate за период)
                        / (кол-во задач DONE за период)

reopenRate = (сумма reopenedCount задач за период) / (кол-во задач за период)

managerRatingNormalized = (managerRating / 5) * 100   // если managerRating есть, иначе не учитывается, веса перераспределяются

kpiScore = w1*(onTimeCompletionRate*100) + w2*(100 - reopenRate*100) + w3*managerRatingNormalized

где по умолчанию: w1 = 0.5, w2 = 0.2, w3 = 0.3 (сумма = 1.0)
```

**Допущение:** веса (0.5/0.2/0.3) — мой предложенный дефолт, его стоит сделать настраиваемым параметром в `application.yml`, а не хардкодить, чтобы на защите можно было сказать «весовые коэффициенты конфигурируемы».

### 6.3 Risk Score — прогноз увольнения (ядро курсовой)

**Шаг 1. Нормализация признаков** (приводим всё к диапазону, удобному для логрегрессии):

| Признак | Формула нормализации | Ожидаемое влияние на риск |
|---|---|---|
| salaryRatio | `salary / avgSalaryForGrade(grade, department)` | ниже 1.0 → выше риск (значит, минус-коэффициент) |
| workloadIndex | из 6.1, без изменений | выше 1.3 → выше риск (плюс-коэффициент) |
| kpiNorm | `kpiScore / 100` | неоднозначно: и очень низкий, и очень высокий (перегоревший топ-перформер) KPI может коррелировать с риском — см. допущение ниже |
| tenureMonths | `MIN(tenureMonths / 60, 1)` (капим на 5 годах) | очень короткий стаж (< 6 мес, "не вписался") и очень длинный плато — оба паттерна существуют; для простоты курсовой беру монотонную связь "чем меньше — тем выше риск" |
| satisfactionNorm | средний LIKERT за последние 3 опроса / 5 | ниже → выше риск (минус-коэффициент) |
| missedOneOnOneRatio | `missed1on1 / total1on1` за полгода | выше → выше риск (плюс-коэффициент) |
| overtimeNorm | `overtimeHours / (capacityHours*0.2)`, капим на 1 | выше → выше риск (плюс-коэффициент) |

**Допущение (важно):** связь KPI↔риск нелинейна (и низкий, и очень высокий KPI могут быть рискованными), но для учебной логрегрессии корректнее и проще обосновать **линейную** зависимость "низкий KPI → выше риск". Сложную U-образную связь можно упомянуть в отчёте как "направление для развития модели", но не реализовывать — иначе придётся уходить в полиномиальные признаки, что избыточно для ОФС.

**Шаг 2. Логистическая регрессия**

```
z = b0
  + b1 * (1 - salaryRatio)
  + b2 * max(workloadIndex - 1.0, 0)
  + b3 * (1 - kpiNorm)
  + b4 * (1 - tenureMonths_norm)
  + b5 * (1 - satisfactionNorm)
  + b6 * missedOneOnOneRatio
  + b7 * overtimeNorm

riskScore = 1 / (1 + e^(-z))   // sigmoid, результат 0..1, умножаем на 100 для процентов
```

**Стартовые коэффициенты (допущение, нужно калибровать на синтетических данных, см. раздел 12):**
`b0 = -1.5, b1 = 1.2, b2 = 1.0, b3 = 0.8, b4 = 0.6, b5 = 1.1, b6 = 0.9, b7 = 0.7`

Эти веса — **не обучены на реальных данных**, это правдоподобная инициализация. Правильный путь:
1. Сгенерировать синтетический датасет (раздел 12) с известной "истинной" зависимостью.
2. Обучить логрегрессию (градиентный спуск, реализуется вручную в Java — десятки строк кода) на этом датасете.
3. Использовать полученные веса в `AttritionMlService`.

**Шаг 3. Категоризация**

| riskScore | riskLevel |
|---|---|
| 0–33% | LOW |
| 33–66% | MEDIUM |
| 66–100% | HIGH |

**Объяснимость (важно для UI и для защиты):** при расчёте сохраняем `featuresSnapshotJson`, на странице риска показываем не только итоговый % но и вклад каждого слагаемого `bi * xi` — это «важности признаков» в простом и честном виде (line item breakdown), без необходимости в SHAP/LIME.

### 6.4 Опросы (Survey) — логика назначения и видимости

```
getVisibleSurveysFor(user):
    RETURN surveys WHERE status = ACTIVE
      AND today BETWEEN startDate AND endDate
      AND (
            targetScope = COMPANY
         OR (targetScope = DEPARTMENT AND targetDepartment = user.department)
         OR (targetScope = TEAM AND targetManagerId = user.managerId)
         OR (targetScope = INDIVIDUAL AND targetUserId = user.id)
      )
      AND NOT EXISTS (SurveyResponse WHERE surveyId = survey.id AND userId = user.id)  -- если не анонимный
```

Для анонимных опросов проверку "уже отвечал" делать через cookie/sessionFlag на конкретный surveyId (т.к. userId не сохраняется) — это нужно явно реализовать, иначе один человек сможет пройти опрос многократно.

### 6.5 1:1 встречи — статусная логика

```
PLANNED → COMPLETED   (вручную, менеджер заполняет notes/actionItems)
PLANNED → CANCELLED   (вручную)
PLANNED → RESCHEDULED (создаётся новая запись PLANNED, старая помечается RESCHEDULED)

Scheduled job (ежедневно):
  ЕСЛИ status = PLANNED AND scheduledAt < now() - 1 день → missed = true (остаётся PLANNED, но флаг missed используется в 6.3)
```

---

## 7. Роли и доступ (RBAC) — детально

| Маршрут / действие | ADMIN | EXEC | HR | PM | EMPLOYEE |
|---|---|---|---|---|---|
| /admin/** (управление пользователями) | ✅ | ❌ | ❌ | ❌ | ❌ |
| /projects/** (CRUD проектов) | ✅ | 👁 | 👁 | ✅ свои | 👁 свои задачи |
| /tasks/** (CRUD задач) | ✅ | 👁 | 👁 | ✅ своя команда | ✅ только свои (статус) |
| /kpi/** | ✅ | 👁 вся компания | ✅ | ✅ своя команда | 👁 только свои |
| /workload/** | ✅ | 👁 | ✅ | ✅ своя команда | 👁 только свои |
| /risk/** (прогноз увольнения) | ✅ | 👁 | ✅ | 👁 своя команда | ❌ **(403, даже не показываем ссылку в меню)** |
| /surveys/manage/** | ✅ | ✅ | ✅ | ✅ своя команда | ❌ |
| /surveys/take/** | — | — | — | — | ✅ |
| /oneonone/** | ✅ | 👁 | ✅ | ✅ своя команда | ✅ только свои встречи |
| /analytics/** | ✅ | ✅ | ✅ | ✅ своя команда | ✅ личная страница только |

**Реализация в Spring Security (псевдокод конфигурации):**
```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .requestMatchers("/risk/**").hasAnyRole("ADMIN", "EXEC", "HR", "PM")
    .requestMatchers("/surveys/manage/**").hasAnyRole("ADMIN", "EXEC", "HR", "PM")
    .requestMatchers("/surveys/take/**").authenticated()
    .requestMatchers("/analytics/personal").authenticated()
    .anyRequest().authenticated()
)
.formLogin(form -> form.loginPage("/login").permitAll());
```

**Доп. правило на уровне сервиса (не только URL):** для PM доступ к данным сотрудника разрешён **только если** `task.assignee.managerId == currentUser.id` (или сотрудник входит в его команду/проект) — это нужно проверять в сервисном слое (`@PreAuthorize` с SpEL или явная проверка в коде), а не только через URL-паттерны.

---

## 8. Карта страниц (UI)

| Страница | Маршрут | Роли | Контент |
|---|---|---|---|
| Логин | `/login` | все | форма входа |
| Дашборд (общий) | `/dashboard` | ADMIN/EXEC/HR/PM | сводка: средний риск по компании/отделам, активные опросы, ближайшие 1:1 |
| Личный кабинет | `/dashboard/personal` | EMPLOYEE | мои задачи, моя нагрузка, мои опросы, мои 1:1 (без риска) |
| Доска задач | `/projects/{id}/board` | все (с ограничением видимости) | канбан TODO/IN_PROGRESS/REVIEW/DONE |
| Список сотрудников | `/employees` | ADMIN/HR/PM | таблица с фильтром по отделу |
| Профиль сотрудника | `/employees/{id}` | ADMIN/HR/PM(своя команда) | данные, история KPI/нагрузки, (риск — если есть доступ) |
| Страница риска увольнения | `/risk` | ADMIN/EXEC/HR/PM | таблица сотрудников + risk_score + цвет-индикатор, сортировка по риску |
| Детали риска сотрудника | `/risk/{userId}` | ADMIN/EXEC/HR/PM | разбивка по слагаемым формулы (объяснимость) |
| Нагрузка (аналитика) | `/analytics/workload` | ADMIN/EXEC/HR/PM | график динамики нагрузки команды/отдела |
| KPI (аналитика) | `/analytics/kpi` | ADMIN/EXEC/HR/PM | график план/факт KPI |
| Well-being (аналитика) | `/analytics/wellbeing` | ADMIN/EXEC/HR | график результатов опросов во времени |
| Управление опросами | `/surveys/manage` | ADMIN/EXEC/HR/PM | создание/список опросов, выбор target_scope и дат |
| Прохождение опроса | `/surveys/take/{id}` | EMPLOYEE | форма с вопросами LIKERT/FREE_TEXT |
| 1:1 встречи (список) | `/oneonone` | все, с фильтром по ролям | список встреч (свои/команды) |
| Создание 1:1 | `/oneonone/new` | HR/PM | форма назначения встречи |

---

## 9. Контроллеры и маршруты (детальная таблица CRUD)

| Controller | Метод | Маршрут | Действие |
|---|---|---|---|
| AuthController | GET | /login | форма логина |
| EmployeeController | GET | /employees | список |
| EmployeeController | GET | /employees/{id} | профиль |
| EmployeeController | POST | /employees | создать (ADMIN) |
| ProjectController | GET/POST | /projects, /projects/new | список/создание |
| TaskController | GET | /projects/{id}/board | канбан |
| TaskController | POST | /tasks/{id}/status | смена статуса (drag&drop через форму/AJAX) |
| TaskController | POST | /tasks/new | создание задачи |
| KpiController | GET | /kpi/{userId} | история KPI |
| WorkloadController | GET | /workload/{userId} | история нагрузки |
| RiskController | GET | /risk | таблица риска |
| RiskController | GET | /risk/{userId} | детали + recompute |
| SurveyController | GET/POST | /surveys/manage, /surveys/manage/new | управление |
| SurveyController | GET/POST | /surveys/take/{id} | прохождение |
| OneOnOneController | GET/POST | /oneonone, /oneonone/new | список/создание |
| AnalyticsController | GET | /analytics/workload, /analytics/kpi, /analytics/wellbeing | страницы с графиками |

---

## 10. Структура проекта (пакеты и файлы)

```
src/main/java/com/staffsense/
 ├── controller/
 │    ├── AuthController.java
 │    ├── EmployeeController.java
 │    ├── ProjectController.java
 │    ├── TaskController.java
 │    ├── KpiController.java
 │    ├── WorkloadController.java
 │    ├── RiskController.java
 │    ├── SurveyController.java
 │    ├── OneOnOneController.java
 │    └── AnalyticsController.java
 ├── service/
 │    ├── EmployeeService.java
 │    ├── TaskService.java
 │    ├── KpiCalculationService.java
 │    ├── WorkloadCalculationService.java
 │    ├── AttritionMlService.java       // формула из 6.3
 │    ├── SurveyService.java
 │    └── OneOnOneService.java
 ├── repository/  (Spring Data JPA интерфейсы под каждую сущность)
 ├── model/       (Entities из раздела 4)
 ├── dto/         (формы/view-модели для Thymeleaf)
 ├── ml/
 │    ├── LogisticRegressionModel.java   // обучение градиентным спуском
 │    └── FeatureVector.java
 ├── config/
 │    ├── SecurityConfig.java
 │    └── AppConfig.java                 // веса KPI, пороги риска - из application.yml
 └── StaffSenseApplication.java

src/main/resources/
 ├── templates/
 │    ├── layout/base.html        (общий layout, Thymeleaf fragments)
 │    ├── auth/login.html
 │    ├── dashboard/main.html
 │    ├── dashboard/personal.html
 │    ├── projects/board.html
 │    ├── employees/list.html
 │    ├── employees/profile.html
 │    ├── risk/list.html
 │    ├── risk/details.html
 │    ├── analytics/workload.html
 │    ├── analytics/kpi.html
 │    ├── analytics/wellbeing.html
 │    ├── surveys/manage.html
 │    ├── surveys/take.html
 │    └── oneonone/list.html
 ├── static/css/styles.css
 ├── static/js/charts.js
 └── application.yml
```

---

## 11. Схема БД (DDL-набросок, главные таблицы)

```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  department VARCHAR(100) NOT NULL,
  position VARCHAR(100) NOT NULL,
  manager_id BIGINT REFERENCES users(id),
  salary NUMERIC(12,2) NOT NULL,
  grade_level VARCHAR(20) NOT NULL,
  weekly_capacity_hours INT NOT NULL DEFAULT 40,
  hire_date DATE NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE projects (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  description TEXT,
  owner_pm_id BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tasks (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id),
  title VARCHAR(200) NOT NULL,
  description TEXT,
  assignee_id BIGINT REFERENCES users(id),
  status VARCHAR(20) NOT NULL DEFAULT 'TODO',
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
  estimate_hours DOUBLE PRECISION NOT NULL,
  actual_hours DOUBLE PRECISION,
  due_date DATE,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  completed_at TIMESTAMP,
  reopened_count INT NOT NULL DEFAULT 0
);

-- остальные таблицы (kpi_records, workload_records, surveys, survey_questions,
-- survey_responses, survey_answers, one_on_one_meetings, attrition_predictions)
-- создаются 1:1 по полям из раздела 4. Рекомендуется: ddl-auto=update на этапе разработки,
-- затем зафиксировать в Flyway-миграции перед сдачей.
```

---

## 12. Синтетический датасет (для обучения и калибровки модели)

Так как реальных исторических данных об увольнениях нет, нужен сгенерированный датасет (можно — отдельным Python/Java скриптом, не частью прод-кода, просто для one-off обучения):

**Логика генерации (предлагаемая):**
```
N = 500..1000 синтетических "сотрудников"
для каждого:
  salaryRatio ~ Normal(1.0, 0.25), обрезать в [0.4, 2.0]
  workloadIndex ~ Normal(0.9, 0.35), обрезать в [0.1, 2.0]
  kpiNorm ~ Normal(0.7, 0.15), обрезать в [0,1]
  tenureMonths ~ Uniform(1, 96)
  satisfactionNorm ~ Normal(0.65, 0.2), обрезать в [0,1]
  missedOneOnOneRatio ~ Beta(1,5)  (большинство — низкое значение)
  overtimeNorm ~ Normal(0.3, 0.25), обрезать в [0,1]

  "истинная" вероятность увольнения =
     sigmoid(b0_true + b1_true*(1-salaryRatio) + b2_true*max(workloadIndex-1,0)
             + b3_true*(1-kpiNorm) + b4_true*(1 - tenureMonths/60)
             + b5_true*(1-satisfactionNorm) + b6_true*missedOneOnOneRatio
             + b7_true*overtimeNorm)
             + случайный шум

  willLeave = 1 если случайное_число < вероятность, иначе 0
```
Полученный CSV (8 признаков + метка) используется для обучения логрегрессии в `LogisticRegressionModel` (градиентный спуск, можно полностью реализовать вручную — это даже лучше для защиты, чем готовая библиотека).

**Допущение:** конкретные true-коэффициенты для генерации можно взять равными стартовым из раздела 6.3 (или немного отличными, чтобы обучение "находило" их заново — это нагляднее демонстрирует, что модель действительно учится, а не просто повторяет вход).

---

## 13. Нефункциональные требования

- Валидация форм через Bean Validation + отображение ошибок в Thymeleaf (`th:errors`).
- Пароли — только BCrypt, никогда в открытом виде.
- Все списки с пагинацией (Spring Data `Pageable`), особенно `/employees`, `/risk`.
- Логирование ключевых действий (создание опроса, расчёт риска) на уровне INFO.
- CSRF защита включена по умолчанию (Spring Security), формы должны содержать `_csrf` токен (Thymeleaf делает это автоматически через `th:action`).
- Часовой пояс — единый, например `Asia/Bishkek` (уточнить, если нужен другой — см. раздел 15).

---

## 14. Поэтапный план реализации (порядок для агента)

1. **Этап 1 — Каркас:** Entities + Repositories + Security (роли, логин) + базовый layout Thymeleaf.
2. **Этап 2 — Задачи/проекты:** Project/Task CRUD + канбан-доска.
3. **Этап 3 — Нагрузка и KPI:** WorkloadCalculationService + KpiCalculationService + страницы профиля с историей.
4. **Этап 4 — Риск увольнения:** LogisticRegressionModel + AttritionMlService + страница `/risk` с разбивкой по слагаемым.
5. **Этап 5 — Опросы:** Survey CRUD (HR/PM) + прохождение (Employee) + агрегация для well-being.
6. **Этап 6 — 1:1 встречи:** CRUD + связь missed-флага с риском.
7. **Этап 7 — Аналитика:** страницы с Chart.js (workload/kpi/wellbeing).
8. **Этап 8 — Полировка:** пагинация, валидации, обработка ошибок, базовые тесты сервисов.

**Рекомендация:** проси у локального агента код **по одному этапу за раз**, прикладывая только релевантные разделы спецификации (например, для этапа 4 — разделы 4.11, 6.3, 7, 8 (только /risk), 9 (RiskController), 12). Так агент не "перегружается" контекстом и меньше ошибается в полях/маршрутах.

---

## 15. Открытые вопросы / чего не хватает (нужно решить нам с тобой)

Это прямые места, где я принял допущение за тебя — стоит явно подтвердить или поменять:

1. **Валюта и диапазон зарплат** — не указаны. Я не привязывался к конкретным цифрам (используется относительный `salaryRatio`), но если нужна реалистичность для презентации — нужно сказать в какой валюте и порядок цифр (сом/доллар, диапазон).
2. **Источник "истории увольнений"** — реальных данных нет, я предложил синтетическую генерацию (раздел 12). Если у тебя есть доступ к какому-то открытому HR-датасету (например, известный Kaggle "HR Analytics / IBM Attrition dataset") — лучше взять оттуда реальные корреляции вместо моих "true-коэффициентов", это усилит научную обоснованность работы.
3. **Глубина каждого модуля** — я держал канбан максимально простым (4 статуса, без спринтов/эпиков). Если препод ожидает что-то ближе к настоящей Jira (спринты, эпики, backlog) — нужно расширять раздел 4.3.
4. **Нужны ли уведомления** (email/in-app) о новом опросе/1:1? Я не включил отдельный модуль Notification — это можно добавить как Этап 9, если время позволяет.
5. **Часовой пояс и язык интерфейса** — предположил Asia/Bishkek и русский интерфейс; подтверди.
6. **KPI весовые коэффициенты (0.5/0.2/0.3)** и **risk-коэффициенты b0..b7** — это мои стартовые допущения, не «истина». Их нужно либо оставить как демонстрационные (с пометкой в отчёте «инициализированы экспертно, далее калибруются»), либо обучить на синтетических/реальных данных по разделу 12.
7. **Нужна ли роль "Team Lead" отдельно от PM?** Сейчас я объединил их — у PM такие же права на свою команду. Если в защите требуется отдельная роль — добавим в enum.

Если решишь — просто скажи, что менять, и я обновлю документ. Если оставляешь как есть — документ уже самодостаточен для передачи агенту.

---

## 16. Как лучше промптить локального ИИ-агента (поэтапная стратегия)

Рекомендую не кидать агенту весь файл одним сообщением на "напиши всё", а делать так:

**Промпт №1 (каркас):**
> "Вот тех. спецификация StaffSense [приложить весь файл]. Начни с Этапа 1 из раздела 14: создай Maven-проект, все Entity из раздела 4, репозитории, SecurityConfig с ролями из раздела 7, и базовый layout Thymeleaf с логином. Не реализуй пока бизнес-логику KPI/risk — только каркас и CRUD для User."

**Промпт №2 (после проверки каркаса):**
> "Каркас принят. Теперь Этап 2: Project/Task CRUD и канбан-доска по разделу 4.3, 8 (страница /projects/{id}/board), 9 (TaskController)."

**И так далее по этапам 3–8.** Перед каждым следующим этапом — компилируй и проверяй предыдущий, чтобы ошибки не накапливались.

**Промпт для обучения модели (отдельно, разово):**
> "Сгенерируй синтетический датасет по правилам раздела 12 (Python-скриптом или Java), обучи логистическую регрессию градиентным спуском, выведи финальные коэффициенты b0..b7, чтобы я подставил их в AttritionMlService."

---

*Конец спецификации. Документ самодостаточен для постадийной генерации кода. Раздел 15 — единственное место, где нужно либо твоё подтверждение, либо явное решение, прежде чем агент начнёт калибровку модели и финальную полировку.*
