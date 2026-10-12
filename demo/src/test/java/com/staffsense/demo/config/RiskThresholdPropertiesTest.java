package com.staffsense.demo.config;

import com.staffsense.demo.model.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-007: уровни риска присваиваются по порогам из конфигурации.
 *
 * <p>До правки правило {@code LOW < 33; MEDIUM 33 ≤ s < 66; HIGH ≥ 66} было
 * продублировано константами в трёх классах, а секция {@code risk.thresholds}
 * в {@code application.yml} не читалась никем. Тест фиксирует два свойства:
 * границы правила и то, что значения действительно приходят из конфигурации.
 */
@SpringBootTest
@ActiveProfiles("test")
class RiskThresholdPropertiesTest {

    @Autowired
    private RiskThresholdProperties riskThresholds;

    @Test
    @DisplayName("Пороги приходят из application.yml, а не из констант в коде")
    void bindsThresholdsFromConfiguration() {
        assertThat(riskThresholds.getLow()).isEqualTo(33.0);
        assertThat(riskThresholds.getMedium()).isEqualTo(66.0);
    }

    @Test
    @DisplayName("Границы BR-007: 33 → MEDIUM, 66 → HIGH")
    void mapsBoundaryValues() {
        assertThat(riskThresholds.levelFor(0.0)).isEqualTo(RiskLevel.LOW);
        assertThat(riskThresholds.levelFor(32.99)).isEqualTo(RiskLevel.LOW);
        assertThat(riskThresholds.levelFor(33.0)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(riskThresholds.levelFor(65.99)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(riskThresholds.levelFor(66.0)).isEqualTo(RiskLevel.HIGH);
        assertThat(riskThresholds.levelFor(100.0)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("Изменение порога меняет классификацию (пороги больше не захардкожены)")
    void customThresholdsChangeClassification() {
        RiskThresholdProperties custom = new RiskThresholdProperties();
        custom.setLow(10.0);
        custom.setMedium(20.0);

        assertThat(custom.levelFor(15.0)).isEqualTo(RiskLevel.MEDIUM);
        assertThat(custom.levelFor(25.0)).isEqualTo(RiskLevel.HIGH);
        assertThat(custom.levelFor(5.0)).isEqualTo(RiskLevel.LOW);
    }
}
