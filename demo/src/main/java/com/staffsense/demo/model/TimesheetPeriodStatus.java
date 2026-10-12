package com.staffsense.demo.model;

/** Статус периода табеля (месяца). */
public enum TimesheetPeriodStatus {

    /** Период открыт — часы можно вносить и править. */
    OPEN("Открыт"),
    /** Период закрыт — правка запрещена (BR-028). */
    CLOSED("Закрыт");

    private final String title;

    TimesheetPeriodStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
