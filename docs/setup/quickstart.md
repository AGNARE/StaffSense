# Quick Start

## Prerequisites

- **Java Development Kit** — JDK 21 (recommended) or JDK 17+
- **Maven** — 3.9+
- **PostgreSQL** — 16 (or use H2 in-memory for development)
- **Git** — for version control

## Clone & Run

### 1. Clone the repository

```bash
git clone https://github.com/AGNARE/StaffSense.git
cd StaffSense
```

### 2. Start with H2 (development mode, no PostgreSQL needed)

```bash
cd demo

# Linux/macOS
export SPRING_DATASOURCE_URL=jdbc:h2:mem:staffsense
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
export SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.H2Dialect

mvn spring-boot:run
```

### 3. Access the application

- **Web interface**: http://localhost:9091
- **Default credentials**: admin@staffsense.com / admin123

### 4. (Optional) Start ML service

The Java app will work without the ML service, but attrition predictions require it.
See [StaffSenseML](https://github.com/AGNARE/StaffSenseML) setup.

## Using PostgreSQL

### 1. Create database

```sql
CREATE DATABASE db_staff_sense;
```

### 2. Run with PostgreSQL

```bash
cd demo
mvn spring-boot:run
```

The app auto-configures from environment variables:

| Variable | Default |
|----------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/db_staff_sense` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `AgnDataBase` |
