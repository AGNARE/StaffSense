# Маршруты приложения

## Обзор

StaffSense отдаёт два вида ответов с одного и того же порта:

- **SSR-страницы** — HTML, отрендеренный Thymeleaf (шаблоны в `templates/`);
- **JSON** — только маршруты под `/api/**`, предназначенные для AJAX-вызовов.

Все маршруты требуют аутентификации, кроме публичного набора:
`/login`, `/error`, `/css/**`, `/js/**`, `/img/**`, `/images/**`, `/favicon.ico`.
Всё остальное закрыто правилом `anyRequest().authenticated()`.

Формат таблиц ниже:

```
| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
```

Роли указаны в терминах приложения: `ADMIN`, `HR`, `PM`, `EXEC`, `EMPLOYEE`,
«любой аутентифицированный». Где доступ ограничен не в `SecurityConfig`,
а аннотацией `@PreAuthorize` или проверкой в сервисном слое, это указано
прямо в столбце «Роли».

---

## Аутентификация

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/login` | публичный | Форма входа | шаблон `auth/login` |
| POST | `/login` | публичный | Обработка входа (`loginProcessingUrl`) | redirect `/dashboard` (успех), `/login?error` (ошибка), `/login?error=rate_limit` (лимит попыток) |
| POST | `/logout` | публичный | Выход, инвалидация сессии, удаление cookie `JSESSIONID` | redirect `/login?logout` |

На `POST /login` действует фильтр `RateLimitingFilter`: 5 попыток с пополнением
1 токен за 60 секунд на IP (заголовок `X-Forwarded-For` учитывается).
При превышении — `429 Too Many Requests`, заголовок `Retry-After: 60`,
браузерный запрос (`Accept: text/html`) уходит на `/login?error=rate_limit`,
остальные получают JSON `{"error": "Слишком много попыток..."}`.

Невалидная сессия перенаправляет на `/login?expired`.

---

## Дашборд

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/dashboard` | любой аутентифицированный | Точка входа: для `EMPLOYEE` делегирует в персональный дашборд, для остальных ролей — в управленческий | шаблон `dashboard/personal` (EMPLOYEE) или `dashboard/main` |
| GET | `/dashboard/personal` | любой аутентифицированный | Персональный дашборд: доступные опросы, свои встречи 1:1, последние KPI и нагрузка | шаблон `dashboard/personal` |

Разделение дашборда выполняется в коде по роли, а не правилами безопасности,
поэтому `GET /dashboard` доступен всем аутентифицированным, но отдаёт разный шаблон.

---

## Сотрудники и риск увольнения

### Сотрудники

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/employees` | ADMIN, HR, PM (класс `@PreAuthorize`); data-scope в `EmployeeService` | Список сотрудников с фильтром по отделу и постраничной навигацией. PM видит только свою команду (`users.manager_id`), доступные отделы также сужены до команды | шаблон `employees/list` |
| GET | `/employees/export/csv` | ADMIN, HR, PM (класс `@PreAuthorize`); data-scope в `CsvExportService` | Экспорт реестра сотрудников в CSV. Для PM выборка ограничена командой (`canViewEmployee`) | CSV-файл (`text/csv`, `Content-Disposition: attachment`) |
| GET | `/employees/{id}` | ADMIN, HR, PM (класс `@PreAuthorize`); data-scope в `TeamAccessService` | Карточка сотрудника: KPI, история нагрузки, последний прогноз риска. PM не может открыть карточку вне своей команды — `requireCanViewEmployee` выбрасывает `AccessDeniedException` | шаблон `employees/profile` |

### Риск увольнения

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/risk` | ADMIN, EXEC, HR, PM (класс `@PreAuthorize`); data-scope в контроллере | Список сотрудников с оценкой риска (сортировка по убыванию score). PM видит только свою команду через `findByManagerAndActiveAndRolePaged` | шаблон `risk/list` |
| GET | `/risk/{userId}` | ADMIN, EXEC, HR, PM (класс `@PreAuthorize`); data-scope в `TeamAccessService` | Детали риска: карточки ML-признаков (`FeatureCard`) и факторы риска XAI. PM ограничен своей командой (`requireCanViewEmployee`) | шаблон `risk/details` |
| GET | `/risk/export/csv` | ADMIN, EXEC, HR, PM (класс `@PreAuthorize`); data-scope в `CsvExportService` | Экспорт отчёта по рискам в CSV. Для PM выборка ограничена командой | CSV-файл (`text/csv`, `Content-Disposition: attachment`) |
| POST | `/risk/{userId}/recompute` | ADMIN, EXEC, HR, PM (класс `@PreAuthorize`) | Пересчёт прогноза через ML-сервис. Ошибка ML не роняет запрос: сообщение уходит во flash-атрибут | redirect `/risk/{userId}` |

