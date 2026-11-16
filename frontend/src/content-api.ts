/**
 * Обращения к API экранов «Конструктор опроса», «Управление порталом» и
 * «Справочник».
 *
 * <p>Отдельный модуль, а не расширение {@code api.ts}: эти три экрана —
 * запись контента (опросы, статьи) и справочник, у них другие права и другие
 * ошибки (409 «нельзя править опубликованное»), и правка общего клиента
 * задела бы чужие экраны. Паттерн тот же: {@code credentials: 'same-origin'},
 * CSRF через {@code GET /api/v1/csrf}, ошибки приводятся к человекочитаемым.
 *
 * <p>В отличие от {@code api.ts} здесь разбирается тело ошибки сервера
 * ({@code {message}}): в формах причина отказа важна — «дата окончания раньше
 * даты начала» полезнее, чем «HTTP 400».
 */

export type QuestionType = 'LIKERT_5' | 'FREE_TEXT';

/** Ответ сервера на ошибку: единая форма у всех REST-эндпоинтов. */
interface ApiError {
  status?: number;
  message?: string;
}

async function request<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });

  if (response.redirected || response.status === 401) {
    throw new Error('Нет доступа: войдите в систему и обновите страницу');
  }
  if (response.status === 403) {
    throw new Error('Недостаточно прав: раздел доступен HR и администратору');
  }
  if (!response.ok) {
    throw new Error(await messageFrom(response, 'Ошибка запроса: HTTP ' + response.status));
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

async function send<T>(method: 'POST' | 'PUT', path: string, body?: unknown): Promise<T> {
  const token = await csrf();
  const response = await fetch(path, {
    method,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      [token.headerName]: token.token
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  if (response.redirected || response.status === 401) {
    throw new Error('Сессия истекла: войдите в систему заново');
  }
  if (response.status === 403) {
    throw new Error('Недостаточно прав: действие доступно HR и администратору');
  }
  if (!response.ok) {
    throw new Error(await messageFrom(response, 'Ошибка запроса: HTTP ' + response.status));
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('Сервер вернул не JSON');
  }
  return response.json() as Promise<T>;
}

/** Текст ошибки из тела ответа, если сервер его прислал. */
async function messageFrom(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.json()) as ApiError;
    return body && body.message ? body.message : fallback;
  } catch {
    return fallback;
  }
}

// ── Конструктор опроса ───────────────────────────────────────────────────

/** Вопрос в теле сохранения; порядок задаётся позицией в массиве. */
export interface SurveyQuestionPayload {
  text: string;
  type: QuestionType;
  required: boolean;
}

/** Тело создания черновика и его правки. */
export interface SurveySaveRequest {
  title: string;
  surveyType: string;
  targetScope: string;
  targetDepartment: string | null;
  targetManagerId: number | null;
  targetUserId: number | null;
  startDate: string;
  endDate: string;
  anonymous: boolean;
  questions: SurveyQuestionPayload[];
}

export interface SurveyQuestionView {
  id: number;
  text: string;
  type: QuestionType;
  typeTitle: string;
  required: boolean;
  orderIndex: number;
}

/** Опрос глазами конструктора (ответ {@code GET /api/v1/surveys/{id}/builder}). */
export interface SurveyDetail {
  id: number;
  title: string;
  surveyType: string;
  surveyTypeTitle: string;
  status: string;
  statusTitle: string;
  targetScope: string;
  targetScopeTitle: string;
  targetDepartment: string | null;
  targetManagerId: number | null;
  targetUserId: number | null;
  startDate: string | null;
  endDate: string | null;
  anonymous: boolean;
  createdBy: string | null;
  questionCount: number;
  questions: SurveyQuestionView[];
  editable: boolean;
  publishable: boolean;
}

// ── Управление порталом ──────────────────────────────────────────────────

export interface ArticleItem {
  id: number;
  section: string;
  sectionTitle: string;
  title: string;
  content: string | null;
  orderIndex: number | null;
  published: boolean;
  statusTitle: string;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface PortalSection {
  code: string;
  title: string;
  total: number;
  drafts: number;
}

export interface PortalOverview {
  totalArticles: number;
  draftArticles: number;
  sections: PortalSection[];
  articles: ArticleItem[];
}

export interface ArticleSaveRequest {
  section: string;
  title: string;
  content: string;
  orderIndex: number | null;
  published?: boolean;
}

// ── Справочник ───────────────────────────────────────────────────────────

export interface DirectoryEmployee {
  id: number;
  fullName: string;
  position: string | null;
  department: string | null;
  email: string | null;
  managerName: string | null;
}

export interface DepartmentGroup {
  name: string;
  count: number;
}

export interface DirectoryManager {
  id: number;
  fullName: string;
  position: string | null;
  department: string | null;
  email: string | null;
  directReports: number;
}

/** Справочник: только неконфиденциальные поля (BR-031). */
export interface DirectoryResponse {
  total: number;
  shown: number;
  departments: DepartmentGroup[];
  employees: DirectoryEmployee[];
  managers: DirectoryManager[];
}

export const contentApi = {
  // Конструктор опроса
  survey: (id: number) => request<SurveyDetail>(`/api/v1/surveys/${id}/builder`),
  createSurvey: (body: SurveySaveRequest) => send<SurveyDetail>('POST', '/api/v1/surveys', body),
  updateSurvey: (id: number, body: SurveySaveRequest) =>
    send<SurveyDetail>('PUT', `/api/v1/surveys/${id}`, body),
  publishSurvey: (id: number) => send<SurveyDetail>('POST', `/api/v1/surveys/${id}/publish`),

  // Управление порталом
  articles: () => request<PortalOverview>('/api/v1/onboarding/manage/articles'),
  createArticle: (body: ArticleSaveRequest) =>
    send<ArticleItem>('POST', '/api/v1/onboarding/articles', body),
  updateArticle: (id: number, body: ArticleSaveRequest) =>
    send<ArticleItem>('PUT', `/api/v1/onboarding/articles/${id}`, body),
  publishArticle: (id: number) =>
    send<ArticleItem>('POST', `/api/v1/onboarding/articles/${id}/publish`),

  // Справочник
  directory: (params?: { department?: string; q?: string }) => {
    const query = new URLSearchParams();
    if (params?.department) query.set('department', params.department);
    if (params?.q) query.set('q', params.q);
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request<DirectoryResponse>('/api/v1/directory' + suffix);
  }
};
