/*
 * Радар рисков — данные из /api/v1/risk в разметку дизайн-системы прототипа.
 *
 * Стили не переписываем: используется assets/proto.css из редизайна
 * (светлая тема, navy-сайдбар, Inter), поэтому экран выглядит как прототип,
 * а не как отдельная тема.
 */
(() => {
    'use strict';

    const API = '/api/v1/risk';
    const state = { items: [], level: '', search: '' };
    const $ = (id) => document.getElementById(id);

    const num = (v, d = 1) => (v === null || v === undefined || Number.isNaN(Number(v)))
        ? '—'
        : Number(v).toLocaleString('ru-RU', { minimumFractionDigits: d, maximumFractionDigits: d });

    const timeShort = (iso) => {
        if (!iso) return '—';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return '—';
        return d.toLocaleString('ru-RU', {
            day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
        }).replace(',', ',');
    };

    const initials = (fullName) => (fullName || '')
        .split(/\s+/).filter(Boolean).slice(0, 2)
        .map((p) => p.charAt(0).toUpperCase()).join('');

    // Стабильный цвет аватара из имени, чтобы не «прыгал» между загрузками.
    const avatarClass = (fullName) => {
        let hash = 0;
        for (const ch of fullName || '') hash = (hash * 31 + ch.charCodeAt(0)) % 8;
        return 'ava ava-sm ava-' + ((hash % 7) + 1);
    };

    const LEVEL = {
        HIGH: { badge: 'badge-danger', title: 'Высокий', score: 'is-high' },
        MEDIUM: { badge: 'badge-warn', title: 'Средний', score: 'is-med' },
        LOW: { badge: 'badge-ok', title: 'Низкий', score: 'is-low' },
        UNKNOWN: { badge: 'badge-neutral', title: 'Без прогноза', score: 'is-low' }
    };

    async function api(path) {
        const response = await fetch(API + path, {
            headers: { Accept: 'application/json' },
            credentials: 'same-origin'
        });
        if (response.redirected || response.status === 401 || response.status === 403) {
            throw new Error('Нет доступа: войдите в систему и обновите страницу');
        }
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
    }

    async function load() {
        try {
            const [page, thresholds] = await Promise.all([
                api('?page=0&size=200'),
                api('/thresholds').catch(() => null)
            ]);

            state.items = page.content || [];

            if (thresholds) {
                $('thresholds').textContent =
                    'пороги ' + num(thresholds.low, 0) + ' / ' + num(thresholds.medium, 0);
                $('thresholds').title = thresholds.usingDefaults
                    ? 'Значения из конфигурации'
                    : 'Значения из настроек приложения';
            }

            renderKpi(page);
            refreshShell(page);
            render();
        } catch (error) {
            $('subtitle').textContent = error.message;
            $('rows').innerHTML = '<tr><td colspan="7" class="muted" style="padding:24px">'
                + error.message + '</td></tr>';
        }
    }

    function renderKpi(page) {
        const count = (level) => state.items.filter((i) => i.riskLevel === level).length;
        const withPrediction = state.items.filter((i) => i.riskScore >= 0);
        const average = withPrediction.length
            ? withPrediction.reduce((sum, i) => sum + i.riskScore, 0) / withPrediction.length
            : null;
        const coverage = page.totalElements
            ? Math.round(withPrediction.length / page.totalElements * 100)
            : 0;

        $('kpi-high').innerHTML = count('HIGH') + ' <span class="unit">чел.</span>';
        $('kpi-medium').innerHTML = count('MEDIUM') + ' <span class="unit">чел.</span>';
        $('kpi-avg').innerHTML = num(average) + ' <span class="unit">%</span>';
        $('kpi-coverage').innerHTML = coverage + ' <span class="unit">%</span>';
        $('kpi-coverage-foot').textContent = withPrediction.length + ' из ' + page.totalElements;

        const last = state.items
            .map((i) => i.computedAt).filter(Boolean).sort().pop();
        $('subtitle').textContent = page.totalElements + ' сотрудник(ов) · последний расчёт '
            + (last ? timeShort(last) : '—') + ' · источник: журнал прогнозов';
    }

    function filtered() {
        const search = state.search.trim().toLowerCase();
        return state.items.filter((item) => {
            if (state.level && item.riskLevel !== state.level) return false;
            if (!search) return true;
            return (item.fullName || '').toLowerCase().includes(search)
                || (item.department || '').toLowerCase().includes(search);
        });
    }

    function render() {
        const rows = filtered();
        const body = $('rows');
        body.innerHTML = '';

        if (!rows.length) {
            body.innerHTML = '<tr><td colspan="7" class="muted" style="padding:24px">'
                + 'Ничего не найдено</td></tr>';
        } else {
            rows.forEach((item) => body.appendChild(rowFor(item)));
        }

        $('foot-count').textContent = 'показано ' + rows.length + ' из ' + state.items.length;
    }

    function rowFor(item) {
        const level = LEVEL[item.riskLevel] || LEVEL.UNKNOWN;
        const tr = document.createElement('tr');
        tr.className = 'table-click';
        tr.addEventListener('click', () => { location.href = '/risk/' + item.userId; });

        // Сотрудник
        const tdName = document.createElement('td');
        const row = document.createElement('div');
        row.className = 'row gap-8';
        const ava = document.createElement('div');
        ava.className = avatarClass(item.fullName);
        ava.textContent = initials(item.fullName);
        const nameBox = document.createElement('div');
        const name = document.createElement('span');
        name.className = 't-main';
        name.textContent = item.fullName;
        const sub = document.createElement('span');
        sub.className = 'sub';
        sub.textContent = [item.position, item.department].filter(Boolean).join(' · ');
        nameBox.append(name, sub);
        row.append(ava, nameBox);
        tdName.appendChild(row);

        // Риск
        const tdRisk = document.createElement('td');
        const meter = document.createElement('div');
        meter.className = 'risk-meter';
        const track = document.createElement('span');
        track.className = 'risk-track';
        const marker = document.createElement('span');
        marker.className = 'marker';
        const known = item.riskScore >= 0;
        marker.style.left = (known ? Math.max(0, Math.min(100, item.riskScore)) : 0) + '%';
        track.appendChild(marker);
        const score = document.createElement('span');
        score.className = 'risk-score ' + level.score;
        score.textContent = known ? num(item.riskScore) + '%' : '—';
        meter.append(track, score);
        tdRisk.appendChild(meter);

        // Уровень
        const tdLevel = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = 'badge ' + level.badge;
        badge.textContent = level.title;
        tdLevel.appendChild(badge);

        // Тренд
        const tdTrend = document.createElement('td');
        if (item.trendDelta !== null && item.trendDelta !== undefined) {
            const delta = Number(item.trendDelta);
            const span = document.createElement('span');
            span.className = 'delta ' + (delta > 0 ? 'delta-down'
                : delta < 0 ? 'delta-up' : 'delta-flat');
            span.textContent = (delta > 0 ? '+' : '') + num(delta);
            tdTrend.appendChild(span);
        } else {
            tdTrend.className = 'muted t-xs';
            tdTrend.textContent = '—';
        }

        // Главный фактор
        const tdFactor = document.createElement('td');
        if (item.topFactor) {
            const factor = document.createElement('span');
            factor.className = 'badge ' + level.badge;
            factor.textContent = item.topFactor;
            tdFactor.appendChild(factor);
        } else {
            tdFactor.className = 'muted t-xs';
            tdFactor.textContent = 'нет объяснения';
        }

        // Обновлён
        const tdDate = document.createElement('td');
        tdDate.className = 'muted t-xs';
        tdDate.textContent = timeShort(item.computedAt);

        // Действие
        const tdAction = document.createElement('td');
        tdAction.className = 't-actions';
        const button = document.createElement('button');
        button.className = 'btn btn-sm btn-outline';
        button.type = 'button';
        button.textContent = 'Разбор';
        button.addEventListener('click', (e) => {
            e.stopPropagation();
            location.href = '/risk/' + item.userId;
        });
        tdAction.appendChild(button);

        tr.append(tdName, tdRisk, tdLevel, tdTrend, tdFactor, tdDate, tdAction);
        return tr;
    }

    /**
     * Обновляет счётчик рядом с пунктом «Радар рисков» и гасит разделы,
     * которых ещё нет в новом клиенте: кликабельный пункт, ведущий в никуда,
     * хуже, чем явно неактивный.
     */
    function refreshShell(page) {
        // Счётчик у пункта «Радар рисков»: в прототипе там статичное число.
        const tail = document.querySelector('.sidebar .sb-subitem.is-on .tail');
        if (tail) tail.textContent = page.totalElements;

        // Разделы, которых ещё нет в новом клиенте: гасим, чтобы клик вёл не в пустоту.
        document.querySelectorAll('.sidebar a[data-todo]').forEach((link) => {
            link.classList.add('is-todo');
            link.title = 'Раздел ещё не перенесён в новый клиент';
            link.addEventListener('click', (e) => e.preventDefault());
        });
        document.querySelectorAll('.sidebar [data-goto]').forEach((button) => {
            if (button.classList.contains('is-on')) return;
            button.classList.add('is-todo');
            button.title = 'Экран ещё не перенесён в новый клиент';
            button.addEventListener('click', (e) => e.preventDefault());
        });
    }

    // ── события ────────────────────────────────────────────────────────
    $('refresh').addEventListener('click', load);

    $('search').addEventListener('input', (e) => {
        state.search = e.target.value;
        render();
    });

    $('seg').addEventListener('click', (e) => {
        const button = e.target.closest('button[data-level]');
        if (!button) return;
        state.level = button.dataset.level;
        [...$('seg').querySelectorAll('button')].forEach((b) =>
            b.classList.toggle('active', b === button));
        render();
    });

    load();
})();
