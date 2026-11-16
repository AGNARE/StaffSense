# StaffSense

**HR-Tech платформа для прогнозирования риска увольнения сотрудников и управления задачами.**

Стек: Java 21, Spring Boot 3.2.5, Spring Security, PostgreSQL/H2, Maven.
Интерфейс: React 18 + TypeScript + Vite (основной) и Thymeleaf (серверные страницы, работают параллельно).

## Архитектура

```
StaffSense (Java/Spring Boot) ──HTTP──► StaffSenseML (Python/FastAPI)
        :9091                                    :8001
                                              /api/v1/predict
```

- **StaffSense** — бизнес-логика (Spring Boot) и два интерфейса: REST-контракт
  `/api/v1/**` с клиентом на React и серверные страницы на Thymeleaf
- **StaffSenseML** — микросервис ML предсказаний (FastAPI + scikit-learn), отдельный репозиторий

### Клиент на React

Основной интерфейс живёт по адресу **`/app/`** (после входа). Разделы:
обзор, риски (список, разбор, администрирование), кадры, проекты с доской,
записки, опросы, онбординг, ИИ-скиллы, документы, дизайн-система.

- Исходники — `frontend/` (Vite + TypeScript), сборка — `demo/src/main/resources/static/app`;
- контракт — `/api/v1/**`, документация — `/swagger-ui/index.html`;
- дизайн-система — `demo/src/main/resources/static/design/proto.css`, витрина — `/app/#/design`;
- серверные страницы Thymeleaf остаются рабочим резервом и вызывают те же
  доменные сервисы, поэтому расхождений между интерфейсами нет.

Сборка клиента (нужен Node) либо использование закоммиченного бандла:

```bash
cd frontend && npm install && npm run build   # собрать в static/app
cd demo && ./mvnw package -Pfrontend          # то же через Maven-профиль
```

## Быстрый старт

### Требования
- JDK 21
- Maven 3.9+ (или встроенный wrapper `./mvnw`)
- PostgreSQL 16+ (проверено на 18) — либо H2 для разработки

### Переменные окружения

Все секреты и адреса задаются через переменные окружения. Значения по умолчанию для секретов **отсутствуют** — переменную нужно задать явно.

| Переменная | Назначение | Пример |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL базы | `jdbc:postgresql://localhost:5433/db_staff_sense` |
| `SPRING_DATASOURCE_USERNAME` | Пользователь БД | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Пароль БД (**обязательно**) | `<ваш-пароль>` |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | JDBC-драйвер | `org.postgresql.Driver` |
| `SPRING_JPA_DATABASE_PLATFORM` | Диалект Hibernate | `org.hibernate.dialect.PostgreSQLDialect` |
| `ML_SERVICE_URL` | Адрес ML-сервиса | `http://localhost:8001/api/v1/predict` |

Подробнее — [docs/setup/configuration.md](docs/setup/configuration.md).

### Запуск

```bash
cd demo
export SPRING_DATASOURCE_PASSWORD=<ваш-пароль>
./mvnw spring-boot:run
```

Приложение будет доступно на http://localhost:9091

### Режим H2 (без PostgreSQL)

Миграции Flyway написаны под PostgreSQL, поэтому для H2 их нужно отключить
и разрешить Hibernate создавать схему:

```bash
cd demo
export SPRING_DATASOURCE_URL='jdbc:h2:mem:staffsense;DB_CLOSE_DELAY=-1'
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
export FLYWAY_ENABLED=false
export SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
./mvnw spring-boot:run
```

> В режиме H2 переменная `SPRING_DATASOURCE_PASSWORD` не требуется (H2 принимает любые учётные данные).

## Миграции базы данных

Схема управляется **Flyway** (`demo/src/main/resources/db/migration`):

| Версия | Содержание |
|---|---|
| `V1__baseline_schema.sql` | 11 таблиц, PK, FK, уникальность email |
| `V2__indexes_and_unique_constraints.sql` | индексы на FK, уникальность ответов и периодов |
| `V3__add_top_risk_factors.sql` | колонка `top_risk_factors_json` (XAI) |

Существующая база помечается как baseline v1 (`baseline-on-migrate=true`),
после чего применяются V2 и V3. `spring.jpa.hibernate.ddl-auto=validate`
гарантирует соответствие схемы и JPA-сущностей.

Отключить миграции: `FLYWAY_ENABLED=false`.

## ML Сервис

