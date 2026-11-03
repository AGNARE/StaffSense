package com.staffsense.demo.service.risk;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Собирает визуальные карточки признаков из снимка {@code featuresSnapshotJson}.
 *
 * <p>Логика перенесена из {@code RiskController.FeatureCardBuilder} без изменения
 * поведения: те же пороги «normal / warning / critical», те же подписи, иконки
 * и порядок вывода. Контроллер больше не содержит презентационных знаний.
 *
 * <p>Почему это отдельный сервис, а не статический хелпер в контроллере:
 * <ul>
 *   <li>пороги «светофора» — это доменное правило, его нужно тестировать
 *       отдельно от MVC;</li>
 *   <li>тот же набор карточек понадобится REST-эндпоинту
 *       {@code /api/v1/risk/{id}} при переходе к API-first (см.
 *       {@code docs/REFACTOR_PLAN.md}, этап 2).</li>
 * </ul>
 */
@Service
public class RiskFeatureCardService {

    /** Метаданные признака: подпись, иконка и правило «светофора». */
    private record FeatureMeta(String label, String icon,
                               Function<Double, String> thresholdFn) {}

    private static final Map<String, FeatureMeta> FEATURE_META = Map.ofEntries(
            Map.entry("salary_ratio", new FeatureMeta("Уровень зарплаты", "💰",
                    v -> v < 0.9 ? "critical" : v < 1.1 ? "warning" : "normal")),
            Map.entry("workload_index", new FeatureMeta("Индекс нагрузки", "⚡",
                    v -> v > 1.3 ? "critical" : v > 1.0 ? "warning" : "normal")),
            Map.entry("kpi_norm", new FeatureMeta("Показатели KPI", "📊",
                    v -> v < 0.5 ? "critical" : v < 0.7 ? "warning" : "normal")),
            Map.entry("tenure_months_norm", new FeatureMeta("Стаж в компании", "🌱",
                    v -> v < 0.3 ? "critical" : v < 0.6 ? "warning" : "normal")),
            Map.entry("satisfaction_norm", new FeatureMeta("Удовлетворённость", "😊",
                    v -> v < 0.5 ? "critical" : v < 0.7 ? "warning" : "normal")),
            Map.entry("missed_1on1_ratio", new FeatureMeta("Пропуски встреч 1:1", "🤝",
                    v -> v > 0.3 ? "critical" : v > 0.1 ? "warning" : "normal")),
            Map.entry("overtime_norm", new FeatureMeta("Переработки", "🕐",
                    v -> v > 0.3 ? "critical" : v > 0.1 ? "warning" : "normal")),
            Map.entry("z_score", new FeatureMeta("Z-оценка (logit)", "📐",
                    v -> Math.abs(v) > 2.0 ? "critical" : Math.abs(v) > 1.0 ? "warning" : "normal"))
    );

    private static final Map<String, String> COLOR_CLASSES = Map.of(
            "normal", "#22c55e",
            "warning", "#f59e0b",
            "critical", "#ef4444"
    );

    private static final Map<String, String> DESCRIPTIONS = Map.of(
            "salary_ratio", "Отношение зарплаты к средней по грейду",
            "workload_index", "Индекс загруженности (1.0 = норма)",
            "kpi_norm", "Нормализованный KPI (0..1)",
            "tenure_months_norm", "Стаж / 60 месяцев",
            "satisfaction_norm", "Средний балл опросов / 5",
            "missed_1on1_ratio", "Доля пропущенных встреч",
            "overtime_norm", "Переработки / лимит",
            "z_score", "Взвешенная сумма до сигмоиды"
    );

    /** Неизвестный признак: нейтральная иконка и статус «normal». */
    private static final FeatureMeta UNKNOWN_META =
            new FeatureMeta("", "📌", v -> "normal");

    /**
     * @param snapshot JSON-снимок признаков (может быть {@code null} или пустым)
     * @return карточки в фиксированном порядке {@link RiskFeatureCard#DISPLAY_ORDER}
     */
    public List<RiskFeatureCard> buildFromSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return RiskFeatureCard.empty();
        }

        List<RiskFeatureCard> cards = new ArrayList<>(snapshot.size());
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            String key = entry.getKey();
            double value = entry.getValue() instanceof Number number
                    ? number.doubleValue()
                    : 0.0;

            FeatureMeta meta = FEATURE_META.getOrDefault(key, UNKNOWN_META);
            String threshold = meta.thresholdFn().apply(value);

            cards.add(new RiskFeatureCard(
                    key,
                    meta.label().isEmpty() ? key : meta.label(),
                    meta.icon(),
                    value,
                    threshold,
                    COLOR_CLASSES.getOrDefault(threshold, "#22c55e"),
                    DESCRIPTIONS.getOrDefault(key, "")
            ));
        }

        cards.sort(Comparator.comparingInt(card -> RiskFeatureCard.displayIndex(card.getKey())));
        return cards;
    }
}
