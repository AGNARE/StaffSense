import type {
  BoardTask, Checklist, Departments, DocumentItem, EmployeeDetail, EmployeeItem, Journal, Memo, Page, ProjectBoard,
  OnboardingArticle, OnboardingArticleItem, OnboardingSection, ProjectCard,
  RiskDetail, RiskListItem, SkillItem, SurveyForm, SurveyItem, SurveyResults,
  TaskStatus, Thresholds
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
const MEMOS = '/api/v1/memos';
const SURVEYS = '/api/v1/surveys';

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
    post<BoardTask>(`/api/tasks/${taskId}/status`, { status }),

  memos: () => request<Memo[]>(MEMOS),
  pendingMemos: () => request<Memo[]>(`${MEMOS}/pending`),
  createMemo: (body: { type: string; startDate: string; endDate: string; reason?: string }) =>
    post<Memo>(MEMOS, body),
  memoAction: (id: number, action: 'approve-pm' | 'approve-hr' | 'cancel' | 'reject', reason?: string) =>
    post<Memo>(`${MEMOS}/${id}/${action}`, action === 'reject' ? { reason } : {}),

  surveys: () => request<SurveyItem[]>(SURVEYS),
  survey: (id: number) => request<SurveyForm>(`${SURVEYS}/${id}`),
  submitSurvey: (id: number, answers: Record<number, string>) =>
    post<{ surveyId: number; answered: number }>(`${SURVEYS}/${id}/responses`, { answers }),
  surveyResults: (id: number) => request<SurveyResults>(`${SURVEYS}/${id}/results`),

  onboardingSections: () => request<OnboardingSection[]>('/api/v1/onboarding/sections'),
  onboardingArticles: (section?: string) =>
    request<OnboardingArticleItem[]>('/api/v1/onboarding/articles' + (section ? `?section=${section}` : '')),
  onboardingArticle: (id: number) => request<OnboardingArticle>(`/api/v1/onboarding/articles/${id}`),
  onboardingChecklist: () => request<Checklist>('/api/v1/onboarding/checklist'),
  toggleChecklist: (articleId: number) =>
    post<Checklist>(`/api/v1/onboarding/checklist/${articleId}/toggle`),

  skills: (role?: string) =>
    request<SkillItem[]>('/api/v1/skills' + (role ? `?role=${role}` : '')),

  documents: (filters?: { type?: string; status?: string }) => {
    const query = new URLSearchParams();
    if (filters?.type) query.set('type', filters.type);
    if (filters?.status) query.set('status', filters.status);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<DocumentItem[]>('/api/v1/documents' + suffix);
  },
  createDocument: (body: {
    type: string; title: string; summary?: string;
    employeeId?: number | null; documentDate?: string | null;
  }) => post<DocumentItem>('/api/v1/documents', body),
  setDocumentStatus: (id: number, status: string) =>
    post<DocumentItem>(`/api/v1/documents/${id}/status`, { status })
};