package com.staffsense.demo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Регрессионные тесты генератора датасета.
 *
 * <p>Покрывают два дефекта, найденных при аудите:
 * <ol>
 *   <li>десятичный разделитель — файл должен быть читаем как CSV с 10 колонками
 *       независимо от локали машины (ранее на ru-RU писалась запятая);</li>
 *   <li>знак связи признака с риском — низкая зарплата должна повышать риск
 *       (ранее формула давала обратную зависимость).</li>
 * </ol>
 */
class DatasetGeneratorTest {

    private static final int EXPECTED_COLUMNS = 10;

    @TempDir
    Path tempDir;

    private List<String[]> generateAndReadTrainingRows() throws Exception {
        // Пороги BR-007 теперь приходят из конфигурации, а не из констант класса.
        DatasetGenerator generator = new DatasetGenerator(new RiskThresholdProperties());
        ReflectionTestUtils.setField(generator, "generateOnStartup", true);
        ReflectionTestUtils.setField(generator, "outputDir", tempDir.toString());

        generator.generate();

        Path training = tempDir.resolve("dataset_training.csv");
        Path test = tempDir.resolve("dataset_test.csv");
        assertTrue(Files.exists(training), "dataset_training.csv должен быть создан");
        assertTrue(Files.exists(test), "dataset_test.csv должен быть создан");

        List<String> lines = Files.readAllLines(training);
        List<String[]> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    @Test
    void csvUsesDotAsDecimalSeparatorAndHasTenColumns() throws Exception {
        generateAndReadTrainingRows();
        List<String> lines = Files.readAllLines(tempDir.resolve("dataset_training.csv"));

        String header = lines.get(0);
        assertEquals(EXPECTED_COLUMNS, header.split(",", -1).length, "Заголовок должен содержать 10 колонок");
        assertTrue(header.endsWith("attrition"), "Заголовок должен заканчиваться колонкой attrition");

        assertEquals(561, lines.size(), "Ожидается 560 записей + заголовок");

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.split(",", -1);
            assertEquals(EXPECTED_COLUMNS, parts.length,
                    "Строка " + i + " должна содержать 10 полей (десятичный разделитель — точка): " + line);

            // Все 7 признаков и risk_score обязаны парситься как число с точкой.
            for (int c = 0; c < 8; c++) {
                Double.parseDouble(parts[c]);
            }
            assertTrue(parts[8].equals("LOW") || parts[8].equals("MEDIUM") || parts[8].equals("HIGH"),
                    "risk_level должен быть LOW/MEDIUM/HIGH, получено: " + parts[8]);
            assertTrue(parts[9].equals("0") || parts[9].equals("1"),
                    "attrition должен быть 0 или 1, получено: " + parts[9]);
        }
    }

    @Test
    void lowerSalaryImpliesHigherRisk() throws Exception {
        List<String[]> rows = generateAndReadTrainingRows();

        double sumLowSalary = 0;
        int countLowSalary = 0;
        double sumHighSalary = 0;
        int countHighSalary = 0;

        for (String[] parts : rows) {
            double salaryRatio = Double.parseDouble(parts[0]);
            double riskScore = Double.parseDouble(parts[7]);

            if (salaryRatio < 0.8) {
                sumLowSalary += riskScore;
                countLowSalary++;
            } else if (salaryRatio > 1.2) {
                sumHighSalary += riskScore;
                countHighSalary++;
            }
        }

        assertTrue(countLowSalary > 0 && countHighSalary > 0,
                "В выборке должны быть обе группы по зарплате");
        double avgLowSalary = sumLowSalary / countLowSalary;
        double avgHighSalary = sumHighSalary / countHighSalary;

        assertTrue(avgLowSalary > avgHighSalary,
                "Низкая зарплата должна давать более высокий риск: avg(low)=" + avgLowSalary
                        + " avg(high)=" + avgHighSalary);
    }
}
