package com.staffsense.demo.controller;

import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.*;
import com.staffsense.demo.repository.kpi.KpiRecordRepository;
import com.staffsense.demo.repository.workload.WorkloadRecordRepository;
import com.staffsense.demo.service.CsvExportService;
import com.staffsense.demo.service.EmployeeService;
import com.staffsense.demo.service.TeamAccessService;
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

import java.util.List;
import java.util.Optional;

/**
 * Сотрудники. Доступен ADMIN, HR, PM.
 *
 * <p>Исправлено: PM ранее видел список, карточки и экспорт CSV по
 * <b>всем</b> сотрудникам компании, включая зарплаты. Теперь для PM
 * выборка ограничена его командой (users.manager_id), а карточка чужого
 * сотрудника отдаёт 403.
 */
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
    private final TeamAccessService teamAccessService;

    public EmployeeController(EmployeeService employeeService,
                              UserRepository userRepository,
                              AttritionPredictionRepository predictionRepository,
                              KpiRecordRepository kpiRecordRepository,
                              WorkloadRecordRepository workloadRecordRepository,
                              CsvExportService csvExportService,
                              TeamAccessService teamAccessService) {
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.kpiRecordRepository = kpiRecordRepository;
        this.workloadRecordRepository = workloadRecordRepository;
        this.csvExportService = csvExportService;
        this.teamAccessService = teamAccessService;
    }

    @GetMapping
    public String listEmployees(@RequestParam(required = false) String department,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName"));
        boolean teamScope = currentUser.getRole() == Role.PM;
        boolean hasDepartment = department != null && !department.isBlank();

        Page<User> employeePage;
        if (teamScope) {
            employeePage = hasDepartment
                    ? employeeService.findTeamOfByDepartment(currentUser.getId(), department, pageable)
                    : employeeService.findTeamOf(currentUser.getId(), pageable);
        } else {
            employeePage = hasDepartment
                    ? employeeService.findByDepartment(department, pageable)
                    : employeeService.findAllActive(pageable);
        }

        model.addAttribute("employeePage", employeePage);
        model.addAttribute("employees", employeePage.getContent());
        model.addAttribute("departments", teamScope
                ? employeeService.getDepartmentsOfTeam(currentUser.getId())
                : employeeService.getAllDepartments());
        model.addAttribute("selectedDept", department);
        model.addAttribute("currentUser", currentUser);
        return "employees/list";
    }

    @GetMapping("/export/csv")
    public void exportCsv(HttpServletResponse response, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        csvExportService.exportEmployeesToCsv(response, currentUser);
    }

    @GetMapping("/{id}")
    public String viewProfile(@PathVariable Long id, Model model, Authentication auth) {
        User currentUser = userRepository.findByEmail(auth.getName()).orElseThrow();
        User employee = employeeService.findById(id);

        // PM не может открыть карточку сотрудника вне своей команды.
        teamAccessService.requireCanViewEmployee(currentUser, employee);

        List<KpiRecord> kpiHistory = kpiRecordRepository.findByUserIdOrderByPeriodEndDesc(id);
        List<WorkloadRecord> workloadHistory = workloadRecordRepository.findByUserIdOrderByPeriodEndDesc(id);
        Optional<AttritionPrediction> latestPred = predictionRepository.findFirstByUserIdOrderByComputedAtDesc(id);

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
