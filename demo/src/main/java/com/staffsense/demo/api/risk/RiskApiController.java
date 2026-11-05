package com.staffsense.demo.api.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.dto.EmployeeRiskDto;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.AttritionPredictionHistory;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.AttritionMlService;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.risk.RiskFeatureCard;
import com.staffsense.demo.service.risk.RiskFeatureCardService;
import com.staffsense.demo.service.risk.RiskHistoryService;
import com.staffsense.demo.service.risk.RiskListService;
import com.staffsense.demo.service.risk.RiskThresholdService;
import com.staffsense.demo.service.risk.RiskTrend;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST-контракт рисков увольнения — первый шаг перехода к API-first.
 *
 * <p>Зачем отдельный контроллер, если есть {@code RiskController}: тот отдаёт
 * готовые HTML-страницы через Thymeleaf, здесь — данные. Оба адаптера вызывают
 * <b>одни и те же</b> сервисы ({@link RiskListService}, {@link RiskHistoryService},
 * {@link RiskFeatureCardService}), поэтому логика не дублируется и страница не
 * может показать одно, а API — другое.
 *
 * <p>Права те же, что у HTML-страниц: ADMIN, EXEC, HR, PM; PM видит только свою
 * команду (ограничение внутри сервисов, а не в контроллере).
 *
 * <p>Версия в пути ({@code /api/v1}) — чтобы будущие изменения формата не ломали
 * уже написанный клиент.
 */
