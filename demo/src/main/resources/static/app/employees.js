/*
 * Каталог сотрудников — второй экран нового клиента.
 *
 * Отличие от экрана рисков: фильтры и пагинация здесь серверные
 * (/api/v1/employees?search=&department=&page=), потому что каталог растёт
 * быстрее списка рисков и тянуть его целиком в браузер неправильно.
 */
(() => {
    'use strict';

    const API = '/api/v1/employees';
    const PAGE_SIZE = 20;
    const { num, dateOnly, badge, api: request, showMessage } = window.SS;

    const state = { page: 0, search: '', department: '', total: 0, pages: 1 };
    const $ = (id) => document.getElementById(id);

    // ── данные ──────────────────────────────────────────────────────────
    async function loadDepartments() {
        try {
            const data = await request(API + '/departments');
            const select = $('department');
            (data.items || []).forEach((name) => {
                const option = document.createElement('option');
                option.value = name;
                option.textContent = name;
                select.appendChild(option);
            });
        } catch (error) {
            // Справочник не критичен: каталог работает и без него.
            console.warn('Справочник отделов недоступен:', error.message);
        }
    }

    async function load() {
        setRows('Загрузка…');
        const query = new URLSearchParams({
            page: String(state.page),
            size: String(PAGE_SIZE),
            search: state.search,
            department: state.department
        });

        try {
            const data = await request(API + '?' + query.toString());
            state.total = data.totalElements;
            state.pages = Math.max(1, data.totalPages);

            renderRows(data.content || []);
            renderPager();
        } catch (error) {
            setRows(error.message, true);
            showMessage($('message'), error.message, true);
        }
    }

    async function openDetail(userId) {
        try {
            const data = await request(API + '/' + userId);
            renderDrawer(data);
            $('drawer').classList.add('open');
            $('drawer').setAttribute('aria-hidden', 'false');
            $('backdrop').classList.add('show');
        } catch (error) {
            showMessage($('message'), error.message, true);
        }
    }

    // ── отрисовка ───────────────────────────────────────────────────────
    function renderRows(items) {
        const body = $('rows');
        body.innerHTML = '';

        if (!items.length) {
            setRows('Сотрудники не найдены');
            $('count-pill').textContent = '0 найдено';
            return;
        }

        items.forEach((item) => {
            const tr = document.createElement('tr');
            tr.tabIndex = 0;
            tr.addEventListener('click', () => openDetail(item.userId));
            tr.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    openDetail(item.userId);
                }
            });

            tr.appendChild(cellWithSub(item.fullName, item.email));
            tr.appendChild(textCell(item.department || '—'));
            tr.appendChild(textCell(item.position || '—'));

            const grade = document.createElement('td');
            const gradeBadge = document.createElement('span');
            gradeBadge.className = 'badge badge-unknown';
            gradeBadge.textContent = item.gradeLevel || '—';
            grade.appendChild(gradeBadge);

            tr.appendChild(textCell(item.managerName || '—'));
            tr.appendChild(textCell(dateOnly(item.hireDate)));

            body.appendChild(tr);
        });

        $('count-pill').textContent = state.total + ' найдено';
    }

    function cellWithSub(main, sub) {
        const td = document.createElement('td');
        const top = document.createElement('div');
        top.className = 'emp-name';
        top.textContent = main;
        const bottom = document.createElement('div');
        bottom.className = 'emp-sub';
        bottom.textContent = sub || '';
        td.append(top, bottom);
        return td;
    }

    function textCell(text) {
        const td = document.createElement('td');
        td.textContent = text;
        return td;
    }

    function setRows(text, isError) {
        const body = $('rows');
        body.innerHTML = '';
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 6;
        td.className = 'empty';
        if (isError) td.style.color = '#fca5a5';
        td.textContent = text;
        tr.appendChild(td);
        body.appendChild(tr);
    }

    function renderPager() {
        $('page-info').textContent = 'страница ' + (state.page + 1) + ' из ' + state.pages
            + ' · всего ' + state.total;
        $('prev').disabled = state.page === 0;
        $('next').disabled = state.page >= state.pages - 1;
    }

    function renderDrawer(data) {
        $('drawer-title').textContent = data.fullName;
        $('drawer-sub').textContent = data.email;
        $('drawer-avatar').textContent = (data.fullName || '?').trim().charAt(0).toUpperCase();
        $('drawer-role').textContent = [data.position, data.department].filter(Boolean).join(' · ');

        const riskBox = $('drawer-risk');
        riskBox.innerHTML = '';
        if (data.risk) {
            const score = document.createElement('div');
            score.className = 'score-big';
            score.style.fontSize = '28px';
            score.textContent = num(data.risk.riskScore) + '%';
            const level = badge(data.risk.riskLevel);
            riskBox.append(score, level);
        } else {
            const none = document.createElement('span');
            none.className = 'badge badge-unknown';
            none.textContent = 'прогноза нет';
            riskBox.appendChild(none);
        }

        const kv = $('drawer-kv');
        kv.innerHTML = '';
        const rows = [
            ['Грейд', data.gradeLevel],
            ['Отдел', data.department],
            ['Должность', data.position],
            ['Руководитель', data.managerName || '—'],
            ['Нанят', dateOnly(data.hireDate)],
            ['Норма часов', data.weeklyCapacityHours != null ? data.weeklyCapacityHours + ' ч/нед' : '—']
        ];
        rows.forEach(([key, value]) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'kv-row';
            const k = document.createElement('div');
            k.className = 'kv-key';
            k.textContent = key;
            const v = document.createElement('div');
            v.className = 'kv-val';
            v.textContent = value || '—';
            wrapper.append(k, v);
            kv.appendChild(wrapper);
        });

        const link = $('drawer-risk-link');
        link.href = data.risk ? '/risk/' + data.userId : '/risk';
        link.textContent = data.risk ? 'Открыть карточку риска' : 'К списку рисков';
    }

    function closeDrawer() {
        $('drawer').classList.remove('open');
        $('drawer').setAttribute('aria-hidden', 'true');
        $('backdrop').classList.remove('show');
    }

    // ── события ─────────────────────────────────────────────────────────
    let searchTimer = null;

    $('search').addEventListener('input', (e) => {
        const value = e.target.value;
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => {
            state.search = value;
            state.page = 0;
            load();
        }, 250);
    });

    $('department').addEventListener('change', (e) => {
        state.department = e.target.value;
        state.page = 0;
        load();
    });

    $('refresh').addEventListener('click', () => load());
    $('prev').addEventListener('click', () => { state.page -= 1; load(); });
    $('next').addEventListener('click', () => { state.page += 1; load(); });
    $('drawer-close').addEventListener('click', closeDrawer);
    $('backdrop').addEventListener('click', closeDrawer);
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeDrawer();
    });

    loadDepartments();
    load();
})();
