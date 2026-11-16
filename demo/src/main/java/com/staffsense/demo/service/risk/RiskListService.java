package com.staffsense.demo.service.risk;

import com.staffsense.demo.dto.EmployeeRiskDto;
import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.AttritionPredictionHistory;
import com.staffsense.demo.repository.AttritionPredictionHistoryRepository;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Формирует страницу списка рисков: сортировка по риску — в запросе.
 *
 * <p>Что изменилось по сравнению с первой версией в {@code RiskController}:
 * <ul>
 *   <li>сортировка по риску выполнялась в памяти <b>после</b> пагинации,
 *       поэтому страница содержала «случайных» сотрудников из алфавитного
 *       диапазона, отсортированных внутри себя. Теперь порядок задаёт SQL
 *       ({@code ORDER BY COALESCE(p.riskScore, -1) DESC});</li>
 *   <li>прогнозы для страницы загружаются одним запросом
 *       ({@code findByUserIdIn}) вместо запроса на каждого сотрудника;</li>
 *   <li>сотрудники без прогноза идут в конце со уровнем {@code UNKNOWN}, как
 *       и раньше — их риск неизвестен, а не нулевой.</li>
 * </ul>
 *
 * <p>Видимость: PM получает только свою команду (матрица доступа, раздел 21 ТЗ),
 * остальные роли с доступом к /risk — всех сотрудников с ролью EMPLOYEE.
 */
@Service
public class RiskListService {

    private static final double UNKNOWN_RISK_SCORE = -1;

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final AttritionPredictionHistoryRepository historyRepository;

    public RiskListService(UserRepository userRepository,
                           AttritionPredictionRepository predictionRepository,
                           AttritionPredictionHistoryRepository historyRepository) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * @param viewer тот, кто смотрит список (определяет видимость)
     * @param page   номер страницы с нуля
     * @param size   размер страницы
     */
    @Transactional(readOnly = true)
    public Page<EmployeeRiskDto> page(User viewer, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<User> employees = viewer.getRole() == Role.PM
                ? userRepository.findByManagerAndActiveAndRoleOrderByRisk(
                        viewer.getId(), true, Role.EMPLOYEE, pageable)
                : userRepository.findByActiveAndRoleOrderByRisk(true, Role.EMPLOYEE, pageable);

        Map<Long, AttritionPrediction> latestByUser = latestPredictions(employees.getContent());
        Map<Long, List<AttritionPredictionHistory>> historyByUser = historyFor(employees.getContent());

        List<EmployeeRiskDto> content = employees.getContent().stream()
                .map(user -> {
                    EmployeeRiskDto dto = toDto(user, latestByUser.get(user.getId()));
                    enrich(dto, latestByUser.get(user.getId()), historyByUser.get(user.getId()));
                    return dto;
                })
                .toList();

        return new PageImpl<>(content, pageable, employees.getTotalElements());
    }

    /**
     * Один запрос на страницу вместо N+1. Если у сотрудника по какой-то причине
     * оказалось несколько прогнозов, берём самый свежий.
     */
    private Map<Long, AttritionPrediction> latestPredictions(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(users.size());
        for (User user : users) {
            ids.add(user.getId());
        }

        Map<Long, AttritionPrediction> result = new HashMap<>();
        for (AttritionPrediction prediction : predictionRepository.findByUserIdIn(ids)) {
            Long userId = prediction.getUser().getId();
            result.merge(userId, prediction, (current, candidate) ->
                    Comparator.nullsFirst(Comparator.<java.time.LocalDateTime>naturalOrder())
                            .compare(candidate.getComputedAt(), current.getComputedAt()) > 0
                            ? candidate : current);
        }
        return result;
    }

    /**
     * Тренд и главный фактор для строки списка.
     *
     * <p>Без этого колонки «Тренд» и «Главный фактор» в интерфейсе пришлось бы
     * либо убрать, либо заполнять «—», хотя данные уже есть в журнале и в
     * объяснении модели.
     */
    private void enrich(EmployeeRiskDto dto, AttritionPrediction prediction,
                        List<AttritionPredictionHistory> history) {
        if (history != null && history.size() >= 2) {
            double latest = history.get(0).getRiskScore();
            double previous = history.get(1).getRiskScore();
            dto.setTrendDelta(Math.round((latest - previous) * 10.0) / 10.0);
        }
        if (prediction != null && prediction.getTopRiskFactorsJson() != null) {
            dto.setTopFactor(parseTopFactor(prediction.getTopRiskFactorsJson()));
        }
    }

    /** Первый фактор из сохранённого объяснения ML; при ошибке — null. */
    private String parseTopFactor(String json) {
        try {
            List<Map<String, Object>> factors = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return factors.isEmpty() ? null
                    : RiskFeatureCardService.labelFor(String.valueOf(factors.get(0).get("factor")));
        } catch (Exception e) {
            return null;
        }
    }

    /** История страницы одним запросом: по убыванию даты, сгруппированная по сотруднику. */
    private Map<Long, List<AttritionPredictionHistory>> historyFor(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(users.size());
        for (User user : users) {
            ids.add(user.getId());
        }
        Map<Long, List<AttritionPredictionHistory>> result = new HashMap<>();
        for (AttritionPredictionHistory row : historyRepository.findByUserIdInOrderByComputedAtDesc(ids)) {
            result.computeIfAbsent(row.getUser().getId(), k -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private EmployeeRiskDto toDto(User employee, AttritionPrediction prediction) {
        EmployeeRiskDto dto = new EmployeeRiskDto();
        dto.setUserId(employee.getId());
        dto.setFullName(employee.getFullName());
        dto.setDepartment(employee.getDepartment());
        dto.setPosition(employee.getPosition());
        dto.setGradeLevel(employee.getGradeLevel().name());

        if (prediction != null) {
            dto.setRiskScore(prediction.getRiskScore());
            dto.setRiskLevel(prediction.getRiskLevel().name());
            dto.setComputedAt(prediction.getComputedAt());
        } else {
            dto.setRiskScore(UNKNOWN_RISK_SCORE);
            dto.setRiskLevel("UNKNOWN");
        }
        return dto;
    }
}