@RestController
@RequestMapping("/api/v1/risk")
@PreAuthorize("hasAnyRole('ADMIN','EXEC','HR','PM')")
@Tag(name = "Риски", description = "Прогнозы увольнений: список, карточка, пороги")
public class RiskApiController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RiskListService riskListService;
    private final RiskHistoryService riskHistoryService;
    private final RiskFeatureCardService featureCardService;
    private final RiskThresholdService thresholdService;
    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final TeamAccessService teamAccessService;
    private final AttritionMlService mlService;

    public RiskApiController(RiskListService riskListService,
                             RiskHistoryService riskHistoryService,
                             RiskFeatureCardService featureCardService,
                             RiskThresholdService thresholdService,
                             UserRepository userRepository,
                             AttritionPredictionRepository predictionRepository,
                             TeamAccessService teamAccessService,
                             AttritionMlService mlService) {
        this.riskListService = riskListService;
        this.riskHistoryService = riskHistoryService;
        this.featureCardService = featureCardService;
        this.thresholdService = thresholdService;
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.teamAccessService = teamAccessService;
        this.mlService = mlService;
    }

    /**
     * Страница списка рисков, отсортированная по убыванию риска.
     *
     * @param page номер страницы с нуля
     * @param size размер страницы (1..200, чтобы один запрос не выгружал всю базу)
     */
    @GetMapping
    public RiskApiDtos.Page<RiskApiDtos.ListItem> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "15") int size,
            Authentication auth) {

        User viewer = currentUser(auth);
        int safeSize = Math.min(Math.max(size, 1), 200);

        Page<EmployeeRiskDto> result = riskListService.page(viewer, Math.max(page, 0), safeSize);
        List<RiskApiDtos.ListItem> items = result.getContent().stream()
                .map(dto -> new RiskApiDtos.ListItem(
                        dto.getUserId(),
                        dto.getFullName(),
                        dto.getDepartment(),
                        dto.getPosition(),
                        dto.getGradeLevel(),
                        dto.getRiskScore(),
                        dto.getRiskLevel(),
                        dto.getComputedAt(),
                        dto.getTrendDelta(),
                        dto.getTopFactor()))
                .toList();

        return new RiskApiDtos.Page<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /** Карточка прогноза: балл, уровень, признаки, факторы и тренд. */
    @GetMapping("/{userId}")
    public RiskApiDtos.Detail detail(@PathVariable Long userId, Authentication auth) {
        User viewer = currentUser(auth);
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Та же проверка доступа, что и у HTML-страницы.
        teamAccessService.requireCanViewEmployee(viewer, employee);

        Optional<AttritionPrediction> prediction =
                predictionRepository.findFirstByUserIdOrderByComputedAtDesc(userId);

        List<RiskApiDtos.Feature> features = featuresOf(prediction.orElse(null));
        List<RiskApiDtos.Factor> factors = factorsOf(prediction.orElse(null));
        RiskTrend trend = trendOf(userId);

        return new RiskApiDtos.Detail(
                employee.getId(),
                employee.getFullName(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getGradeLevel().name(),
                prediction.map(AttritionPrediction::getRiskScore).orElse(null),
                prediction.map(p -> p.getRiskLevel().name()).orElse("UNKNOWN"),
                prediction.map(AttritionPrediction::getComputedAt).orElse(null),
                features,
                factors,
                RiskApiDtos.trendOf(trend),
                latestSource(userId));
    }

    /**
     * Пересчёт прогноза одного сотрудника.
     *
     * <p>Раньше это был только HTML-POST с формы; новому клиенту нужен тот же
     * сценарий в JSON. Возвращаем обновлённую карточку, чтобы интерфейс не
     * делал второй запрос.
     */
    @PostMapping("/{userId}/recompute")
    @PreAuthorize("hasAnyRole('ADMIN','HR','EXEC','PM')")
    public RiskApiDtos.Detail recompute(@PathVariable Long userId, Authentication auth) {
        User viewer = currentUser(auth);
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // PM пересчитывает только своих.
        teamAccessService.requireCanViewEmployee(viewer, employee);
        mlService.computeAndSave(userId, viewer);

        return detail(userId, auth);
    }

    /** Действующие пороги уровней (FR-039) — чтобы клиент рисовал те же границы. */
    @GetMapping("/thresholds")
    public RiskApiDtos.Thresholds thresholds() {
        return new RiskApiDtos.Thresholds(
                thresholdService.getLow(),
                thresholdService.getMedium(),
                thresholdService.getUpdatedAt(),
                thresholdService.isUsingDefaults());
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Не удалось определить пользователя");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }

    private List<RiskApiDtos.Feature> featuresOf(AttritionPrediction prediction) {
        if (prediction == null || prediction.getFeaturesSnapshotJson() == null) {
            return List.of();
        }
        try {
            Map<String, Object> snapshot = MAPPER.readValue(
                    prediction.getFeaturesSnapshotJson(),
                    new TypeReference<Map<String, Object>>() {});
            List<RiskApiDtos.Feature> result = new ArrayList<>();
            for (RiskFeatureCard card : featureCardService.buildFromSnapshot(snapshot)) {
                result.add(new RiskApiDtos.Feature(
                        card.getKey(), card.getLabel(), card.getIcon(), card.getValue(),
                        card.getThreshold(), card.getColorClass(), card.getDescription()));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<RiskApiDtos.Factor> factorsOf(AttritionPrediction prediction) {
        if (prediction == null || prediction.getTopRiskFactorsJson() == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(
                    prediction.getTopRiskFactorsJson(),
                    new TypeReference<List<Map<String, Object>>>() {});
            List<RiskApiDtos.Factor> result = new ArrayList<>(raw.size());
            for (Map<String, Object> item : raw) {
                // Ключ признака переводим в человекочитаемое название — та же
                // таблица подписей, что и в списке рисков.
                String key = String.valueOf(item.getOrDefault("factor", ""));
                String label = RiskFeatureCardService.labelFor(key);
                result.add(new RiskApiDtos.Factor(
                        label != null ? label : key,
                        item.get("impact") instanceof Number n ? n.doubleValue() : 0.0,
                        String.valueOf(item.getOrDefault("description", ""))));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Источник последнего снимка: по нему интерфейс объясняет отсутствие XAI. */
    private String latestSource(Long userId) {
        return riskHistoryService.historyAscending(userId, 1).stream()
                .findFirst()
                .map(row -> row.getSource().name())
                .orElse(null);
    }

    private RiskTrend trendOf(Long userId) {
        List<AttritionPredictionHistory> history = riskHistoryService.historyAscending(userId, 12);
        return RiskTrend.of(
                history.stream()
                        .map(h -> new RiskTrend.Snapshot(h.getComputedAt(), h.getRiskScore()))
                        .toList(),
                240, 56, 6);
    }
}
