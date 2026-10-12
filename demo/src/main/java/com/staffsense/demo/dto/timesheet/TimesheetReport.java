package com.staffsense.demo.dto.timesheet;

import java.time.LocalDate;
import java.util.List;

/**
 * Отчёт по табелю за период.
 *
 * @param periodStart начало периода
 * @param periodEnd   конец периода
 * @param periodLabel подпись периода («октябрь 2026»)
 * @param scope       область отчёта: по команде или сводный по компании
 * @param closed      закрыт ли период
 * @param rows        строки по сотрудникам
 */
public record TimesheetReport(
        LocalDate periodStart,
        LocalDate periodEnd,
        String periodLabel,
        String scope,
        boolean closed,
        List<TimesheetRow> rows
) {
    public double totalHours() {
        return rows.stream().mapToDouble(TimesheetRow::totalHours).sum();
    }

    public double totalOvertime() {
        return rows.stream().mapToDouble(TimesheetRow::overtimeHours).sum();
    }
}
