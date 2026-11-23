package com.staffsense.demo.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.dto.EmployeeRiskDto;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.*;
import com.staffsense.demo.model.AttritionPredictionHistory;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.service.AttritionMlService;
import com.staffsense.demo.service.CsvExportService;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.risk.RiskFeatureCard;
import com.staffsense.demo.service.risk.RiskHistoryService;
import com.staffsense.demo.service.risk.RiskListService;
import com.staffsense.demo.service.risk.RiskPeriod;
import com.staffsense.demo.service.risk.RiskThresholdService;
import com.staffsense.demo.service.risk.RiskTrend;
import com.staffsense.demo.service.risk.RiskFeatureCardService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/risk")
@PreAuthorize("hasAnyRole('ADMIN','EXEC','HR','PM')")
public class RiskController {

    private static final Logger log = LoggerFactory.getLogger(RiskController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final AttritionMlService mlService;
    private final CsvExportService csvExportService;
    private final TeamAccessService teamAccessService;
    private final RiskFeatureCardService featureCardService;
    private final RiskListService riskListService;
    private final RiskHistoryService riskHistoryService;
    private final RiskThresholdService riskThresholdService;

    public RiskController(UserRepository userRepository,
                          AttritionPredictionRepository predictionRepository,
                          AttritionMlService mlService,
                          CsvExportService csvExportService,
                          TeamAccessService teamAccessService,
                          RiskFeatureCardService featureCardService,
                          RiskListService riskListService,
                          RiskHistoryService riskHistoryService,
                          RiskThresholdService riskThresholdService) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.mlService = mlService;
        this.csvExportService = csvExportService;
        this.teamAccessService = teamAccessService;
        this.featureCardService = featureCardService;
        this.riskListService = riskListService;
        this.riskHistoryService = riskHistoryService;
        this.riskThresholdService = riskThresholdService;
    }

    @GetMapping
    public String listRisks(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "15") int size,
                            Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
                // Сортировка по риску выполняется в SQL (ORDER BY COALESCE(riskScore,-1) DESC),
        // а не в памяти после пагинации: см. RiskListService и UserRepository.
        Page<EmployeeRiskDto> riskPage = riskListService.page(currentUser, page, size);

