package com.staffsense.demo.api.risk;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO администрирования прогнозов (FR-039 и журнал V9).
 *
 * <p>Экран администрирования раньше существовал только как серверный шаблон.
 * Для нового клиента нужны данные в JSON: сводка за период, покрытие, последние
 * снимки и запрос на смену порогов.
 */
public final class RiskAdminDtos {

    private RiskAdminDtos() {
    }

    /** Строка журнала. */
    public record Entry(
            LocalDateTime at,
            Long userId,
            String fullName,
            String department,
            Double riskScore,
            String riskLevel,
            String source
    ) {
    }

    /** Покрытие журналом: сколько снимков и кем посчитаны. */
    public record Coverage(
            long totalSnapshots,
            long heuristicSnapshots,
            long seedSnapshots,
            long employeesWithoutHistory,
            LocalDateTime lastComputedAt
    ) {
    }

    /** Сводка за период + последние записи. */
    public record Journal(
            String period,
            LocalDateTime from,
            LocalDateTime to,
            long total,
            long low,
            long medium,
            long high,
            long distinctUsers,
            List<Entry> latest,
            Coverage coverage
    ) {
    }

    /** Запрос на смену порогов. */
    public record ThresholdRequest(double low, double medium, boolean recompute) {
    }
}
