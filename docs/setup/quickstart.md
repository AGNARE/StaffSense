# Быстрый старт

## Требования

- **Java Development Kit** — JDK 21 (рекомендуется) или JDK 17+
- **Maven** — 3.9+
- **PostgreSQL** — 16 (или используйте H2 для разработки)
- **Git** — для контроля версий

## Клонирование и запуск

### 1. Клонировать репозиторий

```bash
git clone https://github.com/AGNARE/StaffSense.git
cd StaffSense
```

### 2. Запуск с H2 (режим разработки, PostgreSQL не требуется)

```bash
cd demo

# Linux/macOS
export SPRING_DATASOURCE_URL=jdbc:h2:mem:staffsense
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect

mvn spring-boot:run
```

### 3. Доступ к приложению

- **Веб-интерфейс**: http://localhost:9091
- **Учётные данные по умолчанию**: admin@staffsense.com / admin123

### 4. (Опционально) Запуск ML-сервиса

Java-приложение будет работать без ML-сервиса, но для прогнозирования увольнений
он необходим. См. настройку [StaffSenseML](https://github.com/AGNARE/StaffSenseML).

## Использование PostgreSQL

### 1. Создать базу данных

```sql
CREATE DATABASE db_staff_sense;
```

### 2. Запуск с PostgreSQL

```bash
cd demo
mvn spring-boot:run
```

Приложение автоматически настраивается через переменные окружения:

| Переменная | Значение по умолчанию |
|----------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/db_staff_sense` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `AgnDataBase` |
