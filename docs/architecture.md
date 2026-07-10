# Architecture

## System Architecture

```
┌──────────────┐     ┌──────────────┐
│   Browser    │     │  PostgreSQL  │
│ (Thymeleaf)  │     │   Database   │
└──────┬───────┘     └──────▲───────┘
       │ HTTP/HTTPS         │ JDBC/JPA
       ▼                    │
┌──────────────────────────────────┐
│        Spring Boot App          │
│  ┌──────────┐  ┌────────────┐   │
│  │Security  │  │  JPA       │   │
│  │  Layer   │  │  Repos     │   │
│  └────┬─────┘  └─────┬──────┘   │
│  ┌────▼──────────────▼──────┐   │
│  │     Controllers          │   │
│  └────┬─────────────────────┘   │
│  ┌────▼─────────────────────┐   │
│  │     Services             │   │
│  │  ┌────────────────────┐  │   │
│  │  │ AttritionMLService │──┼──┼──┐
│  │  └────────────────────┘  │   │  │
│  └──────────────────────────┘   │  │
└─────────────────────────────────┘  │
                                     │
                    ┌─────────────────┘
                    ▼
          ┌──────────────────┐
          │  StaffSenseML    │
          │  (FastAPI)       │
          │  :8001           │
          │  /api/v1/predict │
          │  /health         │
          └──────────────────┘
```

## Layers

### Backend (Spring Boot)
- **Web Layer** — Thymeleaf templates, REST controllers
- **Service Layer** — Business logic, ML integration
- **Data Layer** — JPA repositories, PostgreSQL/H2
- **Security Layer** — Spring Security, role-based access

### Frontend
- Server-side rendered with Thymeleaf
- Chart.js for analytics visualizations
- Drag-and-drop Kanban board

### ML Service
- Separate FastAPI microservice (StaffSenseML)
- Logistic regression model
- REST API on port 8001

## Project Structure

```
StaffSense/
├── demo/                          # Spring Boot application
│   ├── src/main/java/             # Java source
│   │   └── com/staffsense/demo/
│   │       ├── config/           # Security, CORS, DB init
│   │       ├── controller/       # REST + Thymeleaf controllers
│   │       ├── model/            # JPA entities
│   │       ├── repository/       # Spring Data repos
│   │       ├── service/          # Business logic
│   │       └── dto/              # Data transfer objects
│   └── src/main/resources/
│       ├── templates/            # Thymeleaf HTML templates
│       └── static/               # CSS, JS assets
├── ml-service/                   # Legacy Flask ML service
├── docs/                         # Documentation (MkDocs)
└── .github/workflows/            # GitHub Actions
```

## Data Flow

1. User interacts with Thymeleaf UI in browser
2. Controllers handle requests, call Services
3. Services query data via JPA Repositories (PostgreSQL/H2)
4. For attrition prediction: Service → HTTP POST → StaffSenseML
5. StaffSenseML runs logistic regression model → returns risk score
6. Result displayed in dashboard/employee profile

