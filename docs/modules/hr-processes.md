# Кадровые процессы (модуль C)

## Назначение

Модуль C объединяет две кадровые функции StaffSense:

- **Учёт рабочего времени (табель)** — почасовой ввод по дням, отметки «удалённо», «больничный», «отпуск», переработки, закрытие месяца и отчёты по табелю;
- **Служебные записки** — заявки на отсутствие (отпуск, больничный, прочее) с двухэтапным согласованием: виза руководителя → утверждение HR.

Код: `service/TimesheetService.java`, `service/ServiceMemoService.java`; схема БД — `db/migration/V6__timesheet_and_memos.sql`.

## Требования

| ID | Требование | Реализация |
|---|---|---|
| FR-045 | Внесение часов руководителем по своей команде | `TimesheetService.saveEntry`, `canEdit` |
| FR-046 | Сотрудник видит свой табель только на чтение | `canEdit` для роли `EMPLOYEE` всегда `false`; `templates/timesheet/index.html` |
| FR-047 | Закрытие месяца и отчёты по табелю | `TimesheetService.closePeriod`, `buildReport` |
| FR-048 | Служебные записки и согласование | `ServiceMemoService`, `ServiceMemoController` |

## Бизнес-правила

| ID | Правило | Реализация |
|---|---|---|
| BR-027 | Табель ведёт руководитель по своей команде; HR и ADMIN правят любой табель; сотрудник свои часы не редактирует | `TimesheetService.canEdit` / `requireCanEdit` |
| BR-028 | Табель за закрытый период не редактируется | `TimesheetService.isDateLocked` в `saveEntry` / `deleteEntry` |
| BR-029 | Маршрут записки `PENDING_PM → PENDING_HR → APPROVED`, отклонение на любом этапе, автор правит и отзывает до финального решения | `ServiceMemoService.submit` / `approveByPm` / `approveByHr` / `reject` / `update` / `cancel` |
| BR-030 | Утверждённый больничный/отпуск проставляется в табеле и уменьшает доступную ёмкость нагрузки | `TimesheetService.applyApprovedMemo`, `WorkloadCalculationService.calculateAndSave` |

## Доступ

`config/SecurityConfig.java`: `/timesheet/**` и `/memos/**` — любой аутентифицированный пользователь, детальные права проверяются в сервисах. «Своя команда» — `employee.manager.id == actor.id` (`TeamAccessService.isEmployeeInTeamOf`).

| Действие | EMPLOYEE | PM | HR | EXEC | ADMIN |
|---|---|---|---|---|---|
| Ведение табеля (`canEdit`) | — | своя команда | все | — | все |
| Просмотр табеля (`canView`) | только свой | своя команда + свой | все | все | все |
| Закрытие периода | — | свою команду | любую | — | любую |
| Просмотр записок (`getVisibleMemos`) | только свои | свои + подчинённых | все | все | все |
| Виза PM / утверждение HR / отклонение | — | виза и отклонение своего подчинённого | утверждение и отклонение | — | виза, утверждение, отклонение |

## Маршруты

| Метод | Маршрут | Метод контроллера | Назначение |
|---|---|---|---|
| GET | `/timesheet` | `TimesheetController.timesheet` | Табель сотрудника за месяц (`userId`, `month`) |
| POST | `/timesheet/entry` | `TimesheetController.saveEntry` | Внести или обновить запись за день |
| POST | `/timesheet/entry/delete` | `TimesheetController.deleteEntry` | Удалить запись за день |
| POST | `/timesheet/close` | `TimesheetController.closePeriod` | Закрыть месяц команды руководителя |
| GET | `/timesheet/report` | `TimesheetController.report` | Отчёт: `scope=team` или `scope=company` |
| GET | `/memos` | `ServiceMemoController.list` | Список записок и очереди согласования |
| GET, POST | `/memos/new` | `ServiceMemoController.createForm` / `create` | Форма и подача записки |
| GET | `/memos/{id}` | `ServiceMemoController.view` | Карточка записки |
| GET, POST | `/memos/{id}/edit` | `ServiceMemoController.editForm` / `update` | Правка автором до финального решения |
| POST | `/memos/{id}/cancel` | `ServiceMemoController.cancel` | Отзыв записки автором |
| POST | `/memos/{id}/approve-pm` | `ServiceMemoController.approvePm` | Виза руководителя |
| POST | `/memos/{id}/approve-hr` | `ServiceMemoController.approveHr` | Утверждение HR |
| POST | `/memos/{id}/reject` | `ServiceMemoController.reject` | Отклонение (`rejectionReason`) |

