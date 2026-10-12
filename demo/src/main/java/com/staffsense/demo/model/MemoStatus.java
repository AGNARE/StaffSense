package com.staffsense.demo.model;

/**
 * Статус служебной записки.
 *
 * <p>Маршрут согласования (BR-003 ТЗ, BR-029):
 * {@code DRAFT → PENDING_PM → PENDING_HR → APPROVED}, отклонение на любом этапе → {@code REJECTED}.
 * Автор может отозвать записку до финального решения → {@code CANCELLED}.
 */
public enum MemoStatus {

    DRAFT("Черновик"),
    PENDING_PM("На визе руководителя"),
    PENDING_HR("На утверждении HR"),
    APPROVED("Утверждена"),
    REJECTED("Отклонена"),
    CANCELLED("Отозвана автором");

    private final String title;

    MemoStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Финальное решение принято — редактирование и отзыв невозможны. */
    public boolean isFinal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }
}
