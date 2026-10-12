# StaffSense

**HR-Tech платформа для прогнозирования риска увольнения сотрудников и управления задачами.**

Стек: Java 21, Spring Boot 3.2.5, Spring Security, Thymeleaf, PostgreSQL/H2, Maven

## Архитектура

```
StaffSense (Java/Spring Boot) ──HTTP──► StaffSenseML (Python/FastAPI)
        :9091                                    :8001
                                              /api/v1/predict
```

- **StaffSense** — веб-интерфейс и бизнес-логика (Spring Boot, SSR на Thymeleaf)
- **StaffSenseML** — микросервис ML предсказаний (FastAPI + scikit-learn), отдельный репозиторий

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
