# Deployment

## Local Development

```bash
# Clone
git clone https://github.com/AGNARE/StaffSense.git
cd StaffSense/demo

# Run with H2 (no external DB needed)
export SPRING_DATASOURCE_URL=jdbc:h2:mem:staffsense
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect
mvn spring-boot:run
```

## Production Build

```bash
cd demo
mvn clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## Environment Configuration

Set these environment variables in production:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://your-host:5432/db_staff_sense
export SPRING_DATASOURCE_USERNAME=your_user
export SPRING_DATASOURCE_PASSWORD=your_password
export SERVER_PORT=9091
```

## Docker

A `Dockerfile` can be created for containerized deployment:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY demo/target/*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## CI/CD

The repository includes GitHub Actions workflows:

### CI Pipeline (`.github/workflows/ci.yml`)
- Triggered on push/PR to `main`
- Builds with Maven (JDK 21)
- Runs tests
- Uploads JAR artifact

### Docs Deploy (`.github/workflows/docs.yml`)
- Triggered on changes to `docs/` or `mkdocs.yml`
- Builds MkDocs site
- Deploys to GitHub Pages

## Requirements

- **Java**: JDK 21 (runtime: JRE 21)
- **Maven**: 3.9+
- **PostgreSQL**: 16 (production)
- **Memory**: 512MB minimum, 1GB+ recommended
