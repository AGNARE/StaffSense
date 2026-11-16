package com.staffsense.demo.model;

/**
 * Статус документа в реестре.
 *
 * <p>Маршрут: черновик → на согласовании → утверждён → в архиве.
 * Вернуть документ можно только из архива в утверждённые (переоткрытие),
 * остальные переходы — вперёд по маршруту.
 */
public enum DocumentStatus {

    DRAFT("Черновик"),
    ON_APPROVAL("На согласовании"),
    APPROVED("Утверждён"),
    ARCHIVED("В архиве");

    private final String title;

    DocumentStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Допустим ли переход в этот статус. */
    public boolean canTransitionTo(DocumentStatus target) {
        if (target == null || target == this) {
            return true;
        }
        return switch (this) {
            case DRAFT -> target == ON_APPROVAL;
            case ON_APPROVAL -> target == APPROVED || target == DRAFT;
            case APPROVED -> target == ARCHIVED;
            case ARCHIVED -> target == APPROVED;
        };
    }
}
