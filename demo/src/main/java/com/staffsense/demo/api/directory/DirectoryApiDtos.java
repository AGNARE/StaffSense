package com.staffsense.demo.api.directory;

import com.staffsense.demo.dto.directory.DirectoryEntryDto;

import java.util.List;

/**
 * Контракт справочника сотрудников (FR-043, BR-031).
 *
 * <p>🔴 Набор полей намеренно ограничен: справочник открыт любому сотруднику,
 * поэтому в него попадают только ФИО, должность, отдел, рабочий e-mail и
 * руководитель. Оклада, KPI, индекса нагрузки и прогноза риска здесь нет —
 * они живут в разделе «Сотрудники», доступном только ADMIN/HR/PM/EXEC.
 * Строка справочника переиспользует {@link DirectoryEntryDto} серверной
 * страницы, чтобы оба входа отдавали ровно один и тот же безопасный набор.
 */
public final class DirectoryApiDtos {

    private DirectoryApiDtos() {
    }

    /** Подразделение и число активных сотрудников в нём. */
    public record DepartmentGroup(String name, long count) {
    }

    /**
     * Руководитель и число его прямых подчинённых.
     *
     * <p>Считается по активным сотрудникам: справочник показывает структуру
     * компании, а не кадровый архив.
     */
    public record Manager(
            Long id,
            String fullName,
            String position,
            String department,
            String email,
            long directReports
    ) {
    }

    /**
     * Справочник целиком.
     *
     * @param total       всего активных сотрудников в компании
     * @param shown       сколько строк вернулось после фильтра
     * @param departments подразделения со счётчиками (всегда по всей компании)
     * @param employees   строки справочника (с учётом фильтров)
     * @param managers    руководители с числом подчинённых
     */
    public record Response(
            long total,
            long shown,
            List<DepartmentGroup> departments,
            List<DirectoryEntryDto> employees,
            List<Manager> managers
    ) {
    }
}
