import type {
  Departments, EmployeeDetail, EmployeeItem, Page, RiskDetail, RiskListItem, Thresholds
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

const RISK = '/api/v1/risk';
const EMPLOYEES = '/api/v1/employees';

export interface Me {
  id: number;
  fullName: string;
  role: string;
}

export const api = {
  me: () => request<Me>('/api/v1/me'),

  risks: (size = 200) => request<Page<RiskListItem>>(`${RISK}?page=0&size=${size}`),
  risk: (userId: number) => request<RiskDetail>(`${RISK}/${userId}`),
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
  departments: () => request<Departments>(`${EMPLOYEES}/departments`)
};
