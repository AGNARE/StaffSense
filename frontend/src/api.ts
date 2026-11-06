import type {
  BoardTask, Departments, EmployeeDetail, EmployeeItem, Journal, Page, ProjectBoard,
  ProjectCard, RiskDetail, RiskListItem, TaskStatus, Thresholds
} from './types';

/**
 * Единственное место, где клиент знает адреса API.
 *
 * Ошибки приводятся к человекочитаемым: истёкшая сессия — самая частая
 * причина «пустых» таблиц, поэтому она объясняется явно, а не через HTTP 302.
 */
async function request<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });

  if (response.redirected || response.status === 401 || response.status === 403) {
    throw new Error('Нет доступа: войдите в систему и обновите страницу');
  }
  if (!response.ok) {
    throw new Error('Ошибка запроса: HTTP ' + response.status);
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
    csrfToken = await request<{ headerName: string; token: string }>('/api/v1/csrf');
  }
  return csrfToken;
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const token = await csrf();
  const response = await fetch(path, {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      [token.headerName]: token.token
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  if (response.redirected || response.status === 401 || response.status === 403) {
    throw new Error('Недостаточно прав или сессия истекла');
  }
  if (!response.ok) {
    throw new Error('Ошибка запроса: HTTP ' + response.status);
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('Сервер вернул не JSON');
  }
  return response.json() as Promise<T>;
}

const RISK = '/api/v1/risk';
const EMPLOYEES = '/api/v1/employees';
const PROJECTS = '/api/v1/projects';

export interface Me {
  id: number;
  fullName: string;
  role: string;
}

export const api = {
  me: () => request<Me>('/api/v1/me'),

  risks: (size = 200) => request<Page<RiskListItem>>(`${RISK}?page=0&size=${size}`),
  risk: (userId: number) => request<RiskDetail>(`${RISK}/${userId}`),
  recompute: (userId: number) => post<RiskDetail>(`${RISK}/${userId}/recompute`),
  thresholds: () => request<Thresholds>(`${RISK}/thresholds`),

  employees: (params: { page: number; size: number; search: string; department: string }) => {
    const query = new URLSearchParams({
      page: String(params.page),
      size: String(params.size),
      search: params.search,
      department: params.department
    });
    return request<Page<EmployeeItem>>(`${EMPLOYEES}?${query.toString()}`);
  },
  employee: (userId: number) => request<EmployeeDetail>(`${EMPLOYEES}/${userId}`),
  departments: () => request<Departments>(`${EMPLOYEES}/departments`),

  journal: (period: string) => request<Journal>(`${RISK}/admin/journal?period=${period}`),
  updateThresholds: (low: number, medium: number, recompute: boolean) =>
    post<Thresholds>(`${RISK}/admin/thresholds`, { low, medium, recompute }),
  recomputeAll: () => post<{ accepted: boolean; message: string }>(`${RISK}/admin/recompute`),
  projects: () => request<ProjectCard[]>(PROJECTS),
  project: (id: number) => request<ProjectBoard>(`${PROJECTS}/${id}`),
  createTask: (projectId: number, body: {
    title: string; description?: string; assigneeId?: number | null;
    priority?: string; estimateHours?: number | null; dueDate?: string | null;
  }) => post<BoardTask>(`${PROJECTS}/${projectId}/tasks`, body),
  setTaskStatus: (taskId: number, status: TaskStatus) =>
    post<BoardTask>(`/api/tasks/${taskId}/status`, { status })
};
