package com.staffsense.demo.service.tunduk;

/**
 * Результат обращения к «Тундук».
 *
 * <p>Результат возвращается объектом, а не исключением: все три исхода
 * (найден / не найден / сервис недоступен) — это ожидаемые бизнес-ситуации,
 * каждая из которых фиксируется в журнале аудита (BR-026).
 */
public record TundukLookupResult(TundukStatus status, TundukPersonData data, String message) {

    public static TundukLookupResult found(TundukPersonData data) {
        return new TundukLookupResult(TundukStatus.FOUND, data, "Данные получены");
    }

    public static TundukLookupResult notFound(String inn) {
        return new TundukLookupResult(TundukStatus.NOT_FOUND, null,
                "Гражданин с ИНН " + inn + " не найден в госсистеме");
    }

    public static TundukLookupResult unavailable(String reason) {
        return new TundukLookupResult(TundukStatus.UNAVAILABLE, null,
                "Сервис «Тундук» недоступен: " + reason);
    }

    /** ERR-013: ИНН не прошёл проверку формата. */
    public static TundukLookupResult invalid(String message) {
        return new TundukLookupResult(TundukStatus.INVALID, null, message);
    }

    public boolean isFound() {
        return status == TundukStatus.FOUND;
    }
}
