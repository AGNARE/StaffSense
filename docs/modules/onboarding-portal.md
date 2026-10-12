# Портал онбординга (модуль B)

## Назначение

Модуль B — **внутренний портал онбординга**: справочник компании (ценности, структура, должностные
инструкции, регламенты, FAQ, чек-лист новичка) и справочник сотрудников; содержимое редактируют
HR и ADMIN, читают все сотрудники.

Термин «онбординг» здесь означает именно внутренний портал — это **не** опрос адаптации (`SurveyType.ONBOARDING`
из модуля опросов, см. [Опросы](../api/surveys.md)) и **не** портал кандидата: инициатор приёма — HR.

## Требования

| ID | Название | Приоритет |
|---|---|---|
| FR-042 | Внутренний портал онбординга: разделы и статьи | SHOULD |
| FR-043 | Справочник сотрудников с ограниченным набором полей | SHOULD |
| FR-044 | Чек-лист новичка с персональными отметками | COULD |

Разделы портала заданы enum `OnboardingSection`: `ABOUT` (о компании), `STRUCTURE` (структура), `RESPONSIBILITIES`
(должностные инструкции), `POLICIES` (регламенты), `FAQ` (частые вопросы), `CHECKLIST` (чек-лист новичка).

## Доступ

Правила — в `config/SecurityConfig.java` (порядок важен: `/onboarding/manage/**` объявлен раньше
`/onboarding/**`):

| Функция | Маршрут | EMPLOYEE | PM | HR | EXEC | ADMIN |
|---|---|---|---|---|---|---|
| Чтение портала | `/onboarding/**` | ✓ | ✓ | ✓ | ✓ | ✓ |
| Управление статьями | `/onboarding/manage/**` | — | — | ✓ | — | ✓ |
| Справочник сотрудников | `/directory` | ✓ | ✓ | ✓ | ✓ | ✓ |

Чтение требует только аутентификации (иначе редирект на `/login`); управление дополнительно
защищено `@PreAuthorize("hasAnyRole('ADMIN','HR')")` на классе `OnboardingManageController`.

## Маршруты и экраны

| Метод | Маршрут | Обработчик | Шаблон |
|---|---|---|---|
| GET | `/onboarding` | `OnboardingController.index` | `onboarding/index.html` |
| GET | `/onboarding/section/{section}` | `OnboardingController.section` | `onboarding/section.html` |
| GET | `/onboarding/article/{id}` | `OnboardingController.article` | `onboarding/article.html` |
| GET | `/onboarding/checklist` | `OnboardingController.checklist` | `onboarding/checklist.html` |
| POST | `/onboarding/checklist/{articleId}/toggle` | `OnboardingController.toggleChecklist` | редирект на чек-лист |
| GET | `/onboarding/manage` | `OnboardingManageController.list` | `onboarding/manage.html` |
| GET, POST | `/onboarding/manage/new` | `createForm`, `create` | `onboarding/form.html` |
| GET, POST | `/onboarding/manage/{id}/edit` | `editForm`, `update` | `onboarding/form.html` |
| POST | `/onboarding/manage/{id}/delete` | `OnboardingManageController.delete` | редирект на список |
| GET | `/directory?department=&q=` | `DirectoryController.directory` | `directory/index.html` |

Главная портала отдаёт сводку по шести разделам с числом опубликованных статей, последние 6
опубликованных статей и текущий прогресс чек-листа. Экран управления показывает **все** статьи,
включая черновики. Отдельного действия «опубликовать/снять с публикации» нет: флаг `published`
переключается чекбоксом в форме создания/редактирования.

## Чек-лист новичка

* Пункты — опубликованные статьи раздела `CHECKLIST` в порядке `order_index`.
* Прогресс персональный, хранится в `onboarding_checklist_progress`; отметка уникальна по паре
  `(user_id, article_id)` (UNIQUE `uq_onboarding_progress_user_article`).
* `OnboardingService.toggleChecklistItem` при существующей отметке удаляет строку прогресса, иначе
  создаёт её с `completed_at = now()`; отметка возможна только для статей `CHECKLIST`, иначе
  `IllegalArgumentException`.
* Процент (`ChecklistProgress.of`): `total` — число опубликованных статей `CHECKLIST`, `completed` —
  число строк прогресса пользователя, `percent = round(completed * 100.0 / total)`, при `total = 0` — 0.

## Модель данных

Миграция `demo/src/main/resources/db/migration/V5__onboarding_portal.sql`.

### `onboarding_article`

`id` (bigint, PK, identity `onboarding_article_id_seq`), `section` (varchar(30), NOT NULL, enum как
`EnumType.STRING`), `title` (varchar(200), NOT NULL, `@NotBlank`, `@Size(max = 200)`), `content`
(text, nullable), `order_index` (integer, NOT NULL DEFAULT 0), `published` (boolean, NOT NULL DEFAULT
true — черновик не виден сотрудникам), `updated_by_id` (bigint, FK `fk_onboarding_article_updated_by`
→ `users(id)`), `updated_at` (timestamp(6), `@PrePersist` и `@PreUpdate`). Индекс
`idx_onboarding_article_section (section, order_index)`; транзиентный геттер `getSummary()` отдаёт
анонс до 160 символов.

### `onboarding_checklist_progress`

