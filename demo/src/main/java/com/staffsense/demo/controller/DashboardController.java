package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import com.staffsense.demo.service.SurveyService;
import com.staffsense.demo.service.OneOnOneService;
import com.staffsense.demo.service.metrics.BurnoutRadarService;
import com.staffsense.demo.service.metrics.BurnoutRadarService.BurnoutRadarSnapshot;
import com.staffsense.demo.service.metrics.BurnoutRadarService.EmployeeBurnoutFactors;
import com.staffsense.demo.service.metrics.BurnoutRadarService.FactorValue;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final SurveyService surveyService;
    private final OneOnOneService oneOnOneService;
    private final KpiRecordRepository kpiRecordRepository;
    private final WorkloadRecordRepository workloadRecordRepository;
    private final BurnoutRadarService burnoutRadarService;

    public DashboardController(UserRepository userRepository,
                               AttritionPredictionRepository predictionRepository,
                               SurveyService surveyService,
                               OneOnOneService oneOnOneService,
                               KpiRecordRepository kpiRecordRepository,
                               WorkloadRecordRepository workloadRecordRepository,
                               BurnoutRadarService burnoutRadarService) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.surveyService = surveyService;
        this.oneOnOneService = oneOnOneService;
        this.kpiRecordRepository = kpiRecordRepository;
        this.workloadRecordRepository = workloadRecordRepository;
        this.burnoutRadarService = burnoutRadarService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();

        if (user.getRole() == Role.EMPLOYEE) {
            return personalDashboard(user, model);
        }
        return managementDashboard(user, model);
    }

    @GetMapping("/dashboard/personal")
    public String personal(Authentication auth, Model model) {
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        return personalDashboard(user, model);
    }

    private String managementDashboard(User user, Model model) {
        List<User> allUsers = userRepository.findAll().stream().filter(User::isActive).toList();
        List<AttritionPrediction> predictions = predictionRepository.findAll();

        long highRisk = predictions.stream().filter(p -> p.getRiskLevel() == RiskLevel.HIGH).count();
        long mediumRisk = predictions.stream().filter(p -> p.getRiskLevel() == RiskLevel.MEDIUM).count();
        long lowRisk = predictions.stream().filter(p -> p.getRiskLevel() == RiskLevel.LOW).count();
        double avgRisk = predictions.stream().mapToDouble(AttritionPrediction::getRiskScore).average().orElse(0);

        List<OneOnOneMeeting> upcomingMeetings = oneOnOneService.findAll().stream()
                .filter(m -> m.getStatus() == MeetingStatus.PLANNED)
                .limit(5).toList();

        List<Survey> activeSurveys = surveyService.findAll().stream()
                .filter(s -> s.getStatus() == SurveyStatus.ACTIVE)
                .limit(5).toList();

        model.addAttribute("currentUser", user);
        model.addAttribute("totalEmployees", allUsers.size());
        model.addAttribute("highRiskCount", highRisk);
        model.addAttribute("mediumRiskCount", mediumRisk);
        model.addAttribute("lowRiskCount", lowRisk);
        model.addAttribute("avgRisk", String.format("%.1f", avgRisk));
        model.addAttribute("recentPredictions", predictions.stream().limit(5).toList());
        model.addAttribute("upcomingMeetings", upcomingMeetings);
        model.addAttribute("activeSurveys", activeSurveys);

        // FR-019 (WBS 3.4.4): «Радар выгорания» — профиль факторов выгорания
        // по области видимости пользователя (PM — своя команда, ADMIN/HR/EXEC — все).
        BurnoutRadarSnapshot radar = burnoutRadarService.snapshotFor(user);
        addBurnoutRadar(model, radar);
        return "dashboard/main";
    }

    /**
     * Подготовка данных «Радара выгорания» (FR-019) для шаблона.
     *
     * <p>Списки формируются плоскими (без вложенных объектов) — так они
     * безопасно подставляются в JavaScript через Thymeleaf-инлайнинг тем же
     * способом, что уже применяется на страницах аналитики.
     */
    private void addBurnoutRadar(Model model, BurnoutRadarSnapshot radar) {
        List<String> labels = radar.factorLabels().stream().map(Map.Entry::getValue).toList();
        List<String> keys = radar.factorLabels().stream().map(Map.Entry::getKey).toList();

        // Средний профиль команды (для основной радар-диаграммы).
        List<Double> avgValues = new ArrayList<>();
        List<String> avgDisplays = new ArrayList<>();
        for (FactorValue factor : radar.teamAverages()) {
            avgValues.add(factor.value());
            avgDisplays.add(factor.display());
        }

        // Профили сотрудников: строка на сотрудника + готовые строки для таблицы.
        List<EmployeeBurnoutFactors> employees = radar.employees();
        List<List<Double>> rows = new ArrayList<>();
        List<String> employeeNames = new ArrayList<>();
        List<String> employeeDepartments = new ArrayList<>();
        List<Double> employeeScores = new ArrayList<>();
        List<String> employeeRiskLevels = new ArrayList<>();
        List<Long> employeeAvailableFactors = new ArrayList<>();
        for (EmployeeBurnoutFactors profile : employees) {
            List<Double> row = new ArrayList<>();
            for (FactorValue factor : profile.factors()) {
                row.add(factor.value());
            }
            rows.add(row);
            employeeNames.add(profile.employeeName());
            employeeDepartments.add(profile.department());
            employeeScores.add(profile.scoreRounded());
            employeeRiskLevels.add(profile.riskLevel());
            employeeAvailableFactors.add(profile.availableFactorCount());
        }

        model.addAttribute("burnoutHasData", radar.hasData());
        model.addAttribute("burnoutWindowDays", radar.windowDays());
        model.addAttribute("burnoutFactorLabels", labels);
        model.addAttribute("burnoutFactorKeys", keys);
        model.addAttribute("burnoutTeamValues", avgValues);
        model.addAttribute("burnoutTeamDisplays", avgDisplays);
        model.addAttribute("burnoutTeamScore", radar.teamScore() != null ? radar.teamScore() : 0.0);
        model.addAttribute("burnoutEmployeeCount", radar.employeeCount());
        model.addAttribute("burnoutEmployeeNames", employeeNames);
        model.addAttribute("burnoutEmployeeDepartments", employeeDepartments);
        model.addAttribute("burnoutEmployeeScores", employeeScores);
        model.addAttribute("burnoutEmployeeRiskLevels", employeeRiskLevels);
        model.addAttribute("burnoutEmployeeAvailableFactors", employeeAvailableFactors);
        model.addAttribute("burnoutEmployeeValues", rows);
        model.addAttribute("burnoutFactorSources", burnoutRadarService.factorSources());
        model.addAttribute("burnoutDataCoverage", averageCoverage(employees, radar.factorLabels().size()));
    }

    /** Средняя доля доступных факторов по видимым сотрудникам (в процентах). */
    private double averageCoverage(List<EmployeeBurnoutFactors> employees, int factorCount) {
        if (employees.isEmpty() || factorCount == 0) {
            return 0.0;
        }
        double average = employees.stream()
                .mapToLong(EmployeeBurnoutFactors::availableFactorCount)
                .average()
                .orElse(0.0);
        return Math.round(average / factorCount * 1000.0) / 10.0;
    }

    private String personalDashboard(User user, Model model) {
        List<Survey> mySurveys = surveyService.getVisibleForEmployee(user);
        List<OneOnOneMeeting> myMeetings = oneOnOneService.findAllForEmployee(user);
        List<KpiRecord> myKpi = kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(user.getId());
        List<WorkloadRecord> myWorkload = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(user.getId());

        model.addAttribute("currentUser", user);
        model.addAttribute("mySurveys", mySurveys);
        model.addAttribute("myMeetings", myMeetings);
        model.addAttribute("myKpi", myKpi.isEmpty() ? null : myKpi.get(0));
        model.addAttribute("myWorkload", myWorkload.isEmpty() ? null : myWorkload.get(0));
        return "dashboard/personal";
    }
}
