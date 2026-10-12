package com.staffsense.demo.service.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты формулы KPI (BR-009, Q-020).
 *
 * <p>Проверяют, что:
 * <ul>
 *   <li>без оценки руководителя формула совпадает с требованием
 *       {@code (0.5·onTime·100 + 0.2·(100 − reopenRate·100)) / 0.7}
 *       (тест-кейс TC-017: onTime 0.75, reopen 0.25 → 75.0);</li>
 *   <li>при заданной оценке руководителя учитывается третий вес (0.3);</li>
 *   <li>оценка ограничивается шкалой 1..5, а результат — диапазоном 0..100.</li>
 * </ul>
 */
class KpiCalculationServiceTest {

    private static final double W_ON_TIME = 0.5;
    private static final double W_REOPEN = 0.2;
    private static final double W_MANAGER_RATING = 0.3;

    private KpiCalculationService service() {
        // Для проверки чистой функции репозитории не нужны.
        return new KpiCalculationService(null, null, W_ON_TIME, W_REOPEN, W_MANAGER_RATING);
    }

    @Test
    void withoutManagerRatingMatchesSpecFormula() {
        double score = service().computeKpiScore(0.75, 0.25, null);
        double expected = (W_ON_TIME * 75.0 + W_REOPEN * 75.0) / (W_ON_TIME + W_REOPEN);
        assertEquals(75.0, expected, 1e-9, "Контрольное значение из TC-017");
        assertEquals(expected, score, 1e-9);
    }

    @Test
    void withBestManagerRatingGivesHundred() {
        // onTime = 1.0 → 100; reopen = 0 → 100; managerRating = 5 → 100
        double score = service().computeKpiScore(1.0, 0.0, 5.0);
        assertEquals(100.0, score, 1e-9);
    }

    @Test
    void withWorstInputsGivesZero() {
        // onTime = 0 → 0; reopen = 1.0 → 0; managerRating = 1 → 0
        double score = service().computeKpiScore(0.0, 1.0, 1.0);
        assertEquals(0.0, score, 1e-9);
    }

    @Test
    void managerRatingAboveScaleIsClamped() {
        double atMax = service().computeKpiScore(1.0, 0.0, 5.0);
        double aboveMax = service().computeKpiScore(1.0, 0.0, 9.0);
        assertEquals(atMax, aboveMax, 1e-9);
    }

    @Test
    void managerRatingBelowScaleIsClamped() {
        double atMin = service().computeKpiScore(1.0, 0.0, 1.0);
        double belowMin = service().computeKpiScore(1.0, 0.0, -3.0);
        assertEquals(atMin, belowMin, 1e-9);
    }

    @Test
    void reopenRateAboveOneDoesNotProduceNegativeComponent() {
        double score = service().computeKpiScore(1.0, 5.0, null);
        // reopenComponent = 100 - min(500, 100) = 0; onTime = 100
        double expected = (W_ON_TIME * 100.0) / (W_ON_TIME + W_REOPEN);
        assertEquals(expected, score, 1e-9);
        assertTrue(score >= 0.0 && score <= 100.0);
    }

    @Test
    void scoreIsAlwaysWithinBounds() {
        for (double onTime = 0.0; onTime <= 1.0; onTime += 0.25) {
            for (double reopen = 0.0; reopen <= 1.0; reopen += 0.25) {
                double score = service().computeKpiScore(onTime, reopen, 3.0);
                assertTrue(score >= 0.0 && score <= 100.0,
                        "score вне диапазона: " + score + " (onTime=" + onTime + ", reopen=" + reopen + ")");
            }
        }
    }
}
