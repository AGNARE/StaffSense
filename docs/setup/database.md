# Database

## Supported Databases

### PostgreSQL (Production)

PostgreSQL 16 is recommended for production use.

```sql
CREATE DATABASE db_staff_sense;
CREATE USER staffsense WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE db_staff_sense TO staffsense;
```

### H2 (Development)

H2 in-memory database is great for local development and testing.
No setup required — just set the environment variables:

```bash
export SPRING_DATASOURCE_URL=jdbc:h2:mem:staffsense
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
```

H2 Console is available at `/h2-console` when enabled.

## Schema

JPA `ddl-auto: update` mode creates/updates tables automatically.
Key entities:

- **User** — Authentication and role management
- **Task** — Work items with status, priority, assignments
- **Project** — Task grouping
- **AttritionPrediction** — ML prediction results
- **Survey** — Employee feedback surveys
- **SurveyQuestion** — Survey questions
- **SurveyResponse** — Employee responses
- **OneOnOneMeeting** — Manager-employee meetings
- **KpiRecord** — Performance metrics
- **WorkloadRecord** — Capacity tracking

## Data Initialization

The `DataInitializer` config class seeds the database with:
- Admin, HR, PM, and Employee users
- Sample projects
- Initial KPI records

This runs automatically on first startup.
