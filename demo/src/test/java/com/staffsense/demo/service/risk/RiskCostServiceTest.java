package com.staffsense.demo.service.risk;

import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Стоимость под риском.
 *
 * <p>Проверяем два обещания: при пустой конфигурации расчёт не выдаёт ноль
 * (не считали ≠ вышло ноль), а при заполненной — сумма равна ручному пересчёту.
 */
class RiskCostServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AttritionPredictionRepository predictions = mock(AttritionPredictionRepository.class);

    private RiskCostService service(Integer junior, Integer middle, Integer senior, Integer lead) {
        return new RiskCostService(users, predictions, junior, middle, senior, lead);
    }

    @Test
    @DisplayName("Пустая конфигурация отключает расчёт")
    void disabledWithoutConfig() {
        RiskCostService cost = service(null, null, null, null);

        assertThat(cost.isEnabled()).isFalse();
        assertThat(cost.total()).isEmpty();
    }

    @Test
    @DisplayName("Стоимость считается по формуле риск × оклад × месяцы")
    void costFormula() {
        RiskCostService cost = service(12, 15, 18, 24);
        User employee = User.builder().salary(new BigDecimal("40000")).gradeLevel(GradeLevel.MIDDLE).build();

        // 50% × 40 000 × 15 = 300 000
        assertThat(cost.costFor(employee, 50)).isEqualByComparingTo("300000.00");
    }

    @Test
    @DisplayName("Нулевой риск не создаёт стоимость")
    void zeroRiskHasNoCost() {
        RiskCostService cost = service(12, 15, 18, 24);
        User employee = User.builder().salary(new BigDecimal("40000")).gradeLevel(GradeLevel.MIDDLE).build();

        assertThat(cost.costFor(employee, 0)).isNull();
    }

    @Test
    @DisplayName("Сумма по компании складывается из сотрудников с прогнозом")
    void totalSumsEmployees() {
        RiskCostService cost = service(12, 15, 18, 24);
        User first = User.builder().id(1L).salary(new BigDecimal("40000")).gradeLevel(GradeLevel.MIDDLE).build();
        User second = User.builder().id(2L).salary(new BigDecimal("80000")).gradeLevel(GradeLevel.LEAD).build();
        when(users.findByActive(true)).thenReturn(List.of(first, second));
        when(predictions.findFirstByUserIdOrderByComputedAtDesc(1L))
                .thenReturn(Optional.of(prediction(50d)));
        when(predictions.findFirstByUserIdOrderByComputedAtDesc(2L))
                .thenReturn(Optional.of(prediction(25d)));

        // 50% × 40 000 × 15 = 300 000; 25% × 80 000 × 24 = 480 000; итого 780 000
        assertThat(cost.total()).contains(new BigDecimal("780000"));
    }

    @Test
    @DisplayName("Сотрудник без прогноза не попадает в сумму")
    void employeeWithoutPredictionIgnored() {
        RiskCostService cost = service(12, 15, 18, 24);
        User employee = User.builder().id(3L).salary(new BigDecimal("40000")).gradeLevel(GradeLevel.MIDDLE).build();
        when(users.findByActive(true)).thenReturn(List.of(employee));
        when(predictions.findFirstByUserIdOrderByComputedAtDesc(anyLong())).thenReturn(Optional.empty());

        assertThat(cost.total()).contains(BigDecimal.ZERO);
    }

    private AttritionPrediction prediction(double score) {
        AttritionPrediction prediction = new AttritionPrediction();
        prediction.setRiskScore(score);
        return prediction;
    }
}
