package com.staffsense.demo.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Random;

/**
 * Генератор синтетического датасета для обучения ML-модели
 * (FR-029 — скачивание датасета обучения).
 *
 * <p><b>Исправлено относительно исходной версии:</b>
 * <ol>
 *   <li><b>Разделитель дробной части.</b> Ранее {@code String.format} вызывался без
 *       {@link Locale}, поэтому на локали {@code ru-RU} числа писались через запятую
 *       ({@code 0,8855}), и CSV становился нечитаемым: 18 полей вместо 9,
 *       {@code pandas.read_csv} падал с ParserError. Теперь всегда {@code Locale.US}.</li>
 *   <li><b>Знаки признаков.</b> Ранее веса были положительными для зарплаты, KPI,
 *       стажа и удовлетворённости, то есть рост этих показателей <i>увеличивал</i>
 *       риск увольнения — прямое противоречие предметной области и спецификации.
 *       Теперь используется дефицитная форма (см. ниже).</li>
 *   <li><b>Схема.</b> Добавлена колонка {@code attrition} — тот же формат, что
 *       создаёт {@code train.py} в StaffSenseML, поэтому датасеты взаимозаменяемы.</li>
 *   <li><b>Побочные эффекты.</b> Запись файлов можно отключить свойством
 *       {@code staffsense.dataset.generate-on-startup} (в тестах выключено).</li>
 * </ol>
 *
 * <p><b>Формула генерации (дефицитная форма, спецификация §6.3):</b>
 * <pre>
 *   deficit_salary       = 1 - salary_ratio
 *   deficit_workload     = max(workload_index - 1.0, 0)     // норма = 1.0
 *   deficit_kpi          = 1 - kpi_norm
 *   deficit_tenure       = 1 - tenure_months_norm
 *   deficit_satisfaction = 1 - satisfaction_norm
 *   deficit_missed       = missed_1on1_ratio
 *   deficit_overtime     = overtime_norm
 *
 *   z           = -1.5 + 1.2*deficit_salary + 1.0*deficit_workload + 0.8*deficit_kpi
 *                      + 0.6*deficit_tenure + 1.1*deficit_satisfaction
 *                      + 0.9*deficit_missed + 0.7*deficit_overtime
 *   probability = 1 / (1 + exp(-z))
 *   risk_score  = probability * 100
 *   risk_level  = HIGH (>=66) | MEDIUM (>=33) | LOW (<33)
 *   attrition   ~ Bernoulli(probability)
 * </pre>
 *
 * <p>Распределения признаков совпадают с {@code train.py} (StaffSenseML),
 * чтобы оба генератора давали сопоставимые данные.
 */
