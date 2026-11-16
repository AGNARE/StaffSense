import type { DocumentCard } from './documents-api';
import { ensureOk } from './http';

/**
 * Обращения к API подписи документов.
 *
 * <p>Отдельный модуль, а не расширение {@code api.ts}: реестр остаётся на
 * прежнем контракте, а подпись и очередь получают свой — решение, срок (SLA),
 * остаток часов и признак «можно подписать». Паттерн тот же, что в
 * {@code api.ts}: {@code credentials: 'same-origin'}, CSRF-токен через
 * {@code GET /api/v1/csrf}, ошибки приводятся к человекочитаемым.
 *
 * <p>Права клиент не угадывает: и очередь, и {@code canSign} считает сервер.
 */

const DOCUMENTS = '/api/v1/documents';

/** Решение по документу: подпись или отклонение. */
export type SignatureDecision = 'APPROVED' | 'REJECTED';

/** Одна подпись документа. */
export interface SignatureEntry {
  id: number;
  signerId: number | null;
  signerName: string;
  decision: SignatureDecision;
  decisionTitle: string;
  comment: string | null;
  signedAt: string | null;
}

/** Строка очереди на подпись. */
export interface SignatureQueueItem {
  id: number;
  regNumber: string;
  type: string;
  typeTitle: string;
  title: string;
  employeeName: string | null;
  authorName: string | null;
  status: string;
  statusTitle: string;
  /** Срок подписи; null — документ не ждёт подписи. */
  dueAt: string | null;
  /** Просрочен ли срок — решает сервер, чтобы часовой пояс клиента не влиял. */
  overdue: boolean;
  /** Остаток часов: отрицательный — просрочка, null — срока нет. */
  remainingHours: number | null;
  canSign: boolean;
}

/** Карточка документа с подписями и сроком подписи. */
export interface SignatureCard extends DocumentCard {
  dueAt: string | null;
  overdue: boolean;
  canSign: boolean;
  signatures: SignatureEntry[];
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
    // Сервер отвечает 409, если документ уже подписан этим человеком или
    // ушёл с шага «На согласовании» — повторять запрос бессмысленно.
    throw new Error('Документ уже подписан вами или больше не ждёт подписи');
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

export const signatureApi = {
  /** Очередь на подпись: просроченные идут первыми, срока может не быть. */
  queue: () => request<SignatureQueueItem[]>(`${DOCUMENTS}/signature-queue`),

  /** Карточка документа с подписями и сроком. */
  card: (id: number) => request<SignatureCard>(`${DOCUMENTS}/${id}`),

  /** Подписать или отклонить; сервер сам двигает статус и пишет историю. */
  sign: (id: number, decision: SignatureDecision, comment?: string) =>
    post<SignatureCard>(`${DOCUMENTS}/${id}/sign`, {
      decision,
      comment: comment && comment.trim() ? comment.trim() : null
    })
};
