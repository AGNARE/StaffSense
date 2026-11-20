# -*- coding: utf-8 -*-
"""
Генератор документа «Руководство программиста» для проекта StaffSense.

Оформление берётся из `_gost.py`; здесь только содержание.
Запуск: python _gen_prog.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import _gost as gost

OUT = Path(__file__).resolve().parent / "03_Руководство_программиста.docx"
IMG = Path(__file__).resolve().parent / "img"

# Плотность набора: абзацы отделяются двумя пунктами вместо шести —
# документ остаётся читаемым и укладывается в заданный объём.
_para = gost.para


def para(doc, text, **kwargs):
    kwargs.setdefault("space_after", 2)
    return _para(doc, text, **kwargs)


gost.para = para


def steps(doc, items):
    """Нумерованный перечень действий одинакового оформления."""
    for index, item in enumerate(items, start=1):
        gost.para(doc, f"{index}. {item}", indent=False, space_after=2)


def small_table(doc, header, rows, widths, caption, size=10.5):
    """Таблица справочного характера: кегль меньше основного."""
    gost.table(doc, header, rows, widths=widths, caption=caption,
               font_size=gost.Pt(size))


def build():
    doc = gost.new_document()

    gost.title_page(
        doc,
        university="Кыргызский государственный технический университет\nим. И. Раззакова",
        department="Кафедра «Программное обеспечение компьютерных систем»",
        kind="РУКОВОДСТВО ПРОГРАММИСТА",
        title="StaffSense",
        subtitle="HRM-платформа с прогнозированием риска увольнений.\nДокументация разработчика и сопровождения",
        author="Аргенбаев А.",
        group="ПИ-3-24",
        teacher="Беккулова",
        city="Бишкек",
        year="2026",
    )

    gost.toc(doc, [
        ("1. Назначение и область применения", 1),
        ("2. Требования к окружению", 1),
        ("3. Структура проекта", 1),
        ("4. Архитектура и контракт", 1),
        ("4.1. Поток запроса", 2),
        ("4.2. Уровни проверки доступа", 2),
        ("4.3. REST-контракт", 2),
        ("5. Сборка и запуск", 1),
        ("6. База данных", 1),
        ("6.1. Миграции", 2),
        ("6.2. Схема основных таблиц", 2),
        ("6.3. Проверка соответствия схемы", 2),
        ("7. Конфигурация", 1),
        ("8. Расширение системы", 1),
        ("9. Тестирование", 1),
        ("10. Типичные неисправности", 1),
        ("11. Сопровождение", 1),
        ("12. Приложение А. Команды", 1),
    ])

    # ── 1 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "1. НАЗНАЧЕНИЕ И ОБЛАСТЬ ПРИМЕНЕНИЯ")

    gost.para(doc, "Руководство описывает устройство и сопровождение StaffSense — HRM-платформы, "
                   "которая собирает рабочие сигналы сотрудников (нагрузку, KPI, встречи 1:1, "
                   "опросы, табель, кадровые события) и рассчитывает риск увольнения по модели "
                   "машинного обучения. Описаны только механизмы, которые есть в коде.")

    gost.para(doc, "Сопровождение сводится к шести повторяющимся задачам; каждая решается "
                   "штатными средствами проекта, без правки уже применённых миграций.")
    gost.table(
        doc,
        ["Задача", "Инструмент", "Раздел"],
        [
            ["Развернуть платформу на чистой машине", "Maven wrapper, Flyway, Node.js", "5, 6"],
            ["Изменить схему базы", "новая Flyway-миграция", "6.1"],
            ["Добавить раздел интерфейса", "миграция → сущность → сервис → API → клиент → RoleScreens", "8"],
            ["Выдать новый уровень доступа", "Role, SecurityConfig, RoleScreens", "11.1"],
            ["Изменить границы уровней риска", "RiskThresholdService, экран «Администрирование»", "11.2"],
            ["Разобрать инцидент по обращению", "таблица неисправностей, журналы", "10, 11.4"],
        ],
        widths=[6.4, 6.6, 3.2],
        caption="Таблица 1 — Задачи сопровождения и разделы руководства",
    )

    # ── 2 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "2. ТРЕБОВАНИЯ К ОКРУЖЕНИЮ")

    gost.para(doc, "Платформа состоит из трёх исполняемых частей: Java-приложения (Spring Boot), "
                   "клиента на React и ML-сервиса на Python. Клиент собирается в статический "
                   "бандл и раздаётся тем же Java-процессом, отдельного веб-сервера для него нет.")
    gost.table(
        doc,
        ["Компонент", "Версия", "Для чего нужен"],
        [
            ["JDK", "21 (LTS)", "компиляция и запуск приложения; в pom.xml java.version=21"],
            ["Maven", "3.9+ (wrapper mvnw)", "сборка, запуск, тесты; отдельная установка не нужна"],
            ["Node.js и npm", "20.x", "сборка клиента (Vite + TypeScript)"],
            ["PostgreSQL", "18 (минимум 16)", "рабочая база db_staff_sense на порту 5433"],
            ["Python", "3.12", "ML-сервис FastAPI (отдельный репозиторий StaffSenseML)"],
            ["Браузер", "Chromium, Firefox, Edge", "проверка интерфейса; окна от 900 px"],
        ],
        widths=[3.4, 3.8, 9.0],
        caption="Таблица 2 — Требования к программному окружению",
    )

    gost.para(doc, "Порты зафиксированы в конфигурации и не пересекаются. Если порт занят, "
                   "приложение не стартует — диагностика в разделе 10.")
    gost.table(
        doc,
        ["Что", "Адрес", "Назначение"],
        [
            ["Приложение", "http://localhost:9091", "server.port в application.yml"],
            ["Клиент", "http://localhost:9091/app/", "основной интерфейс после входа"],
            ["Swagger UI", "/swagger-ui/index.html", "интерактивная документация контракта"],
            ["OpenAPI JSON", "/v3/api-docs", "машинное описание контракта (springdoc 2.6.0)"],
            ["PostgreSQL", "localhost:5433 / db_staff_sense", "рабочая схема, владелец postgres"],
            ["ML-сервис", "http://localhost:8001", "FastAPI: /health, /api/v1/predict, /model-info"],
        ],
        widths=[3.4, 6.4, 6.4],
        caption="Таблица 3 — Порты и адреса по умолчанию",
    )

    gost.para(doc, "Внешние адреса и секреты задаются переменными окружения: SPRING_DATASOURCE_URL, "
                   "SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD (обязательна при "
                   "подключении к PostgreSQL), ML_SERVICE_URL, RISK_THRESHOLD_LOW/HIGH, "
                   "DOCUMENTS_SIGNATURE_SLA_HOURS. Пароль в репозитории не хранится: в "
                   "application.yml стоит password: ${SPRING_DATASOURCE_PASSWORD:}.")

    # ── 3 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "3. СТРУКТУРА ПРОЕКТА")

    gost.para(doc, "Репозиторий разделён на бэкенд, клиент и документацию; ML-сервис подключён "
                   "по HTTP и показан в дереве как соседний каталог.")
    gost.code(doc, r"""
