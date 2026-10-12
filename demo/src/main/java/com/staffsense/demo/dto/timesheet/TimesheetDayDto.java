package com.staffsense.demo.dto.timesheet;

import java.time.LocalDate;

/** День табеля в отчёте. */
public record TimesheetDayDto(
        LocalDate date,
        Double hours,
        boolean remote,
        boolean sickLeave,
        boolean vacation,
        Double overtimeHours
) {
}
