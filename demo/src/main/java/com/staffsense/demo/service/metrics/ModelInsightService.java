package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.RiskLevel;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ================================================================
 * ФАЙЛ:       ModelInsightService.java
 * ПУТЬ:       demo/src/main/java/com/staffsense/demo/service/metrics/
 * НАЗНАЧЕНИЕ: Сервис для отображения ML-аналитики на веб-странице
 *             ml-model.html (Thymeleaf).
 * ЧТО ДЕЛАЕТ:
 *   - Предоставляет ВЕСА признаков (FeatureWeight) для графика
 *     "Feature Importance" — те же коэффициенты, что у Python-модели
 *   - Собирает статистику распределения рисков (RiskDistribution)
 *     для круговой диаграммы
 *   - Возвращает список сотрудников с их riskScore (EmployeeRiskInsight)
 *     для столбчатой диаграммы сравнения
 *   - Возвращает полные вектора признаков (EmployeeFeatureVector)
 *     для тепловой карты и точечной диаграммы
 *
 * ОТКУДА БЕРУТСЯ ДАННЫЕ:
 *   Все предсказания (AttritionPrediction) ранее вычислены и сохранены
 *   в БД через AttritionMlService (который вызывает Python FastAPI).
 *   Этот сервис просто читает их из репозитория и структурирует.
 *
 * ВЕСА ПРИЗНАКОВ (getFeatureWeights):
 *   Коэффициенты запрашиваются у ML-сервиса (GET /api/v1/model-info) — это
 *   фактические веса действующей модели (FR-018). Если ML-сервис недоступен,
 *   возвращается экспертная оценка с source = EXPERT, и страница подписывает
 *   график как оценку. Кэшируются на ml.service.model-info-cache-ms.
 * ================================================================
 */
@Service
public class ModelInsightService {

    private static final Logger log = LoggerFactory.getLogger(ModelInsightService.class);

    /** Источник весов: коэффициенты обученной модели (FR-018). */
    public static final String SOURCE_TRAINED = "TRAINED";

    /** Источник весов: экспертная оценка, ML-сервис недоступен. */
    public static final String SOURCE_EXPERT = "EXPERT";

    /** Порядок признаков должен совпадать с FEATURE_NAMES в Python-сервисе. */
    private static final List<String> FEATURE_ORDER = List.of(
            "salary_ratio", "workload_index", "kpi_norm", "tenure_months_norm",
            "satisfaction_norm", "missed_1on1_ratio", "overtime_norm");

    private static final Map<String, String> FEATURE_LABELS = Map.of(
            "salary_ratio", "Уровень зарплаты",
            "workload_index", "Индекс нагрузки",
            "kpi_norm", "Показатели KPI",
            "tenure_months_norm", "Стаж в компании",
            "satisfaction_norm", "Удовлетворённость",
            "missed_1on1_ratio", "Пропуски встреч 1:1",
            "overtime_norm", "Переработки");

    private static final Map<String, String> FEATURE_DESCRIPTIONS = Map.of(
            "salary_ratio", "Чем ниже зарплата относительно рынка, тем выше риск увольнения.",
            "workload_index", "Превышение нормы нагрузки увеличивает риск выгорания.",
            "kpi_norm", "Низкие KPI сигнализируют о проблемах в работе и снижении мотивации.",
            "tenure_months_norm", "Новые сотрудники имеют более высокий риск увольнения.",
            "satisfaction_norm", "Низкая удовлетворённость — один из сильнейших предикторов увольнения.",
            "missed_1on1_ratio", "Регулярные пропуски 1:1 — признак разрыва коммуникации с руководством.",
            "overtime_norm", "Хронические переработки ведут к выгоранию и снижению продуктивности.");