Все маршруты — Thymeleaf-формы с POST и редиректом после обработки; REST API для модуля не предусмотрен.

## Табель

`work_schedule` — одна запись на сотрудника в день (`model/WorkSchedule.java`):

| Поле | Тип | Примечание |
|---|---|---|
| `id` | bigint | PK |
| `user_id` | bigint | FK `users.id`, обязательное |
| `work_date` | date | дата записи |
| `hours_worked` | double | отработанные часы, может быть `NULL` |
| `is_remote`, `is_sick_leave`, `is_vacation` | boolean | по умолчанию `false`; отпуск проставляется из утверждённых записок |
| `overtime_hours` | double | по умолчанию `0` |
| `created_by_id` | bigint | FK `users.id`: кто внёс запись (руководитель или HR/ADMIN) |
| `updated_at` | timestamp | обновляется в `@PrePersist` / `@PreUpdate` |

Ограничение `uq_work_schedule_user_date UNIQUE (user_id, work_date)`: одна запись в день, повторный `saveEntry` за ту же дату обновляет существующую строку.

`work_schedule_period` — закрытие месяца для команды руководителя (`model/WorkSchedulePeriod.java`):
`id`, `manager_id` (FK `users.id`), `period_start`, `period_end`, `status` (`OPEN` / `CLOSED` — `TimesheetPeriodStatus`), `closed_at`, `closed_by_id`;
ограничение `uq_work_schedule_period UNIQUE (manager_id, period_start, period_end)`, повторное закрытие обновляет ту же строку.

**Часы отсутствия:** `TimesheetService.ABSENCE_HOURS_PER_DAY = 8.0` — норма, вычитаемая из ёмкости за каждый день больничного или отпуска.

**Отчёты.** `TimesheetService.buildReport(employees, from, to, scope)` возвращает `TimesheetReport` (`periodStart`, `periodEnd`, `periodLabel`, `scope`, `closed`, `rows`); `TimesheetRow` — `totalHours`, `overtimeHours`, `remoteDays`, `sickDays`, `vacationDays`, `workingDays` и детализация по дням. Области отчёта:

- `team` (по умолчанию) — сотрудники из `getEditableEmployees`: команда PM, для HR/ADMIN — все активные `EMPLOYEE`;
- `company` — сводный по компании, только для ADMIN и HR (у остальных ролей `scope=company` игнорируется и превращается в `team`).

## Служебные записки

**Типы** (`MemoType`): `VACATION` («Отпуск»), `SICK_LEAVE` («Больничный»), `OTHER` («Прочее»).
**Статусы** (`MemoStatus`): `DRAFT` («Черновик»), `PENDING_PM` («На визе руководителя»), `PENDING_HR` («На утверждении HR»), `APPROVED` («Утверждена»), `REJECTED` («Отклонена»), `CANCELLED` («Отозвана автором»). Финальные (`isFinal`): `APPROVED`, `REJECTED`, `CANCELLED` — после них правка и отзыв невозможны.

Маршрут согласования:

1. `submit` — подаёт любой сотрудник, статус сразу `PENDING_PM`;
2. `approveByPm` — визирует ADMIN или PM автора (проверка своей команды), статус `PENDING_HR`, заполняются `pm_decision_by_id` / `pm_decision_at`;
3. `approveByHr` — утверждает только HR или ADMIN, статус `APPROVED`, заполняются `hr_decision_by_id` / `hr_decision_at`, затем вызывается `applyApprovedMemo` (BR-030);
4. `reject` — на `PENDING_PM` (PM автора или ADMIN) и `PENDING_HR` (HR или ADMIN): статус `REJECTED`, причина сохраняется в `rejection_reason`;
5. `update` / `cancel` — только автор и только пока `ServiceMemo.isEditable` истинно.

