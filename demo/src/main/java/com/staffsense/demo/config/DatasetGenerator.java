package com.staffsense.demo.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * ================================================================
 * ФАЙЛ:       DatasetGenerator.java
 * ПУТЬ:       demo/src/main/java/com/staffsense/demo/config/
 * НАЗНАЧЕНИЕ: Генерация 400 synthetic записей для обучения ML-модели
 * ЧТО ДЕЛАЕТ: Создаёт dataset_training.csv (320 записей) и
 *              dataset_test.csv (80 записей) с 7 признаками + risk_score
 * ================================================================
 *
 * Это НЕ алгоритм предсказания — это генератор ОБУЧАЮЩИХ данных.
 * Каждая запись считается по той же логистической формуле, которую
 * будет использовать Python-модель (LogisticRegression).
 * Python-скрипт потом обучится на этих CSV и найдёт свои веса.
 *
 * Логистическая регрессия (формула):
 *   logit      = B0 + W1*X1 + W2*X2 + ... + W7*X7
 *   probability = 1 / (1 + exp(-logit))    ← сигмоида
 *   riskScore  = probability * 100
 *   если riskScore >= 66 → HIGH, >= 33 → MEDIUM, иначе → LOW
 *
 * Веса W1..W7 и BIAS (B0) заданы вручную, чтобы сгенерированные
 * данные имели реалистичное распределение рисков.
 */
