import type { Checklist, Memo, SurveyItem } from './types';
import { ensureOk } from './http';

/**
 * Обращения к API для экрана «Мой путь».
 *
 * <p>Отдельный модуль, а не расширение {@code api.ts}: экран сотрудника
 * собирает те же ресурсы, но с другим составом и подписями, и правка общего
 * клиента задела бы чужие экраны. Паттерн тот же: {@code credentials:
 * 'same-origin'}, ошибки приводятся к человекочитаемым, истёкшая сессия
 * объясняется явно.
 *
 * <p>CSRF здесь не нужен: все вызовы — чтение (GET). Мутации (создание
 * записки, ответы на опрос) уже живут в своих экранах и своих модулях.
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

export interface Me {
  id: number;
  fullName: string;
  role: string;
  email: string;
  department: string | null;
  position: string | null;
}

export type MeetingStatus = 'PLANNED' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED';

/** Встреча 1:1 глазами участника (контракт GET /api/v1/oneonone/my). */
export interface MyMeeting {
  id: number;
  employeeId: number;
  employeeName: string;
  managerId: number;
  managerName: string;
  scheduledAt: string;
  status: MeetingStatus;
  statusTitle: string;
  missed: boolean;
  notes: string | null;
  actionItems: string | null;
  upcoming: boolean;
  canManage: boolean;
  myRole: 'EMPLOYEE' | 'MANAGER';
}

export const journeyApi = {
  me: () => request<Me>('/api/v1/me'),

  // limit=20: на экране нужны ближайшие и немного истории, а не весь архив.
  myMeetings: (limit = 20) => request<MyMeeting[]>(`/api/v1/oneonone/my?limit=${limit}`),

  memos: () => request<Memo[]>('/api/v1/memos'),
  surveys: () => request<SurveyItem[]>('/api/v1/surveys'),
  checklist: () => request<Checklist>('/api/v1/onboarding/checklist')
};
