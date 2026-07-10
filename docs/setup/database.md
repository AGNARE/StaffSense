# База данных

## Поддерживаемые СУБД

### PostgreSQL (Production)

PostgreSQL 16 рекомендуется для production.

```sql
CREATE DATABASE db_staff_sense;
CREATE USER staffsense WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE db_staff_sense TO staffsense;
```

### H2 (Разработка)

H2 in-memory база данных отлично подходит для локальной разработки и тестирования.
Не требует настройки — просто установите переменные окружения:

```bash
export SPRING_DATASOURCE_URL=jdbc:h2:mem:staffsense
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
```

H2 Console доступна по `/h2-console` при включении.

## Схема

Режим JPA `ddl-auto: update` создаёт/обновляет таблицы автоматически.
Ключевые сущности:

- **User** — Аутентификация и управление ролями
- **Task** — Задачи со статусом, приоритетом, назначениями
- **Project** — Группировка задач
- **AttritionPrediction** — Результаты ML-прогноза
- **Survey** — Опросы сотрудников
- **SurveyQuestion** — Вопросы опросов
- **SurveyResponse** — Ответы сотрудников
- **OneOnOneMeeting** — Встречи руководителя с сотрудником
- **KpiRecord** — Метрики производительности
- **WorkloadRecord** — Отслеживание загруженности

## Инициализация данных

Класс конфигурации `DataInitializer` заполняет базу данных:
- Пользователи: Admin, HR, PM, Employee
- Примеры проектов
- Начальные записи KPI

Это выполняется автоматически при первом запуске.
