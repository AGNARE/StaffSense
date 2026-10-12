package com.staffsense.demo.model;

/** Результат обращения к «Тундук» (пишется в журнал аудита, BR-026). */
public enum TundukVerificationResult {
    /** Гражданин найден, данные получены. */
    FOUND,
    /** Гражданин с таким ИНН не найден. */
    NOT_FOUND,
    /** ИНН не прошёл проверку формата (14 цифр). */
    INVALID,
    /** Сервис недоступен. */
    UNAVAILABLE
}
