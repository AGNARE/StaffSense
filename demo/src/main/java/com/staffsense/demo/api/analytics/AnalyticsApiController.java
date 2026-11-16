package com.staffsense.demo.api.analytics;

import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.KpiRecord;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkloadRecord;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.metrics.KpiCalculationService;
import com.staffsense.demo.service.metrics.WorkloadCalculationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST-аналитика: нагрузка и KPI как данные, а не HTML-страницы.
 *
 * <p><b>Зачем.</b> Прежние экраны аналитики существовали только как Thymeleaf-
 * шаблоны ({@code AnalyticsController} → {@code /analytics/workload},
 * {@code /analytics/kpi}), и новый React-клиент не мог их показать. Здесь те же
 * показатели отдаются JSON-ом.
 *
 * <p><b>Что важно: вычисления не дублируются.</b> Контроллер не считает индекс
 * нагрузки и не пересчитывает KPI по задачам — он читает уже сохранённые записи
 * через те же сервисы, что и HTML-страницы:
 * {@link WorkloadCalculationService#getHistory(Long)} и
 * {@link KpiCalculationService#getHistory(Long)}. Формулы остаются в одном
 * месте; правка BR-008/BR-009 не потребует правки контроллера.
 *
 * <p><b>Период.</b> «Последняя запись» у каждого сотрудника своя, поэтому
 * {@code periodStart}/{@code periodEnd} отдаются в каждой строке. Никакого
 * «текущего месяца» API не подразумевает: если последний расчёт был за прошлый
 * месяц, клиент увидит именно прошлый месяц, а не пустоту.
 *
 * <p><b>Отличие от HTML-страниц (осознанное).</b> Там отсутствие записи
 * подменялось нулём ({@code workloadIndex = 0.0}, {@code kpiScore = 0.0}).
 * Здесь вместо выдуманного нуля — {@code null} и {@code hasData = false}:
 * «не считали» и «посчитали ноль» — разные утверждения (BR-006).
 *
 * <p><b>Доступ.</b> Те же роли, что у разделов {@code /analytics/**} в
 * {@code SecurityConfig}: ADMIN, EXEC, HR, PM. Проверка продублирована
 * {@code @PreAuthorize} намеренно: если правило в конфигурации будет снято при
 * рефакторинге, защита останется. Дополнительно применяется область данных
 * проекта через {@link TeamAccessService}: ADMIN/HR/EXEC видят всех активных
 * сотрудников, PM — только свою команду (как в модуле рисков).
 */
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('ADMIN','EXEC','HR','PM')")
@Tag(name = "Аналитика", description = "Нагрузка и KPI: те же данные и сервисы, что у HTML-страниц")
public class AnalyticsApiController {

    /** Верхняя граница размера страницы: один запрос не должен выгружать всю базу. */
    private static final int MAX_PAGE_SIZE = 200;

    /** Размер страницы по умолчанию — как у {@code /api/v1/risk}. */
    private static final String DEFAULT_PAGE_SIZE = "50";

    /** Подпись отдела, когда он не заполнен: группировать в «null» нельзя. */
    private static final String WITHOUT_DEPARTMENT = "Без отдела";

    /** Индекс по убыванию, сотрудники без данных — в конце, затем по имени. */
    private static final Comparator<AnalyticsApiDtos.WorkloadEmployee> WORKLOAD_ORDER =
            Comparator.comparing(AnalyticsApiDtos.WorkloadEmployee::workloadIndex,
                            Comparator.nullsLast(Comparator.<Double>reverseOrder()))
                    .thenComparing(AnalyticsApiDtos.WorkloadEmployee::fullName,
                            Comparator.nullsLast(String::compareToIgnoreCase));

    /** KPI по убыванию, сотрудники без данных — в конце, затем по имени. */
    private static final Comparator<AnalyticsApiDtos.KpiEmployee> KPI_ORDER =
            Comparator.comparing(AnalyticsApiDtos.KpiEmployee::kpiScore,
                            Comparator.nullsLast(Comparator.<Double>reverseOrder()))
                    .thenComparing(AnalyticsApiDtos.KpiEmployee::fullName,
                            Comparator.nullsLast(String::compareToIgnoreCase));

    /** Агрегаты по отделам: сначала самые нагруженные, отделы без данных — в конце. */
    private static final Comparator<AnalyticsApiDtos.WorkloadTeam> TEAM_ORDER =
            Comparator.comparing(AnalyticsApiDtos.WorkloadTeam::avgWorkloadIndex,
                            Comparator.nullsLast(Comparator.<Double>reverseOrder()))
                    .thenComparing(AnalyticsApiDtos.WorkloadTeam::department);

    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;
    private final WorkloadCalculationService workloadCalculationService;
    private final KpiCalculationService kpiCalculationService;

    public AnalyticsApiController(UserRepository userRepository,
                                  TeamAccessService teamAccessService,
                                  WorkloadCalculationService workloadCalculationService,
                                  KpiCalculationService kpiCalculationService) {
        this.userRepository = userRepository;
        this.teamAccessService = teamAccessService;
        this.workloadCalculationService = workloadCalculationService;
        this.kpiCalculationService = kpiCalculationService;
    }

    // ── Нагрузка ─────────────────────────────────────────────────────────

    /**
     * Нагрузка по сотрудникам и отделам.
     *
     * @param page номер страницы с нуля
     * @param size размер страницы (1..200)
     */
    @GetMapping("/workload")
    public AnalyticsApiDtos.WorkloadResponse workload(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = DEFAULT_PAGE_SIZE) int size,
            Authentication auth) {

        User actor = currentUser(auth);
        List<AnalyticsApiDtos.WorkloadEmployee> all = workloadRows(visibleEmployees(actor)).stream()
                .map(AnalyticsApiController::toWorkloadEmployee)
                .sorted(WORKLOAD_ORDER)
                .toList();

        return new AnalyticsApiDtos.WorkloadResponse(
                LocalDateTime.now(),
                WorkloadCalculationService.OVERLOAD_THRESHOLD,
                pageOf(all, page, size),
                teamsOf(all),
                countWithWorkload(all));
    }

    // ── KPI ──────────────────────────────────────────────────────────────

    /**
     * KPI по сотрудникам: последняя сохранённая оценка и её слагаемые.
     *
     * @param page номер страницы с нуля
     * @param size размер страницы (1..200)
     */
    @GetMapping("/kpi")
    public AnalyticsApiDtos.KpiResponse kpi(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = DEFAULT_PAGE_SIZE) int size,
            Authentication auth) {

        User actor = currentUser(auth);
        List<AnalyticsApiDtos.KpiEmployee> all = kpiRows(visibleEmployees(actor)).stream()
                .map(AnalyticsApiController::toKpiEmployee)
                .sorted(KPI_ORDER)
                .toList();

        return new AnalyticsApiDtos.KpiResponse(
                LocalDateTime.now(),
                pageOf(all, page, size),
                countWithKpi(all));
    }

    // ── Сводка ───────────────────────────────────────────────────────────

    /**
     * Сводка для карточек над вкладками: сколько сотрудников, у скольких есть
     * расчёт, сколько перегружены и средние значения.
     *
     * <p>Средние считаются только по сотрудникам с данными — «нет расчёта» не
     * тянет среднее к нулю.
     */
    @GetMapping("/summary")
    public AnalyticsApiDtos.Summary summary(Authentication auth) {
        User actor = currentUser(auth);
        List<User> employees = visibleEmployees(actor);

        List<AnalyticsApiDtos.WorkloadEmployee> workload = workloadRows(employees).stream()
                .map(AnalyticsApiController::toWorkloadEmployee)
                .toList();
        List<AnalyticsApiDtos.KpiEmployee> kpi = kpiRows(employees).stream()
                .map(AnalyticsApiController::toKpiEmployee)
                .toList();

        List<Double> indices = workload.stream()
                .map(AnalyticsApiDtos.WorkloadEmployee::workloadIndex)
                .filter(Objects::nonNull)
                .toList();
        List<Double> scores = kpi.stream()
                .map(AnalyticsApiDtos.KpiEmployee::kpiScore)
                .filter(Objects::nonNull)
                .toList();

        int overloaded = (int) indices.stream()
                .filter(index -> index > WorkloadCalculationService.OVERLOAD_THRESHOLD)
                .count();

        return new AnalyticsApiDtos.Summary(
                employees.size(),
                indices.size(),
                scores.size(),
                overloaded,
                average(indices),
                average(scores),
                LocalDateTime.now());
    }

    // ── Выборки ──────────────────────────────────────────────────────────

    /**
     * Активные сотрудники в области видимости актора.
     *
     * <p>ADMIN/HR/EXEC видят всех, PM — только свою команду, как в остальных
     * модулях проекта ({@link TeamAccessService#canViewEmployee}).
     */
    private List<User> visibleEmployees(User actor) {
        return userRepository.findActiveByRole(true, Role.EMPLOYEE).stream()
                .filter(employee -> teamAccessService.canViewEmployee(actor, employee))
                .sorted(Comparator.comparing(User::getFullName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private List<WorkloadRow> workloadRows(List<User> employees) {
        return employees.stream()
                .map(employee -> new WorkloadRow(employee, latestWorkload(employee)))
                .toList();
    }

    private List<KpiRow> kpiRows(List<User> employees) {
        return employees.stream()
                .map(employee -> new KpiRow(employee, latestKpi(employee)))
                .toList();
    }

    /**
     * Последняя запись нагрузки. История отсортирована сервисом по убыванию
     * периода, поэтому «последняя» — первая. Расчёт «на лету» не запускаем:
     * иначе API показывал бы число, которого ещё нет в базе и которое не
     * увидит HTML-страница.
     */
    private WorkloadRecord latestWorkload(User employee) {
        List<WorkloadRecord> history = workloadCalculationService.getHistory(employee.getId());
        return history.isEmpty() ? null : history.get(0);
    }

    private KpiRecord latestKpi(User employee) {
        List<KpiRecord> history = kpiCalculationService.getHistory(employee.getId());
        return history.isEmpty() ? null : history.get(0);
    }

    // ── Преобразование в DTO ─────────────────────────────────────────────

    private static AnalyticsApiDtos.WorkloadEmployee toWorkloadEmployee(WorkloadRow row) {
        WorkloadRecord record = row.latest();
        boolean hasData = record != null;
        Double index = hasData ? record.getWorkloadIndex() : null;

        return new AnalyticsApiDtos.WorkloadEmployee(
                row.employee().getId(),
                row.employee().getFullName(),
                row.employee().getDepartment(),
                row.employee().getPosition(),
                hasData ? record.getPeriodStart() : null,
                hasData ? record.getPeriodEnd() : null,
                index,
                hasData ? record.getTotalEstimatedHours() : null,
                hasData ? record.getCapacityHours() : null,
                hasData ? record.getOvertimeHours() : null,
                hasData ? record.getComputedAt() : null,
                hasData,
                index != null && index > WorkloadCalculationService.OVERLOAD_THRESHOLD);
    }

    private static AnalyticsApiDtos.KpiEmployee toKpiEmployee(KpiRow row) {
        KpiRecord record = row.latest();
        boolean hasData = record != null;

        return new AnalyticsApiDtos.KpiEmployee(
                row.employee().getId(),
                row.employee().getFullName(),
                row.employee().getDepartment(),
                row.employee().getPosition(),
                hasData ? record.getPeriodStart() : null,
                hasData ? record.getPeriodEnd() : null,
                hasData ? record.getKpiScore() : null,
                hasData ? record.getOnTimeCompletionRate() : null,
                hasData ? record.getReopenRate() : null,
                hasData ? record.getManagerRating() : null,
                hasData ? record.getComputedAt() : null,
                hasData);
    }

    // ── Агрегация ────────────────────────────────────────────────────────

    private static List<AnalyticsApiDtos.WorkloadTeam> teamsOf(
            List<AnalyticsApiDtos.WorkloadEmployee> employees) {

        Map<String, List<AnalyticsApiDtos.WorkloadEmployee>> byDepartment = employees.stream()
                .collect(Collectors.groupingBy(
                        AnalyticsApiController::departmentOf,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<AnalyticsApiDtos.WorkloadTeam> teams = new ArrayList<>(byDepartment.size());
        for (Map.Entry<String, List<AnalyticsApiDtos.WorkloadEmployee>> entry : byDepartment.entrySet()) {
            List<AnalyticsApiDtos.WorkloadEmployee> group = entry.getValue();
            List<Double> indices = group.stream()
                    .map(AnalyticsApiDtos.WorkloadEmployee::workloadIndex)
                    .filter(Objects::nonNull)
                    .toList();

            teams.add(new AnalyticsApiDtos.WorkloadTeam(
                    entry.getKey(),
                    group.size(),
                    indices.size(),
                    average(indices),
                    (int) group.stream().filter(AnalyticsApiDtos.WorkloadEmployee::overloaded).count()));
        }
        teams.sort(TEAM_ORDER);
        return teams;
    }

    private static String departmentOf(AnalyticsApiDtos.WorkloadEmployee employee) {
        String department = employee.department();
        return department == null || department.isBlank() ? WITHOUT_DEPARTMENT : department;
    }

    private static int countWithWorkload(List<AnalyticsApiDtos.WorkloadEmployee> employees) {
        return (int) employees.stream()
                .filter(employee -> employee.workloadIndex() != null)
                .count();
    }

    private static int countWithKpi(List<AnalyticsApiDtos.KpiEmployee> employees) {
        return (int) employees.stream()
                .filter(employee -> employee.kpiScore() != null)
                .count();
    }

    /**
     * Среднее по непустой выборке; {@code null}, если данных нет.
     * Округление до двух знаков — презентационное (в JSON не нужны «1.4500000001»).
     */
    static Double average(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return Math.round(sum / values.size() * 100.0) / 100.0;
    }

    /** Обрезка готового списка до страницы: сортировка уже выполнена выше. */
    private static <T> AnalyticsApiDtos.Page<T> pageOf(List<T> all, int requestedPage, int requestedSize) {
        int size = Math.min(Math.max(requestedSize, 1), MAX_PAGE_SIZE);
        int page = Math.max(requestedPage, 0);
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / size);

        return new AnalyticsApiDtos.Page<>(
                List.copyOf(all.subList(from, to)), page, size, all.size(), totalPages);
    }

    // ── Вспомогательное ──────────────────────────────────────────────────

    private User currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResourceNotFoundException("Не удалось определить пользователя");
        }
        return userRepository.findByEmail(auth.getName()).orElseThrow(
                () -> new ResourceNotFoundException("Пользователь не найден: " + auth.getName()));
    }

    /** Сотрудник и его последняя запись нагрузки (записи может не быть). */
    private record WorkloadRow(User employee, WorkloadRecord latest) {
    }

    /** Сотрудник и его последняя запись KPI (записи может не быть). */
    private record KpiRow(User employee, KpiRecord latest) {
    }
}
