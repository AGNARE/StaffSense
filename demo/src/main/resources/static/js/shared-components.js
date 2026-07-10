/* ==========================================================================
   Shared UI Components: Loading Spinner & Confirmation Modal
   ========================================================================== */

/**
 * Show a fullscreen loading spinner overlay
 * @param {string} message - Optional message to display below the spinner
 */
function showLoading(message) {
    // Remove existing spinner if any
    hideLoading();

    var overlay = document.createElement('div');
    overlay.className = 'spinner-overlay';
    overlay.id = 'loadingSpinner';

    var html = '<i class="bx bx-loader-alt spinner-icon"></i>';
    if (message) {
        html += '<div class="spinner-message">' + escapeHtml(message) + '</div>';
    }
    overlay.innerHTML = html;

    document.body.appendChild(overlay);
}

/**
 * Remove the loading spinner overlay
 */
function hideLoading() {
    var existing = document.getElementById('loadingSpinner');
    if (existing && existing.parentNode) {
        existing.parentNode.removeChild(existing);
    }
}

/**
 * Show a confirmation modal dialog
 * @param {string} title - Modal title
 * @param {string} message - Modal body text
 * @param {Function} confirmCallback - Function to call when user confirms
 * @param {Function} cancelCallback - Optional function to call on cancel
 * @param {string} confirmText - Text for confirm button (default: "Подтвердить")
 * @param {string} cancelText - Text for cancel button (default: "Отмена")
 */
function showConfirmModal(title, message, confirmCallback, cancelCallback, confirmText, cancelText) {
    // Remove any existing modal
    removeExistingModal();

    confirmText = confirmText || 'Подтвердить';
    cancelText = cancelText || 'Отмена';

    var overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.id = 'confirmModal';

    overlay.innerHTML =
        '<div class="modal-content">' +
            '<div class="modal-header">' +
                '<h3>' + escapeHtml(title) + '</h3>' +
                '<button class="modal-close-btn" id="modalCloseBtn" aria-label="Close">&times;</button>' +
            '</div>' +
            '<div class="modal-body">' + escapeHtml(message) + '</div>' +
            '<div class="modal-footer">' +
                '<button class="btn btn-secondary" id="modalCancelBtn">' + escapeHtml(cancelText) + '</button>' +
                '<button class="btn btn-primary" id="modalConfirmBtn">' + escapeHtml(confirmText) + '</button>' +
            '</div>' +
        '</div>';

    document.body.appendChild(overlay);

    // ── Event handlers ──

    function handleConfirm() {
        cleanup();
        if (typeof confirmCallback === 'function') {
            confirmCallback();
        }
    }

    function handleCancel() {
        cleanup();
        if (typeof cancelCallback === 'function') {
            cancelCallback();
        }
    }

    function handleOverlayClick(e) {
        if (e.target === overlay) {
            handleCancel();
        }
    }

    function handleKeydown(e) {
        if (e.key === 'Escape') {
            handleCancel();
        }
    }

    function cleanup() {
        var closeBtn = document.getElementById('modalCloseBtn');
        var confirmBtn = document.getElementById('modalConfirmBtn');
        var cancelBtn = document.getElementById('modalCancelBtn');

        if (closeBtn) closeBtn.removeEventListener('click', handleCancel);
        if (confirmBtn) confirmBtn.removeEventListener('click', handleConfirm);
        if (cancelBtn) cancelBtn.removeEventListener('click', handleCancel);

        document.removeEventListener('keydown', handleKeydown);

        // Animate out
        overlay.classList.add('modal-leave');
        setTimeout(function() {
            removeExistingModal();
        }, 200);
    }

    // Use setTimeout to ensure elements exist in DOM
    setTimeout(function() {
        var closeBtn = document.getElementById('modalCloseBtn');
        var confirmBtn = document.getElementById('modalConfirmBtn');
        var cancelBtn = document.getElementById('modalCancelBtn');

        if (closeBtn) closeBtn.addEventListener('click', handleCancel);
        if (confirmBtn) confirmBtn.addEventListener('click', handleConfirm);
        if (cancelBtn) cancelBtn.addEventListener('click', handleCancel);

        overlay.addEventListener('click', handleOverlayClick);
        document.addEventListener('keydown', handleKeydown);
    }, 0);
}

/**
 * Remove the modal overlay from DOM
 */
function removeExistingModal() {
    var existing = document.getElementById('confirmModal');
    if (existing && existing.parentNode) {
        existing.parentNode.removeChild(existing);
    }
}
