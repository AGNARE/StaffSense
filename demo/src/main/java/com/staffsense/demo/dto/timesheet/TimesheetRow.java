package com.staffsense.demo.dto.timesheet;

import com.staffsense.demo.model.User;

import java.util.List;

/**
 * Строка отчёта по табелю за период — по одному сотруднику.
 *
 * @param employee       сотрудник
 * @param totalHours     суммарные отработанные часы
 * @param overtimeHours  суммарные переработки
 * @param remoteDays     дни удалённой работы
 * @param sickDays       дни больничного
 * @param vacationDays   дни отпуска
 * @param workingDays    число дней с заполненным табелем
 * @param entries        детализация по дням
 */
public record TimesheetRow(
        User employee,
        double totalHours,
        double overtimeHours,
        long remoteDays,
        long sickDays,
        long vacationDays,
        long workingDays,
        List<TimesheetDayDto> entries
) {
}
