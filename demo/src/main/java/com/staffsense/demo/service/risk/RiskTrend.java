package com.staffsense.demo.service.risk;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Геометрия мини-графика тренда риска для карточки сотрудника.
 *
 * <p>Считается на сервере и отдаётся в шаблон готовой строкой {@code points}
 * для SVG-полилинии: так график не зависит от JS-библиотек и работает на
 * странице, где Chart.js не подключён. Логика вынесена из контроллера, чтобы
 * её можно было проверить тестом без Spring и без браузера.
 *
 * <p>Данные берутся из журнала {@code attrition_prediction_history} (V9),
 * поэтому тренд появляется только после первого пересчёта: до миграции
 * история не хранилась.
 */
public final class RiskTrend {

    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

    /** Точка тренда: дата, балл и координаты в системе viewBox. */
    public record Point(LocalDateTime at, double score, double x, double y) {

        public String date() {
            return at == null ? "" : at.format(DATE_FORMAT);
        }

        public String scoreText() {
            return String.format(java.util.Locale.US, "%.1f", score).replace('.', ',');
        }
    }

    private final List<Point> points;
    private final double minScore;
    private final double maxScore;
    private final String polyline;
    private final boolean empty;

    private RiskTrend(List<Point> points, double minScore, double maxScore,
                      String polyline, boolean empty) {
        this.points = points;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.polyline = polyline;
        this.empty = empty;
    }

    /**
     * Строит тренд из снимков.
     *
     * @param snapshots снимки в порядке возрастания даты (старые → новые)
     * @param width     ширина viewBox
     * @param height    высота viewBox
     * @param padding   отступ сверху и снизу, чтобы точки не прилипали к краям
     */
    public static RiskTrend of(List<Snapshot> snapshots, double width, double height, double padding) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new RiskTrend(List.of(), 0, 0, "", true);
        }

        int count = snapshots.size();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Snapshot snapshot : snapshots) {
            min = Math.min(min, snapshot.score());
            max = Math.max(max, snapshot.score());
        }
        // Если все баллы равны, рисуем линию по центру, а не делим на ноль.
        double span = (max - min) < 1e-9 ? 1.0 : (max - min);
        double usableHeight = Math.max(1.0, height - 2 * padding);

        List<Point> points = new ArrayList<>(count);
        StringBuilder polyline = new StringBuilder();
        for (int i = 0; i < count; i++) {
            Snapshot snapshot = snapshots.get(i);
            double x = count == 1 ? width / 2 : (double) i / (count - 1) * width;
            double y = padding + (1 - (snapshot.score() - min) / span) * usableHeight;
            Point point = new Point(snapshot.at(), snapshot.score(), x, y);
            points.add(point);
            if (polyline.length() > 0) {
                polyline.append(' ');
            }
            polyline.append(String.format(java.util.Locale.US, "%.1f,%.1f", x, y));
        }

        return new RiskTrend(List.copyOf(points), min, max, polyline.toString(), false);
    }

    /** Снимок для графика: дата расчёта и балл. */
    public record Snapshot(LocalDateTime at, double score) {
    }

    /** Минимум и максимум с запятой — как принято в русскоязычном интерфейсе. */
    public String getMinText() {
        return String.format(java.util.Locale.US, "%.1f", minScore).replace('.', ',');
    }

    public String getMaxText() {
        return String.format(java.util.Locale.US, "%.1f", maxScore).replace('.', ',');
    }

    public List<Point> getPoints() {
        return points;
    }

    public String getPolyline() {
        return polyline;
    }

    public double getMinScore() {
        return minScore;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public boolean isEmpty() {
        return empty;
    }

    public int size() {
        return points.size();
    }

    /** Дельта между последним и первым снимком; положительная — риск вырос. */
    public double getDelta() {
        if (points.size() < 2) {
            return 0.0;
        }
        return points.get(points.size() - 1).score() - points.get(0).score();
    }

    public String getDeltaText() {
        double delta = getDelta();
        String sign = delta > 0 ? "+" : "";
        return sign + String.format(java.util.Locale.US, "%.1f", delta).replace('.', ',');
    }

    public String getFirstDate() {
        return points.isEmpty() ? "" : points.get(0).date();
    }

    public String getLastDate() {
        return points.isEmpty() ? "" : points.get(points.size() - 1).date();
    }
}
