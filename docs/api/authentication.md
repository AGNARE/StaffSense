# Authentication

## Login Flow

StaffSense uses **Spring Security** with form-based authentication.

1. Visit `/login`
2. Enter email and password
3. On success — redirected to dashboard
4. On failure — error message displayed

## Roles

| Role | Access Level |
|------|-------------|
| `ADMIN` | Full system access, user management |
| `EXEC` | Executive dashboard, high-level analytics |
| `HR` | Employee management, surveys, risk reports |
| `PM` | Project and task management |
| `EMPLOYEE` | Personal dashboard, surveys, tasks |

## Default Users

| Email | Password | Role |
|-------|----------|------|
| admin@staffsense.com | admin123 | ADMIN |
| hr@staffsense.com | hr123 | HR |
| pm@staffsense.com | pm123 | PM |
| employee@staffsense.com | emp123 | EMPLOYEE |

## Security Configuration

- Passwords hashed with BCrypt
- Session timeout: 30 minutes
- CSRF protection configurable in `SecurityConfig`
- Role-based access on all endpoints

## API Authentication

AJAX API endpoints use session-based auth (same as the web UI).
Requests from the frontend include credentials automatically.
