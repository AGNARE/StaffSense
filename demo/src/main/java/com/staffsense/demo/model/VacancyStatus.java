package com.staffsense.demo.model;

/**
 * Состояние вакансии.
 */
public enum VacancyStatus {

    DRAFT("Черновик"),
    OPEN("Открыта"),
    ON_HOLD("Приостановлена"),
    CLOSED("Закрыта");

    private final String title;

    VacancyStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** Принимает ли вакансия новых кандидатов. */
    public boolean acceptsCandidates() {
        return this == OPEN;
    }
}
