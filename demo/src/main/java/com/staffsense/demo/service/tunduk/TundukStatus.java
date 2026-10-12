package com.staffsense.demo.service.tunduk;

/** Статус обращения к «Тундук». */
public enum TundukStatus {
    /** Гражданин найден. */
    FOUND,
    /** Гражданин с таким ИНН не найден. */
    NOT_FOUND,
    /** Сервис недоступен. */
    UNAVAILABLE,
    /** ИНН не прошёл проверку формата (обрабатывается до вызова сервиса). */
    INVALID
}
