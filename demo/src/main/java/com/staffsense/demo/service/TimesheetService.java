package com.staffsense.demo.service;

import com.staffsense.demo.dto.timesheet.TimesheetDayDto;
import com.staffsense.demo.dto.timesheet.TimesheetReport;
import com.staffsense.demo.dto.timesheet.TimesheetRow;
import com.staffsense.demo.exception.ResourceNotFoundException;
import com.staffsense.demo.model.*;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.WorkSchedulePeriodRepository;
import com.staffsense.demo.repository.WorkScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Табель рабочего времени (модуль C).
 *
 * <p>Бизнес-правила:
 * <ul>
 *   <li><b>BR-027</b> — часы вносит руководитель по своей команде; HR и ADMIN правят
 *       любые табели; сотрудник свои часы <b>не редактирует</b> (только чтение);</li>
 *   <li><b>BR-028</b> — табель за закрытый период не редактируется;</li>
 *   <li><b>BR-030</b> — утверждённый больничный/отпуск автоматически проставляется в табеле.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TimesheetService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetService.class);

    /** Норма часов в день для дней отсутствия (больничный/отпуск). */
    public static final double ABSENCE_HOURS_PER_DAY = 8.0;

    private final WorkScheduleRepository scheduleRepository;
    private final WorkSchedulePeriodRepository periodRepository;
    private final UserRepository userRepository;
    private final TeamAccessService teamAccessService;

    // ── Права ────────────────────────────────────────────────────────────

    /** Может ли пользователь вести (создавать/править) табель указанного сотрудника. */
    public boolean canEdit(User actor, User employee) {
        if (actor == null || employee == null) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN, HR -> true;
            case PM -> teamAccessService.isEmployeeInTeamOf(actor, employee);
            case EMPLOYEE, EXEC -> false;
        };
    }

    public void requireCanEdit(User actor, User employee) {
        if (!canEdit(actor, employee)) {
            throw new AccessDeniedException(
                    "Табель ведёт руководитель по своей команде (сотрудник ID=" + employee.getId() + ")");
        }
    }

    /** Может ли пользователь просматривать табель указанного сотрудника. */
    public boolean canView(User actor, User employee) {
        if (actor == null || employee == null) {
            return false;
        }
        if (actor.getId() != null && actor.getId().equals(employee.getId())) {
            return true;
        }
        return switch (actor.getRole()) {
            case ADMIN, HR, EXEC -> true;
            case PM -> teamAccessService.isEmployeeInTeamOf(actor, employee);
            case EMPLOYEE -> false;
        };
    }

    /**
     * Сотрудники, чей табель пользователь может вести.
     *
     * <p>ADMIN/HR ведут табель любых сотрудников (дополнение к ТЗ, раздел 6: «✓ все»),
     * а не только пользователей с ролью EMPLOYEE — иначе права в {@link #canEdit}
     * и список в интерфейсе расходились бы.
     */
    public List<User> getEditableEmployees(User actor) {
        return switch (actor.getRole()) {
            case ADMIN, HR -> allActiveUsers();
            case PM -> allActiveUsers().stream()
                    .filter(u -> teamAccessService.isEmployeeInTeamOf(actor, u))
                    .toList();
            default -> List.of();
        };
    }

    /** Сотрудники, чей табель пользователь может видеть (сводный отчёт — по всей компании). */
    public List<User> getViewableEmployees(User actor) {
        return switch (actor.getRole()) {
            case ADMIN, HR, EXEC -> allActiveUsers();
            case PM -> {
                List<User> team = allActiveUsers().stream()
                        .filter(u -> teamAccessService.isEmployeeInTeamOf(actor, u))
                        .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
                team.add(actor);
                yield team;
            }
            default -> List.of(actor);
        };
    }

    private List<User> allActiveUsers() {
        return userRepository.findByActive(true).stream()
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    // ── Записи табеля ────────────────────────────────────────────────────

    public List<WorkSchedule> getEntries(Long userId, LocalDate from, LocalDate to) {
        return scheduleRepository.findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(userId, from, to);
    }

    /**
     * Внести или обновить запись табеля за день.
     *
     * @throws AccessDeniedException    если нет прав на ведение табеля этого сотрудника
     * @throws IllegalStateException    если период закрыт (BR-028)
     */
    @Transactional
    public WorkSchedule saveEntry(User actor, Long employeeId, LocalDate date,
                                  Double hours, boolean remote, boolean sickLeave,
                                  boolean vacation, Double overtime) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден: " + employeeId));

        requireCanEdit(actor, employee);

        if (isDateLocked(employee, date)) {
            throw new IllegalStateException(
                    "Период за " + date.getMonthValue() + "." + date.getYear()
                            + " закрыт — правка табеля запрещена");
        }

        WorkSchedule entry = scheduleRepository.findByUserIdAndWorkDate(employeeId, date)
                .orElseGet(WorkSchedule::new);

        entry.setUser(employee);
        entry.setWorkDate(date);
        entry.setHoursWorked(hours);
        entry.setRemote(remote);
        entry.setSickLeave(sickLeave);
        entry.setVacation(vacation);
        entry.setOvertimeHours(overtime != null ? overtime : 0.0);
        entry.setCreatedBy(actor);

        WorkSchedule saved = scheduleRepository.save(entry);
        log.info("Timesheet: user {} date {} hours {} recorded by {}",
                employeeId, date, hours, actor.getEmail());
        return saved;
    }

    /** Удалить запись табеля за день. */
    @Transactional
    public void deleteEntry(User actor, Long employeeId, LocalDate date) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден: " + employeeId));
        requireCanEdit(actor, employee);

        if (isDateLocked(employee, date)) {
            throw new IllegalStateException("Период закрыт — правка табеля запрещена");
        }
        scheduleRepository.findByUserIdAndWorkDate(employeeId, date)
                .ifPresent(scheduleRepository::delete);
    }

    // ── Закрытие периода ─────────────────────────────────────────────────

    /**
     * Закрыт ли период (месяц), к которому относится дата, для команды сотрудника.
     *
     * <p>Период хранится по руководителю команды. У сотрудника без руководителя
     * (PM, HR, ADMIN, EXEC) владельцем периода считается он сам — иначе BR-028
     * для таких сотрудников не действовал бы вовсе.
     */
    public boolean isDateLocked(User employee, LocalDate date) {
        if (employee == null || date == null) {
            return false;
        }
        User periodOwner = employee.getManager() != null ? employee.getManager() : employee;
        return findPeriod(periodOwner, date)
                .map(p -> p.getStatus() == TimesheetPeriodStatus.CLOSED)
                .orElse(false);
    }

    /**
     * Закрыт ли хотя бы один день диапазона (BR-028).
     *
     * <p>Нужен перед автоматической простановкой дней по утверждённой записке:
     * без этой проверки утверждение постфактум меняло бы табель закрытого месяца.
     */
    public boolean isRangeLocked(User employee, LocalDate from, LocalDate to) {
        if (employee == null || from == null || to == null) {
            return false;
        }
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (isDateLocked(employee, date)) {
                return true;
            }
        }
        return false;
    }

    public Optional<WorkSchedulePeriod> findPeriod(User manager, LocalDate anyDateInPeriod) {
        LocalDate start = anyDateInPeriod.withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return periodRepository.findByManagerIdAndPeriodStartAndPeriodEnd(manager.getId(), start, end);
    }

    /** Закрыть месяц для команды руководителя. */
    @Transactional
    public WorkSchedulePeriod closePeriod(User actor, User manager, int year, int month) {
        boolean allowed = actor.getRole() == Role.ADMIN
                || actor.getRole() == Role.HR
                || (actor.getRole() == Role.PM && actor.getId().equals(manager.getId()));
        if (!allowed) {
            throw new AccessDeniedException("Закрывать период может руководитель команды, HR или ADMIN");
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        WorkSchedulePeriod period = periodRepository
                .findByManagerIdAndPeriodStartAndPeriodEnd(manager.getId(), start, end)
                .orElseGet(() -> WorkSchedulePeriod.builder()
                        .manager(manager)
                        .periodStart(start)
                        .periodEnd(end)
                        .build());

        period.setStatus(TimesheetPeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(actor);

        log.info("Timesheet period {}.{} closed for manager {} by {}",
                month, year, manager.getEmail(), actor.getEmail());
        return periodRepository.save(period);
    }

    // ── Отчёты ───────────────────────────────────────────────────────────

    /** Отчёт по табелю за период для указанных сотрудников. */
    public TimesheetReport buildReport(List<User> employees, LocalDate from, LocalDate to, String scope) {
        List<TimesheetRow> rows = employees.stream()
                .map(employee -> buildRow(employee, from, to))
                .toList();

        // Отчёт считается закрытым, если закрыт период хотя бы одного сотрудника
        // (у сотрудников без руководителя период ведётся по ним самим).
        boolean closed = employees.stream().anyMatch(employee -> isDateLocked(employee, from));

        return new TimesheetReport(from, to, periodLabel(from), scope, closed, rows);
    }

    private TimesheetRow buildRow(User employee, LocalDate from, LocalDate to) {
        List<WorkSchedule> entries = getEntries(employee.getId(), from, to);

        double totalHours = entries.stream()
                .mapToDouble(e -> e.getHoursWorked() != null ? e.getHoursWorked() : 0.0)
                .sum();
        double overtime = entries.stream()
                .mapToDouble(e -> e.getOvertimeHours() != null ? e.getOvertimeHours() : 0.0)
                .sum();

        List<TimesheetDayDto> days = entries.stream()
                .map(e -> new TimesheetDayDto(e.getWorkDate(), e.getHoursWorked(),
                        e.isRemote(), e.isSickLeave(), e.isVacation(), e.getOvertimeHours()))
                .toList();

        return new TimesheetRow(
                employee,
                totalHours,
                overtime,
                entries.stream().filter(WorkSchedule::isRemote).count(),
                entries.stream().filter(WorkSchedule::isSickLeave).count(),
                entries.stream().filter(WorkSchedule::isVacation).count(),
                entries.size(),
                days);
    }

    public String periodLabel(LocalDate date) {
        String[] months = {"январь", "февраль", "март", "апрель", "май", "июнь",
                "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};
        return months[date.getMonthValue() - 1] + " " + date.getYear();
    }

    // ── Связка со служебными записками (BR-030) ──────────────────────────

    /**
     * Проставляет дни отсутствия в табеле по утверждённой записке.
     * Больничный → {@code is_sick_leave}, отпуск → {@code is_vacation};
     * часы в такие дни обнуляются.
     *
     * @return число затронутых дней
     */
    @Transactional
    public int applyApprovedMemo(ServiceMemo memo) {
        if (!memo.isAffectsTimesheet()) {
            return 0;
        }

        boolean sick = memo.getMemoType() == MemoType.SICK_LEAVE;
        int affected = 0;

        for (LocalDate date = memo.getStartDate();
             !date.isAfter(memo.getEndDate());
             date = date.plusDays(1)) {

            WorkSchedule entry = scheduleRepository
                    .findByUserIdAndWorkDate(memo.getUser().getId(), date)
                    .orElseGet(WorkSchedule::new);

            entry.setUser(memo.getUser());
            entry.setWorkDate(date);
            entry.setHoursWorked(0.0);
            entry.setSickLeave(sick);
            entry.setVacation(!sick);
            entry.setOvertimeHours(0.0);
            entry.setCreatedBy(memo.getHrDecisionBy() != null ? memo.getHrDecisionBy() : memo.getUser());
            scheduleRepository.save(entry);
            affected++;
        }

        log.info("Timesheet: applied approved memo {} ({}), {} day(s) marked",
                memo.getId(), memo.getMemoType(), affected);
        return affected;
    }

    /**
     * Дни отсутствия (больничный + отпуск) сотрудника за период —
     * используется при расчёте нагрузки.
     */
    public long countAbsenceDays(Long userId, LocalDate from, LocalDate to) {
        return getEntries(userId, from, to).stream()
                .filter(e -> e.isSickLeave() || e.isVacation())
                .count();
    }
}