    /**
     * Экспертная оценка весов — используется только как резерв, когда ML-сервис
     * недоступен. В этом случае UI обязан показать, что это не коэффициенты
     * обученной модели (FR-018).
     */
    private static final List<Double> EXPERT_COEFFICIENTS =
            List.of(1.2, 1.0, 0.8, 0.6, 1.1, 0.9, 0.7);

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final RestTemplate restTemplate;
    private final String modelInfoUrl;
    private final long cacheTtlMs;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Кэш весов: страница ML-модели не должна ждать ML-сервис при каждой
     * отрисовке (read-timeout 2 с), а веса меняются только при переобучении.
     */
    private volatile CachedWeights weightsCache;

    public ModelInsightService(UserRepository userRepository,
                               AttritionPredictionRepository predictionRepository,
                               RestTemplate restTemplate,
                               @Value("${ml.service.model-info-url:http://localhost:8001/api/v1/model-info}")
                               String modelInfoUrl,
                               @Value("${ml.service.model-info-cache-ms:60000}") long cacheTtlMs) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.restTemplate = restTemplate;
        this.modelInfoUrl = modelInfoUrl;
        this.cacheTtlMs = cacheTtlMs;
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     *  getFeatureWeights — веса признаков логистической регрессии
     * ═══════════════════════════════════════════════════════════════════════
     *
     *  Веса запрашиваются у ML-сервиса (GET /api/v1/model-info) — это
     *  фактические коэффициенты действующей модели (FR-018).
     *
     *  Ранее здесь лежала захардкоженная «экспертная» копия
     *  [1.2, 1.0, 0.8, 0.6, 1.1, 0.9, 0.7] с комментарием, что она совпадает
     *  с model.coef_ обученной модели. Это было неверно: после
     *  StandardScaler коэффициенты обученной модели имеют порядок 0,1–0,3
     *  (см. model/weights.json: b1 = -0,12, b3 = -0,24), поэтому UI показывал
     *  вымышленные числа и неверные знаки влияния.
     *
     *  Если ML-сервис недоступен, возвращается та же экспертная оценка, но
     *  с source = EXPERT, и страница обязана подписать график как оценку,
     *  а не как результат обучения (BR-006: fallback допустим, подмена — нет).
     * ═══════════════════════════════════════════════════════════════════════
     */
    public List<FeatureWeight> getFeatureWeights() {
        CachedWeights cached = weightsCache;
        if (cached != null && !cached.isExpired()) {
            return cached.weights();
        }

        List<FeatureWeight> weights = fetchWeightsFromMlService();
        if (weights == null) {
            log.warn("Веса обученной модели недоступны ({}), показываю экспертную оценку",
                    modelInfoUrl);
            weights = buildWeights(EXPERT_COEFFICIENTS, SOURCE_EXPERT);
        }

        weightsCache = new CachedWeights(weights, System.currentTimeMillis() + cacheTtlMs);
        return weights;
    }

    /**
     * Запрашивает коэффициенты действующей модели у ML-сервиса (FR-018).
     *
     * @return веса или {@code null}, если ML-сервис недоступен/ответ не разобран —
     *         тогда вызывающий код обязан показать экспертную оценку с пометкой
     */
    private List<FeatureWeight> fetchWeightsFromMlService() {
        try {
            String body = restTemplate.getForObject(modelInfoUrl, String.class);
            if (body == null || body.isBlank()) {
                return null;
            }
            Map<String, Object> info = objectMapper.readValue(
                    body, new TypeReference<Map<String, Object>>() {});
            if (!(info.get("coefficients") instanceof List<?> raw) || raw.size() != FEATURE_ORDER.size()) {
                log.warn("ML-сервис вернул неожиданный набор коэффициентов: {}", info.get("coefficients"));
                return null;
            }

            Map<String, Double> byFeature = new HashMap<>();
            for (Object item : raw) {
                if (item instanceof Map<?, ?> entry
                        && entry.get("feature") != null
                        && entry.get("coefficient") instanceof Number value) {
                    byFeature.put(entry.get("feature").toString(), value.doubleValue());
                }
            }

            List<Double> ordered = new ArrayList<>();
            for (String feature : FEATURE_ORDER) {
                Double coefficient = byFeature.get(feature);
                if (coefficient == null) {
                    log.warn("ML-сервис не вернул коэффициент для признака {}", feature);
                    return null;
                }
                ordered.add(coefficient);
            }

            // ML-сервис сам сообщает, обученная это модель или резервные веса;
            // во втором случае честнее показать их как экспертную оценку.
            boolean trained = "trained".equals(String.valueOf(info.get("weights_source")));
            return buildWeights(ordered, trained ? SOURCE_TRAINED : SOURCE_EXPERT);
        } catch (Exception ex) {
            log.warn("Не удалось получить веса модели из ML-сервиса ({}): {}",
                    modelInfoUrl, ex.getMessage());
            return null;
        }
    }

