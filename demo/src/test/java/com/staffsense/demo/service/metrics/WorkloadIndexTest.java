package com.staffsense.demo.service.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Тесты на арифметику индекса нагрузки.
 *
 * <p>Формула вынесена из двух сервисов (аналитика и ML-признак), поэтому тесты
 * фиксируют ровно те свойства, на которые опираются оба: границы ёмкости,
 * защита от деления на ноль и совпадение с прежним расчётом «часы / ёмкость».
 */
class WorkloadIndexTest {

    @Test
    @DisplayName("Ёмкость = недельная норма × недели − дни отсутствия")
    void capacityWithAbsence() {
        // 40 ч/нед на 4 недели = 160 ч, минус 3 дня отпуска по 8 ч = 136 ч
        assertThat(WorkloadIndex.capacityHours(40, 4, 3, 8.0))
                .isCloseTo(136.0, within(1e-9));
    }

    @Test
    @DisplayName("Длинный отпуск не даёт отрицательную ёмкость")
    void capacityNeverNegative() {
        // 40 ч/нед × 1 неделя − 10 дней отсутствия × 8 ч = −40 → 0
        assertThat(WorkloadIndex.capacityHours(40, 1, 10, 8.0)).isZero();
    }

    @Test
    @DisplayName("Нулевая или отрицательная норма часов даёт нулевую ёмкость")
    void zeroCapacity() {
        assertThat(WorkloadIndex.capacityHours(0, 4, 0, 8.0)).isZero();
        assertThat(WorkloadIndex.capacityHours(-5, 2, 0, 8.0)).isZero();
    }

    @Test
    @DisplayName("Число недель не меньше одной")
    void weeksAtLeastOne() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        assertThat(WorkloadIndex.weeksBetween(day, day)).isEqualTo(1);
        assertThat(WorkloadIndex.weeksBetween(day, day.plusDays(6))).isEqualTo(1);
        assertThat(WorkloadIndex.weeksBetween(day, day.plusDays(29))).isEqualTo(4);
        assertThat(WorkloadIndex.weeksBetween(null, day)).isEqualTo(1);
    }

    @Test
    @DisplayName("Индекс нагрузки — отношение часов к ёмкости")
    void indexIsRatio() {
        assertThat(WorkloadIndex.indexOf(100, 40)).isCloseTo(2.5, within(1e-9));
        assertThat(WorkloadIndex.indexOf(40, 40)).isCloseTo(1.0, within(1e-9));
        assertThat(WorkloadIndex.indexOf(20, 40)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("При нулевой ёмкости возвращается 0, а не бесконечность")
    void zeroCapacityGivesZeroIndex() {
        assertThat(WorkloadIndex.indexOf(100, 0)).isZero();
        assertThat(WorkloadIndex.indexOf(100, -1)).isZero();
    }

    @Test
    @DisplayName("Отрицательные часы не превращаются в отрицательный индекс")
    void negativeHoursClamped() {
        assertThat(WorkloadIndex.indexOf(-10, 40)).isZero();
    }

    @Test
    @DisplayName("Недельный срез ML-признака совпадает с прежним расчётом часы/норма")
    void mlSliceMatchesLegacyFormula() {
        int weeklyCapacity = 40;
        double totalHours = 52;   // столько же, сколько уходило в модель раньше

        double capacity = WorkloadIndex.capacityHours(weeklyCapacity, 1, 0, 0);
        double index = WorkloadIndex.indexOf(totalHours, capacity);

        assertThat(index).isCloseTo(totalHours / weeklyCapacity, within(1e-9));
    }
}
