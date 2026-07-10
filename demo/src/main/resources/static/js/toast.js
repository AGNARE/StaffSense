/* ==========================================================================
   Toast Notification System
   ========================================================================== */

/**
 * Show a toast notification
 * @param {string} message - The message to display
 * @param {string} type - 'success', 'error', 'info', 'warning' (default 'info')
 */
function showToast(message, type) {
    type = type || 'info';

    var container = document.getElementById('toast-container');
    if (!container) return;

    // ── Create toast element ──
    var toast = document.createElement('div');
    toast.className = 'toast toast-' + type + ' toast-enter';

    // ── Icon mapping ──
    var icons = {
        success: 'bx bx-check-circle',
        error:   'bx bx-x-circle',
        info:    'bx bx-info-circle',
        warning: 'bx bx-error'
    };
    var iconClass = icons[type] || icons.info;

    // ── Build toast HTML ──
    toast.innerHTML =
        '<div class="toast-icon"><i class="' + iconClass + '"></i></div>' +
        '<div class="toast-message">' + escapeHtml(message) + '</div>' +
        '<button class="toast-close" onclick="dismissToast(this)" aria-label="Close">&times;</button>' +
        '<div class="toast-progress"><div class="toast-progress-bar"></div></div>';

    container.appendChild(toast);

    // ── Auto-dismiss after 5s ──
    toast._dismissTimer = setTimeout(function() {
        fadeOutToast(toast);
    }, 5000);
}

/**
 * Dismiss a toast when close button is clicked
 * @param {HTMLElement} btn - The close button element
 */
function dismissToast(btn) {
    var toast = btn.closest('.toast');
    if (!toast) return;

    // Clear auto-dismiss timer
    if (toast._dismissTimer) {
        clearTimeout(toast._dismissTimer);
        toast._dismissTimer = null;
    }

    fadeOutToast(toast);
}

/**
 * Fade out a toast and remove it from DOM
 * @param {HTMLElement} toast - The toast element
 */
function fadeOutToast(toast) {
    // Prevent double-fade
    if (toast.classList.contains('toast-leave')) return;

    toast.classList.remove('toast-enter');
    toast.classList.add('toast-leave');

    // Remove from DOM after animation
    setTimeout(function() {
        if (toast.parentNode) {
            toast.parentNode.removeChild(toast);
        }
    }, 350);
}

/**
 * Simple HTML escaping to prevent XSS
 * @param {string} text
 * @returns {string}
 */
function escapeHtml(text) {
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}
