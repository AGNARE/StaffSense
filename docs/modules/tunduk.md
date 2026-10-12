# Модуль «Тундук»

## Назначение

Модуль обеспечивает приём сотрудника на работу с верификацией гражданина
Кыргызской Республики по ИНН в госсистеме «Тундук / Кызмат» и автоматическим
заполнением личного дела. Инициатор процесса — **HR**, а не кандидат: сотрудник
отдела кадров вводит ИНН, подтверждает согласие на обработку персональных данных
и получает данные гражданина для оформления. Доступ к госсистеме реализован по
схеме «порт и адаптер»: бизнес-логика зависит только от интерфейса `TundukClient`,
поэтому переход на реальный протокол обмена данными потребует замены одной
реализации (`MockTundukClient`).

## Требования

| Требование | Реализация |
|-----------|------------|
| FR-040 / FR-041 — верификация гражданина по ИНН (в демо-версии — эмуляция) / создание личного дела | `HireEmployeeService.verify()` → `TundukClient.findByInn()`; `hire()` → `EmployeeDocuments` |
| BR-023 / BR-024 — реактивация при повторном приёме без дубля / обязательное согласие на обработку ПДн | Поиск по ИНН, при `active=false` — реактивация; `hire()` + поле `consent_given_at` |
| BR-025 / BR-026 — маскирование ПДн / аудит обращений к госсистеме | `HireEmployeeService.mask()`, `EmployeeDocuments.getMaskedInn()` / `getMaskedPassport()`; `writeLog()` → `tunduk_verification_log` |
| ERR-013 / ERR-014 — ИНН не 14 цифр / гражданин не найден или сервис недоступен | `InnValidator` **до** вызова клиента (`INVALID`) / статусы `NOT_FOUND`, `UNAVAILABLE` и ручной ввод |

## Доступ

Маршруты доступны только ролям **ADMIN** и **HR** (`SecurityConfig`:
`.requestMatchers("/hr/**").hasAnyRole("ADMIN", "HR")` и
`@PreAuthorize("hasAnyRole('ADMIN','HR')")` на классе `HrHireController`).

| Маршрут | Метод | Назначение | ADMIN | HR | EXEC | PM | EMPLOYEE |
|---------|-------|------------|:-----:|:--:|:----:|:--:|:--------:|
| `/hr/hire` | GET | Форма приёма сотрудника | ✔ | ✔ | — | — | — |
| `/hr/hire/verify` | POST | Запрос данных гражданина по ИНН | ✔ | ✔ | — | — | — |
| `/hr/hire` | POST | Сохранение сотрудника и личного дела | ✔ | ✔ | — | — | — |
| `/hr/documents/{userId}` | GET | Просмотр личного дела | ✔ | ✔ | — | — | — |

## Состав данных

| Приходит из «Тундук / Кызмат» | Заполняет HR |
|-------------------------------|--------------|
| `inn` — 14 цифр | `email` — обязателен, уникален; `role` — обязательно (из `Role`) |
| `pin` — до 20 символов | `department`, `position` — обязательны |
| `fullName` — ФИО; `birthDate` — дата рождения | `gradeLevel` — обязательно (из `GradeLevel`) |
| `gender` — `MALE` / `FEMALE` | `salary` — обязательно, больше нуля |
| `passportSeries` — до 4 символов | `weeklyCapacityHours` — по умолчанию 40 ч/нед |
| `passportNumber` — до 6 символов | `hireDate` — обязательно |
| `passportIssuedBy` — до 255 символов | `managerId` — выбор из активных PM / HR / ADMIN |
| `passportIssueDate` — дата выдачи | `password` — при пустом значении генерируется `Ss-NNNNNN` |
| `registrationAddress` — до 500 символов | `consentGiven` — без согласия приём отклоняется (BR-024) |
| `photoUrl` — ссылка на фото (заглушка) | — |

## Модель данных

### `employee_documents` (личное дело, 1:1 с `users`)

