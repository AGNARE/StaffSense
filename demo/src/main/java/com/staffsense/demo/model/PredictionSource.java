package com.staffsense.demo.model;

/**
 * Источник строки истории прогнозов.
 *
 * <p>Нужен, чтобы в отчётах и на странице администрирования можно было отделить
 * расчёт ML-сервисом от эвристики-фолбэка и от демонстрационных данных:
 * у них разная достоверность, и смешивать их в одной цифре нельзя.
 */
public enum PredictionSource {

    /** Расчёт выполнен ML-сервисом (StaffSenseML). */
    ML("ML-модель"),

    /** ML недоступен — сработала встроенная эвристика. */
    HEURISTIC("Эвристика"),

    /** Демонстрационные данные, сгенерированные при старте. */
    SEED("Демо-данные");

    private final String title;

    PredictionSource(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public boolean isReliable() {
        return this == ML;
    }
}
