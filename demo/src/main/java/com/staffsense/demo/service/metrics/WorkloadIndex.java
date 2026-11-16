package com.staffsense.demo.service.metrics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Чистая арифметика индекса нагрузки: без Spring, без БД, без побочных эффектов.
 *
 * <p>Зачем отдельный класс. Формула «часы / ёмкость» была записана дважды —
 * в {@link WorkloadCalculationService} (для аналитики и табеля) и в
 * {@code AttritionMlService} (для признака модели) — с разными деталями:
 * первая учитывает недели периода и дни отсутствия, вторая считает неделю
 * и отсутствия игнорирует. Любая правка формулы требовала помнить о двух
 * местах, а расхождение между ними означало, что модель видит не ту нагрузку,
 * что и руководитель на дашборде.
 *
 * <p>Различия в семантике сохранены намеренно (это разные сценарии), но теперь
 * они задаются параметрами, а не второй копией кода: см.
 * {@code docs/CODE_AUDIT.md}, пункт 2 и {@code TODO(BR-030)} в
 * {@code AttritionMlService}.
 */
public final class WorkloadIndex {

    /** Часы, которые «съедает» один день отсутствия (отпуск, больничный). */
    public static final double HOURS_PER_ABSENCE_DAY = 8.0;

    private WorkloadIndex() {
    }

    /**
     * Число недель в периоде, минимум одна: период короче недели всё равно
     * занимает неделю ёмкости.
     */
    public static long weeksBetween(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            return 1L;
        }
        return Math.max(1L, ChronoUnit.WEEKS.between(periodStart, periodEnd));
    }

    /**
     * Ёмкость периода в часах.
     *
     * <p>{@code weeklyCapacityHours × weeks − absenceDays × hoursPerAbsenceDay},
     * но не меньше нуля: длинный отпуск не должен давать отрицательную ёмкость.
     *
     * @param weeklyCapacityHours норма часов в неделю (0 и меньше трактуются как 0)
     * @param weeks               число недель периода, минимум 1
     * @param absenceDays         дни отсутствия внутри периода
     * @param hoursPerAbsenceDay  сколько часов вычитается за день отсутствия
     */
    public static double capacityHours(int weeklyCapacityHours,
                                       long weeks,
                                       long absenceDays,
                                       double hoursPerAbsenceDay) {
        long effectiveWeeks = Math.max(1L, weeks);
        int capacity = Math.max(0, weeklyCapacityHours);
        double total = (double) capacity * effectiveWeeks
                - Math.max(0L, absenceDays) * Math.max(0.0, hoursPerAbsenceDay);
        return Math.max(0.0, total);
    }

    /**
     * Индекс нагрузки: во сколько раз плановые часы превышают ёмкость.
     *
     * <p>{@code 1.0} — норма, больше — перегрузка, меньше — недогрузка.
     * При нулевой ёмкости возвращается {@code 0.0}: делить на ноль нельзя,
     * а придумывать «бесконечную нагрузку» — тем более.
     */
    public static double indexOf(double totalEstimatedHours, double capacityHours) {
        if (capacityHours <= 0.0) {
            return 0.0;
        }
        double hours = Math.max(0.0, totalEstimatedHours);
        return hours / capacityHours;
    }
}