    /**
     * Собирает список весов для UI.
     *
     * <p>Дополнительно считается относительная значимость {@code importance}
     * (0..1 от максимального по модулю коэффициента): у обученной модели
     * коэффициенты порядка 0,1–0,3, поэтому абсолютные пороги вида «≥ 1.0»
     * больше не подходят для полосы и бейджа «влияние».
     */
    private static List<FeatureWeight> buildWeights(List<Double> coefficients, String source) {
        double maxAbs = coefficients.stream().mapToDouble(Math::abs).max().orElse(0.0);
        List<FeatureWeight> weights = new ArrayList<>();
        for (int i = 0; i < FEATURE_ORDER.size(); i++) {
            String id = FEATURE_ORDER.get(i);
            double coefficient = coefficients.get(i);
            double importance = maxAbs > 0 ? Math.abs(coefficient) / maxAbs : 0.0;
            weights.add(new FeatureWeight(
                    id,
                    FEATURE_LABELS.getOrDefault(id, id),
                    coefficient,
                    importance,
                    describe(id, coefficient, source),
                    source));
        }
        return weights;
    }

    /** Описание фактора; для обученных весов добавляется направление влияния. */
    private static String describe(String id, double coefficient, String source) {
        String base = FEATURE_DESCRIPTIONS.getOrDefault(id, "");
        if (!SOURCE_TRAINED.equals(source)) {
            return base + " (экспертная оценка, а не коэффициент обученной модели)";
        }
        String direction = coefficient < 0
                ? " Рост признака снижает риск."
                : " Рост признака повышает риск.";
        return base + direction + String.format(Locale.US, " Коэффициент модели: %.3f.", coefficient);
    }

