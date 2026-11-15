package com.staffsense.demo.api.hr;

import com.staffsense.demo.dto.timesheet.TimesheetDayDto;
import com.staffsense.demo.dto.timesheet.TimesheetReport;
import com.staffsense.demo.dto.timesheet.TimesheetRow;
import com.staffsense.demo.model.Document;
import com.staffsense.demo.model.ServiceMemo;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkSchedule;
import com.staffsense.demo.service.TeamAccessService;
import com.staffsense.demo.service.TimesheetService;
import com.staffsense.demo.service.hr.HrDeskService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST-модуль HR: «Пульт HR» и «Отсутствия и табель».
 *
 * <p><b>Зачем.</b> Оба экрана прототипа существовали только как Thymeleaf-
 * страницы ({@code /timesheet}, {@code /timesheet/report}) либо не существовали
 * вовсе (сводки по кадрам не было). React-клиенту нужны данные, а не HTML.
 *
 * <p><b>Расчёты не дублируются.</b> Табель строится
 * {@link TimesheetService#buildReport} — тем же сервисом, что и HTML-отчёт,
 * поэтому часы, переработки и дни отсутствий считаются по одним правилам
 * (BR-027/028/030). Сводка собирается {@link HrDeskService}. Контроллер только
 * переводит сущности в DTO и складывает уже посчитанные строки в итоги.
 *
 * <p><b>Доступ.</b> Табель — {@code ADMIN}, {@code HR}; сводка — ещё и
 * {@code EXEC} (чтение картины без операций), как и в остальных управленческих
 * разделах. Правило продублировано {@code @PreAuthorize}: HTTP-уровень
 * ({@code /api/**} → authenticated) роль не проверяет.
 *
 * <p><b>Прочерк вместо нуля.</b> Если записей в табеле нет, строка приходит с
 * {@code hasData = false} и нулями только там, где ноль — правда (число дней).
 * «Нет данных» и «ноль часов» — разные утверждения (BR-006), поэтому клиент
 * показывает прочерк.
 */
@RestController
@RequestMapping("/api/v1/hr")
@Tag(name = "HR", description = "Пульт HR и табель: сводка по кадрам, агрегаты табеля")
public class HrApiController {

    /** Верхняя граница периода табеля: месяц-два, но не вся история. */
    private static final int MAX_PERIOD_DAYS = 366;

    /** Подпись отдела, когда он не заполнен: группировать в «null» нельзя. */
    private static final String WITHOUT_DEPARTMENT = "Без отдела";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final HrDeskService hrDeskService;
    private final TimesheetService timesheetService;
    private final TeamAccessService teamAccessService;

    public HrApiController(HrDeskService hrDeskService,
                           TimesheetService timesheetService,
                           TeamAccessService teamAccessService) {
        this.hrDeskService = hrDeskService;
        this.timesheetService = timesheetService;
        this.teamAccessService = teamAccessService;
    }

    // ── Пульт HR ─────────────────────────────────────────────────────────

    /**
     * Сводка по кадрам: штат, испытательный срок, покрытие прогнозами,
     * отсутствия на неделе и документы, ждущие решения.
     *
     * @param date якорная дата сводки (по умолчанию — сегодня); нужна, чтобы
     *             посмотреть неделю не только «сейчас»
     */
    @GetMapping("/desk")
    @PreAuthorize("hasAnyRole('ADMIN','HR','EXEC')")
    public HrApiDtos.Desk desk(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        HrDeskService.DeskSnapshot snapshot = hrDeskService.desk(date);
        List<User> active = snapshot.activeUsers();
        List<User> employees = snapshot.employees();

        return new HrApiDtos.Desk(
                snapshot.generatedAt(),
                snapshot.date(),
                new HrApiDtos.Period(snapshot.weekStart(), snapshot.weekEnd()),
                headcount(active, employees),
                probation(snapshot.probation()),
                forecast(employees, snapshot.withoutForecast()),
                absences(snapshot),
                documents(snapshot.documentsOnApproval()));
    }

    // ── Табель ───────────────────────────────────────────────────────────

    /**
     * Табель за период с агрегатами по сотруднику.
     *
     * @param from       начало периода (включительно); без параметра — первое число текущего месяца
     * @param to         конец периода (включительно); без параметра — сегодня
     * @param department подразделение для фильтра; пусто — все
     */
    @GetMapping("/timesheet")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public HrApiDtos.Timesheet timesheet(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "department", required = false) String department) {

        LocalDate today = LocalDate.now();
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;
        requireValidPeriod(start, end);

        List<User> all = hrDeskService.activeUsers();
        List<String> departments = all.stream()
                .map(HrApiController::departmentOf)
                .filter(name -> !WITHOUT_DEPARTMENT.equals(name))
                .distinct()
                .sorted()
                .toList();

        String filter = department == null || department.isBlank() ? null : department.trim();
        List<User> selected = filter == null
                ? all
                : all.stream().filter(user -> filter.equals(user.getDepartment())).toList();

        TimesheetReport report = timesheetService.buildReport(selected, start, end,
                filter != null ? "Подразделение «" + filter + "»" : "Сводный по компании");

        List<HrApiDtos.TimesheetEmployee> rows = report.rows().stream()
                .map(HrApiController::toEmployee)
                .toList();

        Set<Long> selectedIds = selected.stream().map(User::getId).collect(Collectors.toSet());
        List<HrApiDtos.Absence> absences = hrDeskService.absences(start, end).stream()
                .filter(segment -> selectedIds.contains(segment.userId()))
                .map(HrApiController::toAbsence)
                .toList();

        List<HrApiDtos.AbsenceRequest> requests = hrDeskService.memosOverlapping(start, end).stream()
                .filter(memo -> memo.getUser() != null && selectedIds.contains(memo.getUser().getId()))
                .map(HrApiController::toAbsenceRequest)
                .toList();

        return new HrApiDtos.Timesheet(
                start,
                end,
                periodLabel(start, end),
                filter,
                departments,
                report.closed(),
                totals(rows),
                rows,
                absences,
                requests);
    }

    /**
     * Отметка часов за день (BR-027: табель ведёт HR/ADMIN; сотрудник — только
     * смотрит). Правила правки — в {@link TimesheetService#saveEntry}: там же
     * проверка закрытого периода (BR-028), которая возвращает 409.
     */
    @PostMapping("/timesheet")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public HrApiDtos.TimesheetEntry saveEntry(@Valid @RequestBody HrApiDtos.TimesheetEntryRequest request,
                                              Authentication auth) {
        User actor = teamAccessService.requireCurrentUser(auth);
        WorkSchedule saved = timesheetService.saveEntry(
                actor,
                request.userId(),
                request.date(),
                request.hours(),
                request.remote(),
                request.sickLeave(),
                request.vacation(),
                request.overtimeHours());

        return new HrApiDtos.TimesheetEntry(
                saved.getId(),
                request.userId(),
                saved.getWorkDate(),
                saved.getHoursWorked(),
                saved.isRemote(),
                saved.isSickLeave(),
                saved.isVacation(),
                saved.getOvertimeHours(),
                saved.getUpdatedAt());
    }

    // ── Сборка сводки ────────────────────────────────────────────────────

    private static HrApiDtos.Headcount headcount(List<User> active, List<User> employees) {
        Map<String, Integer> byDepartment = new LinkedHashMap<>();
        for (User user : active) {
            byDepartment.merge(departmentOf(user), 1, Integer::sum);
        }

        List<HrApiDtos.DepartmentHeadcount> departments = byDepartment.entrySet().stream()
                .map(entry -> new HrApiDtos.DepartmentHeadcount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(HrApiDtos.DepartmentHeadcount::headcount).reversed()
                        .thenComparing(HrApiDtos.DepartmentHeadcount::department))
                .toList();

        return new HrApiDtos.Headcount(
                active.size(),
                employees.size(),
                active.size() - employees.size(),
                departments);
    }

    private static HrApiDtos.Probation probation(List<HrDeskService.ProbationPerson> people) {
        List<HrApiDtos.ProbationItem> items = people.stream()
                .map(person -> new HrApiDtos.ProbationItem(
                        person.user().getId(),
                        person.user().getFullName(),
                        person.user().getDepartment(),
                        person.user().getPosition(),
                        person.user().getHireDate(),
                        person.probationEnd(),
                        person.daysLeft(),
                        person.checklistDone(),
                        person.checklistTotal()))
                .toList();

        return new HrApiDtos.Probation(
                HrDeskService.PROBATION_MONTHS,
                "hireDate",
                items.size(),
                items);
    }

    private static HrApiDtos.Forecast forecast(List<User> employees, List<User> withoutForecast) {
        List<HrApiDtos.MissingForecast> items = withoutForecast.stream()
                .map(user -> new HrApiDtos.MissingForecast(
                        user.getId(),
                        user.getFullName(),
                        user.getDepartment(),
                        user.getPosition(),
                        user.getHireDate()))
                .toList();

        return new HrApiDtos.Forecast(
                employees.size(),
                employees.size() - items.size(),
                items.size(),
                items);
    }

    private static HrApiDtos.Absences absences(HrDeskService.DeskSnapshot snapshot) {
        List<HrApiDtos.DepartmentCoverage> coverage = coverage(snapshot.activeUsers(), snapshot.today());

        return new HrApiDtos.Absences(
                snapshot.today().size(),
                snapshot.week().size(),
                snapshot.upcoming().size(),
                snapshot.today().stream().map(HrApiController::toAbsence).toList(),
                snapshot.week().stream().map(HrApiController::toAbsence).toList(),
                snapshot.upcoming().stream().map(HrApiController::toAbsence).toList(),
                coverage);
    }

    /**
     * Отсутствия по подразделениям на якорную дату.
     *
     * <p>Один сотрудник считается один раз, даже если у него две записки с
     * пересекающимися периодами: «отсутствует 4 из 9», а не «5 отсутствий».
     * Процент не выдумывается при пустом подразделении — приходит {@code null}.
     */
    private static List<HrApiDtos.DepartmentCoverage> coverage(List<User> active,
                                                               List<HrDeskService.AbsenceSegment> today) {
        Map<String, Integer> headcount = new LinkedHashMap<>();
        for (User user : active) {
            headcount.merge(departmentOf(user), 1, Integer::sum);
        }

        Map<String, Set<Long>> absent = new LinkedHashMap<>();
        for (HrDeskService.AbsenceSegment segment : today) {
            absent.computeIfAbsent(normalizeDepartment(segment.department()), key -> new HashSet<>())
                    .add(segment.userId());
        }

        List<HrApiDtos.DepartmentCoverage> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : headcount.entrySet()) {
            int total = entry.getValue();
            int absentCount = absent.getOrDefault(entry.getKey(), Set.of()).size();
            Double share = total > 0
                    ? Math.round(absentCount * 1000.0 / total) / 10.0
                    : null;
            result.add(new HrApiDtos.DepartmentCoverage(entry.getKey(), total, absentCount, share));
        }
        result.sort(Comparator.comparingInt(HrApiDtos.DepartmentCoverage::absent).reversed()
                .thenComparing(HrApiDtos.DepartmentCoverage::department));
        return result;
    }

    private static HrApiDtos.Documents documents(List<Document> documents) {
        List<HrApiDtos.DocumentItem> items = documents.stream()
                .map(document -> new HrApiDtos.DocumentItem(
                        document.getId(),
                        document.getRegNumber(),
                        document.getTitle(),
                        document.getDocType() != null ? document.getDocType().name() : null,
                        document.getDocType() != null ? document.getDocType().getTitle() : null,
                        document.getEmployee() != null ? document.getEmployee().getFullName() : null,
                        document.getDocumentDate()))
                .toList();

        return new HrApiDtos.Documents(items.size(), items);
    }

    private static HrApiDtos.Absence toAbsence(HrDeskService.AbsenceSegment segment) {
        return new HrApiDtos.Absence(
                segment.userId(),
                segment.fullName(),
                segment.department(),
                segment.position(),
                segment.kind().name(),
                segment.kind().getTitle(),
                segment.from(),
                segment.to(),
                segment.days(),
                segment.source());
    }

    private static HrApiDtos.AbsenceRequest toAbsenceRequest(ServiceMemo memo) {
        long days = ChronoUnit.DAYS.between(memo.getStartDate(), memo.getEndDate()) + 1;

        return new HrApiDtos.AbsenceRequest(
                memo.getId(),
                memo.getUser().getId(),
                memo.getUser().getFullName(),
                memo.getUser().getDepartment(),
                memo.getMemoType().name(),
                memo.getMemoType().getTitle(),
                memo.getStartDate(),
                memo.getEndDate(),
                Math.max(days, 0),
                memo.getStatus().name(),
                memo.getStatus().getTitle(),
                memo.getCreatedAt());
    }

    // ── Сборка табеля ────────────────────────────────────────────────────

    private static HrApiDtos.TimesheetEmployee toEmployee(TimesheetRow row) {
        User employee = row.employee();

        return new HrApiDtos.TimesheetEmployee(
                employee.getId(),
                employee.getFullName(),
                employee.getDepartment(),
                employee.getPosition(),
                round(row.totalHours()),
                round(row.overtimeHours()),
                row.workingDays(),
                row.remoteDays(),
                row.sickDays(),
                row.vacationDays(),
                row.workingDays() > 0,
                row.entries().stream().map(HrApiController::toDay).toList());
    }

    private static HrApiDtos.TimesheetDay toDay(TimesheetDayDto day) {
        return new HrApiDtos.TimesheetDay(
                day.date(),
                day.hours(),
                day.remote(),
                day.sickLeave(),
                day.vacation(),
                day.overtimeHours());
    }

    /** Итоги складываются из уже посчитанных строк — «своей» формулы часов здесь нет. */
    private static HrApiDtos.TimesheetTotals totals(List<HrApiDtos.TimesheetEmployee> rows) {
        double hours = rows.stream().mapToDouble(HrApiDtos.TimesheetEmployee::totalHours).sum();
        double overtime = rows.stream().mapToDouble(HrApiDtos.TimesheetEmployee::overtimeHours).sum();

        return new HrApiDtos.TimesheetTotals(
                rows.size(),
                (int) rows.stream().filter(HrApiDtos.TimesheetEmployee::hasData).count(),
                round(hours),
                round(overtime),
                rows.stream().mapToLong(HrApiDtos.TimesheetEmployee::workingDays).sum(),
                rows.stream().mapToLong(HrApiDtos.TimesheetEmployee::remoteDays).sum(),
                rows.stream().mapToLong(HrApiDtos.TimesheetEmployee::sickDays).sum(),
                rows.stream().mapToLong(HrApiDtos.TimesheetEmployee::vacationDays).sum());
    }

    // ── Вспомогательное ──────────────────────────────────────────────────

    /**
     * Подпись периода: «октябрь 2026» для месяца и «01.10.2026 — 25.10.2026»
     * для произвольного диапазона. Месяц берётся у существующего
     * {@link TimesheetService#periodLabel}, чтобы не заводить второй словарь
     * названий месяцев.
     */
    private String periodLabel(LocalDate from, LocalDate to) {
        boolean wholeMonth = from.getDayOfMonth() == 1
                && to.getDayOfMonth() == to.lengthOfMonth()
                && from.getMonth() == to.getMonth()
                && from.getYear() == to.getYear();
        return wholeMonth
                ? timesheetService.periodLabel(from)
                : from.format(DAY) + " — " + to.format(DAY);
    }

    private static void requireValidPeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Начало периода позже его конца: " + from + " > " + to);
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException(
                    "Период табеля не может быть длиннее " + MAX_PERIOD_DAYS + " дней");
        }
    }

    private static String departmentOf(User user) {
        return normalizeDepartment(user.getDepartment());
    }

    private static String normalizeDepartment(String department) {
        return department == null || department.isBlank() ? WITHOUT_DEPARTMENT : department;
    }

    /** Два знака — презентационное округление: в JSON не нужны «39.900000000000006». */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
