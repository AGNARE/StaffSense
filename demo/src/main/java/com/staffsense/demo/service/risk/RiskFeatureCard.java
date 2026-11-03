package com.staffsense.demo.service.risk;

import java.util.List;
import java.util.Map;

/**
 * Визуальная карточка одного ML-признака для страницы «Детали прогноза».
 *
 * <p>Раньше этот класс был вложенным в {@code RiskController} вместе с логикой
 * расчёта «светофора» — то есть контроллер знал, как выглядит карточка и по
 * каким порогам она краснеет. Теперь это чистый DTO: данные приходят из
 * {@link RiskFeatureCardService}, а контроллер только кладёт список в модель.
 *
 * <p>Имена геттеров намеренно сохранены прежними — шаблон
 * {@code templates/risk/details.html} обращается к {@code card.label},
 * {@code card.threshold}, {@code card.colorClass} и не должен меняться.
 */
public class RiskFeatureCard {

    private final String key;
    private final String label;
    private final String icon;
    private final double value;
    private final String threshold;  // "normal" | "warning" | "critical"
    private final String colorClass; // цвет для инлайн-стиля карточки
    private final String description;

    public RiskFeatureCard(String key, String label, String icon, double value,
                           String threshold, String colorClass, String description) {
        this.key = key;
        this.label = label;
        this.icon = icon;
        this.value = value;
        this.threshold = threshold;
        this.colorClass = colorClass;
        this.description = description;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getIcon() { return icon; }
    public double getValue() { return value; }
    public String getThreshold() { return threshold; }
    public String getColorClass() { return colorClass; }
    public String getDescription() { return description; }

    /**
     * Порядок отображения признаков на странице.
     *
     * <p>Вынесен в константу, потому что использовался и в сортировке, и в
     * тестах; при добавлении нового признака правка должна быть в одном месте.
     */
    public static final List<String> DISPLAY_ORDER = List.of(
            "salary_ratio",
            "workload_index",
            "kpi_norm",
            "tenure_months_norm",
            "satisfaction_norm",
            "missed_1on1_ratio",
            "overtime_norm",
            "z_score"
    );

    /** Признаки, которых нет в {@link #DISPLAY_ORDER}, показываются в конце. */
    public static int displayIndex(String key) {
        int idx = DISPLAY_ORDER.indexOf(key);
        return idx >= 0 ? idx : 99;
    }

    /** Пустой список — чтобы вызывающий код не проверял null. */
    public static List<RiskFeatureCard> empty() {
        return List.of();
    }

    /** Снимок признаков в виде «ключ → значение» — только для отладки и логов. */
    public Map<String, Object> asMap() {
        return Map.of(
                "key", key,
                "value", value,
                "threshold", threshold
        );
    }
}
