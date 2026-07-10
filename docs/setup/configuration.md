# Configuration

## Application Properties

The main configuration is in `demo/src/main/resources/application.yml`:

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

## Environment Variables

All sensitive/configurable values use environment variable overrides:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5433/db_staff_sense` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `AgnDataBase` |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | JDBC driver | `org.postgresql.Driver` |
| `SPRING_JPA_DATABASE_PLATFORM` | Hibernate dialect | `org.hibernate.dialect.PostgreSQLDialect` |

## ML Service Configuration

```yaml
ml:
  service:
    url: http://localhost:8001/api/v1/predict
```

## Risk Thresholds

```yaml
risk:
  thresholds:
    low: 33
    medium: 66
```

- **0-33**: LOW risk
- **34-66**: MEDIUM risk
- **67-100**: HIGH risk

## KPI Weights

```yaml
kpi:
  weights:
    on-time: 0.5
    reopen: 0.2
    manager-rating: 0.3
```
