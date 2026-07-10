# Employees API

## List Employees

Page: `GET /employees`

Displays a table of all employees with:
- Name and contact info
- Department and role
- Current risk level
- Quick actions

## Employee Profile

Page: `GET /employees/{id}`

Detailed view including:
- Personal information
- Performance metrics (KPI, workload)
- Survey history
- Task assignments
- Attrition risk score

## Risk Details

Page: `GET /employees/{id}/risk`

ML-powered attrition risk breakdown:
- Risk score (0-100)
- Risk level (LOW/MEDIUM/HIGH)
- Top contributing factors
- AI-generated action plan
- Historical risk trend
