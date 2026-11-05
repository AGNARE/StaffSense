package com.staffsense.demo.api.employee;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.AttritionPrediction;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.repository.AttritionPredictionRepository;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.EmployeeService;
import com.staffsense.demo.service.TeamAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * REST-контракт каталога сотрудников — второй ресурс API-first контура.
 *
 * <p>Проверяет, что схема из модуля рисков переносится, а не держалась на одном
 * экране: тот же подход (DTO-записи, сервисы как источник данных, права в двух
 * местах), но другой домен и другой набор фильтров.
 *
 * <p>Видимость: ADMIN/HR/EXEC — все активные сотрудники, PM — только своя
 * команда (ограничение передаётся в запрос как {@code managerId}, а карточка
 * дополнительно проверяется через {@link TeamAccessService}).
 */
@RestController
@RequestMapping("/api/v1/employees")
@PreAuthorize("hasAnyRole('ADMIN','HR','PM','EXEC')")
public class EmployeeApiController {

    private static final int MAX_PAGE_SIZE = 200;

    private final UserRepository userRepository;
    private final AttritionPredictionRepository predictionRepository;
    private final EmployeeService employeeService;
    private final TeamAccessService teamAccessService;

    public EmployeeApiController(UserRepository userRepository,
                                 AttritionPredictionRepository predictionRepository,
                                 EmployeeService employeeService,
                                 TeamAccessService teamAccessService) {
        this.userRepository = userRepository;
        this.predictionRepository = predictionRepository;
        this.employeeService = employeeService;
        this.teamAccessService = teamAccessService;
    }

    /**
     * Каталог: страница, поиск по имени/отделу, фильтр по отделу.
     *
     * @param search пустая строка — без поиска
     */
    @GetMapping
    public EmployeeApiDtos.Page<EmployeeApiDtos.Item> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "department", defaultValue = "") String department,
            Authentication auth) {

        User viewer = currentUser(auth);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Long managerId = viewer.getRole() == Role.PM ? viewer.getId() : null;
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        Page<User> result = userRepository.searchEmployees(
                managerId, department == null ? "" : department.trim(), pattern, pageable);

        List<EmployeeApiDtos.Item> items = result.getContent().stream()
                .map(this::toItem)
                .toList();

        return new EmployeeApiDtos.Page<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /** Карточка сотрудника: профиль и последний прогноз риска, если он есть. */
    @GetMapping("/{userId}")
    public EmployeeApiDtos.Detail detail(@PathVariable Long userId, Authentication auth) {
        User viewer = currentUser(auth);
        User employee = employeeService.findById(userId);

        // PM не должен открывать чужих сотрудников даже зная id.
        teamAccessService.requireCanViewEmployee(viewer, employee);

        Optional<AttritionPrediction> prediction =
                predictionRepository.findFirstByUserIdOrderByComputedAtDesc(userId);

        EmployeeApiDtos.RiskSummary risk = prediction
                .map(p -> new EmployeeApiDtos.RiskSummary(
                        p.getRiskScore(), p.getRiskLevel().name(), p.getComputedAt()))
                .orElse(null);

        return new EmployeeApiDtos.Detail(
                employee.getId(),
                employee.getFullName(),
                employee.getEmail(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getGradeLevel().name(),
                employee.getManager() != null ? employee.getManager().getFullName() : null,
                employee.getHireDate(),
                employee.isActive(),
                employee.getWeeklyCapacityHours(),
                risk);
    }

    /** Справочник отделов для выпадающего фильтра (у PM — только его команда). */
    @GetMapping("/departments")
    public EmployeeApiDtos.Departments departments(Authentication auth) {
        User viewer = currentUser(auth);
        List<String> items = viewer.getRole() == Role.PM
                ? employeeService.getDepartmentsOfTeam(viewer.getId())
                : employeeService.getAllDepartments();
        return new EmployeeApiDtos.Departments(items);
    }

    // ── вспомогательное ──────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null) {
            throw new ResourceNotFoundException("Не удалось определить пользователя");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }

    private EmployeeApiDtos.Item toItem(User user) {
        return new EmployeeApiDtos.Item(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getDepartment(),
                user.getPosition(),
                user.getGradeLevel().name(),
                user.getManager() != null ? user.getManager().getFullName() : null,
                user.getHireDate(),
                user.isActive());
    }
}
