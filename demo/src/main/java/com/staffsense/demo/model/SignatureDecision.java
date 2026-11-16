package com.staffsense.demo.model;

/**
 * Решение по подписи документа.
 *
 * <p>Два значения, а не статус документа: подпись — это факт «кто и как
 * решил», а статус — следствие. Поэтому в реестре подписей хранится именно
 * решение, а в {@code document} остаётся текущий шаг маршрута.
 */
public enum SignatureDecision {

    APPROVED("Подписано"),
    REJECTED("Отклонено");

    private final String title;

    SignatureDecision(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Целевой статус документа: подпись утверждает, отклонение возвращает. */
    public DocumentStatus targetStatus() {
        return this == APPROVED ? DocumentStatus.APPROVED : DocumentStatus.DRAFT;
    }
}