`id` (bigint, PK, identity `onboarding_checklist_progress_id_seq`), `user_id` (bigint, NOT NULL,
FK `fk_onboarding_progress_user` → `users(id)`), `article_id` (bigint, NOT NULL, FK
`fk_onboarding_progress_article` → `onboarding_article(id)`), `completed_at` (timestamp(6), NOT NULL,
`@PrePersist`). Ограничения: UNIQUE `uq_onboarding_progress_user_article (user_id, article_id)`,
индекс `idx_onboarding_progress_user (user_id)`. Каскадного удаления в схеме нет:
`OnboardingService.delete` сначала удаляет отметки (`deleteByArticleId`), затем статью.

## Демо-контент

`config/OnboardingContentInitializer.java` — `CommandLineRunner` с `@Order(20)`. Срабатывает, только если таблица
`onboarding_article` пуста (`count() == 0`), иначе пишет в лог `Onboarding portal already has content, skipping
initialization.`. Создаёт 20 статей с `published = true`: `ABOUT` — 3, `STRUCTURE` — 1, `RESPONSIBILITIES` — 3,
`POLICIES` — 4, `FAQ` — 3, `CHECKLIST` — 6.

## Справочник сотрудников (BR-031)

`DirectoryController` передаёт в шаблон `DirectoryEntryDto` только неконфиденциальные поля: ФИО
(`User.fullName`), должность (`User.position`), отдел (`User.department`), рабочий email
(`User.email`) и руководитель (`User.manager.fullName`).

Не показываются: оклад (`User.salary`), KPI (`KpiRecord`) и индекс нагрузки (`WorkloadRecord`), прогноз риска
увольнения (`AttritionPrediction.riskScore`), паспортные данные, ИНН и ПИН (`EmployeeDocuments`). Причина —
справочник открыт **всем** аутентифицированным сотрудникам; `/employees` для этого не используется (он ограничен
ролями ADMIN/HR/PM и показывает KPI, нагрузку и риск). Список строится по активным сотрудникам
(`findByActive(true)`), фильтруется по отделу и подстроке `q` (ФИО, должность, email) и сортируется по ФИО.

## Тесты

`demo/src/test/java/com/staffsense/demo/service/OnboardingServiceTest.java` (`@SpringBootTest`,
профиль `test`, `@Transactional`) покрывает 9 сценариев: сводка содержит все разделы с непустыми
названиями; в `CHECKLIST` есть демо-пункты; черновик скрыт при `includeDrafts = false` и виден
при `true`; отметка создаётся и снимается повторным вызовом; `completed` и `percent` растут,
`percent <= 100`; `ChecklistItem` отдаёт `completed` и `completedAt`; статья не из `CHECKLIST`
вызывает `IllegalArgumentException`; удаление статьи удаляет связанные отметки; перенос отмеченного
пункта в другой раздел убирает его из прогресса.

## Ограничения

Не реализовано: полнотекстовый поиск по порталу (поиск по подстроке есть только в `/directory`);
версионирование и история правок статей (перезаписываются `updated_at` и `updated_by`); вложения
и форматирование (`content` — простой текст, выводится как есть с сохранением переносов);
комментарии и учёт прочтения; отдельный экшен «опубликовать/снять с публикации» (только чекбокс
`published` в форме); пагинация списка статей; REST API портала (только Thymeleaf-экраны);
каскадное удаление отметок на уровне БД (очистка в сервисе).

Особенность расчёта прогресса: `total` считается только по опубликованным статьям раздела
`CHECKLIST`, и `completed` — тоже только по актуальным пунктам чек-листа (см. ниже).

### Исправлено при приёмке

- **Расчёт прогресса:** `completed` теперь берётся по актуальным пунктам чек-листа
  (`countByUserIdAndArticleSectionAndArticlePublishedTrue`), поэтому перенос отмеченной статьи
  в другой раздел больше не завышает `percent` — раньше он мог превысить 100 %.

## Дополнение к ТЗ

Модуль B описан в дополнении к ТЗ [StaffSense_TZ_ADDENDUM_V1_1.md](../tz/StaffSense_TZ_ADDENDUM_V1_1.md),
раздел 4 «Модуль B — внутренний портал онбординга» (пункты 4.1–4.5: FR-042…FR-044, разделы портала,
ограничение справочника BR-031, таблицы, экраны SCREEN-030…SCREEN-035).

## Связанные файлы

* `demo/src/main/java/com/staffsense/demo/service/OnboardingService.java`
* `demo/src/main/java/com/staffsense/demo/controller/{OnboardingController,OnboardingManageController,DirectoryController}.java`
* `demo/src/main/java/com/staffsense/demo/model/{OnboardingArticle,OnboardingChecklistProgress,OnboardingSection}.java`
* `demo/src/main/java/com/staffsense/demo/dto/onboarding/{ChecklistItem,ChecklistProgress,SectionSummary}.java`
* `demo/src/main/java/com/staffsense/demo/dto/directory/DirectoryEntryDto.java`
* `demo/src/main/java/com/staffsense/demo/repository/{OnboardingArticleRepository,OnboardingChecklistProgressRepository}.java`
* `demo/src/main/java/com/staffsense/demo/config/{OnboardingContentInitializer,SecurityConfig}.java`
* `demo/src/main/resources/db/migration/V5__onboarding_portal.sql`
* `demo/src/main/resources/templates/onboarding/{index,section,article,checklist,manage,form}.html`
* `demo/src/main/resources/templates/directory/index.html`
* `demo/src/test/java/com/staffsense/demo/service/OnboardingServiceTest.java`
