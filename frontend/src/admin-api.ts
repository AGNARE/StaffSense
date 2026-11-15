/**
 * Клиент раздела администрирования пользователей.
 *
 * <p>Отдельный модуль, а не расширение {@code api.ts}: у администрирования
 * другой набор прав (только ADMIN) и другие ошибки — сервер объясняет отказ
 * правилом («нельзя заблокировать самого себя»), и эту причину надо показать
 * человеку, а не превращать в «HTTP 409». Поэтому здесь ошибки разбираются
 * из тела ответа.
 *
 * <p>Соглашения те же, что в основном клиенте: сессионная cookie
 * ({@code credentials: 'same-origin'}) и CSRF-токен из {@code /api/v1/csrf}
 * в заголовке, который назвал сервер (Spring Security 6 маскирует значение
 * в cookie, подставлять её напрямую нельзя).
 */

/** Роли в порядке убывания широты доступа — как в матрице на сервере. */
export const ADMIN_ROLES = [
  { value: 'ADMIN', title: 'Администратор' },
  { value: 'EXEC', title: 'Руководство компании' },
  { value: 'HR', title: 'HR-специалист' },
  { value: 'PM', title: 'Руководитель проекта' },
  { value: 'EMPLOYEE', title: 'Сотрудник' }
] as const;

export type AdminRole = (typeof ADMIN_ROLES)[number]['value'];

/** Строка таблицы: сервер отдаёт только эти поля, без пароля и ПДн. */
export interface AdminUser {
  userId: number;
  fullName: string;
  email: string;
  role: string;
  department: string | null;
  position: string | null;
  active: boolean;
}

export interface AdminUserPage {
  content: AdminUser[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateUserBody {
  fullName: string;
  email: string;
  role: string;
  department: string;
  position: string;
  /** Необязателен: пусто — сервер сгенерирует временный пароль. */
  password?: string;
  salary?: number;
  gradeLevel?: string;
}

export interface CreatedUser {
  user: AdminUser;
  /** Показывается один раз; при переданном пароле — null. */
  temporaryPassword: string | null;
}

export interface AccessScreen {
  id: string;
  title: string;
  route: string;
  group: string;
}

export interface RoleAccess {
  role: string;
  title: string;
  screens: string[];
}

export interface AccessMatrix {
  screens: AccessScreen[];
  roles: RoleAccess[];
}

const ADMIN = '/api/v1/admin';

/** Причина отказа из тела ответа — иначе человек видит только код. */
async function readErrorMessage(response: Response): Promise<string> {
  if (response.redirected || response.status === 401) {
    return 'Сессия истекла — войдите заново и обновите страницу';
  }
  if (response.status === 403) {
    return 'Недостаточно прав: раздел доступен только администратору';
  }
  try {
    const body = (await response.json()) as { message?: string };
    if (body && typeof body.message === 'string' && body.message.trim()) {
      return body.message;
    }
  } catch {
    // Тело не JSON — ниже вернём общий текст по коду.
  }
  return 'Ошибка запроса: HTTP ' + response.status;
}

async function request<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('Сервер вернул не JSON — вероятно, сессия истекла');
  }
  return response.json() as Promise<T>;
}

/** CSRF спрашиваем один раз на страницу и переиспользуем. */
let csrfToken: { headerName: string; token: string } | null = null;

async function csrf(): Promise<{ headerName: string; token: string }> {
  if (!csrfToken) {
    csrfToken = await request<{ headerName: string; token: string }>('/api/v1/csrf');
  }
  return csrfToken;
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const token = await csrf();
  const response = await fetch(path, {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      [token.headerName]: token.token
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('Сервер вернул не JSON');
  }
  return response.json() as Promise<T>;
}

export const adminApi = {
  users: (params: { page: number; size: number; role: string; search: string }) => {
    const query = new URLSearchParams({
      page: String(params.page),
      size: String(params.size),
      role: params.role,
      search: params.search
    });
    return request<AdminUserPage>(`${ADMIN}/users?${query.toString()}`);
  },

  createUser: (body: CreateUserBody) => post<CreatedUser>(`${ADMIN}/users`, body),

  changeRole: (userId: number, role: string) =>
    post<AdminUser>(`${ADMIN}/users/${userId}/role`, { role }),

  setActive: (userId: number, active: boolean) =>
    post<AdminUser>(`${ADMIN}/users/${userId}/active`, { active }),

  access: () => request<AccessMatrix>(`${ADMIN}/access`)
};
