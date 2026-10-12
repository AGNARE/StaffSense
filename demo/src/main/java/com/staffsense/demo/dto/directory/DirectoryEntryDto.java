package com.staffsense.demo.dto.directory;

/**
 * Запись справочника сотрудников.
 *
 * <p>🔴 Намеренно ограниченный набор полей: справочник доступен всем сотрудникам,
 * поэтому в него <b>не попадают</b> оклад, KPI, нагрузка и прогноз риска —
 * в отличие от {@code /employees}, который доступен только ADMIN/HR/PM.
 */
public record DirectoryEntryDto(
        Long id,
        String fullName,
        String position,
        String department,
        String email,
        String managerName
) {
}
