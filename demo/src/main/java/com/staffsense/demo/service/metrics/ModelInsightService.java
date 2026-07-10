package com.staffsense.demo.service.metrics;

import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.RiskLevel;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

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
 *   Эти веса — те же самые коэффициенты W1..W7, которые использует
 *   LogisticRegression в Python-сервисе. Они не вычисляются заново,
 *   а заданы вручную, чтобы UI мог показать, какой фактор на что влияет.
 *   Настоящие веса хранятся в обученной модели (model.pkl), а здесь —
 *   их копия для отображения в интерфейсе.
 * ================================================================
 */
@Service
public class ModelInsightService {

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelInsightService(UserRepository userRepository,
                               AttritionPredictionRepository predictionRepository) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════
     *  getFeatureWeights — веса признаков логистической регрессии
     * ═══════════════════════════════════════════════════════════════════════
     *
     *  Эти веса — те же коэффициенты W1..W7, которые использует
     *  LogisticRegression в Python-сервисе (model.py).
     *
     *  Зачем они здесь:
     *    Чтобы отобразить на UI график "Feature Importance" — насколько
     *    каждый фактор влияет на риск увольнения. Чем выше вес —
     *    тем сильнее влияние.
     *
     *  Признак         Вес  Почему?
     *  ───────         ───  ───────
     *  salary_ratio    1.2  Зарплата — самый сильный фактор удержания
     *  workload_index  1.0  Перегрузка — прямой путь к выгоранию
     *  kpi_norm        0.8  Низкий KPI → снижение мотивации
     *  tenure_norm     0.6  Новые сотрудники уходят чаще всего
     *  satisfaction    1.1  Недовольство — второй по значимости
     *  missed_1on1     0.9  Пропуск встреч = разрыв связи
     *  overtime_norm   0.7  Хронические переработки → выгорание
     *
     *  ⚠️ ВАЖНО: Это статическая копия. Настоящие веса вычисляет
     *     scikit-learn при обучении на dataset_training.csv.
     *     Но для отображения UI они должны совпадать (т.к. признаки
     *     и их влияние не меняются между запусками).
     *
     *  Связь с Python:
     *    При обучении LogisticRegression в model.py:
     *      model.coef_ == [1.2, 1.0, 0.8, 0.6, 1.1, 0.9, 0.7]
     *      model.intercept_ == -1.5
     *
     *  Формула расчёта logit:
     *    logit = -1.5 + 1.2*salary + 1.0*workload + 0.8*kpi
     *                  + 0.6*tenure + 1.1*satisfaction + 0.9*missed + 0.7*overtime
     * ═══════════════════════════════════════════════════════════════════════
     */
    public List<FeatureWeight> getFeatureWeights() {
        return List.of(
            new FeatureWeight("salary_ratio", "Уровень зарплаты", 1.2,
                "Чем ниже зарплата относительно рынка, тем выше риск. Коэффициент 1.2 — сильный фактор."),
            new FeatureWeight("workload_index", "Индекс нагрузки", 1.0,
                "Превышение нормы нагрузки (>1.0) увеличивает риск выгорания."),
            new FeatureWeight("kpi_norm", "Показатели KPI", 0.8,
                "Низкие KPI сигнализируют о проблемах в работе и снижении мотивации."),
            new FeatureWeight("tenure_months_norm", "Стаж в компании", 0.6,
                "Новые сотрудники имеют более высокий риск увольнения."),
            new FeatureWeight("satisfaction_norm", "Удовлетворённость", 1.1,
                "Низкая удовлетворённость — второй по значимости предиктор увольнения."),
            new FeatureWeight("missed_1on1_ratio", "Пропуски встреч 1:1", 0.9,
                "Регулярные пропуски 1:1 — признак разрыва коммуникации с руководством."),
            new FeatureWeight("overtime_norm", "Переработки", 0.7,
                "Хронические переработки ведут к выгоранию и снижению продуктивности.")
        );
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
        private double weight;
        private String description;
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
