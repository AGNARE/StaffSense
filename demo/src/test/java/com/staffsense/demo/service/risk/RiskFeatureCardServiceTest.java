package com.staffsense.demo.service.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты на сборку карточек признаков.
 *
 * <p>Появились вместе с выносом {@code FeatureCardBuilder} из
 * {@code RiskController}: раньше пороги «светофора» жили в приватном статическом
 * классе контроллера и не покрывались тестами вообще.
 */
class RiskFeatureCardServiceTest {

    private final RiskFeatureCardService service = new RiskFeatureCardService();

    private static Map<String, Object> snapshot(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("Порядок карточек фиксирован, даже если в снимке они вперемешку")
    void keepsDisplayOrder() {
        List<RiskFeatureCard> cards = service.buildFromSnapshot(snapshot(
                "overtime_norm", 0.5,
                "salary_ratio", 0.7,
                "kpi_norm", 0.9
        ));

        assertThat(cards)
                .extracting(RiskFeatureCard::getKey)
                .containsExactly("salary_ratio", "kpi_norm", "overtime_norm");
    }

    @Test
    @DisplayName("Пороги зарплаты: ниже 0.9 — critical, 0.9..1.1 — warning, выше — normal")
    void salaryThresholds() {
        assertThat(thresholdOf("salary_ratio", 0.5)).isEqualTo("critical");
        assertThat(thresholdOf("salary_ratio", 0.95)).isEqualTo("warning");
        assertThat(thresholdOf("salary_ratio", 1.2)).isEqualTo("normal");
    }

    @Test
    @DisplayName("Пороги нагрузки инвертированы: выше 1.3 — critical")
    void workloadThresholds() {
        assertThat(thresholdOf("workload_index", 0.8)).isEqualTo("normal");
        assertThat(thresholdOf("workload_index", 1.1)).isEqualTo("warning");
        assertThat(thresholdOf("workload_index", 1.5)).isEqualTo("critical");
    }

    @Test
    @DisplayName("Пропуски 1:1 и переработки: чем больше, тем хуже")
    void missedAndOvertimeThresholds() {
        assertThat(thresholdOf("missed_1on1_ratio", 0.05)).isEqualTo("normal");
        assertThat(thresholdOf("missed_1on1_ratio", 0.2)).isEqualTo("warning");
        assertThat(thresholdOf("missed_1on1_ratio", 0.4)).isEqualTo("critical");
        assertThat(thresholdOf("overtime_norm", 0.5)).isEqualTo("critical");
    }

    @Test
    @DisplayName("Цвет соответствует статусу")
    void colorMatchesThreshold() {
        RiskFeatureCard critical = card("salary_ratio", 0.5);
        RiskFeatureCard normal = card("salary_ratio", 1.2);

        assertThat(critical.getColorClass()).isEqualTo("#ef4444");
        assertThat(normal.getColorClass()).isEqualTo("#22c55e");
    }

    @Test
    @DisplayName("Неизвестный признак не роняет страницу: нейтральная карточка")
    void unknownFeatureIsSafe() {
        RiskFeatureCard card = card("some_new_feature", 0.42);

        assertThat(card.getLabel()).isEqualTo("some_new_feature");
        assertThat(card.getThreshold()).isEqualTo("normal");
        assertThat(card.getIcon()).isNotNull();
    }

    @Test
    @DisplayName("Пустой и null-снимок дают пустой список, а не исключение")
    void emptySnapshot() {
        assertThat(service.buildFromSnapshot(null)).isEmpty();
        assertThat(service.buildFromSnapshot(Map.of())).isEmpty();
    }

    private RiskFeatureCard card(String key, double value) {
        List<RiskFeatureCard> cards = service.buildFromSnapshot(snapshot(key, value));
        assertThat(cards).hasSize(1);
        return cards.get(0);
    }

    private String thresholdOf(String key, double value) {
        return card(key, value).getThreshold();
    }
}