| Поле | Тип | Ключи и ограничения |
|------|-----|---------------------|
| `id` | bigint | PK, identity |
| `user_id` | bigint | FK → `users(id)`, NOT NULL, UNIQUE (`uq_employee_documents_user`) |
| `inn` | varchar(14) | NOT NULL, UNIQUE (`uq_employee_documents_inn`) |
| `pin` / `birth_date` / `gender` | varchar(20) / date / varchar(10) | — |
| `passport_series` / `passport_number` | varchar(4) / varchar(6) | — |
| `passport_issued_by` / `passport_issue_date` | varchar(255) / date | — |
| `registration_address` / `photo_url` | varchar(500) / varchar(500) | — |
| `tunduk_verified` | boolean | NOT NULL, DEFAULT false |
| `verified_at` | timestamp | Момент подтверждения данных госсистемой |
| `consent_given_at` | timestamp | BR-024 |
| `updated_at` | timestamp | `@PrePersist` / `@PreUpdate` |

Индексы: `idx_employee_documents_user_id`, `idx_employee_documents_inn`.

### `tunduk_verification_log` (журнал обращений, BR-026)

| Поле | Тип | Ключи и ограничения |
|------|-----|---------------------|
| `id` | bigint | PK, identity |
| `inn` | varchar(14) | NOT NULL; при пустом ИНН пишется `—` |
| `requested_by_id` / `requested_at` | bigint / timestamp | FK → `users(id)` NOT NULL (HR/ADMIN) / NOT NULL |
| `result` | varchar(20) | NOT NULL: `FOUND` / `NOT_FOUND` / `INVALID` / `UNAVAILABLE` |
| `target_user_id` | bigint | FK → `users(id)`, если сотрудник уже есть в системе |
| `details` | text | Сообщение результата |

Индексы: `idx_tunduk_log_inn`, `idx_tunduk_log_requested_at`.

## Эмуляция

`MockTundukClient` — детерминированный справочник из 9 вымышленных граждан
(реальные персональные данные в репозиторий не попадают).

| ИНН | ФИО | ИНН | ФИО | ИНН | ФИО |
|-----|-----|-----|-----|-----|-----|
| `12345678901234` | Абдиев Нурлан Маратович | `12345678901235` | Асанова Гульмира Тилековна | `12345678901236` | Бекмуратов Азамат Эркинович |
| `12345678901237` | Джумагулова Айнура Бакытовна | `12345678901238` | Иванов Сергей Петрович | `12345678901239` | Кадырова Жамиля Асановна |
| `12345678901240` | Маматов Эрмек Абдулаевич | `12345678901241` | Осмонова Аида Муратовна | `12345678901242` | Токтогулов Данияр Болотович |

- `99999999999999` (`MockTundukClient.UNAVAILABLE_INN`) — недоступность сервиса, `UNAVAILABLE` (ERR-014); любой другой неизвестный ИНН — `NOT_FOUND` (ERR-014).
- ИНН не из 14 цифр — `INVALID` (ERR-013), обрабатывается до вызова клиента.
- Фото: `PHOTO_PLACEHOLDER = /img/tunduk-photo.svg` (файл `static/img/tunduk-photo.svg`); список ИНН подставляется в форму через `MockTundukClient.getDemoInns()`.

## Маскирование и аудит

Маскирование (BR-025): `HireEmployeeService.mask()` — `**********` + последние
4 цифры (для журнала приложения; при длине ИНН менее 4 цифр — `****`);
`EmployeeDocuments.getMaskedInn()` — `**********1234`;
`getMaskedPassport()` — `<серия> ****<2 последние цифры>`. Транзиентные геттеры
маскирования в шаблонах и сервисах не используются: экраны `/hr/**` доступны только
ADMIN и HR и показывают полные данные.

Аудит (BR-026): `HireEmployeeService.verify()` журналирует каждое обращение, включая
`INVALID`. Запись содержит ИНН, инициатора, время, результат, сотрудника (если найден)
и текст сообщения. Сбой журналирования не прерывает приём сотрудника — фиксируется
предупреждением в логе приложения. Просмотра журнала в UI нет:
`TundukVerificationLogRepository` используется только сервисом (запись) и тестами.

## Тесты

