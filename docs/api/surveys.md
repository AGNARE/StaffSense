# Surveys

## Overview

StaffSense supports customizable employee surveys for gathering feedback on
workplace satisfaction, engagement, and wellbeing.

## Survey Types

| Type | Purpose |
|------|---------|
| Engagement | Employee engagement and satisfaction |
| Wellbeing | Mental health and work-life balance |
| Performance | Self-assessment and goals |
| Feedback | General feedback collection |
| Pulse | Quick 2-3 question check-ins |

## Manage Surveys

### List Surveys
`GET /surveys` — View all surveys with status and response counts.

### Create/Edit Surveys
`GET /surveys/{id}/edit` — Design survey with:
- Multiple choice questions
- Text/Open-ended questions  
- Rating scale questions
- Target audience selection

### Taking Surveys
`GET /surveys/{id}` — Fill out a survey.
`POST /surveys/{id}/responses` — Submit responses.

## Response Analysis

Survey results feed into:
- Wellbeing analytics dashboard
- Employee risk factor analysis
- Department-level trends
