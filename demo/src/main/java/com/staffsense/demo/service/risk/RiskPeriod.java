package com.staffsense.demo.service.risk;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Период выгрузки и сводки по истории прогнозов.
 *
 * <p>Нужен, чтобы «за неделю / месяц / квартал» считались в одном месте и
 * одинаково на странице администрирования и в CSV-выгрузке. Раньше периода
 * не было вовсе: выгружался только текущий срез.
 */
public enum RiskPeriod {

    WEEK("Неделя", 7),
    MONTH("Месяц", 30),
    QUARTER("Квартал", 90),
    ALL("Всё время", 0);

    private final String title;
    private final int days;

    RiskPeriod(String title, int days) {
        this.title = title;
        this.days = days;
    }

    public String getTitle() {
        return title;
    }

    public int getDays() {
        return days;
    }

    /** Левая граница периода. Для {@link #ALL} — «от начала времён». */
    public LocalDateTime from(LocalDateTime now) {
        return days <= 0 ? LocalDateTime.of(1970, 1, 1, 0, 0) : now.minusDays(days);
    }

    /**
     * Разбор параметра запроса.
     *
     * <p>Неизвестное значение не роняет страницу, а откатывается к месяцу:
     * пользователь мог руками поправить URL.
     */
    public static RiskPeriod fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return MONTH;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MONTH;
        }
    }
}
