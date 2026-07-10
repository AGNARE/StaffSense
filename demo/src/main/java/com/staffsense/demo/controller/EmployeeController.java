package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import com.staffsense.demo.service.CsvExportService;
import com.staffsense.demo.service.EmployeeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employees")
@PreAuthorize("hasAnyRole('ADMIN','HR','PM')")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final KpiRecordRepository kpiRecordRepository;
    private final WorkloadRecordRepository workloadRecordRepository;
    private final CsvExportService csvExportService;

    public EmployeeController(EmployeeService employeeService,
                              UserRepository userRepository,
                              AttritionPredictionRepository predictionRepository,
                              KpiRecordRepository kpiRecordRepository,
                              WorkloadRecordRepository workloadRecordRepository,
                              CsvExportService csvExportService) {
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.kpiRecordRepository = kpiRecordRepository;
        this.workloadRecordRepository = workloadRecordRepository;
        this.csvExportService = csvExportService;
    }

    @GetMapping
    public String listEmployees(@RequestParam(required = false) String department,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                Model model, Authentication auth) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName"));
        Page<User> employeePage;
        if (department != null && !department.isBlank()) {
            employeePage = employeeService.findByDepartment(department, pageable);
        } else {
            employeePage = employeeService.findAllActive(pageable);
        }
        model.addAttribute("employeePage", employeePage);
        model.addAttribute("employees", employeePage.getContent());
        model.addAttribute("departments", employeeService.getAllDepartments());
        model.addAttribute("selectedDept", department);
        model.addAttribute("currentUser", userRepository.findByEmail(auth.getName()).orElseThrow());
        return "employees/list";
    }

    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response) {
        csvExportService.exportEmployeesToCsv(response);
    }

    @GetMapping("/{id}")
    public String viewProfile(@PathVariable Long id, Model model, Authentication auth) {
        User employee = employeeService.findById(id);
        List<KpiRecord> kpiHistory = kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(id);
        List<WorkloadRecord> workloadHistory = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(id);
        Optional<AttritionPrediction> latestPred = predictionRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(id))
                .max(Comparator.comparing(AttritionPrediction::getComputedAt));

        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        boolean canViewRisk = currentUser.getRole() != Role.EMPLOYEE;

        model.addAttribute("employee", employee);
        model.addAttribute("kpiHistory", kpiHistory);
        model.addAttribute("workloadHistory", workloadHistory);
        model.addAttribute("prediction", canViewRisk ? latestPred.orElse(null) : null);
        model.addAttribute("canViewRisk", canViewRisk);
        model.addAttribute("currentUser", currentUser);
        return "employees/profile";
    }
}
