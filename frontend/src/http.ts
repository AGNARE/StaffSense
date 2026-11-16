/**
 * Единая обработка ответов API: различает «сессия истекла» и «нет прав».
 *
 * Раньше каждый модуль обращений (`api.ts`, `journey-api.ts`, `hr-api.ts`, …)
 * смешивал 401 и 403 в одно сообщение «Нет доступа: войдите в систему».
 * Из-за этого при истёкшей сессии страница оставалась открытой и показывала
 * нули вместо редиректа на вход, а при реальном отсутствии прав человек
 * получал совет «войдите», хотя он уже внутри.
 *
 * Теперь:
 * - 401 или редирект на форму входа → уводим на `/login?expired`, чтобы
 *   человек не смотрел на мёртвую страницу;
 * - 403 → честное «недостаточно прав», без советов про вход;
 * - остальное → HTTP-код.
 */

/** Куда уводить при истёкшей сессии. */
const LOGIN_EXPIRED = '/login?expired';

/** Ответ означает, что сессия закончилась. */
function isUnauthenticated(response: Response): boolean {
  if (response.status === 401) {
    return true;
  }
  // Spring на неавторизованный запрос страницы отдаёт 302 на /login —
  // fetch следует редиректу, и это видно по флагу (или по адресу).
  return response.redirected && /\/login/.test(response.url ?? '');
}

/** Проверяет ответ и бросает понятную ошибку; на 401 уводит на вход. */
export function ensureOk(response: Response): void {
  if (isUnauthenticated(response)) {
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.href = LOGIN_EXPIRED;
    }
    throw new Error('Сессия истекла — войдите заново');
  }

  if (response.status === 403) {
    throw new Error('Недостаточно прав для этого раздела');
  }

  if (!response.ok) {
    throw new Error('Ошибка запроса: HTTP ' + response.status);
  }
}

/** Сработала ли ошибка из-за истёкшей сессии (для тихих сценариев). */
export function isSessionExpired(error: unknown): boolean {
  return error instanceof Error && error.message.includes('Сессия истекла');
}
