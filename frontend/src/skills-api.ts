import type { SkillItem } from './types';

/**
 * Клиент каталога ИИ-скиллов — свой модуль, а не расширение общего `api.ts`.
 *
 * Зачем отдельно: карточка скилла и экран управления переносятся независимо,
 * и их контракт не должен тянуть за собой риск-модуль, каталог сотрудников и
 * остальные чтения. Форма запроса скопирована с `api.ts`/`hr-api.ts`, чтобы
 * ошибки выглядели одинаково на всех экранах: `credentials: 'same-origin'`,
 * проверка `redirected`/401/403 (истёкшая сессия — самая частая причина
 * «пустых» таблиц), проверка что пришёл действительно JSON, и текст ошибки
 * сервера вместо голого кода, когда он есть.
 *
 * **Создание и правка — `multipart/form-data`, а не JSON + base64.** Файл и
 * метаданные едут одним запросом, сервер не декодирует base64 и не раздувает
 * тело на треть, а браузер собирает `FormData` сам — без чтения файла в
 * память скриптом. CSRF при этом работает штатно: токен уходит заголовком
 * `X-XSRF-TOKEN` (его имя и значение отдаёт `GET /api/v1/csrf`), поэтому
 * `Content-Type` для multipart не выставляется вручную — границу multipart
 * дописывает браузер.
 */

// ── транспорт ────────────────────────────────────────────────────────────

/**
 * Текст ошибки: сначала сообщение сервера (валидация названия, роли, размер
 * файла), потом — общая формулировка по коду.
 */
async function toError(response: Response, fallback: string): Promise<never> {
  if (response.redirected || response.status === 401) {
    throw new Error('Нет доступа: войдите в систему и обновите страницу');
  }
  if (response.status === 403) {
    throw new Error('Недостаточно прав: управление каталогом доступно ADMIN и HR');
  }
  // 404 сервер отдаёт общим текстом «Ресурс не найден»; для скилла понятнее
  // наш вариант — он объясняет, что карточка может быть просто черновиком.
  if (response.status === 404) {
    throw new Error(fallback);
  }
  const detail = await response.json().catch(() => null) as { message?: string } | null;
  throw new Error(detail?.message ?? fallback + ': HTTP ' + response.status);
}

async function request<T>(path: string, fallback: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });

  if (!response.ok) {
    return toError(response, fallback);
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('Сервер вернул не JSON — вероятно, сессия истекла');
  }
  return response.json() as Promise<T>;
}

/**
 * CSRF-токен спрашиваем у сервера один раз и переиспользуем: Spring Security 6
 * маскирует значение в cookie, поэтому подставлять cookie напрямую нельзя.
 */
let csrfToken: { headerName: string; token: string } | null = null;

async function csrf(): Promise<{ headerName: string; token: string }> {
  if (!csrfToken) {
    csrfToken = await request<{ headerName: string; token: string }>(
      '/api/v1/csrf', 'Не удалось получить CSRF-токен');
  }
  return csrfToken;
}

/** Запись: POST/PUT с телом (multipart или пустым) и заголовком CSRF. */
async function send<T>(path: string, method: 'POST' | 'PUT', form: FormData | null,
                       fallback: string): Promise<T> {
  const token = await csrf();
  const response = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      [token.headerName]: token.token
    },
    body: form ?? undefined
  });

  if (!response.ok) {
    return toError(response, fallback);
  }
  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('Сервер вернул не JSON');
  }
  return response.json() as Promise<T>;
}

async function remove(path: string, fallback: string): Promise<void> {
  const token = await csrf();
  const response = await fetch(path, {
    method: 'DELETE',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      [token.headerName]: token.token
    }
  });

  if (!response.ok) {
    return toError(response, fallback);
  }
}

// ── контракт ─────────────────────────────────────────────────────────────

const SKILLS = '/api/v1/skills';

/**
 * Роль для выпадающего списка. Приходит с сервера: набор ролей каталога —
 * доменное решение, клиент его не дублирует.
 */
export interface SkillRoleOption {
  value: string;
  title: string;
}

/**
 * Поля формы скилла. `undefined` означает «не менять» (для правки) — поэтому
 * в `FormData` попадают только заданные ключи, а не пустые строки.
 */
export interface SkillFormInput {
  name?: string;
  description?: string;
  targetRole?: string;
  githubUrl?: string;
  published?: boolean;
  file?: File | null;
}

/** Собрать тело multipart-запроса: только заполненные поля. */
function toForm(input: SkillFormInput): FormData {
  const form = new FormData();
  if (input.name !== undefined) form.set('name', input.name);
  if (input.description !== undefined) form.set('description', input.description);
  if (input.targetRole !== undefined) form.set('targetRole', input.targetRole);
  if (input.githubUrl !== undefined) form.set('githubUrl', input.githubUrl);
  if (input.published !== undefined) form.set('published', String(input.published));
  if (input.file) form.set('file', input.file);
  return form;
}

export const skillsApi = {
  /**
   * Каталог. ADMIN и HR получают и черновики — это решает сервер по роли
   * (rows differ), клиент не передаёт «покажи черновики».
   */
  list: (role?: string) => request<SkillItem[]>(
    SKILLS + (role ? `?role=${encodeURIComponent(role)}` : ''),
    'Не удалось загрузить каталог скиллов'),

  /** Метаданные одного скилла. Черновик открывается только ADMIN/HR. */
  detail: (id: number) => request<SkillItem>(
    `${SKILLS}/${id}`, 'Скилл не найден или ещё не опубликован'),

  /** Роли для формы создания и правки. */
  roles: () => request<SkillRoleOption[]>(`${SKILLS}/roles`, 'Не удалось загрузить роли'),

  /** Создание: название и роль обязательны (проверяет сервер). */
  create: (input: SkillFormInput) => send<SkillItem>(
    SKILLS, 'POST', toForm(input), 'Не удалось создать скилл'),

  /** Правка: не переданное поле не меняется, новый файл заменяет прежний. */
  update: (id: number, input: SkillFormInput) => send<SkillItem>(
    `${SKILLS}/${id}`, 'PUT', toForm(input), 'Не удалось сохранить скилл'),

  /** Публикация и снятие с публикации — один флаг, одна ручка. */
  setPublished: (id: number, published: boolean) => send<SkillItem>(
    `${SKILLS}/${id}/publish?published=${published}`, 'POST', null,
    published ? 'Не удалось опубликовать скилл' : 'Не удалось снять скилл с публикации'),

  /** Удаление из каталога. Ответ 204 — тела нет. */
  remove: (id: number) => remove(`${SKILLS}/${id}`, 'Не удалось удалить скилл')
};
