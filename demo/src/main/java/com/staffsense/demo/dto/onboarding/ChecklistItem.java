package com.staffsense.demo.dto.onboarding;

import com.staffsense.demo.model.OnboardingArticle;

import java.time.LocalDateTime;

/**
 * Пункт чек-листа новичка с отметкой о выполнении.
 */
public record ChecklistItem(OnboardingArticle article, boolean completed, LocalDateTime completedAt) {
}
