# Конфигурация

## Свойства приложения

Основная конфигурация — `demo/src/main/resources/application.yml`:

```yaml
server:
  port: 9091
  servlet:
    session:
      timeout: 30m

spring:
  application:
    name: StaffSense
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/db_staff_sense}
    driver-class-name: ${SPRING_DATASOURCE_DRIVER_CLASS_NAME:org.postgresql.Driver}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    # Пароль в репозитории НЕ хранится — задаётся только переменной окружения
    password: ${SPRING_DATASOURCE_PASSWORD:}
  jpa:
    database-platform: ${SPRING_JPA_DATABASE_PLATFORM:org.hibernate.dialect.PostgreSQLDialect}
    hibernate:
      ddl-auto: update
```

## Переменные окружения

> ⚠️ **Пароль БД больше не имеет значения по умолчанию.** Для PostgreSQL
> переменную `SPRING_DATASOURCE_PASSWORD` нужно задать явно, иначе подключение
> не пройдёт аутентификацию. Для H2 переменная не требуется.

| Переменная | Обязательна | Описание | Пример |
|---|---|---|---|
| `SPRING_DATASOURCE_PASSWORD` | **да (PostgreSQL)** | Пароль пользователя БД | `<ваш-пароль>` |
| `SPRING_DATASOURCE_URL` | нет | JDBC URL подключения | `jdbc:postgresql://localhost:5433/db_staff_sense` |
| `SPRING_DATASOURCE_USERNAME` | нет | Имя пользователя БД | `postgres` |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | нет | JDBC драйвер | `org.postgresql.Driver` |
| `SPRING_JPA_DATABASE_PLATFORM` | нет | Диалект Hibernate | `org.hibernate.dialect.PostgreSQLDialect` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | нет | Режим схемы (`validate` в проде, `create-drop` для H2) | `validate` |
| `FLYWAY_ENABLED` | нет | Миграции Flyway; для H2 нужно `false` | `true` |
| `ML_SERVICE_URL` | нет | Адрес эндпоинта ML-сервиса | `http://localhost:8001/api/v1/predict` |
| `ML_SERVICE_CONNECT_TIMEOUT_MS` | нет | Таймаут установки соединения с ML (NFR-003) | `2000` |
| `ML_SERVICE_READ_TIMEOUT_MS` | нет | Таймаут чтения ответа ML (NFR-003) | `2000` |
| `DATASET_GENERATE_ON_STARTUP` | нет | Генерировать `dataset_*.csv` при старте | `true` |
| `DATASET_OUTPUT_DIR` | нет | Каталог для CSV-датасетов | `.` |
| `ONEONONE_MISSED_CRON` | нет | CRON автопометки пропущенных встреч 1:1 (BR-021) | `0 0 3 * * *` |

### Пример запуска (Windows PowerShell)

```powershell
cd demo
$env:SPRING_DATASOURCE_PASSWORD = '<ваш-пароль>'
.\mvnw.cmd spring-boot:run
```

### Пример запуска (Linux/macOS)

```bash
cd demo
export SPRING_DATASOURCE_PASSWORD='<ваш-пароль>'
./mvnw spring-boot:run
```

### Режим H2 (PostgreSQL не нужен)

```bash
export SPRING_DATASOURCE_URL='jdbc:h2:mem:staffsense;DB_CLOSE_DELAY=-1'
export SPRING_DATASOURCE_DRIVER_CLASS_NAME='org.h2.Driver'
export SPRING_JPA_DATABASE_PLATFORM='org.hibernate.dialect.H2Dialect'
export SPRING_DATASOURCE_USERNAME='sa'
./mvnw spring-boot:run
```

## Конфигурация ML-сервиса

```yaml
ml:
  service:
    url: ${ML_SERVICE_URL:http://localhost:8001/api/v1/predict}
    connect-timeout-ms: ${ML_SERVICE_CONNECT_TIMEOUT_MS:2000}
    read-timeout-ms: ${ML_SERVICE_READ_TIMEOUT_MS:2000}
```

Таймауты обязательны (NFR-003, BR-006): если ML-сервис не ответил за 2 секунды,
платформа переходит на встроенную эвристику (FR-015) и сохраняет прогноз
с пометкой о недоступности ML.

## Пороги риска

```yaml
risk:
  thresholds:
    low: 33
    medium: 66
```

- **0 – 32,9**: `LOW` (низкий)
- **33 – 65,9**: `MEDIUM` (средний)
- **66 – 100**: `HIGH` (высокий)

Значения согласованы с кодом: `riskScore >= 66 → HIGH`, `>= 33 → MEDIUM`
(`AttritionMlService`, `services.py`, `DatasetGenerator`).

## Веса KPI

```yaml
kpi:
  weights:
    on-time: 0.5
    reopen: 0.2
    manager-rating: 0.3
```

> ⚠️ На текущий момент эти значения **не читаются кодом**: веса KPI заданы
> константами в `KpiCalculationService`, а вес `manager-rating` (0.3) в формуле
> не используется. Подключение конфигурации — в плане работ (FR-012, Q-020).

## Генерация датасета

```yaml
staffsense:
  dataset:
    generate-on-startup: ${DATASET_GENERATE_ON_STARTUP:true}
    output-dir: ${DATASET_OUTPUT_DIR:.}
```

`dataset_training.csv` (560 записей) и `dataset_test.csv` (140 записей)
создаются при старте приложения, если `generate-on-startup=true`.
В тестах генерация отключена (`application-test.yml`).
