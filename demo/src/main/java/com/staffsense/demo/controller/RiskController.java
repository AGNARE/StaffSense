package com.staffsense.demo.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffsense.demo.dto.EmployeeRiskDto;
import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.service.AttritionMlService;
import com.staffsense.demo.service.CsvExportService;
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

    public RiskController(UserRepository userRepository,
                          AttritionPredictionRepository predictionRepository,
                          AttritionMlService mlService,
                          CsvExportService csvExportService) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.mlService = mlService;
        this.csvExportService = csvExportService;
    }

    @GetMapping
    public String listRisks(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "15") int size,
                            Model model, Authentication auth) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName"));
        Page<User> employeePage = userRepository.findByActiveAndRolePaged(true, Role.EMPLOYEE, pageable);

        List<EmployeeRiskDto> dtoList = employeePage.getContent().stream().map(emp -> {
            Optional<AttritionPrediction> pred = predictionRepository.findAll().stream()
                    .filter(p -> p.getUser().getId().equals(emp.getId()))
                    .max(Comparator.comparing(AttritionPrediction::getComputedAt));
            EmployeeRiskDto dto = new EmployeeRiskDto();
            dto.setUserId(emp.getId());
            dto.setFullName(emp.getFullName());
            dto.setDepartment(emp.getDepartment());
            dto.setPosition(emp.getPosition());
            dto.setGradeLevel(emp.getGradeLevel().name());
            if (pred.isPresent()) {
                dto.setRiskScore(pred.get().getRiskScore());
                dto.setRiskLevel(pred.get().getRiskLevel().name());
                dto.setComputedAt(pred.get().getComputedAt());
            } else {
                dto.setRiskScore(-1);
                dto.setRiskLevel("UNKNOWN");
            }
            return dto;
        }).sorted(Comparator.comparingDouble(EmployeeRiskDto::getRiskScore).reversed()).toList();

        Page<EmployeeRiskDto> riskPage = new PageImpl<>(dtoList, pageable, employeePage.getTotalElements());

        model.addAttribute("riskPage", riskPage);
        model.addAttribute("employees", riskPage.getContent());
        model.addAttribute("currentUser", userRepository.findByEmail(auth.getName()).orElseThrow());
        return "risk/list";
    }

    @GetMapping("/{userId}")
    public String viewRiskDetail(@PathVariable Long userId, Model model, Authentication auth) {
        User employee = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Optional<AttritionPrediction> pred = predictionRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .max(Comparator.comparing(AttritionPrediction::getComputedAt));

        // Parse featuresSnapshotJson into visual FeatureCards
        List<FeatureCard> featureCards = new ArrayList<>();
        if (pred.isPresent() && pred.get().getFeaturesSnapshotJson() != null) {
            try {
                Map<String, Object> snapshot = OBJECT_MAPPER.readValue(
                        pred.get().getFeaturesSnapshotJson(),
                        new TypeReference<Map<String, Object>>() {});
                featureCards = FeatureCardBuilder.buildFromSnapshot(snapshot);
            } catch (Exception e) {
                log.warn("Failed to parse featuresSnapshotJson for userId={}: {}", userId, e.getMessage());
            }
        }

        model.addAttribute("employee", employee);
        model.addAttribute("prediction", pred.orElse(null));
        model.addAttribute("featureCards", featureCards);
        model.addAttribute("currentUser", userRepository.findByEmail(auth.getName()).orElseThrow());
        return "risk/details";
    }

    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response) {
        csvExportService.exportRisksToCsv(response);
    }

    @PostMapping("/{userId}/recompute")
    public String recomputeRisk(@PathVariable Long userId, RedirectAttributes redirectAttrs) {
        try {
            AttritionPrediction result = mlService.computeAndSave(userId);
            redirectAttrs.addFlashAttribute("success",
                    String.format("Прогноз пересчитан: %.1f%% (%s)", result.getRiskScore(), result.getRiskLevel()));
        } catch (Exception e) {
            log.error("Error recomputing risk for userId={}: {}", userId, e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Ошибка при пересчёте: " + e.getMessage());
        }
        return "redirect:/risk/" + userId;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  FeatureCard — визуальная карточка одного ML-признака
    // ═══════════════════════════════════════════════════════════════════

    public static class FeatureCard {
        private final String key;
        private final String label;
        private final String icon;
        private final double value;
        private final String threshold;  // "normal", "warning", "critical"
        private final String colorClass;  // CSS class
        private final String description;

        public FeatureCard(String key, String label, String icon, double value,
                           String threshold, String colorClass, String description) {
            this.key = key; this.label = label; this.icon = icon;
            this.value = value; this.threshold = threshold;
            this.colorClass = colorClass; this.description = description;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public String getIcon() { return icon; }
        public double getValue() { return value; }
        public String getThreshold() { return threshold; }
        public String getColorClass() { return colorClass; }
        public String getDescription() { return description; }
    }

    /**
     * Builds FeatureCard list from a snapshot JSON map.
     */
    private static class FeatureCardBuilder {
        // feature_key -> {label, icon, threshold_fn}
        private static final Map<String, FeatureMeta> FEATURE_META = Map.ofEntries(
            Map.entry("salary_ratio",       new FeatureMeta("Уровень зарплаты", "💰", v -> v < 0.9 ? "critical" : v < 1.1 ? "warning" : "normal")),
            Map.entry("workload_index",     new FeatureMeta("Индекс нагрузки", "⚡", v -> v > 1.3 ? "critical" : v > 1.0 ? "warning" : "normal")),
            Map.entry("kpi_norm",           new FeatureMeta("Показатели KPI", "📊", v -> v < 0.5 ? "critical" : v < 0.7 ? "warning" : "normal")),
            Map.entry("tenure_months_norm", new FeatureMeta("Стаж в компании", "🌱", v -> v < 0.3 ? "critical" : v < 0.6 ? "warning" : "normal")),
            Map.entry("satisfaction_norm",  new FeatureMeta("Удовлетворённость", "😊", v -> v < 0.5 ? "critical" : v < 0.7 ? "warning" : "normal")),
            Map.entry("missed_1on1_ratio",  new FeatureMeta("Пропуски встреч 1:1", "🤝", v -> v > 0.3 ? "critical" : v > 0.1 ? "warning" : "normal")),
            Map.entry("overtime_norm",      new FeatureMeta("Переработки", "🕐", v -> v > 0.3 ? "critical" : v > 0.1 ? "warning" : "normal")),
            Map.entry("z_score",            new FeatureMeta("Z-оценка (logit)", "📐", v -> Math.abs(v) > 2.0 ? "critical" : Math.abs(v) > 1.0 ? "warning" : "normal"))
        );

        private static final Map<String, String> COLOR_CLASSES = Map.of(
            "normal",   "#22c55e",
            "warning", "#f59e0b",
            "critical", "#ef4444"
        );

        private static final Map<String, String> DESCRIPTIONS = Map.of(
            "salary_ratio", "Отношение зарплаты к средней по грейду",
            "workload_index", "Индекс загруженности (1.0 = норма)",
            "kpi_norm", "Нормализованный KPI (0..1)",
            "tenure_months_norm", "Стаж / 60 месяцев",
            "satisfaction_norm", "Средний балл опросов / 5",
            "missed_1on1_ratio", "Доля пропущенных встреч",
            "overtime_norm", "Переработки / лимит",
            "z_score", "Взвешенная сумма до сигмоиды"
        );

        static List<FeatureCard> buildFromSnapshot(Map<String, Object> snapshot) {
            List<FeatureCard> cards = new ArrayList<>();
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                String key = entry.getKey();
                double value = entry.getValue() instanceof Number
                        ? ((Number) entry.getValue()).doubleValue()
                        : 0.0;
                FeatureMeta meta = FEATURE_META.getOrDefault(key,
                        new FeatureMeta(key, "📌", v -> "normal"));
                String threshold = meta.thresholdFn.apply(value);
                String color = COLOR_CLASSES.getOrDefault(threshold, "#22c55e");
                String desc = DESCRIPTIONS.getOrDefault(key, "");
                cards.add(new FeatureCard(key, meta.label, meta.icon, value, threshold, color, desc));
            }
            // Preserve a fixed order: salary, workload, kpi, tenure, satisfaction, missed, overtime, z_score
            List<String> order = List.of("salary_ratio", "workload_index", "kpi_norm",
                    "tenure_months_norm", "satisfaction_norm", "missed_1on1_ratio",
                    "overtime_norm", "z_score");
            cards.sort(Comparator.comparingInt(c -> {
                int idx = order.indexOf(c.key);
                return idx >= 0 ? idx : 99;
            }));
            return cards;
        }

        private record FeatureMeta(String label, String icon, java.util.function.Function<Double, String> thresholdFn) {}
    }
}