Поля `service_memo`: `id`, `user_id` (FK, автор), `memo_type`, `start_date`, `end_date`, `reason`, `status`, `pm_decision_by_id`, `pm_decision_at`, `hr_decision_by_id`, `hr_decision_at`, `rejection_reason`, `created_at`.
Вычисляемые: `getDaysCount()` — число календарных дней включительно; `isAffectsTimesheet()` — `true` только для `APPROVED` + `SICK_LEAVE` / `VACATION`.

## Ошибки

| ID | Ситуация | Фактический текст из кода |
|---|---|---|
| ERR-015 | Правка табеля за закрытый период | `saveEntry`: «Период за {M}.{YYYY} закрыт — правка табеля запрещена»; `deleteEntry`: «Период закрыт — правка табеля запрещена»; баннер UI: «Период «{месяц год}» закрыт — правка табеля запрещена (BR-028).» (`IllegalStateException`) |
| ERR-016 | Ведение табеля вне своей команды | «Табель ведёт руководитель по своей команде (сотрудник ID=N)» (`AccessDeniedException`) |

Прочие сообщения: «Закрывать период может руководитель команды, HR или ADMIN», «Нет доступа к табелю сотрудника ID=N», «Записка не находится на визе руководителя», «Записка не находится на утверждении HR», «Записка уже не может быть отклонена», «Записка уже получила решение — изменение невозможно», «Редактировать или отозвать записку может только её автор», «Визировать записку может руководитель автора», «Утверждать записку может только HR», «Укажите период записки», «Дата окончания не может быть раньше даты начала».

## Интеграция с расчётом нагрузки

1. `ServiceMemoService.approveByHr` после сохранения статуса `APPROVED` вызывает `TimesheetService.applyApprovedMemo(memo)`.
2. `applyApprovedMemo` идёт по календарным дням от `start_date` до `end_date` включительно и для каждого создаёт или обновляет запись табеля: `hours_worked = 0`, `overtime_hours = 0`, `is_sick_leave = true` для `SICK_LEAVE` либо `is_vacation = true` для `VACATION`, `created_by_id` — утвердивший HR (или автор, если решения HR нет). Для типа `OTHER` возвращается `0`, табель не меняется.
3. `WorkloadCalculationService.calculateAndSave` берёт `absenceDays` из `TimesheetService.countAbsenceDays` (дни с `is_sick_leave` или `is_vacation`) и уменьшает ёмкость:

   ```
   capacityHours = weeklyCapacityHours × weeks − absenceDays × 8.0
   workloadIndex = totalEstimatedHours / capacityHours   (0.0, если capacityHours ≤ 0)
   ```

   Отрицательная ёмкость обрезается до нуля, деление на ноль защищено. Утверждённый отпуск или больничный снижает доступную ёмкость и повышает индекс нагрузки за период.

## Тесты

| Тест | Тестов | Что покрывает |
|---|---|---|
| `TimesheetServiceTest` | 12 | PM ведёт табель своей команды и не ведёт чужую; `EMPLOYEE` не редактирует даже свой табель; HR/ADMIN правят любой; повторный `saveEntry` за день обновляет запись, а не дублирует; закрытый период блокирует правку; `EMPLOYEE` не закрывает период; агрегация отчёта (часы, переработки, удалённые дни, больничные, число дней); подсчёт дней отсутствия для нагрузки; закрытый период определяется и у сотрудника без руководителя; `isRangeLocked` покрывает весь диапазон; HR видит в списке ведения табеля не только роль `EMPLOYEE` |
| `ServiceMemoServiceTest` | 14 | подача записки любым сотрудником (`PENDING_PM`, `daysCount`); виза PM переводит в `PENDING_HR`; чужой PM не визирует; утверждение HR проставляет дни больничного/отпуска с нулевыми часами; `EMPLOYEE` не утверждает; отклонение на этапе PM с причиной; правка и отзыв до решения; запрет после решения; только автор; некорректный период; PM видит записки своей команды; утверждение блокируется при закрытом периоде (BR-028) и не меняет табель; `requireEditableBy` отклоняет чужую и уже решённую записку |

## Ограничения и известные расхождения

