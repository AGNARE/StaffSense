# StaffSense

**HR-Tech платформа для прогнозирования риска увольнения сотрудников и управления задачами.**

Стек: Java 21, Spring Boot 3.2.5, Spring Security, Thymeleaf, PostgreSQL/H2, Maven

## Архитектура

```
StaffSense (Java) ──HTTP──► StaffSenseML (Python/FastAPI)
                               ↑
                          ML Model (Logistic Regression)
```

- **StaffSense** — веб-интерфейс и бизнес-логика (Spring Boot)
- **StaffSenseML** — микросервис ML предсказаний (FastAPI + scikit-learn)
- В репозитории также есть legacy `ml-service/` (устаревший Flask ML сервис)

## Быстрый старт

### Требования
- JDK 21
- Maven 3.9+
- PostgreSQL 16 (или H2 для разработки)

### Запуск

```bash
cd demo
mvn spring-boot:run
```

Приложение будет доступно на http://localhost:9091

### Настройка БД

По умолчанию используется PostgreSQL. Для разработки можно переключить на H2:
```bash
export SPRING_DATASOURCE_URL=jdbc:h2:mem:staffsense
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
```

## ML Сервис

Основной ML сервис находится в отдельном репозитории:
[AGNARE/StaffSenseML](https://github.com/AGNARE/StaffSenseML)

Документация: https://agnare.github.io/StaffSense/

## Лицензия

MIT
