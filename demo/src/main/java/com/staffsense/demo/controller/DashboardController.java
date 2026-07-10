package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import com.staffsense.demo.service.SurveyService;
import com.staffsense.demo.service.OneOnOneService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final SurveyService surveyService;
    private final OneOnOneService oneOnOneService;
    private final KpiRecordRepository kpiRecordRepository;
    private final WorkloadRecordRepository workloadRecordRepository;

    public DashboardController(UserRepository userRepository,
                               AttritionPredictionRepository predictionRepository,
                               SurveyService surveyService,
                               OneOnOneService oneOnOneService,
                               KpiRecordRepository kpiRecordRepository,
                               WorkloadRecordRepository workloadRecordRepository) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.surveyService = surveyService;
        this.oneOnOneService = oneOnOneService;
        this.kpiRecordRepository = kpiRecordRepository;
        this.workloadRecordRepository = workloadRecordRepository;
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
        return "dashboard/main";
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
