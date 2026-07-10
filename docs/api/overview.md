# API Reference

## Overview

StaffSense provides both server-rendered pages (Thymeleaf) and REST API endpoints
for AJAX operations.

## Base URL

```
http://localhost:9091
```

## Authentication

Most endpoints require authentication via Spring Security session-based auth.
See [Authentication](authentication.md) for details.

## Endpoints Summary

### Auth

| Method | Path | Description |
|--------|------|-------------|
| GET | `/login` | Login page |
| POST | `/login` | Login action |
| GET | `/logout` | Logout |

### Dashboard

| Method | Path | Description |
|--------|------|-------------|
| GET | `/dashboard` | Main dashboard (routes to personal/project) |
| GET | `/dashboard/personal` | Personal employee dashboard |

### Employees

| Method | Path | Description |
|--------|------|-------------|
| GET | `/employees` | Employee list |
| GET | `/employees/{id}` | Employee profile |
| GET | `/employees/{id}/risk` | Attrition risk details |

### Tasks

| Method | Path | Description |
|--------|------|-------------|
| GET | `/projects/{id}/board` | Kanban board |
| GET | `/api/tasks` | Get all tasks |
| POST | `/api/tasks` | Create task |
| PUT | `/api/tasks/{id}/status` | Update task status |

### Surveys

| Method | Path | Description |
|--------|------|-------------|
| GET | `/surveys` | Survey list |
| GET | `/surveys/{id}` | Take survey |
| GET | `/surveys/{id}/edit` | Edit survey |
| POST | `/surveys/{id}/responses` | Submit response |

### Analytics

| Method | Path | Description |
|--------|------|-------------|
| GET | `/analytics/kpi` | KPI analytics |
| GET | `/analytics/workload` | Workload analysis |
| GET | `/analytics/wellbeing` | Wellbeing dashboard |
| GET | `/analytics/ml-model` | ML model insights |
