/*
 * Каталог сотрудников на дизайн-системе редизайна (assets/proto.css).
 *
 * Фильтры и пагинация — серверные: каталог растёт быстрее списка рисков,
 * тянуть его целиком в браузер неправильно.
 */
(() => {
    'use strict';

    const API = '/api/v1/employees';
    const PAGE_SIZE = 20;
    const state = { page: 0, search: '', department: '', total: 0, pages: 1 };
    const $ = (id) => document.getElementById(id);

    const dateOnly = (iso) => {
        if (!iso) return '—';
        const d = new Date(iso);
        return Number.isNaN(d.getTime()) ? '—' : d.toLocaleDateString('ru-RU');
    };

    const initials = (fullName) => (fullName || '')
        .split(/\s+/).filter(Boolean).slice(0, 2)
        .map((p) => p.charAt(0).toUpperCase()).join('');

    const avatarClass = (fullName) => {
        let hash = 0;
        for (const ch of fullName || '') hash = (hash * 31 + ch.charCodeAt(0)) % 8;
        return 'ava ava-sm ava-' + ((hash % 7) + 1);
    };

    const GRADE_BADGE = {
        JUNIOR: 'badge-info', MIDDLE: 'badge-outline',
        SENIOR: 'badge-ok', LEAD: 'badge-ai'
    };

    async function request(path) {
        const response = await fetch(path, {
            headers: { Accept: 'application/json' },
            credentials: 'same-origin'
        });
        if (response.redirected || response.status === 401 || response.status === 403) {
            throw new Error('Нет доступа: войдите в систему');
        }
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
    }

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
            console.warn('Справочник отделов недоступен:', error.message);
        }
    }

    async function load() {
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
            $('count').textContent = state.total + ' сотрудник(ов)';
            $('subtitle').textContent = 'Каталог активных сотрудников · поиск и фильтр работают на сервере';
        } catch (error) {
            $('rows').innerHTML = '<tr><td colspan="7" class="muted" style="padding:24px">'
                + error.message + '</td></tr>';
        }
    }

    function renderRows(items) {
        const body = $('rows');
        body.innerHTML = '';

        if (!items.length) {
            body.innerHTML = '<tr><td colspan="7" class="muted" style="padding:24px">'
                + 'Сотрудники не найдены</td></tr>';
            return;
        }

        items.forEach((item) => body.appendChild(rowFor(item)));
    }

    function rowFor(item) {
        const tr = document.createElement('tr');
        tr.className = 'table-click';
        tr.addEventListener('click', () => openDrawer(item));

        const tdName = document.createElement('td');
        const row = document.createElement('div');
        row.className = 'row gap-8';
        const ava = document.createElement('div');
        ava.className = avatarClass(item.fullName);
        ava.textContent = initials(item.fullName);
        const box = document.createElement('div');
        const main = document.createElement('span');
        main.className = 't-main';
        main.textContent = item.fullName;
        const sub = document.createElement('span');
        sub.className = 'sub';
        sub.textContent = item.email;
        box.append(main, sub);
        row.append(ava, box);
        tdName.appendChild(row);

        const tdDept = document.createElement('td');
        tdDept.textContent = item.department || '—';

        const tdPos = document.createElement('td');
        tdPos.textContent = item.position || '—';

        const tdGrade = document.createElement('td');
        const grade = document.createElement('span');
        grade.className = 'badge ' + (GRADE_BADGE[item.gradeLevel] || 'badge-outline');
        grade.textContent = item.gradeLevel || '—';
        tdGrade.appendChild(grade);

        const tdManager = document.createElement('td');
        tdManager.textContent = item.managerName || '—';

        const tdHired = document.createElement('td');
        tdHired.className = 'muted t-xs';
        tdHired.textContent = dateOnly(item.hireDate);

        const tdAction = document.createElement('td');
        tdAction.className = 't-actions';
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm btn-outline';
        button.textContent = 'Профиль';
        button.addEventListener('click', (e) => { e.stopPropagation(); openDrawer(item); });
        tdAction.appendChild(button);

        tr.append(tdName, tdDept, tdPos, tdGrade, tdManager, tdHired, tdAction);
        return tr;
    }

    function renderPager() {
        $('page-info').textContent = 'страница ' + (state.page + 1) + ' из ' + state.pages
            + ' · всего ' + state.total;
        $('prev').disabled = state.page === 0;
        $('next').disabled = state.page >= state.pages - 1;
        $('prev').className = 'btn btn-sm ' + (state.page === 0 ? 'btn-ghost' : 'btn-outline');
        $('next').className = 'btn btn-sm ' + (state.page >= state.pages - 1 ? 'btn-ghost' : 'btn-outline');
    }

    // ── панель профиля ─────────────────────────────────────────────────
    async function openDrawer(item) {
        fillDrawer(item, null);
        showDrawer();

        try {
            const detail = await request(API + '/' + item.userId);
            fillDrawer(detail, detail.risk);
        } catch (error) {
            console.warn('Профиль не загрузился:', error.message);
        }
    }

    function fillDrawer(item, risk) {
        $('d-ava').textContent = initials(item.fullName);
        $('d-name').textContent = item.fullName;
        $('d-grade').textContent = item.gradeLevel || '—';
        $('d-sub').textContent = [item.position, item.department].filter(Boolean).join(' · ');

        $('d-dept').textContent = item.department || '—';
        $('d-pos').textContent = item.position || '—';
        $('d-manager').textContent = item.managerName || '—';
        $('d-hired').textContent = dateOnly(item.hireDate);
        $('d-cap').textContent = item.weeklyCapacityHours != null
            ? item.weeklyCapacityHours + ' ч/нед' : '—';
        $('d-email').textContent = item.email || '—';

        const badge = $('d-risk');
        const link = $('d-link');
        if (risk) {
            const level = risk.riskLevel || 'UNKNOWN';
            badge.className = 'badge ' + (level === 'HIGH' ? 'badge-danger'
                : level === 'MEDIUM' ? 'badge-warn' : 'badge-ok');
            badge.textContent = 'риск ' + Number(risk.riskScore).toLocaleString('ru-RU', {
                minimumFractionDigits: 1, maximumFractionDigits: 1
            }) + '% · ' + (level === 'HIGH' ? 'высокий'
                : level === 'MEDIUM' ? 'средний' : 'низкий');
            link.href = '/risk/' + item.userId;
            link.textContent = 'Карточка риска';
        } else {
            badge.className = 'badge badge-neutral';
            badge.textContent = 'прогноза нет';
            link.href = '/risk';
            link.textContent = 'К списку рисков';
        }
    }

    function showDrawer() {
        $('drawer').classList.add('is-open');
        $('drawer').setAttribute('aria-hidden', 'false');
        $('overlay').classList.add('is-open');
    }

    function hideDrawer() {
        $('drawer').classList.remove('is-open');
        $('drawer').setAttribute('aria-hidden', 'true');
        $('overlay').classList.remove('is-open');
    }

    // ── события ────────────────────────────────────────────────────────
    let timer = null;

    $('search').addEventListener('input', (e) => {
        const value = e.target.value;
        clearTimeout(timer);
        timer = setTimeout(() => {
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

    $('refresh').addEventListener('click', load);
    $('prev').addEventListener('click', () => { if (state.page > 0) { state.page -= 1; load(); } });
    $('next').addEventListener('click', () => {
        if (state.page < state.pages - 1) { state.page += 1; load(); }
    });
    $('d-close').addEventListener('click', hideDrawer);
    $('overlay').addEventListener('click', hideDrawer);
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') hideDrawer(); });

    // Пункты меню, чьи разделы ещё не перенесены, не должны «проваливаться».
    document.querySelectorAll('.sidebar a[data-todo]').forEach((link) => {
        link.classList.add('is-todo');
        link.title = 'Раздел ещё не перенесён в новый клиент';
        link.addEventListener('click', (e) => e.preventDefault());
    });

    loadDepartments();
    load();
})();
