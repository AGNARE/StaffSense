import { ensureOk } from './http';

/**
 * Клиент HR-модуля — свой модуль, а не расширение общего `api.ts`.
 *
 * Зачем отдельно: экраны «Пульт HR» и «Отсутствия и табель» переносятся
 * независимо от остальных разделов, и их контракт не должен тянуть за собой
 * риск-модуль или каталог сотрудников. Форма обращений скопирована с `api.ts`:
 * `credentials: 'same-origin'`, проверка `redirected`/401/403 (истёкшая сессия —
 * самая частая причина «пустых» таблиц) и проверка, что пришёл действительно
 * JSON.
 *
 * CSRF берётся лениво: `GET /api/v1/csrf` нужен только для отметки часов
 * (`POST /api/v1/hr/timesheet`). Чтение сводки и табеля — обычный GET и токена
 * не требует, поэтому лишний запрос при открытии экрана не делается.
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

  ensureOk(response);
  if (response.status === 409) {
    // Закрытый период (BR-028) — не ошибка ввода, правило: сообщение сервера.
    const detail = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(detail?.message ?? 'Период закрыт — правка табеля запрещена');
  }
  if (response.status === 400) {
    const detail = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(detail?.message ?? 'Некорректные данные записи');
  }
  if (!response.ok) {
    throw new Error('Ошибка запроса: HTTP ' + response.status);
  }
  return response.json() as Promise<T>;
}

// ── Пульт HR ─────────────────────────────────────────────────────────────

export interface HrPeriod {
  from: string;
  to: string;
}

export interface DepartmentHeadcount {
  department: string;
  headcount: number;
}

export interface HrHeadcount {
  total: number;
  employees: number;
  other: number;
  byDepartment: DepartmentHeadcount[];
}

export interface ProbationItem {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  hireDate: string | null;
  probationEnd: string | null;
  /** Дней до конца испытательного срока; 0 — последний день. */
  daysLeft: number;
  checklistDone: number;
  checklistTotal: number;
}

export interface HrProbation {
  /** Срок в месяцах — приходит с сервера, клиент его не зашивает. */
  months: number;
  /** Основание расчёта: поле в БД отсутствует, срок выводится из даты приёма. */
  basis: string;
  total: number;
  items: ProbationItem[];
}

export interface MissingForecast {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  hireDate: string | null;
}

export interface HrForecast {
  total: number;
  withForecast: number;
  withoutForecast: number;
  items: MissingForecast[];
}

/** Тип отсутствия: отпуск, больничный или прочее (командировка и т.п.). */
export type AbsenceType = 'VACATION' | 'SICK_LEAVE' | 'OTHER';

export interface HrAbsence {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  type: AbsenceType;
  typeTitle: string;
  from: string;
  to: string;
  days: number;
  /** MEMO — утверждённая записка, TIMESHEET — только отметки в табеле. */
  source: 'MEMO' | 'TIMESHEET';
}

export interface DepartmentCoverage {
  department: string;
  headcount: number;
  absent: number;
  /** null — в подразделении нет людей, доля не определена. */
  sharePercent: number | null;
}

export interface HrAbsences {
  todayCount: number;
  weekCount: number;
  upcomingCount: number;
  today: HrAbsence[];
  week: HrAbsence[];
  upcoming: HrAbsence[];
  coverage: DepartmentCoverage[];
}

export interface HrDocumentItem {
  id: number;
  regNumber: string;
  title: string;
  type: string | null;
  typeTitle: string | null;
  employeeName: string | null;
  documentDate: string | null;
}

export interface HrDocuments {
  onApproval: number;
  items: HrDocumentItem[];
}

export interface HrDesk {
  generatedAt: string;
  /** Якорная дата сводки (по умолчанию — сегодня). */
  date: string;
  week: HrPeriod;
  headcount: HrHeadcount;
  probation: HrProbation;
  forecast: HrForecast;
  absences: HrAbsences;
  documents: HrDocuments;
}

// ── Табель ───────────────────────────────────────────────────────────────

export interface TimesheetDay {
  date: string;
  hours: number | null;
  remote: boolean;
  sickLeave: boolean;
  vacation: boolean;
  overtimeHours: number | null;
}

export interface TimesheetEmployee {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  totalHours: number;
  overtimeHours: number;
  workingDays: number;
  remoteDays: number;
  sickDays: number;
  vacationDays: number;
  /** false — записей за период нет; нули в строке означают «не заполнено». */
  hasData: boolean;
  days: TimesheetDay[];
}