ML-сервис находится в отдельном репозитории:
[AGNARE/StaffSenseML](https://github.com/AGNARE/StaffSenseML)

Локальная рабочая копия: `../StaffSenseML` (порт 8001).

```bash
cd ../StaffSenseML
python main.py          # http://localhost:8001  (Swagger: /docs)
```

Проверка: `GET http://localhost:8001/health`

Документация: https://agnare.github.io/StaffSense/

## Сборка и тесты

```bash
cd demo
./mvnw test             # тесты на профиле test (H2, PostgreSQL не нужен)
./mvnw clean package    # сборка jar
```

## Запуск в IDE: частые грабли

- **`SPRING_DATASOURCE_PASSWORD` обязателен** (в `application.yml` нет значения по умолчанию).
  В IntelliJ: `Run → Edit Configurations → Environment variables`. Либо на уровне пользователя
  Windows: `[Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_PASSWORD','<пароль>','User')`
  — здесь `User` это область действия переменной, а не имя пользователя БД; после этого IDE
  нужно перезапустить. Симптом без пароля: `PSQLException: The server requested SCRAM-based
  authentication, but no password was provided`.
- **Working directory** Run-конфигурации должен быть `demo/`, иначе относительные пути
  (Flyway, датасет ML) указывают не туда.
- **Кеш Thymeleaf** по умолчанию включён: после правки шаблонов нужен перезапуск приложения
  либо `SPRING_THYMELEAF_CACHE=false` для итеративной разработки.
- **Страница ошибки** (`templates/error.html`) рендерится и контроллерами, и контейнером
  (`/error` для 403 от фильтров безопасности и 404 на статику). Поэтому `currentUser` отдаётся
  глобально через `GlobalControllerAdvice#currentUser`, а `fragments/layout` терпим к
  `currentUser == null` (показывает «Гость» и ссылку на вход). Не добавляйте в шаблоны
  `currentUser.role...` без проверки на null.
- **`/error`, `/img/**`, `/favicon.ico` открыты в `SecurityConfig`**, а иконка
  `static/img/favicon.svg` объявлена в `fragments/layout :: head`. Без этого браузер на каждой
  странице запрашивает `/favicon.ico`, получает 404 и уводит пользователя на страницу ошибки.
- **Светлая тема и `select`**: в CSS не используйте сокращённый `background` для элементов
  с `background-image` — он сбрасывает `background-repeat/-position/-size`, и SVG-стрелка
  `select.form-control` размножается по всей ширине поля. Пишите `background-color`.
- **Уведомления (тосты)** позиционируются в правом верхнем углу окна. Контейнер
  `#toast-container` переносится в `<body>` функцией `ensureToastContainer()` (`js/toast.js`),
  потому что `.sidebar` имеет `backdrop-filter` и делает `position: fixed` относительным себя.

## Документация

- [docs/index.md](docs/index.md) — обзор
- [docs/architecture.md](docs/architecture.md) — архитектура
- [docs/setup/quickstart.md](docs/setup/quickstart.md) — быстрый старт
- [docs/setup/configuration.md](docs/setup/configuration.md) — конфигурация
- [docs/setup/database.md](docs/setup/database.md) — база данных
- [docs/api/overview.md](docs/api/overview.md) — справочник API

## Лицензия

MIT
## Что уже работает

Двенадцать доменов с REST-контрактом и экранами в клиенте:

| Домен | Ключевое |
|---|---|
| Риски увольнений | радар, карточка с факторами и трендом, ML-модель, симулятор удержания, стоимость под риском |
| Кадры | каталог, профиль, пульт HR, табель, отпуска с балансом |
| **Найм и воронка** | вакансии, кандидаты, этапы с проверкой переходов, автозакрытие вакансии |
| Документы | реестр, входящие, карточка с историей маршрута, подписи с SLA |
| Записки | маршрут визы и утверждения, свои записки у сотрудника |
| Опросы | прохождение, результаты, конструктор с обязательностью вопросов |
| Онбординг | портал статей, чек-лист, управление |
| Проекты | портфель, доска с фильтрами, план-график |
| Аналитика | нагрузка и KPI, график нагрузки с порогом перегруза |
| Администрирование | пользователи, роли, блокировка, сброс пароля, матрица доступа |
| **Аудит** | журнал действий администратора и кадровых операций |
| **Уведомления** | внутрисистемные: подпись, визы, просрочка SLA |

Ролевая модель: пять ролей, у каждой свой набор экранов. Меню строит сервер
(`RoleScreens`), клиент не дублирует правила доступа.

## Известные ограничения

- **Почтовой рассылки нет.** Уведомления только внутри системы — чтобы узнать
  о подписи, надо зайти. SMTP в бэклоге.
- **Модель обучена на синтетике.** ROC-AUC 0,617 при потолке 0,623, отчёт —
  в `StaffSenseML/RESULTS.md`. На реальных уходах не проверялась.
- **Сроки хранения документов** — допущение из макета, требует подтверждения HR.
- **Плановые даты задач** появились в V20, но форма их пока не заполняет:
  старые задачи показывают «план не задан».