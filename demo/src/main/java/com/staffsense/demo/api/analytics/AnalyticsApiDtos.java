package com.staffsense.demo.api.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO REST-контракта {@code /api/v1/analytics}.
 *
 * <p>Собраны в одном файле по тому же принципу, что и {@code RiskApiDtos}:
 * это законченный контракт одного ресурса. JPA-сущности наружу не отдаются —
 * у {@code WorkloadRecord}/{@code KpiRecord} ленивая связь на пользователя,
 * и сериализация сущности дала бы либо {@code LazyInitializationException},
 * либо утечку лишних полей.
 *
 * <p><b>Правило честности контракта:</b> если показателя нет, поле равно
 * {@code null}, а {@code hasData} — {@code false}. Прежние HTML-страницы
 * подставляли {@code 0.0} («нагрузка 0», «KPI 0»), из-за чего отсутствие
 * расчёта выглядело как измеренный нулевой результат. Здесь это разные
 * утверждения, и клиент показывает прочерк «—», а не ноль.
 *
 * <p>Имена полей — camelCase, даты — ISO-8601, {@code workloadIndex} —
 * безразмерный коэффициент (1.0 = норма), {@code kpiScore} и проценты — 0..100.
 */
public final class AnalyticsApiDtos {

    private AnalyticsApiDtos() {
    }

    /**
     * Страница в форме, привычной клиенту (см. {@code RiskApiDtos.Page}).
     * Spring {@code Page} наружу не отдаём: его JSON — внутренний формат
     * фреймворка, а не контракт.
     */
    public record Page<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    // ── Нагрузка ─────────────────────────────────────────────────────────

    /**
     * Ответ {@code GET /api/v1/analytics/workload}.
     *
     * @param generatedAt       момент формирования ответа
     * @param overloadThreshold порог критической перегрузки BR-002
     *                          ({@code WorkloadCalculationService.OVERLOAD_THRESHOLD});
     *                          отдаётся клиенту, чтобы интерфейс не дублировал константу
     * @param employees         страница сотрудников (сортировка: индекс по убыванию,
     *                          без данных — в конце)
     * @param teams             агрегаты по отделам, посчитанные по всем видимым
     *                          сотрудникам (не только по текущей странице)
     * @param withDataCount     сколько видимых сотрудников имеют запись нагрузки
     */
    public record WorkloadResponse(
            LocalDateTime generatedAt,
            double overloadThreshold,
            Page<WorkloadEmployee> employees,
            List<WorkloadTeam> teams,
            int withDataCount
    ) {
    }

    /**
     * Последняя запись нагрузки сотрудника.
     *
     * <p>Период у каждого сотрудника свой — это период его последней записи
     * {@code WorkloadRecord}, а не «текущий месяц»: API не придумывает окно,
     * которого нет в данных.
     */
    public record WorkloadEmployee(
            long userId,
            String fullName,
            String department,
            String position,
            LocalDate periodStart,
            LocalDate periodEnd,
            Double workloadIndex,
            Double totalEstimatedHours,
            Double capacityHours,
            Double overtimeHours,
            LocalDateTime computedAt,
            boolean hasData,
            boolean overloaded
    ) {
    }

    /**
     * Агрегат по отделу.
     *
     * @param avgWorkloadIndex среднее по сотрудникам отдела, у которых есть
     *                         запись; {@code null}, если записей нет ни у кого
     */
    public record WorkloadTeam(
            String department,
            int employeeCount,
            int withDataCount,
            Double avgWorkloadIndex,
            int overloadedCount
    ) {
    }

    // ── KPI ──────────────────────────────────────────────────────────────

    /** Ответ {@code GET /api/v1/analytics/kpi}. */
    public record KpiResponse(
            LocalDateTime generatedAt,
            Page<KpiEmployee> employees,
            int withDataCount
    ) {
    }

    /** Последняя запись KPI сотрудника; {@code null}-поля означают «расчёта не было». */
    public record KpiEmployee(
            long userId,
            String fullName,
            String department,
            String position,
            LocalDate periodStart,
            LocalDate periodEnd,
            Double kpiScore,
            Double onTimeCompletionRate,
            Double reopenRate,
            Double managerRating,
            LocalDateTime computedAt,
            boolean hasData
    ) {
    }

    // ── Сводка ───────────────────────────────────────────────────────────

    /**
     * Ответ {@code GET /api/v1/analytics/summary} — карточки над вкладками.
     *
     * <p>Средние считаются только по сотрудникам с данными и округляются до
     * двух знаков (презентационное округление, не изменение метрики).
     * Пустая выборка даёт {@code null}, а не ноль.
     */
    public record Summary(
            int employees,
            int withWorkload,
            int withKpi,
            int overloaded,
            Double avgWorkloadIndex,
            Double avgKpiScore,
            LocalDateTime generatedAt
    ) {
    }
}
