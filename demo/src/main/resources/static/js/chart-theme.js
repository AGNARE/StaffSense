/**
 * chart-theme.js — Theme-aware Chart.js color defaults
 *
 * Reads the current theme (data-theme attribute) and provides colour
 * palettes for Chart.js grid lines, ticks, borders, and backgrounds.
 * Automatically re-reads the theme each time get() is called, so it
 * works even after runtime theme toggles.
 */
window.StaffSenseChartTheme = (function() {

    function isLight() {
        return document.documentElement.getAttribute('data-theme') === 'light';
    }

    /**
     * Return a palette object. Call each time you create a chart so the
     * most recent theme is picked up.
     */
    function get() {
        if (isLight()) {
            return {
                gridColor:  'rgba(0, 0, 0, 0.06)',
                tickColor:  '#5a5248',
                borderColor: '#e0d8cc',
                labelColor: '#5a5248',
                // Used for doughnut border (inner cutout ring)
                doughnutBorder: '#f5efe8',
            };
        }
        // Dark theme (default)
        return {
            gridColor:    'rgba(255, 255, 255, 0.05)',
            tickColor:    '#94a3b8',
            borderColor:  '#1e293b',
            labelColor:   '#94a3b8',
            doughnutBorder: '#0f111a',
        };
    }

    /**
     * Apply theme-aware scale colours to an existing Chart.js options object
     * (mutates in-place for convenience and returns it).
     *
     * Usage:
     *   const opts = { scales: { y: { … }, x: { … } } };
     *   StaffSenseChartTheme.applyScaleColours(opts);
     *   new Chart(ctx, { …, options: opts });
     */
    function applyScaleColours(options) {
        var t = get();
        if (!options || !options.scales) return options;
        Object.values(options.scales).forEach(function(scale) {
            if (scale.grid === undefined) scale.grid = {};
            if (scale.ticks === undefined) scale.ticks = {};
            if (scale.grid.color === undefined) scale.grid.color = t.gridColor;
            if (scale.ticks.color === undefined) scale.ticks.color = t.tickColor;
        });
        return options;
    }

    return { get: get, applyScaleColours: applyScaleColours };
})();
