package com.staffsense.demo.model;

/**
 * Classification of survey types in StaffSense.
 * Each type has a distinct purpose, target audience, and question template.
 *
 * PULSE        — Monthly quick check on team morale
 * ENGAGEMENT   — Quarterly deep dive on employee engagement
 * EXIT         — Exit interview when an employee leaves
 * ONBOARDING   — 30/60/90 day new-hire check-in
 * PERFORMANCE  — Quarterly self + manager performance review
 * TRAINING     — Post-training feedback
 * WELLBEING    — Monthly well-being and burnout survey
 * MANAGER_360  — Semi-annual manager feedback (360 degrees)
 * CULTURE      — Annual culture and values assessment
 * CUSTOM       — Free-form ad-hoc survey
 */
public enum SurveyType {
    PULSE,
    ENGAGEMENT,
    EXIT,
    ONBOARDING,
    PERFORMANCE,
    TRAINING,
    WELLBEING,
    MANAGER_360,
    CULTURE,
    CUSTOM
}
