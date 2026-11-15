package com.staffsense.demo.model;

/** Статус встречи 1:1 — с подписью для интерфейса, как у остальных доменов. */
public enum MeetingStatus {

    PLANNED("Запланирована"),
    COMPLETED("Завершена"),
    CANCELLED("Отменена"),
    RESCHEDULED("Перенесена");

    private final String title;

    MeetingStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
