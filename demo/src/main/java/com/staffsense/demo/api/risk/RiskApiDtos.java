package com.staffsense.demo.api.risk;

import com.staffsense.demo.service.risk.RiskTrend;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO REST-контракта {@code /api/v1/risk}.
 *
 * <p>Собраны в одном файле намеренно: это законченный контракт одного ресурса,
 * и читать его целиком удобнее, чем прыгать по десятку файлов ради record из
 * трёх полей. Сущности JPA наружу не отдаются: у них ленивые связи, и любой
 * {@code @Entity} в ответе рано или поздно даёт {@code LazyInitializationException}
 * или утечку лишних полей.
 *
 * <p>Правило контракта: имена полей — в camelCase, даты — ISO-8601,
 * {@code riskScore} — 0..100, {@code riskLevel} — LOW / MEDIUM / HIGH / UNKNOWN.
 */
public final class RiskApiDtos {

    private RiskApiDtos() {
    }

    /** Строка списка рисков. */
    public record ListItem(
            Long userId,
            String fullName,
            String department,
            String position,
            String gradeLevel,
            double riskScore,
            String riskLevel,
            LocalDateTime computedAt,
            Double trendDelta,
            String topFactor
    ) {
    }

    /** Страница в форме, привычной для клиента (Spring Page наружу не отдаём). */
    public record Page<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    /** Карточка одного признака с «светофором». */
    public record Feature(
            String key,
            String label,
            String icon,
            double value,
            String status,
            String color,
            String description
    ) {
    }

    /** Фактор риска из объяснения ML-модели. */
    public record Factor(
            String factor,
            double impact,
            String description
    ) {
    }

    /** Тренд риска для графика. */
    public record Trend(
            String polyline,
            double minScore,
            double maxScore,
            double delta,
            List<TrendPoint> points
    ) {
    }

    public record TrendPoint(LocalDateTime at, double score) {
    }

    /** Детали прогноза сотрудника. */
    public record Detail(
            Long userId,
            String fullName,
            String department,
            String position,
            String gradeLevel,
            Double riskScore,
            String riskLevel,
            LocalDateTime computedAt,
            List<Feature> features,
            List<Factor> factors,
            Trend trend,
            /** ML | HEURISTIC | SEED — чем посчитан текущий прогноз. */
            String source
    ) {
    }

    /** Действующие пороги уровней (FR-039). */
    public record Thresholds(
            double low,
            double medium,
            LocalDateTime updatedAt,
            boolean usingDefaults
    ) {
    }

    /** Преобразование сервисных объектов в DTO. */
    public static Trend trendOf(RiskTrend source) {
        if (source == null || source.isEmpty()) {
            return new Trend("", 0, 0, 0, List.of());
        }
        return new Trend(
                source.getPolyline(),
                source.getMinScore(),
                source.getMaxScore(),
                source.getDelta(),
                source.getPoints().stream()
                        .map(p -> new TrendPoint(p.at(), p.score()))
                        .toList()
        );
    }
}
