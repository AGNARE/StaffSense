# Конфигурация

## Свойства приложения

Основная конфигурация в `demo/src/main/resources/application.yml`:

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
    password: ${SPRING_DATASOURCE_PASSWORD:AgnDataBase}
  jpa:
    database-platform: ${SPRING_JPA_DATABASE_PLATFORM:org.hibernate.dialect.PostgreSQLDialect}
    hibernate:
      ddl-auto: update
```

## Переменные окружения

Все конфиденциальные/настраиваемые значения поддерживают переопределение через переменные окружения:

| Переменная | Описание | По умолчанию |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC URL подключения | `jdbc:postgresql://localhost:5433/db_staff_sense` |
| `SPRING_DATASOURCE_USERNAME` | Имя пользователя БД | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Пароль БД | `AgnDataBase` |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | JDBC драйвер | `org.postgresql.Driver` |
| `SPRING_JPA_DATABASE_PLATFORM` | Hibernate диалект | `org.hibernate.dialect.PostgreSQLDialect` |

## Конфигурация ML-сервиса

```yaml
ml:
  service:
    url: http://localhost:8001/api/v1/predict
```

## Пороги риска

```yaml
risk:
  thresholds:
    low: 33
    medium: 66
```

- **0–33**: НИЗКИЙ риск
- **34–66**: СРЕДНИЙ риск
- **67–100**: ВЫСОКИЙ риск

## Веса KPI

```yaml
kpi:
  weights:
    on-time: 0.5
    reopen: 0.2
    manager-rating: 0.3
```
