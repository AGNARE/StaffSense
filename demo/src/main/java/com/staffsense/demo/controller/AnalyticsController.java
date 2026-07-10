package com.staffsense.demo.controller;

import com.staffsense.demo.service.AttritionMlService;
import com.staffsense.demo.service.metrics.ModelInsightService;
import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import com.staffsense.demo.repository.SurveyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final UserRepository userRepository;
    private final KpiRecordRepository kpiRecordRepository;
    private final WorkloadRecordRepository workloadRecordRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyRepository surveyRepository;
    private final ModelInsightService modelInsightService;
    private final AttritionMlService attritionMlService;

    public AnalyticsController(UserRepository userRepository,
                               KpiRecordRepository kpiRecordRepository,
                               WorkloadRecordRepository workloadRecordRepository,
                               SurveyAnswerRepository surveyAnswerRepository,
                               SurveyResponseRepository surveyResponseRepository,
                               SurveyRepository surveyRepository,
                               ModelInsightService modelInsightService,
                               AttritionMlService attritionMlService) {
        this.userRepository = userRepository;
        this.kpiRecordRepository = kpiRecordRepository;
        this.workloadRecordRepository = workloadRecordRepository;
        this.surveyAnswerRepository = surveyAnswerRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.surveyRepository = surveyRepository;
        this.modelInsightService = modelInsightService;
        this.attritionMlService = attritionMlService;
    }

    @GetMapping("/workload")
    public String workloadAnalytics(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        List<User> employees = userRepository.findActiveByRole(true, Role.EMPLOYEE);

        // Build chart data: employee names + their latest workload index
        List<String> labels = new ArrayList<>();
        List<Double> workloadValues = new ArrayList<>();
        for (User emp : employees) {
            List<WorkloadRecord> records = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(emp.getId());
            labels.add(emp.getFullName());
            workloadValues.add(records.isEmpty() ? 0.0 : records.get(0).getWorkloadIndex());
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("workloadLabels", labels);
        model.addAttribute("workloadValues", workloadValues);
        model.addAttribute("employees", employees);
        return "analytics/workload";
    }

    @GetMapping("/kpi")
    public String kpiAnalytics(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        List<User> employees = userRepository.findActiveByRole(true, Role.EMPLOYEE);

        List<String> labels = new ArrayList<>();
        List<Double> kpiValues = new ArrayList<>();
        for (User emp : employees) {
            List<KpiRecord> records = kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(emp.getId());
            labels.add(emp.getFullName());
            kpiValues.add(records.isEmpty() ? 0.0 : records.get(0).getKpiScore());
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("kpiLabels", labels);
        model.addAttribute("kpiValues", kpiValues);
        return "analytics/kpi";
    }

    @GetMapping("/wellbeing")
    public String wellbeingAnalytics(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();

        // Aggregate LIKERT scores by response date
        List<SurveyResponse> responses = surveyResponseRepository.findAll();

        // Batch-load ALL answers in a single query and index by responseId
        List<Long> allResponseIds = responses.stream().map(SurveyResponse::getId).collect(Collectors.toList());
        Map<Long, List<SurveyAnswer>> answersByResponse = surveyAnswerRepository.findByResponseIdIn(allResponseIds)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getResponse().getId()));

        Map<String, List<Integer>> scoresByDate = new TreeMap<>();
        Map<Long, List<Integer>> likertsByEmployee = new HashMap<>();

        for (SurveyResponse resp : responses) {
            List<SurveyAnswer> likertAnswers = answersByResponse.getOrDefault(resp.getId(), List.of()).stream()
                    .filter(a -> a.getLikertValue() != null)
                    .toList();
            if (!likertAnswers.isEmpty()) {
                // Aggregate by date
                String dateKey = resp.getSubmittedAt().toLocalDate().toString();
                scoresByDate.computeIfAbsent(dateKey, k -> new ArrayList<>())
                        .addAll(likertAnswers.stream().map(SurveyAnswer::getLikertValue).toList());

                // Aggregate by employee (skip anonymous)
                if (resp.getUserId() != null) {
                    for (SurveyAnswer a : likertAnswers) {
                        likertsByEmployee.computeIfAbsent(resp.getUserId(), k -> new ArrayList<>()).add(a.getLikertValue());
                    }
                }
            }
        }

        List<String> dateLabels = new ArrayList<>(scoresByDate.keySet());
        List<Double> avgScores = dateLabels.stream()
                .map(d -> scoresByDate.get(d).stream().mapToInt(i -> i).average().orElse(0))
                .collect(Collectors.toList());

        // ── Heatmap data: satisfaction by employee across departments ──
        List<User> allEmployees = userRepository.findActiveByRole(true, Role.EMPLOYEE);

        List<WellbeingHeatEntry> heatEntries = new ArrayList<>();
        for (User emp : allEmployees) {
            List<Integer> scores = likertsByEmployee.getOrDefault(emp.getId(), List.of());
            if (scores.isEmpty()) continue; // skip employees with no survey data
            double avg = scores.stream().mapToInt(i -> i).average().orElse(0.0);
            heatEntries.add(new WellbeingHeatEntry(emp.getId(), emp.getFullName(), emp.getDepartment(), avg, scores.size()));
        }

        // Group employees by department for axis rendering
        Map<String, List<WellbeingHeatEntry>> deptMap = heatEntries.stream()
                .collect(Collectors.groupingBy(e -> e.department != null ? e.department : "Без отдела",
                        Collectors.toList()));

        // Build ordered department groups with indices for the frontend
        List<String> deptLabels = new ArrayList<>(deptMap.keySet());
        List<WellbeingDeptGroup> deptGroups = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<String, List<WellbeingHeatEntry>> entry : deptMap.entrySet()) {
            deptGroups.add(new WellbeingDeptGroup(entry.getKey(), idx, idx + entry.getValue().size()));
            idx += entry.getValue().size();
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("wellbeingLabels", dateLabels);
        model.addAttribute("wellbeingValues", avgScores);
        model.addAttribute("wellbeingHeatEntries", heatEntries);
        model.addAttribute("wellbeingDeptGroups", deptGroups);
        model.addAttribute("wellbeingDeptLabels", deptLabels);
        return "analytics/wellbeing";
    }

    @GetMapping("/ml-model")
    public String mlModelAnalytics(Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();

        // Auto-compute predictions if no data exists yet — kick off async
        List<?> employeeFeatures = modelInsightService.getEmployeeFeatureVectors();
        boolean computing = false;
        if (employeeFeatures.isEmpty()) {
            log.info("No ML predictions found — starting async computation...");
            attritionMlService.computeAllEmployeesAsync();
            computing = true;
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("mlComputing", computing);
        model.addAttribute("featureWeights", modelInsightService.getFeatureWeights());
        model.addAttribute("riskDistribution", modelInsightService.getRiskDistribution());
        model.addAttribute("employeeRisks", modelInsightService.getEmployeeRiskInsights());
        model.addAttribute("employeeFeatures", employeeFeatures);

        // Pre-build feature column names for the heatmap
        model.addAttribute("heatmapFeatureKeys", List.of(
            "salaryRatio", "workloadIndex", "kpiNorm", "tenureMonthsNorm",
            "satisfactionNorm", "missed1on1Ratio", "overtimeNorm"
        ));
        model.addAttribute("heatmapFeatureLabels", List.of(
            "Зарплата", "Нагрузка", "KPI", "Стаж",
            "Удовл.", "1:1 проп.", "Перераб."
        ));
        return "analytics/ml-model";
    }

    @PostMapping("/ml-model/recompute")
    public String recomputeAllML(Model model, Authentication auth) {
        log.info("Manual recompute triggered for all employees");
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        attritionMlService.computeAllEmployeesAsync();

        model.addAttribute("success", "Пересчёт ML-прогнозов запущен. Обновите страницу через несколько секунд.");
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("featureWeights", modelInsightService.getFeatureWeights());
        model.addAttribute("riskDistribution", modelInsightService.getRiskDistribution());
        model.addAttribute("employeeRisks", modelInsightService.getEmployeeRiskInsights());
        model.addAttribute("employeeFeatures", modelInsightService.getEmployeeFeatureVectors());
        model.addAttribute("heatmapFeatureKeys", List.of(
            "salaryRatio", "workloadIndex", "kpiNorm", "tenureMonthsNorm",
            "satisfactionNorm", "missed1on1Ratio", "overtimeNorm"
        ));
        model.addAttribute("heatmapFeatureLabels", List.of(
            "Зарплата", "Нагрузка", "KPI", "Стаж",
            "Удовл.", "1:1 проп.", "Перераб."
        ));
        return "analytics/ml-model";
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Inner DTOs for wellbeing heatmap
    // ═══════════════════════════════════════════════════════════════════

    /**
     * One employee's satisfaction snapshot for the wellbeing heatmap.
     */
    public static class WellbeingHeatEntry {
        private final long employeeId;
        private final String employeeName;
        private final String department;
        private final double avgSatisfaction;
        private final int responseCount;

        public WellbeingHeatEntry(long employeeId, String employeeName, String department,
                                  double avgSatisfaction, int responseCount) {
            this.employeeId = employeeId;
            this.employeeName = employeeName;
            this.department = department;
            this.avgSatisfaction = avgSatisfaction;
            this.responseCount = responseCount;
        }

        public long getEmployeeId() { return employeeId; }
        public String getEmployeeName() { return employeeName; }
        public String getDepartment() { return department; }
        public double getAvgSatisfaction() { return avgSatisfaction; }
        public int getResponseCount() { return responseCount; }
    }

    /**
     * Department grouping metadata — start/end indices in the employee list.
     */
    public static class WellbeingDeptGroup {
        private final String department;
        private final int startIndex;
        private final int endIndex;

        public WellbeingDeptGroup(String department, int startIndex, int endIndex) {
            this.department = department;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        public String getDepartment() { return department; }
        public int getStartIndex() { return startIndex; }
        public int getEndIndex() { return endIndex; }
    }
}
