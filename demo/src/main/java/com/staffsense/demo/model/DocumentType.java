package com.staffsense.demo.model;

/**
 * Тип документа в реестре.
 *
 * <p>Сроки хранения — ДОПУЩЕНИЕ, взятое из макета реестра («Хранить до»
 * для приказа о приёме — пять лет, для трудового договора — «постоянно»).
 * Перед боевым использованием значения должен подтвердить HR/юрист:
 * в ТЗ конкретные сроки не зафиксированы. Это записано в отчёте как
 * известное ограничение, а не спрятано в коде.
 */
public enum DocumentType {

    ORDER("Приказ", "пр", 5),
    CONTRACT("Трудовой договор", "тд", null),
    CERTIFICATE("Справка", "сп", 3);

    private final String title;
    private final String numberPrefix;

    /** Срок хранения в годах; null — хранить постоянно. */
    private final Integer retentionYears;

    DocumentType(String title, String numberPrefix, Integer retentionYears) {
        this.title = title;
        this.numberPrefix = numberPrefix;
        this.retentionYears = retentionYears;
    }

    public String getTitle() {
        return title;
    }

    public String getNumberPrefix() {
        return numberPrefix;
    }

    public Integer getRetentionYears() {
        return retentionYears;
    }

    public boolean isPermanent() {
        return retentionYears == null;
    }
}
