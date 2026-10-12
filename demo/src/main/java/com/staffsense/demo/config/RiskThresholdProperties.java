package com.staffsense.demo.config;

import com.staffsense.demo.model.RiskLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Пороги уровней риска увольнения (BR-007) — единственный источник значений.
 *
 * <p>Задаются в {@code application.yml}:
 * <pre>
 * risk:
 *   thresholds:
 *     low: 33
 *     medium: 66
 * </pre>
 *
 * <p>До этой правки правило {@code LOW < 33; MEDIUM 33 ≤ s < 66; HIGH ≥ 66} было
 * продублировано константами в {@code AttritionMlService}, {@code DataInitializer}
 * и {@code DatasetGenerator}, а секция {@code risk.thresholds} в конфигурации не
 * читалась никем. Изменение порога требовало синхронной правки нескольких файлов
 * и легко рассинхронизировалось с Python-сервисом.
 *
 * <p>Правило BR-007: {@code LOW < low ≤ MEDIUM < medium ≤ HIGH}.
 */
@ConfigurationProperties(prefix = "risk.thresholds")
public class RiskThresholdProperties {

    /** Граница LOW/MEDIUM: оценки ниже — LOW. */
    private double low = 33.0;

    /** Граница MEDIUM/HIGH: оценки выше или равные — HIGH. */
    private double medium = 66.0;

    public double getLow() {
        return low;
    }

    public void setLow(double low) {
        this.low = low;
    }

    public double getMedium() {
        return medium;
    }

    public void setMedium(double medium) {
        this.medium = medium;
    }

    /** Уровень риска для оценки 0–100 по правилу BR-007. */
    public RiskLevel levelFor(double score) {
        if (score >= medium) {
            return RiskLevel.HIGH;
        }
        if (score >= low) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
