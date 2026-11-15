package com.staffsense.demo.service.risk;

import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.GradeLevel;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Стоимость под риском — сколько компания теряет, если сотрудники с ненулевой
 * вероятностью ухода действительно уйдут.
 *
 * <p>Формула: {@code стоимость = (risk_score / 100) × оклад × месяцы(грейд)}.
 *
 * <p>Коэффициенты заданы по данным местного исследования: El Group Consulting,
 * рынок зарплат и компенсаций Кыргызстана 2025 — потеря сотрудника стоит
 * от 0,5 до 2 годовых окладов; для рядовых специалистов 100–150 % (12–18
 * месячных окладов), для руководителей 200 % и более. Значения вынесены в
 * конфигурацию, потому что это политика компании, а не константа кода.
 *
 * <p>Если конфигурация пуста, сервис не считает ничего и возвращает
 * {@link Optional#empty()} — интерфейс показывает прочерк вместо выдуманного
 * числа.
 */
@Service
public class RiskCostService {

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;

    /** Месяцы оклада по грейдам; пустая карта — расчёт отключён. */
    private final Map<GradeLevel, Integer> monthsByGrade;

    public RiskCostService(UserRepository userRepository,
                           AttritionPredictionRepository predictionRepository,
                           @Value("${risk.cost.months-by-grade.junior:}") Integer junior,
                           @Value("${risk.cost.months-by-grade.middle:}") Integer middle,
                           @Value("${risk.cost.months-by-grade.senior:}") Integer senior,
                           @Value("${risk.cost.months-by-grade.lead:}") Integer lead) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;

        Map<GradeLevel, Integer> months = new EnumMap<>(GradeLevel.class);
        putIfPositive(months, GradeLevel.JUNIOR, junior);
        putIfPositive(months, GradeLevel.MIDDLE, middle);
        putIfPositive(months, GradeLevel.SENIOR, senior);
        putIfPositive(months, GradeLevel.LEAD, lead);
        this.monthsByGrade = months;
    }

    private static void putIfPositive(Map<GradeLevel, Integer> target, GradeLevel grade, Integer value) {
        if (value != null && value > 0) {
            target.put(grade, value);
        }
    }

    /** Расчёт включён только при заполненной конфигурации. */
    public boolean isEnabled() {
        return !monthsByGrade.isEmpty();
    }

    /** Сколько месяцев оклада заложено для грейда (null — не задано). */
    public Integer monthsFor(GradeLevel grade) {
        return monthsByGrade.get(grade);
    }

    /**
     * Стоимость под риском по компании.
     *
     * @return сумма или {@link Optional#empty()}, если конфигурация пуста
     */
    public Optional<BigDecimal> total() {
        if (!isEnabled()) {
            return Optional.empty();
        }

        List<User> employees = userRepository.findByActive(true);
        BigDecimal total = BigDecimal.ZERO;
        int counted = 0;

        for (User employee : employees) {
            Optional<AttritionPrediction> prediction =
                    predictionRepository.findFirstByUserIdOrderByComputedAtDesc(employee.getId());
            if (prediction.isEmpty() || prediction.get().getRiskScore() == null) {
                continue;
            }
            BigDecimal cost = costFor(employee, prediction.get().getRiskScore());
            if (cost != null) {
                total = total.add(cost);
                counted++;
            }
        }

        if (counted == 0) {
            return Optional.of(BigDecimal.ZERO);
        }
        return Optional.of(total.setScale(0, RoundingMode.HALF_UP));
    }

    /** Стоимость риска по одному сотруднику; null — нет оклада или грейда. */
    public BigDecimal costFor(User employee, double riskScore) {
        Integer months = monthsByGrade.get(employee.getGradeLevel());
        if (months == null || employee.getSalary() == null || riskScore <= 0) {
            return null;
        }
        BigDecimal probability = BigDecimal.valueOf(riskScore).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return employee.getSalary()
                .multiply(BigDecimal.valueOf(months))
                .multiply(probability)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Месяцы по грейдам — для отображения источника в интерфейсе. */
    public Map<GradeLevel, Integer> monthsByGrade() {
        return Map.copyOf(monthsByGrade);
    }
}
