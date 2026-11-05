/*
 * Общий слой клиента: навигация между экранами и мелкие помощники.
 *
 * Держим в одном файле, чтобы второй экран не копировал форматирование дат,
 * запросы и разметку шапки. Это не фреймворк и не сборка — просто модуль,
 * который подключается обычным <script> до кода страницы.
 */
(function () {
    'use strict';

    const SCREENS = [
        { id: 'risk', href: 'index.html', title: 'Риски' },
        { id: 'employees', href: 'employees.html', title: 'Сотрудники' }
    ];

    // ── форматирование ──────────────────────────────────────────────────
    function num(value, digits) {
        if (value === null || value === undefined || Number.isNaN(Number(value))) return '—';
        return Number(value).toLocaleString('ru-RU', {
            minimumFractionDigits: digits === undefined ? 1 : digits,
            maximumFractionDigits: digits === undefined ? 1 : digits
        });
    }

    function dateTime(iso) {
        if (!iso) return '—';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return '—';
        return d.toLocaleString('ru-RU', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }

    function dateOnly(iso) {
        if (!iso) return '—';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return '—';
        return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
    }

    const LEVEL_TITLES = {
        HIGH: 'Высокий', MEDIUM: 'Средний', LOW: 'Низкий', UNKNOWN: 'Без прогноза'
    };

    function levelClass(level) {
        const key = String(level || 'UNKNOWN').toLowerCase();
        return ['high', 'medium', 'low'].includes(key) ? key : 'unknown';
    }

    function levelTitle(level) {
        return LEVEL_TITLES[level] || level || '—';
    }

    function badge(level) {
        const span = document.createElement('span');
        span.className = 'badge badge-' + levelClass(level);
        span.textContent = levelTitle(level);
        return span;
    }

    // ── сеть ────────────────────────────────────────────────────────────
    async function api(path) {
        const response = await fetch(path, {
            headers: { Accept: 'application/json' },
            credentials: 'same-origin'
        });

        if (response.redirected || response.status === 401 || response.status === 403) {
            throw new Error('Нет доступа. Войдите в систему и обновите страницу.');
        }
        if (!response.ok) {
            throw new Error('Ошибка запроса: HTTP ' + response.status);
        }
        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('json')) {
            throw new Error('Сервер вернул не JSON — вероятно, сессия истекла.');
        }
        return response.json();
    }

    // ── навигация ───────────────────────────────────────────────────────
    function renderNav() {
        const holder = document.getElementById('nav');
        if (!holder) return;

        const current = document.body.dataset.page || '';
        SCREENS.forEach((screen) => {
            const link = document.createElement('a');
            link.href = screen.href;
            link.textContent = screen.title;
            link.className = screen.id === current ? 'active' : '';
            holder.appendChild(link);
        });
    }

    function showMessage(element, text, isError) {
        if (!element) return;
        element.innerHTML = '';
        const box = document.createElement('div');
        box.className = 'notice';
        if (isError) box.style.borderColor = 'rgba(239,68,68,.45)',
            box.style.background = 'rgba(239,68,68,.12)',
            box.style.color = '#fca5a5';
        box.textContent = text;
        element.appendChild(box);
    }

    window.SS = {
        api, num, dateTime, dateOnly, levelClass, levelTitle, badge, showMessage
    };

    document.addEventListener('DOMContentLoaded', renderNav);
})();
