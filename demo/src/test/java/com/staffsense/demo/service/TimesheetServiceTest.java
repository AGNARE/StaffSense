package com.staffsense.demo.service;

import com.staffsense.demo.dto.timesheet.TimesheetReport;
import com.staffsense.demo.model.Role;
import com.staffsense.demo.model.User;
import com.staffsense.demo.model.WorkSchedule;
import com.staffsense.demo.repository.UserRepository;
import com.staffsense.demo.repository.WorkScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты табеля рабочего времени (BR-027, BR-028).
 *
 * <p>Ключевые правила: часы вносит руководитель по своей команде;
 * HR и ADMIN правят любые табели; сотрудник — только чтение;
 * закрытый период не редактируется.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimesheetServiceTest {

    @Autowired
    private TimesheetService timesheetService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkScheduleRepository scheduleRepository;

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void pmCanEditOwnTeamMember() {
        User pm = user("pm@staffsense.com");
        User employee = user("ivan@staffsense.com");

        assertTrue(timesheetService.canEdit(pm, employee),
                "PM должен вести табель своей команды");
    }

    @Test
    void pmCannotEditAnotherTeam() {
        User pm = user("pm@staffsense.com");
        User otherTeamEmployee = userRepository.findByActive(true).stream()
                .filter(u -> u.getManager() != null && !u.getManager().getId().equals(pm.getId()))
                .findFirst()
                .orElseThrow();

        assertFalse(timesheetService.canEdit(pm, otherTeamEmployee),
                "PM не должен вести табель чужой команды");
        assertThrows(AccessDeniedException.class,
                () -> timesheetService.saveEntry(pm, otherTeamEmployee.getId(),
                        LocalDate.now(), 8.0, false, false, false, 0.0));
    }

    @Test
    void employeeCannotEditEvenOwnTimesheet() {
        User employee = user("ivan@staffsense.com");

        assertFalse(timesheetService.canEdit(employee, employee),
                "Сотрудник не вносит часы сам — только чтение (BR-027)");
        assertThrows(AccessDeniedException.class,
                () -> timesheetService.saveEntry(employee, employee.getId(),
                        LocalDate.now(), 8.0, false, false, false, 0.0));
    }

    @Test
    void hrAndAdminCanEditAnyTimesheet() {
        User employee = user("ivan@staffsense.com");
        assertTrue(timesheetService.canEdit(user("hr@staffsense.com"), employee));
        assertTrue(timesheetService.canEdit(user("admin@staffsense.com"), employee));
    }

    @Test
    void saveEntryCreatesAndUpdatesSameDay() {
        User pm = user("pm@staffsense.com");
        User employee = user("ivan@staffsense.com");
        LocalDate date = LocalDate.now().withDayOfMonth(1);

        WorkSchedule first = timesheetService.saveEntry(pm, employee.getId(), date,
                8.0, true, false, false, 0.0);
        assertEquals(8.0, first.getHoursWorked(), 0.001);
        assertTrue(first.isRemote());
        assertEquals(pm.getId(), first.getCreatedBy().getId());

        WorkSchedule second = timesheetService.saveEntry(pm, employee.getId(), date,
                6.0, false, false, false, 1.5);

        assertEquals(first.getId(), second.getId(), "Запись за день должна обновляться, а не дублироваться");
        assertEquals(6.0, second.getHoursWorked(), 0.001);
        assertEquals(1.5, second.getOvertimeHours(), 0.001);
        assertEquals(1, scheduleRepository
                .findByUserIdAndWorkDateBetweenOrderByWorkDateAsc(employee.getId(), date, date).size());
    }

    @Test
    void closedPeriodBlocksEditing() {
        User pm = user("pm@staffsense.com");
        User employee = user("ivan@staffsense.com");
        LocalDate date = LocalDate.now().withDayOfMonth(1);

        timesheetService.saveEntry(pm, employee.getId(), date, 8.0, false, false, false, 0.0);
        timesheetService.closePeriod(pm, pm, date.getYear(), date.getMonthValue());

        assertTrue(timesheetService.isDateLocked(employee, date), "Период должен считаться закрытым");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> timesheetService.saveEntry(pm, employee.getId(), date, 4.0, false, false, false, 0.0));
        assertTrue(ex.getMessage().contains("закрыт"));
    }

    @Test
    void employeeCannotClosePeriod() {
        User employee = user("ivan@staffsense.com");
        LocalDate date = LocalDate.now();

        assertThrows(AccessDeniedException.class,
                () -> timesheetService.closePeriod(employee, employee,
                        date.getYear(), date.getMonthValue()));
    }

    @Test
    void reportAggregatesHoursAndMarks() {
        User pm = user("pm@staffsense.com");
        User employee = user("ivan@staffsense.com");
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        timesheetService.saveEntry(pm, employee.getId(), start, 8.0, true, false, false, 0.0);
        timesheetService.saveEntry(pm, employee.getId(), start.plusDays(1), 4.0, false, true, false, 2.0);

        TimesheetReport report = timesheetService.buildReport(List.of(employee), start, end, "По команде");

        assertEquals(1, report.rows().size());
        var row = report.rows().get(0);
        assertEquals(12.0, row.totalHours(), 0.001);
        assertEquals(2.0, row.overtimeHours(), 0.001);
        assertEquals(1, row.remoteDays());
        assertEquals(1, row.sickDays());
        assertEquals(2, row.workingDays());
        assertEquals(12.0, report.totalHours(), 0.001);
    }

    @Test
    void absenceDaysCountedForWorkload() {
        User pm = user("pm@staffsense.com");
        User employee = user("ivan@staffsense.com");
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        assertEquals(0, timesheetService.countAbsenceDays(employee.getId(), start, end));

        timesheetService.saveEntry(pm, employee.getId(), start, 0.0, false, true, false, 0.0);
        timesheetService.saveEntry(pm, employee.getId(), start.plusDays(1), 0.0, false, false, true, 0.0);

        assertEquals(2, timesheetService.countAbsenceDays(employee.getId(), start, end),
                "Дни больничного и отпуска учитываются при расчёте нагрузки (BR-030)");
    }

    /**
     * У сотрудника без руководителя (PM, HR, ADMIN) период ведётся по нему самому.
     * Раньше {@code isDateLocked} в этом случае всегда возвращал false и BR-028
     * для таких сотрудников не действовал.
     */
    @Test
    void lockedPeriodIsDetectedForEmployeeWithoutManager() {
        User pm = user("pm@staffsense.com");
        LocalDate date = LocalDate.now().withDayOfMonth(1);

        assertFalse(timesheetService.isDateLocked(pm, date), "До закрытия период открыт");

        timesheetService.closePeriod(pm, pm, date.getYear(), date.getMonthValue());

        assertTrue(timesheetService.isDateLocked(pm, date),
                "Закрытый период должен блокировать правку и без руководителя");
    }

    @Test
    void rangeLockCheckCoversWholePeriod() {
        User pm = user("pm@staffsense.com");
        User employee = user("ivan@staffsense.com");
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        assertFalse(timesheetService.isRangeLocked(employee, start, end));

        timesheetService.closePeriod(pm, pm, start.getYear(), start.getMonthValue());

        assertTrue(timesheetService.isRangeLocked(employee, start, end),
                "Закрытие месяца должно обнаруживаться на всём диапазоне (BR-028)");
    }

    /** HR ведёт табель любых сотрудников (дополнение к ТЗ, раздел 6: «✓ все»). */
    @Test
    void hrCanPickAnyEmployeeForTimesheet() {
        User hr = user("hr@staffsense.com");

        List<User> editable = timesheetService.getEditableEmployees(hr);

        assertTrue(editable.stream().anyMatch(u -> u.getRole() != Role.EMPLOYEE),
                "В списке HR должны быть не только сотрудники роли EMPLOYEE");
        assertTrue(editable.stream().anyMatch(u -> "pm@staffsense.com".equals(u.getEmail())),
                "Руководитель тоже доступен HR для ведения табеля");
    }
}
