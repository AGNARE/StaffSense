package com.staffsense.demo.model;

/** Тип служебной записки. */
public enum MemoType {

    VACATION("Отпуск"),
    SICK_LEAVE("Больничный"),
    OTHER("Прочее");

    private final String title;

    MemoType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
