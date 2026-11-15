package com.staffsense.demo.api.oneonone;

import java.time.LocalDateTime;

/**
 * DTO встреч 1:1 для клиента.
 *
 * <p>Отдаём обе стороны встречи и признак «моя роль»: страница сотрудника
 * рисует одно и то же событие и когда он подчинённый, и когда он руководитель,
 * не выясняя это сравнением id на клиенте.
 */
public final class OneOnOneApiDtos {

    private OneOnOneApiDtos() {
    }

    /**
     * Встреча глазами текущего пользователя.
     *
     * @param upcoming  встреча ещё впереди ({@code PLANNED} и время не прошло)
     * @param canManage текущий пользователь может завершить/отменить встречу
     *                  (ADMIN или её руководитель) — клиент не повторяет правило
     * @param myRole    {@code EMPLOYEE} или {@code MANAGER} — кем участвует пользователь
     */
    public record Meeting(
            Long id,
            Long employeeId,
            String employeeName,
            Long managerId,
            String managerName,
            LocalDateTime scheduledAt,
            String status,
            String statusTitle,
            boolean missed,
            String notes,
            String actionItems,
            boolean upcoming,
            boolean canManage,
            String myRole
    ) {
    }
}