---

## Проекты и задачи

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/projects` | любой аутентифицированный | Список всех проектов (просмотр открыт всем ролям) | шаблон `projects/list` |
| GET | `/projects/new` | ADMIN, PM (`@PreAuthorize` в методе) | Форма создания проекта, список активных PM для выбора владельца | шаблон `projects/new` |
| POST | `/projects/new` | ADMIN, PM (`@PreAuthorize` в методе) | Создание проекта с назначением владельца-PM | redirect `/projects` |
| GET | `/projects/{id}/board` | любой аутентифицированный | Канбан-доска проекта: задачи, сгруппированные по статусам | шаблон `projects/board` |
| GET | `/projects/{id}/tasks/new` | ADMIN, PM (`@PreAuthorize` в методе) | Форма создания задачи в проекте, список активных сотрудников | шаблон `projects/task-form` |
| POST | `/projects/{id}/tasks/new` | ADMIN, PM (`@PreAuthorize` в методе); дополнительно `requireCanCreateTaskIn` в `TaskService` | Создание задачи из HTML-формы. При ошибках валидации форма возвращается с `BindingResult`; PM может создавать задачи только в своих проектах | шаблон `projects/task-form` (ошибки валидации) или redirect `/projects/{id}/board` |

`SecurityConfig` пропускает `/projects/**` для любого аутентифицированного,
поэтому ограничение «только ADMIN/PM» на создании задаётся исключительно
аннотациями `@PreAuthorize` и проверками в сервисе.

---

## Опросы

### Управление опросами

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/surveys/manage` | ADMIN, EXEC, HR, PM | Список опросов с количеством ответов, постранично | шаблон `surveys/manage` |
| GET | `/surveys/manage/new` | ADMIN, EXEC, HR, PM | Форма создания опроса: типы, охваты, отделы, руководители, сотрудники | шаблон `surveys/new` |
| POST | `/surveys/manage/new` | ADMIN, EXEC, HR, PM | Создание черновика (`DRAFT`). Проверяются `end_date >= start_date` и заполненность параметра выбранного охвата. Опрос создаётся со статусом `DRAFT` | redirect `/surveys/manage` (успех) или `/surveys/manage/new` (ошибка валидации) |
| GET | `/surveys/manage/{id}/edit` | ADMIN, EXEC, HR, PM | Форма редактирования опроса. BR-013: доступна только для статуса `DRAFT` | шаблон `surveys/edit` или redirect `/surveys/manage` (не черновик) |
| POST | `/surveys/manage/{id}/edit` | ADMIN, EXEC, HR, PM | Обновление опроса и его вопросов; повторная проверка дат и охвата | redirect `/surveys/manage` |
| GET | `/surveys/manage/{id}/export/csv` | ADMIN, EXEC, HR, PM | Экспорт результатов опроса в CSV | CSV-файл (`text/csv`, `Content-Disposition: attachment`) |
| GET | `/surveys/manage/{id}/results` | ADMIN, EXEC, HR, PM | Аналитика опроса: распределение ответов, средний балл | шаблон `surveys/results` |
| POST | `/surveys/manage/{id}/activate` | ADMIN, EXEC, HR, PM | Активация опроса | redirect `/surveys/manage` |
| POST | `/surveys/manage/{id}/close` | ADMIN, EXEC, HR, PM | Закрытие опроса | redirect `/surveys/manage` |

### Прохождение опроса

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/surveys/take/{id}` | любой аутентифицированный | Страница прохождения опроса с вопросами | шаблон `surveys/take` |
| POST | `/surveys/take/{id}` | любой аутентифицированный | Сохранение ответов (поля с префиксом `question_`). Читает глобальные записи опросов, но привязывает ответ только к текущему пользователю | redirect `/dashboard/personal` |

---

## Встречи 1:1

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/oneonone` | любой аутентифицированный; выборка по роли в контроллере | Список встреч: `EMPLOYEE` — только свои, `PM` — только где он менеджер, `ADMIN`/`HR`/`EXEC` — все | шаблон `oneonone/list` |
| GET | `/oneonone/new` | ADMIN, HR, PM (`@PreAuthorize` в методе); фильтрация списка через `canScheduleMeetingWith` | Форма назначения встречи. PM видит только своих подчинённых, ADMIN/HR — всех сотрудников | шаблон `oneonone/new` |
| POST | `/oneonone/new` | ADMIN, HR, PM (`@PreAuthorize` в методе); проверка в `OneOnOneService` | Назначение встречи. Менеджером записывается фактический руководитель сотрудника, иначе инициатор. Встреча «с самим собой» запрещена | redirect `/oneonone` |
| POST | `/oneonone/{id}/complete` | ADMIN, HR, PM (`@PreAuthorize` в методе); `requireCanManageMeeting` в `TeamAccessService` | Завершение встречи с заметками и поручениями. Завершить чужую встречу нельзя (закрыт IDOR) | redirect `/oneonone` |
| POST | `/oneonone/{id}/cancel` | ADMIN, HR, PM (`@PreAuthorize` в методе); `requireCanManageMeeting` в `TeamAccessService` | Отмена встречи. Отменить чужую встречу нельзя | redirect `/oneonone` |

---

## Аналитика (KPI, нагрузка, благополучие, ML-модель)

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/analytics/workload` | ADMIN, EXEC, HR, PM | Анализ загруженности: по каждому активному `EMPLOYEE` берётся последний `WorkloadRecord`. Выборка глобальная, сужения по команде у PM нет | шаблон `analytics/workload` |
| GET | `/analytics/kpi` | ADMIN, EXEC, HR, PM | KPI-аналитика по всем активным сотрудникам (последний `KpiRecord`). Сужения по команде у PM нет | шаблон `analytics/kpi` |
| GET | `/analytics/wellbeing` | ADMIN, EXEC, HR, PM | Панель благополучия: агрегация LIKERT-оценок по датам плюс тепловая карта удовлетворённости по сотрудникам и отделам | шаблон `analytics/wellbeing` |
| GET | `/analytics/ml-model` | ADMIN, EXEC, HR, PM | ML-аналитика: веса признаков, распределение рисков, инсайты по сотрудникам и тепловая карта признаков. При отсутствии прогнозов запускает асинхронный пересчёт и выставляет `mlComputing` | шаблон `analytics/ml-model` |
| POST | `/analytics/ml-model/recompute` | ADMIN, EXEC, HR, PM | Ручной запуск пересчёта ML-прогнозов по всем сотрудникам; результат отдаётся тем же шаблоном с сообщением об успехе | шаблон `analytics/ml-model` |

У `/analytics/**` нет ни одной аннотации `@PreAuthorize` — доступ ограничен
только правилом `SecurityConfig` (`hasAnyRole("ADMIN","EXEC","HR","PM")`).

---

## Админ-панель

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/admin` | ADMIN | Список пользователей с фильтром по роли и пагинацией | шаблон `admin/users` |
| GET | `/admin/users/new` | ADMIN | Форма создания пользователя: роли, грейды | шаблон `admin/user-form` |
| POST | `/admin/users/new` | ADMIN | Создание пользователя. Пароль хешируется `BCryptPasswordEncoder` (12 раундов) | redirect `/admin` |
| POST | `/admin/users/{id}/toggle-active` | ADMIN | Активация/деактивация пользователя. Отключить собственную активную учётную запись нельзя | redirect `/admin` |

---

## Модуль «Тундук» (приём на работу, личные дела)

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/hr/hire` | ADMIN, HR (класс `@PreAuthorize`) | Шаг 1 — форма приёма: корпоративные поля, справочники ролей и грейдов, список руководителей, демо-ИНН | шаблон `hr/hire` |
| POST | `/hr/hire/verify` | ADMIN, HR (класс `@PreAuthorize`) | Шаг 2 — верификация ИНН через «Тундук». При `FOUND` поля заполняются из ответа и ставится `tundukVerified`; при `NOT_FOUND`/`UNAVAILABLE` — предупреждение «заполните вручную» | шаблон `hr/hire` |
| POST | `/hr/hire` | ADMIN, HR (класс `@PreAuthorize`) | Шаг 3 — сохранение сотрудника. Дубли не создаются: при совпадении ИНН учётная запись реактивируется либо данные обновляются; может вернуться временный пароль | шаблон `hr/hire` (ошибки валидации) или redirect `/hr/documents/{userId}` |
| GET | `/hr/documents/{userId}` | ADMIN, HR (класс `@PreAuthorize`) | Личное дело сотрудника: паспортные данные, ПИН, адрес регистрации | шаблон `hr/documents` |

`/hr/**` закрыт в `SecurityConfig` правилом `hasAnyRole("ADMIN","HR")`,
и та же пара ролей продублирована на уровне класса.

---

## Портал онбординга и справочник

### Портал онбординга (чтение)

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/onboarding` | любой аутентифицированный | Главная портала: разделы, последние статьи, прогресс чек-листа | шаблон `onboarding/index` |
| GET | `/onboarding/section/{section}` | любой аутентифицированный | Статьи выбранного раздела (перечисление `OnboardingSection`) | шаблон `onboarding/section` |
| GET | `/onboarding/article/{id}` | любой аутентифицированный | Просмотр статьи справочника | шаблон `onboarding/article` |
| GET | `/onboarding/checklist` | любой аутентифицированный | Чек-лист новичка с персональным прогрессом | шаблон `onboarding/checklist` |
| POST | `/onboarding/checklist/{articleId}/toggle` | любой аутентифицированный | Отметить/снять пункт чек-листа для текущего пользователя | redirect `/onboarding/checklist` |

### Управление содержимым онбординга

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/onboarding/manage` | ADMIN, HR (класс `@PreAuthorize`) | Список статей онбординга для редактирования | шаблон `onboarding/manage` |
| GET | `/onboarding/manage/new` | ADMIN, HR (класс `@PreAuthorize`) | Форма создания статьи (`mode=create`) | шаблон `onboarding/form` |
| POST | `/onboarding/manage/new` | ADMIN, HR (класс `@PreAuthorize`) | Создание статьи | redirect `/onboarding/manage` |
| GET | `/onboarding/manage/{id}/edit` | ADMIN, HR (класс `@PreAuthorize`) | Форма редактирования статьи (`mode=edit`) | шаблон `onboarding/form` |
| POST | `/onboarding/manage/{id}/edit` | ADMIN, HR (класс `@PreAuthorize`) | Обновление статьи | redirect `/onboarding/manage` |
| POST | `/onboarding/manage/{id}/delete` | ADMIN, HR (класс `@PreAuthorize`) | Удаление статьи | redirect `/onboarding/manage` |

Порядок правил в `SecurityConfig` важен: `/onboarding/manage/**` (`ADMIN`, `HR`)
объявлен раньше `/onboarding/**` (любой аутентифицированный). Дефолтное
правило `anyRequest().authenticated()` тоже покрывает `/onboarding/**`,
поэтому доступ сохранится даже при удалении явной строки.

### Справочник сотрудников

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/directory` | любой аутентифицированный | Справочник (FR-043) с фильтром по отделу и поиском по ФИО, должности и email. Отдаёт только ФИО, должность, отдел, рабочий email и руководителя — без оклада, KPI, нагрузки и риска | шаблон `directory/index` |

---

## Табель

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/timesheet` | любой аутентифицированный; права — `canView`/`canEdit` в `TimesheetService` | Табель сотрудника за месяц. Целевой сотрудник берётся из `userId`, при отсутствии — сам актор или первый доступный. Нет прав на просмотр — `AccessDeniedException` (403). `EMPLOYEE` получает режим только для чтения (BR-027) | шаблон `timesheet/index` |
| POST | `/timesheet/entry` | любой аутентифицированный; `requireCanEdit` в `TimesheetService` | Внести или обновить запись за день (часы, удалёнка, больничный, отпуск, переработки). Отказ по правам или блокировке периода уходит во flash-атрибут, а не в 403 | redirect `/timesheet?userId=…&month=…` |
| POST | `/timesheet/entry/delete` | любой аутентифицированный; `requireCanEdit` в `TimesheetService` | Удалить запись за день | redirect `/timesheet?userId=…&month=…` |
| POST | `/timesheet/close` | любой аутентифицированный; проверка в `TimesheetService` | Закрыть месяц (BR-028: после закрытия правка запрещена). Без `managerId` закрывается команда актора. Закрывать может только руководитель команды, HR или ADMIN | redirect `/timesheet/report?month=…` |
| GET | `/timesheet/report` | любой аутентифицированный; выбор охвата в контроллере | Отчёт за месяц: по команде либо, при `scope=company`, сводный по компании. Сводный охват доступен только ADMIN и HR | шаблон `timesheet/report` |

`/timesheet/**` открыт в `SecurityConfig` для любого аутентифицированного;
конкретные права (кто чей табель ведёт и видит) определяются в
`TimesheetService.canEdit` / `canView`.
`/timesheet/entry` и `/timesheet/entry/delete` перехватывают
`AccessDeniedException` и возвращают flash-сообщение об ошибке вместо кода 403.

---

## Служебные записки

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| GET | `/memos` | любой аутентифицированный | Список записок, видимых пользователю, плюс очереди на визирование (PM) и утверждение (HR) | шаблон `memos/list` |
| GET | `/memos/new` | любой аутентифицированный | Форма подачи записки: типы записок, текущая дата | шаблон `memos/form` |
| POST | `/memos/new` | любой аутентифицированный | Подача записки (BR-029), направляется на визу руководителю | redirect `/memos/{id}` или `/memos/new` (ошибка) |
| GET | `/memos/{id}` | любой аутентифицированный; видимость проверяется в контроллере | Просмотр записки. Доступен автору, а также ADMIN, HR, EXEC и руководителю автора; остальным — `AccessDeniedException` (403) | шаблон `memos/view` |
| GET | `/memos/{id}/edit` | любой аутентифицированный | Форма редактирования записки | шаблон `memos/edit` |
| POST | `/memos/{id}/edit` | автор (проверка `requireAuthor` в `ServiceMemoService`) | Обновление записки. Только автор и только пока записка редактируема | redirect `/memos/{id}` |
| POST | `/memos/{id}/cancel` | автор (проверка `requireAuthor` в `ServiceMemoService`) | Отзыв записки | redirect `/memos/{id}` |
| POST | `/memos/{id}/approve-pm` | руководитель автора или ADMIN (`requirePmOfAuthor` в `ServiceMemoService`) | Виза руководителя, записка уходит в HR | redirect `/memos/{id}` |
| POST | `/memos/{id}/approve-hr` | HR или ADMIN (`requireHr` в `ServiceMemoService`) | Утверждение записки; дни автоматически проставляются в табеле | redirect `/memos/{id}` |
| POST | `/memos/{id}/reject` | руководитель автора, HR или ADMIN (проверки в `ServiceMemoService`) | Отклонение записки с указанием причины | redirect `/memos/{id}` |

`/memos/**` открыт для любого аутентифицированного; вся детальная логика
(автор, визирование, утверждение) лежит в `ServiceMemoService`.

---

## REST API (`/api/**`)

| Метод | Путь | Роли | Описание | Ответ (шаблон / redirect / JSON) |
|-------|------|------|----------|-----------------------------------|
| POST | `/api/tasks/new` | ADMIN, PM (`@PreAuthorize` в методе) | Создание задачи в проекте. Тело — `TaskCreateRequest` (`@Valid`). Дополнительно `requireCanCreateTaskIn` в `TaskService` | JSON — объект `Task` |
| POST | `/api/tasks/{id}/status` | любой аутентифицированный (`@PreAuthorize`); data-scope в `TeamAccessService` | Смена статуса задачи. Тело — `TaskStatusUpdateRequest` (`@Valid`). `EMPLOYEE` — только свои задачи, `PM` — свои проекты или своя команда, `ADMIN` — любые | JSON — объект `Task` |
| GET | `/api/dataset/training` | любой аутентифицированный | Скачивание обучающего датасета `dataset_training.csv` | файл (`text/plain`, `Content-Disposition: attachment`) либо `404`, если файла нет |
| GET | `/api/dataset/test` | любой аутентифицированный | Скачивание тестового датасета `dataset_test.csv` | файл (`text/plain`, `Content-Disposition: attachment`) либо `404`, если файла нет |

`/api/**` закрыт правилом `hasAnyRole`-нет: доступ требует лишь аутентификации
(`requestMatchers("/api/**").authenticated()`). Для REST-клиентов вместо
редиректа на форму логина настроен `HttpStatusEntryPoint` — неаутентифицированный
запрос получает `401 Unauthorized`.

Эндпоинтов `GET /api/tasks` и `PUT /api/tasks/{id}/status` в коде нет,
хотя они упомянуты в [Справочнике API](overview.md).

---

## Особенности доступа

### Data-scope PM: «только своя команда»

Правило принадлежности: сотрудник считается подчинённым менеджера, если
`employee.manager.id == manager.id` (поле `users.manager_id`). Реализация —
`isEmployeeInTeamOf` в `TeamAccessService`. Сужение действует в следующих местах:

| Где | Что ограничено | Проверка |
|-----|----------------|----------|
| `GET /employees` | Список и доступные отделы | `EmployeeService.findTeamOf` / `getDepartmentsOfTeam` в контроллере |
| `GET /employees/export/csv` | Экспорт реестра | `CsvExportService` → `canViewEmployee` |
| `GET /employees/{id}` | Карточка сотрудника | `TeamAccessService.requireCanViewEmployee` |
| `GET /risk` | Список рисков | `findByManagerAndActiveAndRolePaged` в контроллере |
| `GET /risk/{userId}` | Детали риска | `TeamAccessService.requireCanViewEmployee` |
| `GET /risk/export/csv` | Экспорт рисков | `CsvExportService` → `canViewEmployee` |
| `GET /oneonone` | Список встреч: только где PM — менеджер | `OneOnOneService.findAllForManager` в контроллере |
| `GET /oneonone/new` | Список доступных сотрудников | `TeamAccessService.canScheduleMeetingWith` |
| `POST /oneonone/new`, `/{id}/complete`, `/{id}/cancel` | Чужие встречи | `TeamAccessService.canManageMeeting` |
| `GET /timesheet`, `/timesheet/report` | Круг видимых сотрудников | `TimesheetService.canView` / `getViewableEmployees` / `getEditableEmployees` |
| `POST /timesheet/entry`, `/entry/delete`, `/close` | Ведение чужого табеля | `TimesheetService.canEdit` → `requireCanEdit` |
| `GET /memos`, `GET /memos/{id}` | Видимость записок и очередь визирования | `ServiceMemoService.getVisibleMemos`, `getPendingForPm`, проверка в контроллере |
| `POST /memos/{id}/approve-pm` | Виза только руководителем автора | `ServiceMemoService.requirePmOfAuthor` |
| `POST /api/tasks/{id}/status` | Чужая задача | `TeamAccessService.requireCanModifyTask` через `TaskService` |

У `TeamAccessService.canViewEmployee` приоритет такой: сначала проверяется
совпадение `id` (пользователь всегда видит себя), затем роль — `ADMIN`, `HR`,
`EXEC` видят всех, `PM` — только свою команду, `EMPLOYEE` — только себя.

### JSON вместо HTML и обработка ошибок

JSON отдают только маршруты под `/api/**`:

- `POST /api/tasks/new`, `POST /api/tasks/{id}/status` — объект `Task` через `@RestController`;
- `GET /api/dataset/training`, `GET /api/dataset/test` — файлы, а не JSON.

Ошибки обрабатывает `config/GlobalControllerAdvice.java`. Признак REST-запроса —
путь начинается с `/api/` **или** заголовок `Accept` содержит `application/json`.
Для REST-запроса возвращается JSON-тело с полями `timestamp`, `status`, `error`,
`message`, `path`; для остальных — HTML-шаблон `error` с атрибутами `status`,
`errorTitle`, `errorMessage`.

| Исключение | HTTP | Сообщение (`message`) |
|------------|------|------------------------|
| `ResourceNotFoundException`, `NoResourceFoundException` | 404 | «Ресурс не найден» |
| `AccessDeniedException` | 403 | текст исключения или «Доступ запрещён» |
| `MethodArgumentNotValidException` | 400 | список ошибок полей: `поле: сообщение` |
| `HttpMessageNotReadableException` | 400 | «Некорректный формат тела запроса.» |
| `IllegalArgumentException` | 400 | текст исключения |
| `DateTimeParseException` | 400 | «Некорректный формат даты или времени.» |
| `DataIntegrityViolationException` | 409 | «Данные конфликтуют с текущим состоянием базы…» |
| `Exception` | 500 | «Внутренняя ошибка сервера» |

Advice также добавляет в модель всех шаблонов атрибут `currentUser` (или `null`
для анонима) и `requestURI`: это нужно, чтобы страница ошибки, которую рендерят
фильтры или контейнер без контроллера, не падала на `currentUser.role.name()`.

### CSRF

CSRF включён для всех путей, включая `/api/**` (ранее стояло
`ignoringRequestMatchers("/api/**")`, из-за чего POST-мутации задач были уязвимы).
Хранилище токена — `CookieCsrfTokenRepository.withHttpOnlyFalse()`, то есть токен
кладётся в cookie, доступную из JavaScript.

Ожидаемый заголовок для AJAX задаётся Spring Security и равен `X-CSRF-TOKEN`
(`CsrfToken.getHeaderName()`). Работает это так:

1. `templates/fragments/layout.html` кладёт в `<head>` два meta-тега:
   `_csrf` (значение токена) и `_csrf_header` (имя заголовка);
2. `templates/projects/board.html` — единственный AJAX-вызов, канбан-доска —
   читает эти meta-теги и отправляет токен в заголовке
   `headers[csrfHeaderName()] = csrfToken()`;
3. обычные HTML-формы передают скрытое поле `_csrf`
   (`th:name="${_csrf.parameterName}"`), например в `templates/admin/users.html`.

Запрос на мутацию без токена отклоняется Spring Security до входа в контроллер.

---

## Расхождения

### 1. `EXEC` в сервисном слое vs `SecurityConfig` и `@PreAuthorize`

`TeamAccessService.canViewEmployee` разрешает `EXEC` видеть любого сотрудника,
а `TimesheetService.canView` / `getViewableEmployees` — видеть любой табель.
При этом:

- `SecurityConfig` для `/employees/**` даёт `hasAnyRole("ADMIN","HR","PM")` — `EXEC` не входит;
- на `EmployeeController` висит `@PreAuthorize("hasAnyRole('ADMIN','HR','PM')")`;
- `/timesheet/**` открыт любому аутентифицированному, но `TimesheetController`
  через `canView` пускает `EXEC` к любому табелю, а `canEdit` — нет.

То есть ветка `EXEC` в `canViewEmployee` недостижима через HTTP: `EXEC`
до неё не доходит и получает 403. Расхождение проявится, если правила
`SecurityConfig` ослабят.

### 2. Назначение встречи «с самим собой» и сотрудника без руководителя

`OneOnOneService.create` вызывает `canScheduleMeetingWith`, которая возвращает
`false`, если `actor.id == employee.id` — ни для `ADMIN`, ни для `HR` это условие
не обходится другой веткой. Попытка назначить встречу самому себе завершается
`AccessDeniedException` (403), а не понятной ошибкой валидации в форме.

По той же причине `PM` не может назначить встречу сотруднику без руководителя:
`isEmployeeInTeamOf` требует непустой `employee.manager`, поэтому такой сотрудник
не попадает в список на `/oneonone/new` и недоступен для `POST /oneonone/new`.

### 3. `/onboarding/**` открыт шире, чем подсказывает домен

`SecurityConfig` пускает любого аутентифицированного на `/onboarding/**`,
и на `OnboardingController` нет ни одной аннотации `@PreAuthorize`. В частности,
`POST /onboarding/checklist/{articleId}/toggle` доступен любому сотруднику.
Для чек-листа это ожидаемо (персональный прогресс), но действие — мутация,
и оно не отделено от административных прав.

### 4. Разделение чтения и записи в табеле и записках

`/timesheet/**` и `/memos/**` открыты любому аутентифицированному, а ролевые
ограничения живут в сервисах. Из-за этого на уровне URL нет видимой разницы
между чтением и записью, а форма ответа на отказ по правам зависит от того,
куда ведёт редирект:

- `GET /timesheet` выбрасывает `AccessDeniedException` → 403;
- `POST /timesheet/entry`, `/entry/delete` перехватывают исключение и кладут
  текст во flash-атрибут `error`; редирект ведёт на `/timesheet?userId=…`,
  который актор имеет право открыть, поэтому сообщение действительно отображается;
- `POST /memos/{id}/edit`, `/cancel`, `/approve-pm`, `/approve-hr`, `/reject`
  тоже перехватывают исключение и кладут текст во flash-атрибут `error`
  (`info` при отзыве/отклонении), но редирект ведёт на `/memos/{id}` —
  страницу, которая проверяет видимость записки. Если актор не автор,
  не ADMIN/HR/EXEC и не руководитель автора, `GET /memos/{id}` завершается
  `AccessDeniedException` → 403, и flash-сообщение до пользователя не доходит.

Это не нарушение доступа — мутации по-прежнему запрещены, — но расхождение
в том, как отказ выглядит для клиента: сообщение об ошибке вместо кода 403.

### 5. `GET /memos/{id}/edit` без проверки видимости

`GET /memos/{id}` проверяет авторство и привилегии (автор, ADMIN, HR, EXEC,
руководитель автора), а `GET /memos/{id}/edit` открывает форму любому
аутентифицированному. Мутации закрыты `requireAuthor`, поэтому запись
недоступна, но чтение формы чужой записки возможно.

### 6. Ветка `EMPLOYEE` в `EmployeeController.viewProfile`

`@PreAuthorize("hasAnyRole('ADMIN','HR','PM')")` на классе не пускает `EMPLOYEE`
в `/employees/{id}`, поэтому вычисление `canViewRisk = currentUser.getRole() != Role.EMPLOYEE`
всегда истинно, а `TeamAccessService.canViewEmployee` для `EMPLOYEE` недостижим.

### 7. Устаревший `api/overview.md`

`docs/api/overview.md` описывает `POST /api/tasks` и `PUT /api/tasks/{id}/status`.
В коде это `POST /api/tasks/new` и `POST /api/tasks/{id}/status`; маршрута
`GET /api/tasks` не существует. `docs/api/web-routes.md` не добавлен в `nav:`
в `mkdocs.yml`.

---

## Связанные файлы

### Контроллеры

- `demo/src/main/java/com/staffsense/demo/controller/AuthController.java`
- `demo/src/main/java/com/staffsense/demo/controller/DashboardController.java`
- `demo/src/main/java/com/staffsense/demo/controller/EmployeeController.java`
- `demo/src/main/java/com/staffsense/demo/controller/RiskController.java`
- `demo/src/main/java/com/staffsense/demo/controller/ProjectController.java`
- `demo/src/main/java/com/staffsense/demo/controller/task/TaskController.java`
- `demo/src/main/java/com/staffsense/demo/controller/task/ProjectBoardController.java`
- `demo/src/main/java/com/staffsense/demo/controller/SurveyController.java`
- `demo/src/main/java/com/staffsense/demo/controller/OneOnOneController.java`
- `demo/src/main/java/com/staffsense/demo/controller/AnalyticsController.java`
- `demo/src/main/java/com/staffsense/demo/controller/AdminController.java`
- `demo/src/main/java/com/staffsense/demo/controller/DatasetController.java`
- `demo/src/main/java/com/staffsense/demo/controller/hr/HrHireController.java`
- `demo/src/main/java/com/staffsense/demo/controller/OnboardingController.java`
- `demo/src/main/java/com/staffsense/demo/controller/OnboardingManageController.java`
- `demo/src/main/java/com/staffsense/demo/controller/DirectoryController.java`
- `demo/src/main/java/com/staffsense/demo/controller/TimesheetController.java`
- `demo/src/main/java/com/staffsense/demo/controller/ServiceMemoController.java`

### Конфигурация и безопасность

- `demo/src/main/java/com/staffsense/demo/config/SecurityConfig.java`
- `demo/src/main/java/com/staffsense/demo/config/GlobalControllerAdvice.java`
- `demo/src/main/java/com/staffsense/demo/config/RateLimitingFilter.java`

### Сервисы с проверками прав

- `demo/src/main/java/com/staffsense/demo/service/TeamAccessService.java`
- `demo/src/main/java/com/staffsense/demo/service/TimesheetService.java`
- `demo/src/main/java/com/staffsense/demo/service/ServiceMemoService.java`
- `demo/src/main/java/com/staffsense/demo/service/OneOnOneService.java`
- `demo/src/main/java/com/staffsense/demo/service/task/TaskService.java`
- `demo/src/main/java/com/staffsense/demo/service/CsvExportService.java`

### Шаблоны

- `demo/src/main/resources/templates/fragments/layout.html`
- `demo/src/main/resources/templates/projects/board.html`
- `demo/src/main/resources/templates/error.html`
- остальные шаблоны — в подкаталогах `auth/`, `dashboard/`, `employees/`, `risk/`,
  `projects/`, `surveys/`, `oneonone/`, `analytics/`, `admin/`, `hr/`,
  `onboarding/`, `directory/`, `timesheet/`, `memos/`

### Документация

- [Справочник API](overview.md)
- [Архитектура](../architecture.md)
