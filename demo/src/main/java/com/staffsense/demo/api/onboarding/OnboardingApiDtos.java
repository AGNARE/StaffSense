package com.staffsense.demo.api.onboarding;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO портала онбординга (модуль B).
 *
 * <p>Портал — справочник, поэтому контракт только на чтение плюс отметка
 * чек-листа. Черновики статей не отдаются никому, кроме ADMIN и HR.
 */
public final class OnboardingApiDtos {

    private OnboardingApiDtos() {
    }

    /** Раздел портала со счётчиком статей. */
    public record Section(String code, String title, long articleCount) {
    }

    /** Строка списка статей. */
    public record ArticleItem(
            Long id,
            String section,
            String sectionTitle,
            String title,
            Integer orderIndex,
            LocalDateTime updatedAt,
            String updatedBy
    ) {
    }

    /** Полный текст статьи. */
    public record Article(
            Long id,
            String section,
            String sectionTitle,
            String title,
            String content,
            LocalDateTime updatedAt,
            String updatedBy
    ) {
    }

    /** Пункт чек-листа новичка. */
    public record ChecklistItem(Long articleId, String title, boolean completed,
                                LocalDateTime completedAt) {
    }

    /** Прогресс по чек-листу. */
    public record Checklist(long total, long completed, int percent,
                            List<ChecklistItem> items) {
    }
}
