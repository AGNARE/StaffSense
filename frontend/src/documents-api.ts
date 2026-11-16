import type { DocumentItem } from './types';
import { ensureOk } from './http';

/**
 * Обращения к API документов для новых экранов раздела.
 *
 * <p>Отдельный модуль, а не расширение {@code api.ts}: реестр остаётся на
 * прежнем контракте ({@code DocumentItem}), а карточка и входящие получают
 * историю маршрута и список доступных действий. Паттерн тот же, что в
 * {@code api.ts}: {@code credentials: 'same-origin'}, CSRF-токен через
 * {@code GET /api/v1/csrf}, ошибки приводятся к человекочитаемым.
 */

const DOCUMENTS = '/api/v1/documents';

export type DocumentStatus = 'DRAFT' | 'ON_APPROVAL' | 'APPROVED' | 'ARCHIVED';

/** Один переход маршрута. */
export interface DocumentHistoryEntry {
  id: number;
  fromStatus: DocumentStatus | null;
  fromStatusTitle: string | null;
  toStatus: DocumentStatus | null;
  toStatusTitle: string | null;
  actorName: string;
  comment: string | null;
  createdAt: string | null;
}

/** Действие, доступное текущему пользователю. */
export interface DocumentTransition {
  status: DocumentStatus;
  title: string;
}

/** Карточка документа: строка реестра + история + действия. */
export interface DocumentCard extends DocumentItem {
  /** Текущий статус в карточке всегда известен — уточняем тип строки реестра. */
  status: DocumentStatus;
  employeeDepartment: string | null;
  employeePosition: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  history: DocumentHistoryEntry[];
  allowedTransitions: DocumentTransition[];
}

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
 * CSRF-токен спрашиваем один раз и переиспользуем: Spring Security 6
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

  ensureOk(response);
  if (response.status === 409) {
    throw new Error('Переход недопустим по маршруту документа');
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

export const documentsApi = {
  /** Документы, ожидающие решения текущего пользователя. */
  inbox: () => request<DocumentItem[]>(`${DOCUMENTS}/inbox`),

  /** Карточка: полные данные, история статусов и доступные действия. */
  card: (id: number) => request<DocumentCard>(`${DOCUMENTS}/${id}`),

  /** Смена статуса с комментарием; сервер пишет переход в историю. */
  setStatus: (id: number, status: DocumentStatus, comment?: string) =>
    post<DocumentCard>(`${DOCUMENTS}/${id}/status`, {
      status,
      comment: comment && comment.trim() ? comment.trim() : null
    })
};
