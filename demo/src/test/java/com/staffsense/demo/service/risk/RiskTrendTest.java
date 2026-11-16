package com.staffsense.demo.service.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Тесты геометрии мини-графика тренда.
 *
 * <p>График строится на сервере (без JS), поэтому координаты нужно проверять
 * здесь: ошибка в них даёт «пустой» или перевёрнутый график, который в браузере
 * выглядит как отсутствие данных.
 */
class RiskTrendTest {

    private static RiskTrend.Snapshot snap(int day, double score) {
        return new RiskTrend.Snapshot(LocalDateTime.of(2026, 10, day, 12, 0), score);
    }

    @Test
    @DisplayName("Пустая история даёт пустой тренд, а не исключение")
    void emptyHistory() {
        RiskTrend trend = RiskTrend.of(List.of(), 240, 56, 6);

        assertThat(trend.isEmpty()).isTrue();
        assertThat(trend.getPoints()).isEmpty();
        assertThat(trend.getPolyline()).isEmpty();
        assertThat(trend.size()).isZero();
        assertThat(trend.getDelta()).isZero();
    }

    @Test
    @DisplayName("Точки распределены по ширине, риск инвертирован по оси Y")
    void pointsGeometry() {
        RiskTrend trend = RiskTrend.of(
                List.of(snap(1, 80.0), snap(2, 40.0), snap(3, 60.0)),
                240, 56, 6);

        assertThat(trend.size()).isEqualTo(3);
        assertThat(trend.getPoints().get(0).x()).isCloseTo(0, within(1e-9));
        assertThat(trend.getPoints().get(1).x()).isCloseTo(120, within(1e-9));
        assertThat(trend.getPoints().get(2).x()).isCloseTo(240, within(1e-9));

        // максимум балла должен быть ВЫШЕ на экране (меньше y)
        double yHigh = trend.getPoints().get(0).y();
        double yLow = trend.getPoints().get(1).y();
        assertThat(yHigh).isLessThan(yLow);
        // отступ сверху/снизу соблюдён
        assertThat(yHigh).isCloseTo(6, within(1e-9));
        assertThat(yLow).isCloseTo(50, within(1e-9));
    }

    @Test
    @DisplayName("Одинаковые баллы рисуются ровной линией по центру")
    void flatLine() {
        RiskTrend trend = RiskTrend.of(
                List.of(snap(1, 50.0), snap(2, 50.0), snap(3, 50.0)),
                240, 56, 6);

        double y = trend.getPoints().get(0).y();
        assertThat(trend.getPoints()).allSatisfy(p -> assertThat(p.y()).isCloseTo(y, within(1e-9)));
        assertThat(trend.getDelta()).isZero();
    }

    @Test
    @DisplayName("Одна точка ставится по центру, дельта нулевая")
    void singlePoint() {
        RiskTrend trend = RiskTrend.of(List.of(snap(5, 70.0)), 240, 56, 6);

        assertThat(trend.size()).isEqualTo(1);
        assertThat(trend.getPoints().get(0).x()).isCloseTo(120, within(1e-9));
        assertThat(trend.getDelta()).isZero();
        assertThat(trend.getDeltaText()).isEqualTo("0,0");
    }

    @Test
    @DisplayName("Дельта показывает рост риска со знаком плюс")
    void deltaSign() {
        RiskTrend up = RiskTrend.of(List.of(snap(1, 40.0), snap(2, 63.5)), 240, 56, 6);
        RiskTrend down = RiskTrend.of(List.of(snap(1, 60.0), snap(2, 45.0)), 240, 56, 6);

        assertThat(up.getDelta()).isCloseTo(23.5, within(1e-9));
        assertThat(up.getDeltaText()).isEqualTo("+23,5");
        assertThat(down.getDeltaText()).isEqualTo("-15,0");
    }

    @Test
    @DisplayName("Строка polyline содержит по паре координат на точку")
    void polylineString() {
        RiskTrend trend = RiskTrend.of(
                List.of(snap(1, 30.0), snap(2, 70.0)), 240, 56, 6);

        String[] pairs = trend.getPolyline().split(" ");
        assertThat(pairs).hasSize(2);
        assertThat(pairs[0]).startsWith("0.0,");
        assertThat(pairs[1]).startsWith("240.0,");
        // координаты форматируются с точкой (Locale.US) — так требует SVG
    }

    @Test
    @DisplayName("Крайние даты берутся из первой и последней точки")
    void dateLabels() {
        RiskTrend trend = RiskTrend.of(
                List.of(snap(1, 30.0), snap(15, 55.0)), 240, 56, 6);

        assertThat(trend.getFirstDate()).isEqualTo("01.10");
        assertThat(trend.getLastDate()).isEqualTo("15.10");
    }
    @Test
    @DisplayName("Мин и макс форматируются с запятой — как остальной интерфейс")
    void minMaxFormatting() {
        RiskTrend trend = RiskTrend.of(
                List.of(snap(1, 76.44), snap(2, 81.35)), 240, 56, 6);

        assertThat(trend.getMinText()).isEqualTo("76,4");
        assertThat(trend.getMaxText()).isEqualTo("81,4");   // 81.35 округляется вверх
    }
}