        model.addAttribute("riskPage", riskPage);
        model.addAttribute("employees", riskPage.getContent());
        model.addAttribute("currentUser", currentUser);
        return "risk/list";
    }

    @GetMapping("/{userId}")
    public String viewRiskDetail(@PathVariable Long userId, Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // PM не может открыть риск сотрудника вне своей команды.
        teamAccessService.requireCanViewEmployee(currentUser, employee);

        Optional<AttritionPrediction> pred =
                predictionRepository.findFirstByUserIdOrderByComputedAtDesc(userId);

        // Parse featuresSnapshotJson into visual FeatureCards
        List<RiskFeatureCard> featureCards = new ArrayList<>();
        if (pred.isPresent() && pred.get().getFeaturesSnapshotJson() != null) {
            try {
                Map<String, Object> snapshot = OBJECT_MAPPER.readValue(
                        pred.get().getFeaturesSnapshotJson(),
                        new TypeReference<Map<String, Object>>() {});
                featureCards = featureCardService.buildFromSnapshot(snapshot);
            } catch (Exception e) {
                log.warn("Failed to parse featuresSnapshotJson for userId={}: {}", userId, e.getMessage());
            }
        }

        // XAI: факторы риска, которые вернула ML-модель (ранее не сохранялись).
        List<Map<String, Object>> riskFactors = new ArrayList<>();
        if (pred.isPresent() && pred.get().getTopRiskFactorsJson() != null) {
            try {
                riskFactors = OBJECT_MAPPER.readValue(
                        pred.get().getTopRiskFactorsJson(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse topRiskFactorsJson for userId={}: {}", userId, e.getMessage());
            }
        }

        // Тренд риска из журнала (V9). Последние 12 снимков: больше на
        // мини-графике не читается, а лишние запросы не нужны.
        List<AttritionPredictionHistory> history = riskHistoryService.historyAscending(userId, 12);
        RiskTrend trend = RiskTrend.of(
                history.stream()
                        .map(h -> new RiskTrend.Snapshot(h.getComputedAt(), h.getRiskScore()))
                        .toList(),
                240, 56, 6);

        model.addAttribute("trend", trend);
        model.addAttribute("history", history);
        model.addAttribute("employee", employee);
        model.addAttribute("prediction", pred.orElse(null));
        model.addAttribute("featureCards", featureCards);
        model.addAttribute("riskFactors", riskFactors);
        model.addAttribute("currentUser", currentUser);
        return "risk/details";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Администрирование прогнозов
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Страница администрирования прогнозов: покрытие историей, сводка за период,
     * последние снимки и кнопка массового пересчёта.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public String adminPanel(@RequestParam(name = "period", defaultValue = "MONTH") String period,
                             Model model) {
        RiskPeriod selected = RiskPeriod.fromParam(period);

        model.addAttribute("summary", riskHistoryService.summary(selected));
        model.addAttribute("periods", RiskPeriod.values());
        model.addAttribute("currentPeriod", selected);
        model.addAttribute("latest", riskHistoryService.latest(15));
        model.addAttribute("totalSnapshots", riskHistoryService.totalSnapshots());
        model.addAttribute("heuristicSnapshots", riskHistoryService.heuristicSnapshots());
        model.addAttribute("seedSnapshots", riskHistoryService.seedSnapshots());
        model.addAttribute("withoutHistory", riskHistoryService.employeesWithoutHistory());
        model.addAttribute("lastComputedAt", riskHistoryService.lastComputedAt());
        model.addAttribute("thresholdLow", riskThresholdService.getLow());
        model.addAttribute("thresholdMedium", riskThresholdService.getMedium());
        model.addAttribute("thresholdUpdatedAt", riskThresholdService.getUpdatedAt());
        model.addAttribute("thresholdDefaults", riskThresholdService.isUsingDefaults());
        return "risk/admin";
    }

    /**
     * Массовый пересчёт всех сотрудников. Выполняется асинхронно, страница
     * возвращается сразу — прогресс виден по журналу.
     */
    @PostMapping("/admin/recompute-all")
    @PreAuthorize("hasRole('ADMIN')")
    public String recomputeAll(RedirectAttributes redirectAttrs) {
        mlService.computeAllEmployeesAsync();
        redirectAttrs.addFlashAttribute("success",
                "Массовый пересчёт запущен в фоне: результаты появятся в журнале");
        return "redirect:/risk/admin";
    }

    /**
     * Выгрузка журнала прогнозов за период: {@code WEEK}, {@code MONTH},
     * {@code QUARTER} или {@code ALL}.
     */
    /**
     * Сохранение порогов риска (FR-039). Изменение сразу применяется к
     * работающему приложению и, по желанию, пересчитывает уровни уже
     * сохранённых прогнозов.
     */
    @PostMapping("/admin/thresholds")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateThresholds(@RequestParam(name = "low") double low,
                                   @RequestParam(name = "medium") double medium,
                                   @RequestParam(name = "recompute", defaultValue = "false") boolean recompute,
                                   Authentication auth,
                                   RedirectAttributes redirectAttrs) {
        try {
            User actor = userRepository.findByEmail(auth.getName()).orElse(null);
            riskThresholdService.update(low, medium, actor);
            int changed = recompute ? riskThresholdService.recomputeStoredLevels() : 0;
            redirectAttrs.addFlashAttribute("success",
                    "Пороги обновлены: LOW < " + formatThreshold(low)
                            + ", HIGH ≥ " + formatThreshold(medium)
                            + (changed > 0 ? ". Пересчитано прогнозов: " + changed : ""));
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/risk/admin";
    }

    private static String formatThreshold(double value) {
        return String.format(java.util.Locale.US, "%.1f", value).replace('.', ',');
    }

    @GetMapping("/export/history")
    public void exportHistory(@RequestParam(name = "period", defaultValue = "MONTH") String period,
                              HttpServletResponse response) {
        RiskPeriod selected = RiskPeriod.fromParam(period);
        java.time.LocalDateTime to = java.time.LocalDateTime.now();
        csvExportService.exportRiskHistoryToCsv(response, selected.from(to), to);
    }

    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        csvExportService.exportRisksToCsv(response, currentUser);
    }

    @PostMapping("/{userId}/recompute")
    public String recomputeRisk(@PathVariable Long userId, RedirectAttributes redirectAttrs,
                                Authentication auth) {
        try {
            User actor = auth != null
                    ? userRepository.findByEmail(auth.getName()).orElse(null)
                    : null;
            AttritionPrediction result = mlService.computeAndSave(userId, actor);
            redirectAttrs.addFlashAttribute("success",
                    String.format(java.util.Locale.ROOT, "Прогноз пересчитан: %.1f%% (%s)",
                            result.getRiskScore(), result.getRiskLevel()).replace('.', ','));
        } catch (Exception e) {
            log.error("Error recomputing risk for userId={}: {}", userId, e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Ошибка при пересчёте: " + e.getMessage());
        }
        return "redirect:/risk/" + userId;
    }
}
