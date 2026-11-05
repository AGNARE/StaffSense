/*
 * Клиент модуля рисков. Работает только с /api/v1/risk и ничего не знает о
 * серверных шаблонах: любая попытка отрисовать HTML на сервере здесь
 * отсутствует по замыслу (см. docs/REFACTOR_PLAN.md, этап 3).
 */
(() => {
    'use strict';

    const API = '/api/v1/risk';
    const PAGE_SIZE = 20;
    const LOAD_LIMIT = 200;   // сколько записей тянем за один раз

    const state = {
        items: [],
        total: 0,
        page: 0,
        search: '',
        level: '',
        truncated: false
    };

    const $ = (id) => document.getElementById(id);

    // ── помощники (общие для всех экранов) ──────────────────────────────
    // Форматирование, бейджи и сетевой слой живут в shared.js, чтобы второй
    // экран не копировал их. Здесь оставляем только префикс API.
    const { num, dateTime, levelClass, levelTitle } = window.SS;

    const api = (path) => window.SS.api(API + path);
    // ── загрузка ────────────────────────────────────────────────────────
    async function load() {
        setRowsMessage('Загрузка…');
        try {
            const [page, thresholds] = await Promise.all([
                api('?page=0&size=' + LOAD_LIMIT),
                api('/thresholds').catch(() => null)
            ]);

            state.items = page.content || [];
            state.total = page.totalElements || state.items.length;
            state.truncated = state.total > state.items.length;

            renderThresholds(thresholds);
            renderStats();
            render();
        } catch (error) {
            setRowsMessage(error.message, true);
        }
    }

    async function openDetail(userId) {
        try {
            const detail = await api('/' + userId);
            renderDrawer(detail);
            $('drawer').classList.add('open');
            $('drawer').setAttribute('aria-hidden', 'false');
            $('backdrop').classList.add('show');
        } catch (error) {
            setRowsMessage(error.message, true);
        }
    }

    function closeDrawer() {
        $('drawer').classList.remove('open');
        $('drawer').setAttribute('aria-hidden', 'true');
        $('backdrop').classList.remove('show');
    }

    // ── отрисовка ───────────────────────────────────────────────────────
    function renderThresholds(thresholds) {
        const pill = $('thresholds-pill');
        if (!thresholds) {
            pill.textContent = 'пороги: —';
            return;
        }
        pill.textContent = 'пороги: ' + num(thresholds.low) + ' / ' + num(thresholds.medium);
        pill.title = thresholds.usingDefaults
            ? 'Значения из конфигурации'
            : 'Значения из настроек приложения';
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

    function renderStats() {
        const count = (level) => state.items.filter((i) => i.riskLevel === level).length;
        $('stat-total').textContent = state.total;
        $('stat-high').textContent = count('HIGH');
        $('stat-medium').textContent = count('MEDIUM');
        $('stat-low').textContent = count('LOW');
        $('stat-unknown').textContent = count('UNKNOWN');
    }

    function render() {
        const rows = filtered();
        const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
        if (state.page >= pages) state.page = pages - 1;

        const start = state.page * PAGE_SIZE;
        const slice = rows.slice(start, start + PAGE_SIZE);

        const body = $('rows');
        body.innerHTML = '';

        if (!slice.length) {
            setRowsMessage('Ничего не найдено');
        } else {
            slice.forEach((item) => body.appendChild(rowFor(item)));
        }

        $('page-info').textContent = 'страница ' + (state.page + 1) + ' из ' + pages
            + (state.truncated ? ' · загружено ' + state.items.length + ' из ' + state.total : '');
        $('prev').disabled = state.page === 0;
        $('next').disabled = state.page >= pages - 1;
    }

    function rowFor(item) {
        const tr = document.createElement('tr');
        tr.tabIndex = 0;
        tr.addEventListener('click', () => openDetail(item.userId));
        tr.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                openDetail(item.userId);
            }
        });

        const name = document.createElement('td');
        const nameMain = document.createElement('div');
        nameMain.className = 'emp-name';
        nameMain.textContent = item.fullName;
        const nameSub = document.createElement('div');
        nameSub.className = 'emp-sub';
        nameSub.textContent = item.gradeLevel || '';
        name.append(nameMain, nameSub);

        const dept = document.createElement('td');
        const deptMain = document.createElement('div');
        deptMain.textContent = item.department || '—';
        const deptSub = document.createElement('div');
        deptSub.className = 'emp-sub';
        deptSub.textContent = item.position || '';
        dept.append(deptMain, deptSub);

        const score = document.createElement('td');
        score.className = 'num';
        const known = item.riskScore >= 0;
        score.textContent = known ? num(item.riskScore) + '%' : '—';
        const bar = document.createElement('div');
        bar.className = 'risk-bar risk-' + levelClass(item.riskLevel);
        const fill = document.createElement('i');
        fill.style.width = known ? Math.max(0, Math.min(100, item.riskScore)) + '%' : '0%';
        bar.appendChild(fill);
        score.appendChild(bar);

        const level = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = 'badge badge-' + levelClass(item.riskLevel);
        badge.textContent = levelTitle(item.riskLevel);
        level.appendChild(badge);

        const computed = document.createElement('td');
        computed.className = 'muted';
        computed.textContent = dateTime(item.computedAt);

        tr.append(name, dept, score, level, computed);
        return tr;
    }

    function setRowsMessage(text, isError) {
        const body = $('rows');
        body.innerHTML = '';
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 5;
        td.className = 'empty';
        if (isError) td.style.color = '#fca5a5';
        td.textContent = text;
        tr.appendChild(td);
        body.appendChild(tr);
    }

    function renderDrawer(detail) {
        $('drawer-title').textContent = detail.fullName;
        $('drawer-sub').textContent = [detail.department, detail.position]
            .filter(Boolean).join(' · ');

        const known = typeof detail.riskScore === 'number';
        $('drawer-score').textContent = known ? num(detail.riskScore) + '%' : 'нет прогноза';
        const level = $('drawer-level');
        level.className = 'badge badge-' + levelClass(detail.riskLevel);
        level.textContent = levelTitle(detail.riskLevel);

        renderTrend(detail.trend);
        renderFeatures(detail.features || []);
        renderFactors(detail.factors || []);
    }

    function renderTrend(trend) {
        const polyline = $('drawer-polyline');
        const dots = $('drawer-dots');
        dots.innerHTML = '';

        if (!trend || !trend.points || trend.points.length === 0) {
            polyline.setAttribute('points', '');
            $('drawer-delta').textContent = '—';
            $('drawer-trend-foot').textContent = 'История появится после первого пересчёта';
            return;
        }

        polyline.setAttribute('points', trend.polyline || '');

        // Точки пересчитываем из тех же координат, что пришли в polyline:
        // API отдаёт их парами «x,y» — так клиент не дублирует геометрию.
        const pairs = (trend.polyline || '').trim().split(/\s+/).filter(Boolean);
        pairs.forEach((pair) => {
            const [x, y] = pair.split(',').map(Number);
            if (!Number.isFinite(x) || !Number.isFinite(y)) return;
            const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', x);
            circle.setAttribute('cy', y);
            circle.setAttribute('r', '2.4');
            circle.setAttribute('class', 'trend-dot');
            dots.appendChild(circle);
        });

        const delta = trend.delta || 0;
        const badge = $('drawer-delta');
        badge.textContent = (delta > 0 ? '+' : '') + num(delta) + ' п.п.';
        badge.className = 'badge ' + (delta > 0 ? 'badge-high' : 'badge-low');

        const first = trend.points[0];
        const last = trend.points[trend.points.length - 1];
        $('drawer-trend-foot').textContent =
            dateTime(first.at) + '  →  ' + dateTime(last.at)
            + '   ·   мин ' + num(trend.minScore) + ' · макс ' + num(trend.maxScore);
    }

    function renderFeatures(features) {
        const box = $('drawer-features');
        box.innerHTML = '';

        if (!features.length) {
            box.innerHTML = '<div class="muted">Признаки не сохранены</div>';
            return;
        }

        features.forEach((feature) => {
            const card = document.createElement('div');
            card.className = 'feature';
            card.style.borderLeftColor = feature.color || '#8b9ab5';

            const title = document.createElement('div');
            title.className = 'feature-title';
            title.textContent = (feature.icon ? feature.icon + ' ' : '') + feature.label;

            const value = document.createElement('div');
            value.className = 'feature-value';
            value.textContent = num(feature.value, 2);

            card.append(title, value);
            box.appendChild(card);
        });
    }

    function renderFactors(factors) {
        const box = $('drawer-factors');
        box.innerHTML = '';

        if (!factors.length) {
            box.innerHTML = '<div class="muted">Объяснение модели не сохранено '
                + '(демо-данные без XAI). После пересчёта через ML-сервис появится.</div>';
            return;
        }

        factors.forEach((factor) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'factor';

            const head = document.createElement('div');
            head.className = 'factor-head';
            const name = document.createElement('span');
            name.textContent = factor.factor;
            const impact = document.createElement('span');
            impact.textContent = num(factor.impact);
            head.append(name, impact);

            const bar = document.createElement('div');
            bar.className = 'factor-bar';
            const fill = document.createElement('i');
            fill.style.width = Math.max(0, Math.min(100, factor.impact)) + '%';
            bar.appendChild(fill);

            wrapper.append(head, bar);

            if (factor.description) {
                const description = document.createElement('div');
                description.className = 'factor-desc';
                description.textContent = factor.description;
                wrapper.appendChild(description);
            }

            box.appendChild(wrapper);
        });
    }

    // ── события ─────────────────────────────────────────────────────────
    $('refresh').addEventListener('click', () => load());
    $('prev').addEventListener('click', () => { state.page -= 1; render(); });
    $('next').addEventListener('click', () => { state.page += 1; render(); });

    $('search').addEventListener('input', (e) => {
        state.search = e.target.value;
        state.page = 0;
        render();
    });

    $('level-filter').addEventListener('change', (e) => {
        state.level = e.target.value;
        state.page = 0;
        render();
    });

    $('drawer-close').addEventListener('click', closeDrawer);
    $('backdrop').addEventListener('click', closeDrawer);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeDrawer();
    });

    load();
})();