export interface TimesheetTotals {
  employees: number;
  employeesWithData: number;
  totalHours: number;
  overtimeHours: number;
  workingDays: number;
  remoteDays: number;
  sickDays: number;
  vacationDays: number;
}

export interface AbsenceRequest {
  id: number;
  userId: number;
  fullName: string;
  department: string | null;
  type: AbsenceType;
  typeTitle: string;
  startDate: string;
  endDate: string;
  days: number;
  status: string;
  statusTitle: string;
  createdAt: string;
}

export interface Timesheet {
  from: string;
  to: string;
  periodLabel: string;
  department: string | null;
  departments: string[];
  /** Закрыт ли период (BR-028): правка запрещена. */
  closed: boolean;
  totals: TimesheetTotals;
  rows: TimesheetEmployee[];
  absences: HrAbsence[];
  absenceRequests: AbsenceRequest[];
}

export interface TimesheetEntry {
  id: number;
  userId: number;
  date: string;
  hours: number | null;
  remote: boolean;
  sickLeave: boolean;
  vacation: boolean;
  overtimeHours: number | null;
  updatedAt: string | null;
}

export interface TimesheetQuery {
  from?: string;
  to?: string;
  department?: string;
}

export interface TimesheetEntryBody {
  userId: number;
  date: string;
  hours: number | null;
  remote: boolean;
  sickLeave: boolean;
  vacation: boolean;
  overtimeHours: number | null;
}

// ── Отпуска ──────────────────────────────────────────────────────────────

/**
 * Тело начисления права на отпуск.
 *
 * <p>{@code entitledDays} — <b>итоговое</b> право за год, а не добавка: сервер
 * ({@code VacationBalanceService#grant}) перезаписывает {@code entitled_days}
 * одним значением. Экран «Отпуска» поэтому сам прибавляет введённые дни к
 * текущему праву и отправляет сумму — иначе «начислить 2 дня» при праве 30
 * обнуляло бы право до 2.
 *
 * <p>Год приходит из выбранного периода экрана, а не из «сегодня»: HR правит
 * право за тот год, который видит перед собой. Перенос необязателен — сервер
 * трактует отсутствие как ноль.
 */
export interface VacationGrantBody {
  year: number;
  entitledDays: number;
  carriedOverDays: number;
  note: string | null;
}

/** Ответ начисления: одна строка баланса того же вида, что в списке. */
export interface VacationBalance {
  userId: number;
  year: number;
  entitledDays: number | null;
  carriedOverDays: number | null;
  usedDays: number;
  remainingDays: number | null;
  entitledKnown: boolean;
}

const HR = '/api/v1/hr';

export const hrApi = {
  desk: (date?: string) => request<HrDesk>(`${HR}/desk` + (date ? `?date=${encodeURIComponent(date)}` : '')),
  myActions: (limit = 20) => request<MyAction[]>(`/api/v1/hr/audit/my?limit=${limit}`),

  timesheet: (query: TimesheetQuery = {}) => {
    const params = new URLSearchParams();
    if (query.from) params.set('from', query.from);
    if (query.to) params.set('to', query.to);
    if (query.department) params.set('department', query.department);
    const suffix = params.toString() ? `?${params.toString()}` : '';
    return request<Timesheet>(`${HR}/timesheet${suffix}`);
  },

  saveEntry: (body: TimesheetEntryBody) => post<TimesheetEntry>(`${HR}/timesheet`, body),

  /**
   * Начислить право на отпуск сотруднику (HR/ADMIN).
   *
   * <p>Ответ — обновлённый баланс, но экран всё равно перечитывает список:
   * одна строка не показывает, как изменился итог по компании.
   */
  grantVacation: (userId: number, body: VacationGrantBody) =>
    post<VacationBalance>(`${HR}/vacations/${userId}`, body)
};

/**
 * Shell ещё не знает разделов HR: ключи добавляет интегратор вместе с
 * маршрутами `#/hr-desk` и `#/timesheet` (`App.tsx`/`Shell.tsx` — чужие файлы,
 * их не правим). Приведение через `unknown` собирается и до, и после этой
 * правки; до неё в меню просто нет активного пункта.
 */
export interface MyAction {
  id: number;
  action: string;
  targetEmail: string | null;
  details: string | null;
  createdAt: string;
}

export const HR_DESK_SCREEN = 'hrDesk';
export const TIMESHEET_SCREEN = 'timesheet';