    private record CachedWeights(List<FeatureWeight> weights, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    /**
     * Risk distribution statistics across all employees with predictions
     */
    public RiskDistribution getRiskDistribution() {
        List<AttritionPrediction> predictions = predictionRepository.findAll();
        if (predictions.isEmpty()) {
            return new RiskDistribution(0, 0, 0, 0.0);
        }

        Map<RiskLevel, Long> counts = predictions.stream()
                .collect(Collectors.groupingBy(AttritionPrediction::getRiskLevel, Collectors.counting()));

        long high = counts.getOrDefault(RiskLevel.HIGH, 0L);
        long medium = counts.getOrDefault(RiskLevel.MEDIUM, 0L);
        long low = counts.getOrDefault(RiskLevel.LOW, 0L);
        double avgRisk = predictions.stream()
                .mapToDouble(AttritionPrediction::getRiskScore)
                .average().orElse(0.0);

        return new RiskDistribution(high, medium, low, avgRisk);
    }

    /**
     * Employee risk scores for comparison chart
     */
    public List<EmployeeRiskInsight> getEmployeeRiskInsights() {
        List<User> employees = userRepository.findActiveByRole(true, com.staffsense.demo.model.Role.EMPLOYEE);

        List<AttritionPrediction> allPredictions = predictionRepository.findAll();

        return employees.stream().map(emp -> {
            AttritionPrediction pred = allPredictions.stream()
                    .filter(p -> p.getUser().getId().equals(emp.getId()))
                    .max(Comparator.comparing(AttritionPrediction::getComputedAt))
                    .orElse(null);

            double score = pred != null ? pred.getRiskScore() : -1;
            String level = pred != null ? pred.getRiskLevel().name() : "UNKNOWN";

            return new EmployeeRiskInsight(
                    emp.getId(),
                    emp.getFullName(),
                    emp.getDepartment(),
                    score,
                    level
            );
        }).sorted(Comparator.comparingDouble(EmployeeRiskInsight::getRiskScore).reversed())
          .collect(Collectors.toList());
    }

    /**
     * Full feature vectors for every employee — used for heatmap and scatter charts.
     * Each vector includes all 7 normalized features plus risk score and level.
     */
    public List<EmployeeFeatureVector> getEmployeeFeatureVectors() {
        List<User> employees = userRepository.findActiveByRole(true, com.staffsense.demo.model.Role.EMPLOYEE);

        List<AttritionPrediction> allPredictions = predictionRepository.findAll();

        return employees.stream().map(emp -> {
            AttritionPrediction pred = allPredictions.stream()
                    .filter(p -> p.getUser().getId().equals(emp.getId()))
                    .max(Comparator.comparing(AttritionPrediction::getComputedAt))
                    .orElse(null);

            double[] features = new double[7];
            if (pred != null && pred.getFeaturesSnapshotJson() != null) {
                try {
                    Map<String, Object> map = objectMapper.readValue(
                            pred.getFeaturesSnapshotJson(),
                            new TypeReference<Map<String, Object>>() {});
                    features[0] = toDouble(map.getOrDefault("salary_ratio", 0.0));
                    features[1] = toDouble(map.getOrDefault("workload_index", 0.0));
                    features[2] = toDouble(map.getOrDefault("kpi_norm", 0.0));
                    features[3] = toDouble(map.getOrDefault("tenure_months_norm", 0.0));
                    features[4] = toDouble(map.getOrDefault("satisfaction_norm", 0.0));
                    features[5] = toDouble(map.getOrDefault("missed_1on1_ratio", 0.0));
                    features[6] = toDouble(map.getOrDefault("overtime_norm", 0.0));
                } catch (Exception e) {
                    // fall through with zeros
                }
            }

            double score = pred != null ? pred.getRiskScore() : -1;
            String level = pred != null ? pred.getRiskLevel().name() : "UNKNOWN";

            return new EmployeeFeatureVector(
                    emp.getId(), emp.getFullName(), emp.getDepartment(),
                    features[0], features[1], features[2], features[3],
                    features[4], features[5], features[6],
                    score, level
            );
        }).sorted(Comparator.comparingDouble(EmployeeFeatureVector::getRiskScore).reversed())
          .collect(Collectors.toList());
    }

    private static double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        return 0.0;
    }

    @Data
    @AllArgsConstructor
    public static class FeatureWeight {
        private String id;
        private String label;
        /** Сырой коэффициент модели; может быть отрицательным. */
        private double weight;
        /** Относительная значимость 0..1 — для полосы и бейджа «влияние». */
        private double importance;
        private String description;
        /** TRAINED — коэффициенты обученной модели, EXPERT — экспертная оценка. */
        private String source;
    }

    @Data
    @AllArgsConstructor
    public static class RiskDistribution {
        private long highCount;
        private long mediumCount;
        private long lowCount;
        private double avgRiskScore;
    }

    @Data
    @AllArgsConstructor
    public static class EmployeeFeatureVector {
        private long userId;
        private String fullName;
        private String department;
        private double salaryRatio;
        private double workloadIndex;
        private double kpiNorm;
        private double tenureMonthsNorm;
        private double satisfactionNorm;
        private double missed1on1Ratio;
        private double overtimeNorm;
        private double riskScore;
        private String riskLevel;
    }

    @Data
    @AllArgsConstructor
    public static class EmployeeRiskInsight {
        private long userId;
        private String fullName;
        private String department;
        private double riskScore;
        private String riskLevel;
    }
}
