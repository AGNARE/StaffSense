package com.staffsense.demo.model;

/**
 * Тип уведомления.
 *
 * <p>Набор намеренно узкий: уведомление должно подталкивать к действию,
 * а не пересказывать систему. Расширять стоит только тем, что требует
 * реакции человека.
 */
public enum NotificationType {

    DOCUMENT_TO_SIGN("Документ на подпись"),
    DOCUMENT_SIGNED("Документ подписан"),
    MEMO_TO_APPROVE("Записка ждёт решения"),
    SIGNATURE_OVERDUE("Срок подписи истёк"),
    VACATION_GRANTED("Начислен отпуск");

    private final String title;

    NotificationType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