StaffSense/
├── demo/                      Java-приложение (Maven, Spring Boot)
│   ├── mvnw, mvnw.cmd         Maven wrapper — своя версия сборщика
│   ├── pom.xml                зависимости, профиль frontend
│   └── src/main/
│       ├── java/com/staffsense/demo/
│       │   ├── api/           REST-контроллеры /api/v1/** (14 подпакетов)
│       │   ├── controller/    серверные страницы Thymeleaf
│       │   ├── service/       доменная логика и правила
│       │   ├── repository/    Spring Data JPA
│       │   ├── model/         сущности и перечисления
│       │   ├── config/        безопасность, пороги, планировщики
│       │   └── exception/     доменные исключения
│       └── resources/
│           ├── application.yml    конфигурация
│           ├── db/migration/      20 миграций Flyway (V1 … V20)
│           └── static/            собранный клиент в static/app
├── frontend/                  React 18 + TypeScript + Vite
│   ├── src/pages/             экраны клиента (37 экранов)
│   └── src/components/        Shell (меню, колокол), Modal
├── docs/deliverables/         документы поставки (этот файл)
└── ../StaffSenseML/           ML-сервис (FastAPI) — отдельный репозиторий
""")
    gost.para(doc, "Границы ответственности простые: правка сущности почти всегда требует миграции, "
                   "правка правила — теста, правка экрана — сборки клиента. Применённые миграции "
                   "лежат только в db/migration и не редактируются.")

    # ── 4 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "4. АРХИТЕКТУРА И КОНТРАКТ")

    gost.para(doc, "Платформа — модульный монолит: одно Java-приложение со слоями и односторонними "
                   "зависимостями api → service → repository → model. Клиент на React и серверные "
                   "страницы Thymeleaf вызывают одни и те же сервисы, поэтому правила не "
                   "расходятся. ML-сервис вынесен в отдельный процесс и вызывается по HTTP с "
                   "таймаутами 2 с на соединение и чтение.")

    gost.heading(doc, "4.1. Поток запроса", level=2)
    steps(doc, [
        "Браузер отправляет запрос на /api/v1/** с cookie сессии JSESSIONID; "
        "RateLimitingFilter проверяет только POST /login.",
        "FilterChain сверяет путь с правилами SecurityConfig и без сессии отдаёт 401 для "
        "/api/** вместо редиректа на форму входа.",
        "Контроллер проверяет второй уровень — @PreAuthorize на методе.",
        "Сервис выполняет правило домена и проверяет владельца через TeamAccessService; "
        "транзакцию открывает сервис, а не контроллер.",
        "Ответ сериализуется в JSON; ошибки домена превращаются в 404/409 с текстом правила.",
    ])

    gost.heading(doc, "4.2. Уровни проверки доступа", level=2)
    gost.para(doc, "Права описаны в четырёх местах, и каждое отвечает за своё. Меню строит сервер: "
                   "клиент рисует только то, что пришло в GET /api/v1/me → screens, поэтому "
                   "«пункта нет в меню» и «API отвечает 403» — две независимые проверки.")
    gost.table(
        doc,
        ["Уровень", "Механизм", "Файл", "Что проверяет"],
        [
            ["URL", "requestMatchers", "config/SecurityConfig.java", "грубые группы: /admin/**, /api/v1/admin/**"],
            ["Метод", "@PreAuthorize", "api/**/*ApiController.java", "роль на конкретной операции"],
            ["Строка данных", "TeamAccessService", "service/TeamAccessService.java",
             "своя команда, владелец записки, автор документа"],
            ["Навигация", "RoleScreens", "api/me/RoleScreens.java",
             "состав меню и доступ по прямой ссылке через REQUIRED_SCREEN"],
        ],
        widths=[2.6, 3.4, 5.6, 4.9],
        caption="Таблица 4 — Уровни проверки доступа",
    )

    gost.heading(doc, "4.3. REST-контракт", level=2)
    gost.para(doc, "Контракт версионирован префиксом /api/v1 и описан springdoc: документация — "
                   "по /swagger-ui/index.html, машинное описание — по /v3/api-docs. Мутирующие "
                   "запросы требуют CSRF-токен из GET /api/v1/csrf; имя заголовка приходит в том "
                   "же ответе. Списки отдаются страницами (page, size).")
    gost.figure(doc, str(IMG / "swagger.png"),
                "Рисунок 1 — Ресурсы REST-контракта в Swagger UI", width_cm=14.5)
    gost.code(doc, r"""
GET /api/v1/risk?page=0&size=15   →   200 OK
{ "content": [ { "userId": 4, "riskScore": 87.6, "riskLevel": "HIGH" } ],
  "totalElements": 14, "totalPages": 1, "number": 0 }
""")
    gost.para(doc, "Коды ответов одинаковы во всех доменах: 200 — успех, 400 — нарушена валидация, "
                   "401 — нет сессии, 403 — не подходят роль или владелец, 404 — объект не найден, "
                   "409 — нарушено правило перехода (например, повторная подпись).")

    # ── 5 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "5. СБОРКА И ЗАПУСК")

    gost.para(doc, "База создаётся один раз; схему внутри неё строит Flyway при первом старте "
                   "приложения. Кодировка — UTF-8, имя базы — db_staff_sense.")
    gost.code(doc, r"""
createdb -h localhost -p 5433 -U postgres db_staff_sense
""")
    steps(doc, [
        "Перейдите в каталог demo — от рабочей директории зависят относительные пути Flyway "
        "и датасета.",
        "Скомпилируйте проект без тестов, чтобы ошибки компиляции не смешивались с тестами.",
        "Задайте пароль базы в переменной окружения и запустите приложение.",
        "Дождитесь строки «Started DemoApplication».",
    ])
    gost.code(doc, r"""
cd demo
.\mvnw.cmd -o -q -DskipTests compile
$env:SPRING_DATASOURCE_PASSWORD = "<ваш-пароль>"
.\mvnw.cmd spring-boot:run
""")
    gost.para(doc, "Ключ -o включает офлайн-режим: сборка идёт из локального репозитория Maven. Если "
                   "зависимостей в кэше нет, первый запуск выполняется без -o. Дистрибутив "
                   "собирается командой .\\mvnw.cmd clean package и запускается как "
                   "java -jar target\\demo-0.0.1-SNAPSHOT.jar.")
    gost.para(doc, "Клиент собирается в demo/src/main/resources/static/app и раздаётся тем же "
                   "приложением: Node.js нужен только при правке интерфейса.")
    gost.code(doc, r"""
cd frontend
npm install
npm run build
.\mvnw.cmd package -Pfrontend      # альтернатива: сборка клиента через Maven
""")
    gost.para(doc, "ML-сервис — отдельный репозиторий StaffSenseML (FastAPI + scikit-learn). Без него "
                   "платформа работает: прогноз считается эвристикой, а в карточке риска "
                   "появляется пояснение, что объяснения факторов нет.")
    gost.code(doc, r"""
cd ..\StaffSenseML
uvicorn main:app --host 127.0.0.1 --port 8001
""")
    gost.para(doc, "После старта проверяются пять точек; если все отвечают как ожидается, окружение "
                   "собрано верно.")
    small_table(
        doc,
        ["Проверка", "Как", "Ожидаемый результат"],
        [
            ["ML живо", "GET http://localhost:8001/health", "{\"status\":\"ok\"}"],
            ["Приложение живо", "открыть http://localhost:9091/login", "форма «Вход в систему»"],
            ["Миграции применены", "SELECT version FROM flyway_schema_history", "20 записей, последняя — 20"],
            ["Вход работает", "admin@staffsense.com / password", "переход на /app/#/overview"],
            ["Контракт доступен", "GET /v3/api-docs", "JSON со списком путей /api/v1/**"],
        ],
        [3.8, 6.6, 6.1],
        "Таблица 5 — Проверки после запуска",
    )
    gost.note(doc, "При первом старте DataInitializer создаёт демонстрационные данные: 20 сотрудников "
                   "в четырёх отделах, прогнозы, документы, вакансии. Повторный запуск их не "
                   "дублирует и не сбрасывает.")

    # ── 6 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "6. БАЗА ДАННЫХ")

    gost.heading(doc, "6.1. Миграции", level=2)
    gost.para(doc, "Схема управляется Flyway 11.20.3: версия выбрана явно, потому что PostgreSQL 18 "
                   "поддерживается только с Flyway 11, а Spring Boot 3.2.5 по умолчанию подключает "
                   "9.x. Миграции лежат в classpath:db/migration, baseline-on-migrate=true и "
                   "baseline-version=1 позволяют подключить Flyway к базе, созданной ранее Hibernate.")
    gost.para(doc, "Главное правило: применённую миграцию не редактируют. Flyway хранит контрольную "
                   "сумму файла и при расхождении останавливает старт. Любое изменение — новый "
                   "файл со следующим номером; демонстрационные данные живут в Java-коде "
                   "(DataInitializer), а не в SQL.")
    gost.code(doc, r"""
FlywayValidateException: Validate failed:
Migration checksum mismatch for migration version 11
  -> Applied to database : -1345019304
  -> Resolved locally    : 845120430
""")
    small_table(
        doc,
        ["Версия", "Файл", "Назначение"],
        [
            ["V1", "baseline_schema.sql", "11 таблиц, ключи, уникальность email"],
            ["V2", "indexes_and_unique_constraints.sql", "индексы на FK, уникальность ответов"],
            ["V3", "add_top_risk_factors.sql", "объяснение прогноза, колонка JSON"],
            ["V4", "tunduk_and_employee_documents.sql", "личные дела, журнал «Тундук»"],
            ["V5", "onboarding_portal.sql", "портал: разделы, статьи, чек-лист"],
            ["V6", "timesheet_and_memos.sql", "табель и служебные записки"],
            ["V7", "survey_participation.sql", "факт участия в опросе"],
            ["V8", "ai_skills_catalog.sql", "каталог ИИ-скиллов с файлом"],
            ["V9", "prediction_history.sql", "журнал прогнозов, основа тренда"],
            ["V10", "risk_thresholds.sql", "пороги уровней риска в базе"],
            ["V11", "documents.sql", "реестр документов и сроки хранения"],
            ["V12", "admin_audit.sql", "журнал действий администратора"],
            ["V13", "document_route.sql", "история статусов документа"],
            ["V14", "survey_question_required.sql", "обязательность вопроса опроса"],
            ["V15", "vacation_entitlement.sql", "право на отпуск по годам"],
            ["V16", "hiring_funnel.sql", "вакансии и кандидаты воронки"],
            ["V17", "document_signature.sql", "подпись документа и SLA"],
            ["V18", "audit_hr_actions.sql", "аудит кадровых операций"],
            ["V19", "notifications.sql", "уведомления и отметка о прочтении"],
            ["V20", "task_start_date.sql", "плановая дата старта задачи"],
        ],
        [1.4, 7.3, 7.8],
        "Таблица 6 — Миграции схемы и их назначение",
        size=10,
    )

    gost.heading(doc, "6.2. Схема основных таблиц", level=2)
    gost.para(doc, "Все доменные таблицы ссылаются на users(id); стрелки на рисунке 2 — внешние "
                   "ключи, отметки PK и FK стоят у полей. Связи «один ко многим» читаются по "
                   "направлению стрелки: проект → задачи, документ → подписи, вакансия → кандидаты.")
    gost.figure(doc, str(IMG / "db-tables.png"),
                "Рисунок 2 — Основные таблицы и внешние ключи", width_cm=14.5)

    gost.heading(doc, "6.3. Проверка соответствия схемы", level=2)
    gost.para(doc, "В application.yml задано spring.jpa.hibernate.ddl-auto=validate: Hibernate не "
                   "меняет схему «на лету», а сверяет её с сущностями при старте и падает при "
                   "расхождении. Сущность и миграция меняются одним коммитом.")
    gost.code(doc, r"""
Schema-validation: missing column [start_date] in table [task]
""")
    gost.para(doc, "Если сообщение появилось после добавления поля в сущность — не хватает миграции. "
                   "Если после переключения на другую базу — приложение подключилось не к той схеме: "
                   "проверьте SPRING_DATASOURCE_URL и порт 5433.")

    # ── 7 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "7. КОНФИГУРАЦИЯ")

    gost.para(doc, "Вся конфигурация собрана в demo/src/main/resources/application.yml; значения, "
                   "которые отличаются между средами, вынесены в переменные окружения.")
    small_table(
        doc,
        ["Свойство", "Значение", "Смысл"],
        [
            ["server.port", "9091", "порт приложения"],
            ["server.servlet.session.timeout", "30m", "сессия живёт 30 минут простоя"],
            ["spring.flyway.baseline-on-migrate", "true", "существующая база получает версию 1"],
            ["spring.jpa.hibernate.ddl-auto", "validate", "схему меняет только Flyway"],
            ["spring.servlet.multipart.max-file-size", "2MB", "предел файла ИИ-скилла"],
            ["ml.service.*-timeout-ms", "2000", "таймауты соединения и чтения ML"],
            ["risk.thresholds.low / medium", "33 / 66", "границы уровней, пока их не изменили в базе"],
            ["kpi.weights.*", "0.5 / 0.2 / 0.3", "веса KPI, нормируются на сумму"],
            ["documents.signature.sla-hours", "24", "срок подписи после перехода «На согласовании»"],
            ["staffsense.dataset.generate-on-startup", "true", "генерация CSV-датасета при старте"],
        ],
        [6.4, 4.4, 5.4],
        "Таблица 7 — Основные свойства конфигурации",
    )
    gost.para(doc, "Боевой профиль — PostgreSQL и Flyway. Тестовый профиль описан в "
                   "demo/src/test/resources/application-test.yml: H2 в памяти, ddl-auto=create-drop, "
                   "Flyway выключен. Миграции написаны под PostgreSQL и на H2 неприменимы, поэтому в "
                   "тестах схему создаёт Hibernate. Ручной запуск без PostgreSQL — тем же приёмом:")
    gost.code(doc, r"""
$env:SPRING_DATASOURCE_URL = "jdbc:h2:mem:staffsense;DB_CLOSE_DELAY=-1"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = "create-drop"
$env:FLYWAY_ENABLED = "false"
.\mvnw.cmd spring-boot:run
""")
    gost.para(doc, "RateLimitingFilter перехватывает только POST /login: на каждый IP заводится ведро "
                   "Bucket4j на 5 токенов с пополнением 1 токен в 60 секунд, вёдра живут в "
                   "Caffeine-кэше час. При исчерпании лимита браузер получает /login?error=rate_limit, "
                   "API-клиент — 429 с заголовком Retry-After: 60. Счётчик живёт в памяти процесса, "
                   "перезапуск его обнуляет.")
    gost.para(doc, "Загрузка файлов ограничена 2 МБ на файл и на запрос: файл ИИ-скилла хранится в "
                   "базе, и предел защищает от случайной загрузки архива. Лимит меняется только в "
                   "application.yml с перезапуском.")

    # ── 8 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "8. РАСШИРЕНИЕ СИСТЕМЫ")

    gost.para(doc, "Новый раздел добавляется одной цепочкой из восьми шагов. Нарушение порядка даёт "
                   "типичные ошибки: сущность без миграции роняет старт на validate, экран без записи "
                   "в RoleScreens недостижим из меню, раздел без правила в REQUIRED_SCREEN "
                   "открывается по прямой ссылке всем ролям.")
    steps(doc, [
        "Миграция: новый файл V21__<название>.sql в db/migration с идемпотентным DDL.",
        "Сущность в model/: @Entity, @Table, обязательные поля с nullable=false.",
        "Репозиторий в repository/: интерфейс JpaRepository и методы выборки.",
        "Сервис в service/: правила, @Transactional на изменяющих методах, проверки владельца.",
        "DTO и контроллер в api/<домен>/: маршруты /api/v1/<домен>, @PreAuthorize на каждом методе.",
        "Экран клиента: страница в frontend/src/pages, маршрут в parseRoute и REQUIRED_SCREEN.",
        "Запись в RoleScreens: строка Screen(id, заголовок, маршрут, группа) для каждой роли.",
        "Тесты: правила в сервисе и видимость по ролям; прогон .\\mvnw.cmd -o test.",
    ])
    gost.para(doc, "Ниже — скелет условного раздела «Визиты»: иллюстрация порядка слоёв, а не "
                   "существующая функция.")
    gost.code(doc, r"""
-- db/migration/V21__visits.sql
CREATE TABLE IF NOT EXISTS visit (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES users(id),
    visited_at  TIMESTAMP NOT NULL,
    purpose     VARCHAR(255)
);

@Entity @Table(name = "visit") @Getter @Setter @NoArgsConstructor
public class Visit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "visited_at", nullable = false) private LocalDateTime visitedAt;
}

@RestController @RequestMapping("/api/v1/visits")
public class VisitApiController {
    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
    public List<Visit> list(@PathVariable Long employeeId) { return service.forEmployee(employeeId); }
}

// api/me/RoleScreens.java — выдать раздел HR и администратору
screens.add(new Screen("visits", "Визиты", "#/visits", "люди"));
""")
    gost.para(doc, "Экран добавляется в клиент: маршрут в parseRoute, ключ в REQUIRED_SCREEN («visits» — "
                   "тот же идентификатор, что в RoleScreens) и пункт в GROUPS файла Shell.tsx. "
                   "Совпадение идентификаторов обязательно: по нему меню, прямая ссылка и матрица "
                   "доступа говорят об одном и том же.")

    # ── 9 ────────────────────────────────────────────────────────────────
    gost.heading(doc, "9. ТЕСТИРОВАНИЕ")

    gost.para(doc, "Набор состоит из 67 тестовых классов и 369 тестов. Тесты идут на профиле test: "
                   "H2 в памяти, PostgreSQL не нужен, Flyway и генерация датасета выключены.")
    gost.code(doc, r"""
cd demo
.\mvnw.cmd -o test
.\mvnw.cmd -o test -Dtest=RiskListServiceTest
""")
    gost.para(doc, "Отчёт пишется в demo/target/surefire-reports: сводка — в *.txt, подробности — в "
                   "XML. При падении в консоли печатается причина утверждения и стек.")
    gost.bullets(doc, [
        "Тест, меняющий данные, помечается @Transactional: откат выполняется автоматически, и "
        "набор не зависит от порядка запуска.",
        "Свои сотрудники создаются с уникальной почтой (qa-<uuid>@test.local), иначе прогон "
        "ломается о нарушение уникальности email.",
        "Проверяются оба уровня доступа: код ответа API и наличие пункта в RoleScreens — именно "
        "это делает RoleMenuConsistencyTest.",
        "Активна аннотация @ActiveProfiles(\"test\"); без неё тест пойдёт в PostgreSQL.",
    ])

    # ── 10 ───────────────────────────────────────────────────────────────
    gost.heading(doc, "10. ТИПИЧНЫЕ НЕИСПРАВНОСТИ")

    gost.para(doc, "Таблица собрана по фактическим отказам стенда: симптом, причина и действие, "
                   "которое устраняет причину, а не симптом.")
    small_table(
        doc,
        ["Симптом", "Причина", "Что делать"],
        [
            ["Web server failed to start. Port 9091 was already in use",
             "предыдущий процесс не завершился",
             "netstat -ano | findstr :9091, затем taskkill /PID <pid> /F"],
            ["FlywayValidateException: checksum mismatch",
             "применённая миграция отредактирована",
             "вернуть файл, изменения оформить новой миграцией; flyway repair"],
            ["401 на всех /api/v1 после перезапуска",
             "старая сессия в браузере, cookie недействительна",
             "выйти и войти заново; запросы с credentials same-origin"],
            ["Прогноз без факторов, источник HEURISTIC",
             "ML-сервис недоступен, сработал таймаут 2 с",
             "поднять ML, проверить /health, нажать «Пересчитать прогноз»"],
            ["Правки в миграции не применяются",
             "правка попала в target/classes, а не в src",
             ".\\mvnw.cmd clean, затем повторная сборка"],
            ["Редирект на /login?error=rate_limit",
             "исчерпан лимит 5 попыток входа с одного IP",
             "подождать 60 секунд; перезапуск обнуляет счётчик"],
            ["Клиент не собирается: ошибки типов TypeScript",
             "рассинхронизация types.ts и ответа API",
             "npm ci, затем npm run build; править типы, а не проверку"],
            ["Таблицы пустые, ошибок нет",
             "сессия истекла: ответ — форма входа, а не JSON",
             "перейти на /login?expired и войти заново (ensureOk)"],
            ["PSQLException: SCRAM-based authentication, no password",
             "не задан SPRING_DATASOURCE_PASSWORD",
             "задать переменную и перезапустить процесс или IDE"],
            ["Schema-validation: missing column",
             "сущность изменена без миграции",
             "добавить миграцию с этим полем; ddl-auto оставить validate"],
        ],
        [6.2, 4.9, 5.4],
        "Таблица 8 — Симптом, причина и действие",
    )

    # ── 11 ───────────────────────────────────────────────────────────────
    gost.heading(doc, "11. СОПРОВОЖДЕНИЕ")

    gost.heading(doc, "11.1. Добавление роли", level=2)
    gost.para(doc, "Роль — это не только запись в перечислении: она участвует в правилах "
                   "безопасности, навигации, демо-наборе и тестах. Роль без правила в SecurityConfig "
                   "получает 403 на всё, роль без RoleScreens видит пустое меню.")
    steps(doc, [
        "Добавить константу в model/Role.java и заголовок, если он нужен интерфейсу.",
        "Описать правила в SecurityConfig: какие пути роль открывает, какие закрывает.",
        "Добавить роль в RoleScreens.forRole — иначе меню для неё останется пустым.",
        "Сверить REQUIRED_SCREEN и GROUPS в клиенте: идентификаторы должны совпадать.",
        "Дополнить демо-пользователя в DataInitializer и RoleMenuConsistencyTest.",
    ])

    gost.heading(doc, "11.2. Изменение порогов риска", level=2)
    gost.para(doc, "Пороги — единственный источник правды для деления на LOW/MEDIUM/HIGH (правило "
                   "BR-007). Значение по умолчанию (33 и 66) задано в application.yml, но как только "
                   "пороги изменены из интерфейса, действует значение из базы: RiskThresholdService "
                   "загружает его при старте и перекрывает конфигурацию.")
    steps(doc, [
        "Открыть «Администрирование» → блок «Пороги уровней риска».",
        "Задать границы: 0 < low < medium < 100, иначе сервер вернёт 400 с текстом правила.",
        "Отметить «пересчитать уровни прогнозов», чтобы применить пороги к сохранённым снимкам.",
        "Нажать «Сохранить пороги» и дождаться сообщения «Пороги обновлены».",
    ])
    gost.para(doc, "Сохранение доступно только роли ADMIN (POST /api/v1/risk/admin/thresholds). Экран "
                   "«Администрирование» видят и HR, но сохранение для них вернёт 403 — это ожидаемое "
                   "поведение. Снимки журнала пороги не переписывают.")

    gost.heading(doc, "11.3. Откат миграции", level=2)
    gost.para(doc, "В Flyway Community нет команды undo: схема меняется только вперёд. Откат "
                   "оформляется тремя способами, от предпочтительного к крайнему.")
    gost.bullets(doc, [
        "Компенсирующая миграция: новый файл V22, который возвращает прежнее состояние (удаляет "
        "колонку, восстанавливает ограничение). Это штатный путь.",
        "repair: .\\mvnw.cmd flyway:repair приводит flyway_schema_history в соответствие с файлами на "
        "диске. Лечит контрольные суммы, но не отменяет изменения схемы.",
        "Восстановление из дампа: когда миграция испортила данные; после восстановления историю "
        "миграций нужно привести в соответствие с содержимым базы.",
    ])
    gost.para(doc, "Перед экспериментом снимается дамп схемы и данных: откат «на глазок» почти "
                   "всегда дороже восстановления.")

    gost.heading(doc, "11.4. Журналы", level=2)
    gost.para(doc, "В системе три независимых журнала: кто изменил доступ, кто подписал и начислил, "
                   "что происходило с прогнозами. Журнал действий пишется только на мутирующие "
                   "операции и не заменяет логи приложения.")
    gost.table(
        doc,
        ["Что искать", "Где смотреть", "Кто видит"],
        [
            ["Создание учёток, смена роли, блокировка, сброс пароля",
             "«Пользователи» → вкладка «Журнал действий»", "ADMIN"],
            ["Кадровые операции: отпуск, подпись, вакансия, кандидат",
             "та же вкладка; свои операции — «Пульт HR» → «Мои последние действия»", "ADMIN, HR"],
            ["История расчётов риска и покрытие прогнозом",
             "«Администрирование» → «Последние расчёты», CSV за период", "ADMIN, HR"],
            ["Технические события: вызов ML, таймаут, сохранение прогноза",
             "консоль приложения (SLF4J, INFO/WARN)", "инженер"],
        ],
        widths=[7.2, 6.4, 2.9],
        caption="Таблица 9 — Журналы системы",
    )

    # ── 12 ───────────────────────────────────────────────────────────────
    gost.heading(doc, "12. ПРИЛОЖЕНИЕ А. КОМАНДЫ")

    gost.para(doc, "Команды сгруппированы по задачам и выполняются из каталога demo, если не указано "
                   "иное.")
    gost.code(doc, r"""
# сборка и запуск
.\mvnw.cmd -o -q -DskipTests compile     # компиляция без тестов
.\mvnw.cmd spring-boot:run               # запуск на 9091
.\mvnw.cmd clean package                 # jar в target/
.\mvnw.cmd package -Pfrontend            # сборка вместе с клиентом
.\mvnw.cmd -o test                       # весь набор тестов, профиль test
# база данных
psql -h localhost -p 5433 -U postgres -d db_staff_sense
SELECT version, success FROM flyway_schema_history;
# клиент
cd ..\frontend && npm ci && npm run build
# ML-сервис
cd ..\StaffSenseML
uvicorn main:app --host 127.0.0.1 --port 8001
""")

    # Пустой абзац после последнего блока кода даёт лишнюю пустую страницу.
    while doc.paragraphs and not doc.paragraphs[-1].text.strip():
        element = doc.paragraphs[-1]._element
        element.getparent().remove(element)

    gost.finish(doc)
    doc.save(str(OUT))
    return OUT


if __name__ == "__main__":
    path = build()
    print("saved:", path)