@Component
public class DatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatasetGenerator.class);
    private static final Random RND = new Random(42);
    private static final int TOTAL = 400;

    // ═══════════════════════════════════════════════════════════════
    //  ВЕСА ЛОГИСТИЧЕСКОЙ РЕГРЕССИИ (для генерации CSV данных)
    // ═══════════════════════════════════════════════════════════════
    //  Каждый вес показывает, насколько сильно признак влияет на риск.
    //  Чем ВЫШЕ вес, тем СИЛЬНЕЕ влияние.
    //  Положительный вес = рост признака увеличивает риск.
    //  Эти же веса используются в ModelInsightService для отображения.
    //
    //  Признак         Вес  Почему?
    //  ───────         ───  ──────
    //  salary_ratio    1.2  Низкая зарплата → сильный предиктор ухода
    //  workload_index  1.0  Перегрузка → выгорание
    //  kpi_norm        0.8  Низкий KPI → демотивация
    //  tenure_norm     0.6  Новые сотрудники уходят чаще
    //  satisfaction    1.1  Недовольство → второй по значимости фактор
    //  missed_1on1     0.9  Пропуск встреч → разрыв коммуникации
    //  overtime_norm   0.7  Переработки → выгорание
    //  BIAS (B0)      -1.5  Смещение (базовый уровень логита)
    // ═══════════════════════════════════════════════════════════════
    private static final double W_SALARY = 1.2;
    private static final double W_WORKLOAD = 1.0;
    private static final double W_KPI = 0.8;
    private static final double W_TENURE = 0.6;
    private static final double W_SATISFACTION = 1.1;
    private static final double W_MISSED_1ON1 = 0.9;
    private static final double W_OVERTIME = 0.7;
    private static final double BIAS = -1.5;

    @PostConstruct
    public void generate() {
        try {
            Path projectDir = Paths.get(".").toAbsolutePath().normalize();
            Path trainingPath = projectDir.resolve("dataset_training.csv");
            Path testPath = projectDir.resolve("dataset_test.csv");

            // 400 records: 320 training + 80 test
            generateCsv(trainingPath, 320);
            generateCsv(testPath, 80);

            log.info("Dataset files created:");
            log.info("  Training: {} (320 records)", trainingPath);
            log.info("  Test:     {} (80 records)", testPath);
        } catch (IOException e) {
            log.error("Failed to generate dataset CSV", e);
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     *  generateCsv — генерация CSV файла с синтетическими данными
     * ═══════════════════════════════════════════════════════════════════
     *  Что происходит:
     *  1. Генерируем 7 случайных признаков (каждый от 0.0 до 1.0)
     *  2. Вычисляем logit = B0 + W1*X1 + ... + W7*X7
     *  3. Применяем сигмоиду: probability = 1 / (1 + e^(-logit))
     *  4. riskScore = probability * 100 (переводим в проценты 0..100)
     *  5. Добавляем шум, чтобы данные были реалистичными
     *  6. Определяем уровень: >=66 HIGH, >=33 MEDIUM, иначе LOW
     *  7. Записываем строку в CSV
     *
     *  Это УЧЕБНЫЙ датасет — настоящая ML-модель будет искать свои веса,
     *  анализируя эти данные. Если данные сгенерированы правильно,
     *  обученная модель найдёт веса, близкие к исходным W1..W7.
     * ═══════════════════════════════════════════════════════════════════
     */
    private void generateCsv(Path path, int count) throws IOException {
        try (FileWriter fw = new FileWriter(path.toFile())) {
            // Заголовок CSV — 7 признаков + целевая переменная (risk_score + risk_level)
            fw.write("salary_ratio,workload_index,kpi_norm,tenure_months_norm,satisfaction_norm,missed_1on1_ratio,overtime_norm,risk_score,risk_level\n");

            for (int i = 0; i < count; i++) {
                // ── ШАГ 1: Генерируем 7 случайных признаков ──────────
                // Каждый признак имитирует нормализованное значение (0..1)
                // для вымышленного сотрудника.
                double salaryRatio       = clamp(0.5 + RND.nextGaussian() * 0.25 + 0.1);  // отношение зарплаты к рынку
                double workloadIndex     = clamp(0.7 + RND.nextGaussian() * 0.3 + 0.3);   // индекс нагрузки (>1 = перегрузка)
                double kpiNorm           = clamp(0.4 + RND.nextGaussian() * 0.2 + 0.1);   // нормализованный KPI (0..1)
                double tenureNorm        = clamp(RND.nextDouble() * 0.9 + 0.1);            // стаж (0 = новый, 1 = 5+ лет)
                double satisfactionNorm  = clamp(0.3 + RND.nextGaussian() * 0.25 + 0.2);  // удовлетворённость (0..1)
                double missed1on1        = clamp(RND.nextDouble() * 0.8);                  // доля пропущенных 1:1 встреч
                double overtimeNorm      = clamp(RND.nextDouble() * 0.8 + 0.1);            // переработки (0..1)

                // ── ШАГ 2: Вычисляем LOGIT (линейная комбинация) ────
                // logit = B0 + W1*X1 + W2*X2 + ... + W7*X7
                // Это взвешенная сумма всех признаков. Чем больше logit,
                // тем выше вероятность увольнения.
                double logit = BIAS
                        + W_SALARY * salaryRatio
                        + W_WORKLOAD * workloadIndex
                        + W_KPI * kpiNorm
                        + W_TENURE * tenureNorm
                        + W_SATISFACTION * satisfactionNorm
                        + W_MISSED_1ON1 * missed1on1
                        + W_OVERTIME * overtimeNorm;

                // ── ШАГ 3: Применяем СИГМОИДУ ────────────────────────
                // Сигмоида "сжимает" logit (который может быть любым
                // числом от -∞ до +∞) в вероятность от 0 до 1:
                //   probability = 1 / (1 + e^(-logit))
                double probability = sigmoid(logit);

                // ── ШАГ 4: Переводим в проценты ──────────────────────
                // riskScore от 0 до 100 — удобно для отображения и хранения
                double riskScore = probability * 100;

                // ── ШАГ 5: Добавляем шум для реалистичности ──────────
                // В реальных данных всегда есть случайные отклонения.
                // Без шума модель была бы "идеальной" и неинтересной.
                riskScore = Math.min(100, Math.max(0, riskScore + (RND.nextGaussian() - 0.5) * 8));

                // ── ШАГ 6: Определяем УРОВЕНЬ РИСКА ──────────────────
                // >= 66% → HIGH   (красный — критический риск)
                // >= 33% → MEDIUM (жёлтый — повышенный риск)
                // < 33%  → LOW    (зелёный — низкий риск)
                String level;
                if (riskScore >= 66) level = "HIGH";
                else if (riskScore >= 33) level = "MEDIUM";
                else level = "LOW";

                // ── ШАГ 7: Записываем строку в CSV ───────────────────
                fw.write(String.format("%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.1f,%s\n",
                        salaryRatio, workloadIndex, kpiNorm, tenureNorm,
                        satisfactionNorm, missed1on1, overtimeNorm, riskScore, level));
            }
        }
    }

    /**
     * ═══════════════════════════════════════════════════════════════════
     *  sigmoid — Сигмоидная функция активации
     * ═══════════════════════════════════════════════════════════════════
     *  Формула:  f(x) = 1 / (1 + e^(-x))
     *
     *  Зачем: превращает ЛЮБОЕ число в вероятность от 0 до 1.
     *
     *  Примеры:
     *    sigmoid(-5) ≈ 0.0067  (очень малая вероятность)
     *    sigmoid(0)  = 0.5     (равновероятно)
     *    sigmoid(+5) ≈ 0.9933  (очень высокая вероятность)
     *
     *  Именно эта функция делает логистическую регрессию "логистической".
     *  Без неё мы бы получили обычную линейную регрессию, которая может
     *  выдавать значения < 0 или > 100 — бессмысленные для вероятности.
     * ═══════════════════════════════════════════════════════════════════
     */
    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Ограничивает значение в диапазоне [0, 1].
     * Признаки и risk_score нормализованы, поэтому значения вне [0,1]
     * были бы некорректны.
     */
    private static double clamp(double v) {
        return Math.min(1.0, Math.max(0.0, v));
    }
}
