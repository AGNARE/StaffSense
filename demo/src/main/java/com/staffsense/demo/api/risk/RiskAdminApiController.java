package com.staffsense.demo.api.risk;

import com.staffsense.demo.model.AttritionPredictionHistory;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.AttritionMlService;
import com.staffsense.demo.service.risk.RiskHistoryService;
import com.staffsense.demo.service.risk.RiskPeriod;
import com.staffsense.demo.service.risk.RiskThresholdService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Администрирование прогнозов для нового клиента.
 *
 * <p>Повторяет возможности серверной страницы {@code /risk/admin}, но отдаёт
 * данные, а не HTML: сводку журнала за период, покрытие, последние снимки,
 * смену порогов и массовый пересчёт.
 *
 * <p>Права: чтение — ADMIN и HR (как у страницы), изменения — только ADMIN.
 */
@RestController
@RequestMapping("/api/v1/risk/admin")
@Tag(name = "Администрирование прогнозов", description = "Журнал, покрытие, пороги, пересчёт")
public class RiskAdminApiController {

    private final RiskHistoryService historyService;
    private final RiskThresholdService thresholdService;
    private final AttritionMlService mlService;
    private final UserRepository userRepository;

    public RiskAdminApiController(RiskHistoryService historyService,
                                  RiskThresholdService thresholdService,
                                  AttritionMlService mlService,
                                  UserRepository userRepository) {
        this.historyService = historyService;
        this.thresholdService = thresholdService;
        this.mlService = mlService;
        this.userRepository = userRepository;
    }

    /** Сводка журнала за период и последние снимки. */
    @GetMapping("/journal")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public RiskAdminDtos.Journal journal(
            @RequestParam(name = "period", defaultValue = "MONTH") String period) {

        RiskPeriod selected = RiskPeriod.fromParam(period);
        RiskHistoryService.PeriodSummary summary = historyService.summary(selected);
        List<RiskAdminDtos.Entry> latest = historyService.latest(20).stream()
                .map(this::toEntry)
                .toList();

        return new RiskAdminDtos.Journal(
                selected.name(),
                summary.from(),
                summary.to(),
                summary.total(),
                summary.low(),
                summary.medium(),
                summary.high(),
                summary.distinctUsers(),
                latest,
                new RiskAdminDtos.Coverage(
                        historyService.totalSnapshots(),
                        historyService.heuristicSnapshots(),
                        historyService.seedSnapshots(),
                        historyService.employeesWithoutHistory(),
                        historyService.lastComputedAt()));
    }

    /** Смена порогов; при {@code recompute=true} уровни существующих прогнозов пересчитываются. */
    @PostMapping("/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    public RiskApiDtos.Thresholds updateThresholds(@RequestBody RiskAdminDtos.ThresholdRequest request,
                                                   Authentication auth) {
        User actor = auth != null
                ? userRepository.findByEmail(auth.getName()).orElse(null)
                : null;

        thresholdService.update(request.low(), request.medium(), actor);
        if (request.recompute()) {
            thresholdService.recomputeStoredLevels();
        }

        return new RiskApiDtos.Thresholds(
                thresholdService.getLow(),
                thresholdService.getMedium(),
                thresholdService.getUpdatedAt(),
                thresholdService.isUsingDefaults());
    }

    /** Массовый пересчёт всех сотрудников — в фоне. */
    @PostMapping("/recompute")
    @PreAuthorize("hasRole('ADMIN')")
    public RecomputeAccepted recompute() {
        mlService.computeAllEmployeesAsync();
        return new RecomputeAccepted(true, "Пересчёт запущен в фоне");
    }

    private RiskAdminDtos.Entry toEntry(AttritionPredictionHistory row) {
        return new RiskAdminDtos.Entry(
                row.getComputedAt(),
                row.getUser().getId(),
                row.getUser().getFullName(),
                row.getUser().getDepartment(),
                row.getRiskScore(),
                row.getRiskLevel().name(),
                row.getSource().getTitle());
    }

    public record RecomputeAccepted(boolean accepted, String message) {
    }
}
