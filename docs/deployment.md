# Развёртывание

## Локальная разработка

```bash
# Клонирование
git clone https://github.com/AGNARE/StaffSense.git
cd StaffSense/demo

# Запуск с H2 (не требуется внешняя БД)
export SPRING_DATASOURCE_URL=jdbc:h2:mem:staffsense
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
mvn spring-boot:run
```

## Production сборка

```bash
cd demo
mvn clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## Конфигурация окружения

Установите переменные окружения в production:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://your-host:5432/db_staff_sense
export SPRING_DATASOURCE_USERNAME=your_user
export SPRING_DATASOURCE_PASSWORD=your_password
export SERVER_PORT=9091
```

## Docker

Для контейнерного развёртывания можно создать `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY demo/target/*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## CI/CD

Репозиторий включает GitHub Actions:

### CI Pipeline (`.github/workflows/ci.yml`)
- Запускается при push/PR в `main`
- Сборка Maven (JDK 21)
- Запуск тестов
- Загрузка JAR артефакта

### Docs Deploy (`.github/workflows/docs.yml`)
- Запускается при изменениях в `docs/` или `mkdocs.yml`
- Сборка MkDocs
- Публикация на GitHub Pages

## Требования

- **Java**: JDK 21 (runtime: JRE 21)
- **Maven**: 3.9+
- **PostgreSQL**: 16 (production)
- **Память**: минимум 512MB, рекомендуется 1ГБ+
