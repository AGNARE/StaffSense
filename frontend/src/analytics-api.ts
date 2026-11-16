import type { Page } from './types';
import { ensureOk } from './http';

/**
 * Клиент аналитики — свой модуль, а не расширение общего `api.ts`.
 *
 * Зачем отдельно: экран аналитики переносится независимо от остальных
 * разделов, и его контракт не должен тянуть за собой риск-модуль. Форма
 * обращений скопирована с `api.ts`: `credentials: 'same-origin'`, проверка
 * `redirected`/401/403 (истёкшая сессия — самая частая причина «пустых»
 * таблиц) и проверка, что пришёл действительно JSON.
 *
 * CSRF-токен здесь не запрашивается намеренно: контракт аналитики сейчас
 * только `GET`. Если появится мутация (например, «пересчитать»), её нужно
 * добавить по образцу `api.ts`: `GET /api/v1/csrf` → заголовок из ответа
 * (`token.headerName`) в POST-запросе. Иначе токен будет мёртвым кодом.
 */
async function request<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });

  ensureOk(response);
  if (!response.ok) {
    throw new Error('Ошибка запроса: HTTP ' + response.status);
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('Сервер вернул не JSON — вероятно, сессия истекла');
  }
  return response.json() as Promise<T>;
}

/** Строка нагрузки: последняя запись сотрудника, а не «среднее за всё время». */
export interface WorkloadEmployee {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  workloadIndex: number | null;
  totalEstimatedHours: number | null;
  capacityHours: number | null;
  overtimeHours: number | null;
  computedAt: string | null;
  /** false — расчёта не было; показатели приходят как null, а не как 0. */
  hasData: boolean;
  /** Индекс выше порога BR-002 (порог приходит в ответе, не дублируется в клиенте). */
  overloaded: boolean;
}

/** Агрегат по отделу; среднее считается только по сотрудникам с данными. */
export interface WorkloadTeam {
  department: string;
  employeeCount: number;
  withDataCount: number;
  avgWorkloadIndex: number | null;
  overloadedCount: number;
}

export interface WorkloadResponse {
  generatedAt: string;
  overloadThreshold: number;
  employees: Page<WorkloadEmployee>;
  teams: WorkloadTeam[];
  withDataCount: number;
}

/** Строка KPI: последняя сохранённая оценка и её слагаемые. */
export interface KpiEmployee {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  kpiScore: number | null;
  /** Доля задач в срок, 0..1. */
  onTimeCompletionRate: number | null;
  /** Доля переоткрытий, 0..1. */
  reopenRate: number | null;
  managerRating: number | null;
  computedAt: string | null;
  hasData: boolean;
}

export interface KpiResponse {
  generatedAt: string;
  employees: Page<KpiEmployee>;
  withDataCount: number;
}

/** Сводка для карточек над вкладками. */
export interface AnalyticsSummary {
  employees: number;
  withWorkload: number;
  withKpi: number;
  overloaded: number;
  avgWorkloadIndex: number | null;
  avgKpiScore: number | null;
  generatedAt: string;
}

const ANALYTICS = '/api/v1/analytics';

export const analyticsApi = {
  workload: (page = 0, size = 50) =>
    request<WorkloadResponse>(`${ANALYTICS}/workload?page=${page}&size=${size}`),
  kpi: (page = 0, size = 50) =>
    request<KpiResponse>(`${ANALYTICS}/kpi?page=${page}&size=${size}`),
  summary: () => request<AnalyticsSummary>(`${ANALYTICS}/summary`)
};