@Component
public class DatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatasetGenerator.class);

    /** Единый поток случайных чисел для train+test: test — продолжение train. */
    private static final Random RND = new Random(42);

    private static final int TRAIN_SIZE = 560;
    private static final int TEST_SIZE = 140;

    // ── Веса дефицитной формы ────────────────────────────────────────────
    private static final double BIAS = -1.5;
    private static final double W_SALARY = 1.2;
    private static final double W_WORKLOAD = 1.0;
    private static final double W_KPI = 0.8;
    private static final double W_TENURE = 0.6;
    private static final double W_SATISFACTION = 1.1;
    private static final double W_MISSED_1ON1 = 0.9;
    private static final double W_OVERTIME = 0.7;

    // ── Пороги уровней риска (BR-007) ────────────────────────────────────
    // Значения приходят из risk.thresholds (application.yml): до этого они были
    // продублированы константами здесь, в AttritionMlService и DataInitializer.
    private final RiskThresholdProperties riskThresholds;

    @Value("${staffsense.dataset.generate-on-startup:true}")
    private boolean generateOnStartup;

    public DatasetGenerator(RiskThresholdProperties riskThresholds) {
        this.riskThresholds = riskThresholds;
    }

    @Value("${staffsense.dataset.output-dir:.}")
    private String outputDir;

    @PostConstruct
    public void generate() {
        if (!generateOnStartup) {
            log.info("Dataset generation on startup is disabled (staffsense.dataset.generate-on-startup=false)");
            return;
        }
        try {
            Path dir = Paths.get(outputDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path trainingPath = dir.resolve("dataset_training.csv");
            Path testPath = dir.resolve("dataset_test.csv");

            generateCsv(trainingPath, TRAIN_SIZE);
            generateCsv(testPath, TEST_SIZE);

            log.info("Dataset files created in {}:", dir);
            log.info("  Training: {} ({} records)", trainingPath, TRAIN_SIZE);
            log.info("  Test:     {} ({} records)", testPath, TEST_SIZE);
        } catch (IOException e) {
            log.error("Failed to generate dataset CSV", e);
        }
    }

    /**
     * Записывает CSV с заголовком
     * {@code salary_ratio,workload_index,kpi_norm,tenure_months_norm,satisfaction_norm,missed_1on1_ratio,overtime_norm,risk_score,risk_level,attrition}.
     *
     * <p>Формат чисел всегда с точкой (Locale.US) — CSV должен быть читаем
     * любым инструментом независимо от локали машины.
     */
    private void generateCsv(Path path, int count) throws IOException {
        try (FileWriter fw = new FileWriter(path.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
            fw.write("salary_ratio,workload_index,kpi_norm,tenure_months_norm,satisfaction_norm,"
                    + "missed_1on1_ratio,overtime_norm,risk_score,risk_level,attrition\n");

            for (int i = 0; i < count; i++) {
                // ── Признаки: распределения как в train.py ──────────────
                double salaryRatio = clamp(0.5 + RND.nextGaussian() * 0.25 + 0.5, 0.4, 2.0);
                double workloadIndex = clamp(0.9 + RND.nextGaussian() * 0.35, 0.1, 2.0);
                double kpiNorm = clamp(0.7 + RND.nextGaussian() * 0.15, 0.0, 1.0);
                double tenureNorm = Math.min(RND.nextDouble() * 95.0 + 1.0, 96.0) / 60.0;
                tenureNorm = Math.min(tenureNorm, 1.0);
                double satisfactionNorm = clamp(0.65 + RND.nextGaussian() * 0.2, 0.0, 1.0);
                double missed1on1 = beta15();          // Beta(1, 5)
                double overtimeNorm = clamp(0.3 + RND.nextGaussian() * 0.25, 0.0, 1.0);

                // ── Дефициты: чем больше дефицит, тем выше риск ─────────
                double deficitSalary = 1.0 - salaryRatio;
                double deficitWorkload = Math.max(workloadIndex - 1.0, 0.0);
                double deficitKpi = 1.0 - kpiNorm;
                double deficitTenure = 1.0 - tenureNorm;
                double deficitSatisfaction = 1.0 - satisfactionNorm;
                double deficitMissed = missed1on1;
                double deficitOvertime = overtimeNorm;

                double z = BIAS
                        + W_SALARY * deficitSalary
                        + W_WORKLOAD * deficitWorkload
                        + W_KPI * deficitKpi
                        + W_TENURE * deficitTenure
                        + W_SATISFACTION * deficitSatisfaction
                        + W_MISSED_1ON1 * deficitMissed
                        + W_OVERTIME * deficitOvertime;

                double probability = sigmoid(z);
                double riskScore = Math.min(100.0, Math.max(0.0, probability * 100.0));

                // BR-007: уровень риска по порогам из конфигурации
                String level = riskThresholds.levelFor(riskScore).name();

                // Метка увольнения — сэмплируется по вероятности (шум в данных).
                int attrition = RND.nextDouble() < probability ? 1 : 0;

                // Locale.US — десятичный разделитель всегда точка.
                fw.write(String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.1f,%s,%d%n",
                        salaryRatio, workloadIndex, kpiNorm, tenureNorm,
                        satisfactionNorm, missed1on1, overtimeNorm, riskScore, level, attrition));
            }
        }
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static double clamp(double v, double min, double max) {
        return Math.min(max, Math.max(min, v));
    }

    /**
     * Выборка из Beta(1, 5) методом обратной функции распределения.
     * Для Beta(1, b): {@code x = 1 - (1-u)^(1/b)}.
     * Используется для доли пропущенных встреч 1:1 (как в train.py).
     */
    private static double beta15() {
        double u = RND.nextDouble();
        return 1.0 - Math.pow(1.0 - u, 1.0 / 5.0);
    }
}