- **Графика отпусков нет** — записка фиксирует отсутствие, но не сверяется с планом отпусков и остатком дней.
- **Интеграции с 1С/бухгалтерией нет**; экспорт табеля в CSV/Excel не реализован (CSV-выгрузка есть только у справочника сотрудников, риска и опросов).
- **Частичных дней нет**: один набор флагов на день (`UNIQUE (user_id, work_date)`), одно значение `hours_worked`, разбивки часов по проектам или задачам нет.
- **`DRAFT` не используется** — `submit` сразу создаёт `PENDING_PM`, переходов в `DRAFT` в коде нет (в ТЗ и дополнении статус упомянут как состояние маршрута).
- **Источник дней отсутствия не отделён**: `countAbsenceDays` считает флаги `is_sick_leave`/`is_vacation` в `work_schedule`, поэтому день, помеченный вручную руководителем, тоже уменьшает ёмкость при расчёте нагрузки.
- **Тексты ERR-015 / ERR-016** в коде отличаются от формулировок дополнения к ТЗ: `saveEntry` добавляет месяц и год («Период за 10.2026 закрыт…»), `requireCanEdit` добавляет ID сотрудника.
- **Отмена утверждённой записки не предусмотрена**, автоматического отката уже проставленных дней нет; уведомлений и вложений (сканов) также нет.

### Исправлено при приёмке

- **BR-028 при утверждении записки:** `approveByHr` проверяет `isRangeLocked` и отклоняет утверждение, если период закрыт, — раньше `applyApprovedMemo` переписывал табель закрытого месяца.
- **Период у сотрудника без руководителя:** `isDateLocked` считает владельцем периода самого сотрудника (PM, HR, ADMIN, EXEC), поэтому BR-028 для них теперь действует.
- **HR/ADMIN ведут табель любых сотрудников** (дополнение к ТЗ, раздел 6: «✓ все») — список больше не ограничен ролью `EMPLOYEE`.
- **`GET /memos/{id}/edit`** проверяет автора и `isEditable` (`ServiceMemoService.requireEditableBy`): чужая записка → 403, записка с решением → редирект с сообщением.

## Ссылка на дополнение к ТЗ

Модуль описан в [StaffSense_TZ_ADDENDUM_V1_1.md](../tz/StaffSense_TZ_ADDENDUM_V1_1.md), **раздел 5 «МОДУЛЬ C — КАДРОВЫЕ ПРОЦЕССЫ»** (FR-045…FR-048, BR-027…BR-030, ERR-015, ERR-016, экраны SCREEN-036…SCREEN-040).

## Связанные файлы

- Сервисы: `demo/src/main/java/com/staffsense/demo/service/TimesheetService.java`, `demo/src/main/java/com/staffsense/demo/service/ServiceMemoService.java`, `demo/src/main/java/com/staffsense/demo/service/TeamAccessService.java`, `demo/src/main/java/com/staffsense/demo/service/metrics/WorkloadCalculationService.java`
- Контроллеры: `demo/src/main/java/com/staffsense/demo/controller/TimesheetController.java`, `demo/src/main/java/com/staffsense/demo/controller/ServiceMemoController.java`
- Модели: `demo/src/main/java/com/staffsense/demo/model/WorkSchedule.java`, `demo/src/main/java/com/staffsense/demo/model/WorkSchedulePeriod.java`, `demo/src/main/java/com/staffsense/demo/model/TimesheetPeriodStatus.java`, `demo/src/main/java/com/staffsense/demo/model/ServiceMemo.java`, `demo/src/main/java/com/staffsense/demo/model/MemoType.java`, `demo/src/main/java/com/staffsense/demo/model/MemoStatus.java`
- DTO: `demo/src/main/java/com/staffsense/demo/dto/timesheet/TimesheetReport.java`, `demo/src/main/java/com/staffsense/demo/dto/timesheet/TimesheetRow.java`
- Безопасность: `demo/src/main/java/com/staffsense/demo/config/SecurityConfig.java`
- Миграция БД: `demo/src/main/resources/db/migration/V6__timesheet_and_memos.sql`
- Шаблоны: `demo/src/main/resources/templates/timesheet/index.html`, `demo/src/main/resources/templates/timesheet/report.html`, `demo/src/main/resources/templates/memos/list.html`, `demo/src/main/resources/templates/memos/form.html`, `demo/src/main/resources/templates/memos/view.html`, `demo/src/main/resources/templates/memos/edit.html`
- Тесты: `demo/src/test/java/com/staffsense/demo/service/TimesheetServiceTest.java`, `demo/src/test/java/com/staffsense/demo/service/ServiceMemoServiceTest.java`