| Тест-класс | Что проверяет |
|------------|---------------|
| `InnValidatorTest`, `MockTundukClientTest` | Ровно 14 цифр, обрезка пробелов, отказ при 13/15 цифрах, нецифровых символах, `null` и пустой строке (Q-015: без контрольной суммы); три исхода `FOUND` / `NOT_FOUND` / `UNAVAILABLE`, обрезка ИНН, непустой справочник из 14-значных ИНН |
| `HireEmployeeServiceTest` | FR-040 и FR-041: данные по известному ИНН, запись в журнал (BR-026), `INVALID`/`NOT_FOUND`/`UNAVAILABLE`, личное дело с фиксацией согласия (BR-024), отказ без согласия и при некорректном ИНН, отсутствие дубля (BR-023), реактивация уволенного, занятый email |

## Ограничения

- Реальная интеграция с госсистемой «Тундук / Кызмат» **не реализована**: используется только `MockTundukClient`.
- Алгоритм контрольной суммы ИНН **не применяется** (Q-015): проверяется только формат — 14 цифр.
- Хранение фото в файловом хранилище **не реализовано**: сохраняется ссылка на статическую заглушку `/img/tunduk-photo.svg`.
- Просмотр журнала обращений в UI **не реализован** (только запись в БД).
- Помощники маскирования `EmployeeDocuments.getMaskedInn()` / `getMaskedPassport()` (BR-025)
  объявлены, но в шаблонах не используются: экраны `/hr/**` доступны только ADMIN и HR, поэтому
  ИНН и паспорт показываются полностью, а маскируется только запись в лог приложения.

### Исправлено при приёмке

- **BR-024:** `verify()` больше не запрашивает персональные данные без согласия —
  `HireEmployeeService.verify(inn, consentGiven, actor)` отклоняет запрос до обращения
  к госсистеме и не пополняет журнал (раньше согласие проверялось только в `hire()`).
- **Потеря поля «Пол»:** после верификации `select` отключён и не отправлялся формой,
  поэтому значение дублируется скрытым полем в `hr/hire.html`.

## Дополнение к ТЗ

Требования модуля детализированы в дополнении к ТЗ
[StaffSense_TZ_ADDENDUM_V1_1.md](../tz/StaffSense_TZ_ADDENDUM_V1_1.md) (раздел 3).
Концепция модуля — в отчёте
[StaffSense_CONCEPT_TUNDUK_ONBOARDING.md](../reports/StaffSense_CONCEPT_TUNDUK_ONBOARDING.md).

## Связанные файлы

- `demo/src/main/java/com/staffsense/demo/service/tunduk/TundukClient.java`, `MockTundukClient.java`, `InnValidator.java`
- `demo/src/main/java/com/staffsense/demo/service/tunduk/TundukPersonData.java`, `TundukLookupResult.java`, `TundukStatus.java`
- `demo/src/main/java/com/staffsense/demo/service/hr/HireEmployeeService.java`
- `demo/src/main/java/com/staffsense/demo/controller/hr/HrHireController.java`
- `demo/src/main/java/com/staffsense/demo/dto/hr/HireEmployeeForm.java`, `HireResult.java`
- `demo/src/main/java/com/staffsense/demo/model/EmployeeDocuments.java`, `TundukVerificationLog.java`, `TundukVerificationResult.java`
- `demo/src/main/java/com/staffsense/demo/repository/EmployeeDocumentsRepository.java`, `TundukVerificationLogRepository.java`
- `demo/src/main/java/com/staffsense/demo/config/SecurityConfig.java`
- `demo/src/main/resources/db/migration/V4__tunduk_and_employee_documents.sql`
- `demo/src/main/resources/templates/hr/hire.html`, `demo/src/main/resources/templates/hr/documents.html`
- `demo/src/main/resources/static/img/tunduk-photo.svg`
- `demo/src/test/java/com/staffsense/demo/service/hr/HireEmployeeServiceTest.java`
- `demo/src/test/java/com/staffsense/demo/service/tunduk/InnValidatorTest.java`, `MockTundukClientTest.java`
