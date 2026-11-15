import { useEffect, useState } from 'react';
import { adminApi } from '../admin-api';
import { formatShortDateTime } from '../format';

/** Коды действий с сервера → человеческий текст. */
const ACTION_TITLES: Record<string, string> = {
  USER_CREATED: 'Создана учётная запись',
  ROLE_CHANGED: 'Смена роли',
  ACTIVE_CHANGED: 'Блокировка доступа',
  PASSWORD_RESET: 'Сброс пароля'
};

interface Entry {
  id: number;
  action: string;
  actorEmail: string | null;
  targetEmail: string | null;
  details: string | null;
  createdAt: string;
}

/**
 * Журнал действий администратора: кто, что и когда сделал.
 *
 * Читает /api/v1/admin/audit. Актор и объект приходят снимком с сервера —
 * переименование пользователя не переписывает историю.
 */
export function AuditTab() {
  const [items, setItems] = useState<Entry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    adminApi.auditLog()
      .then((page) => setItems(page.content))
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="card card-flush">
      <div className="card-hd">
        <h2 className="t-h3">Журнал действий</h2>
        <div className="spacer" />
        <span className="muted t-xs">новые сверху</span>
      </div>
      <div className="table-wrap">
        <table className="table table-wide">
          <thead>
            <tr>
              <th>Когда</th>
              <th>Действие</th>
              <th>Администратор</th>
              <th>Объект</th>
              <th>Детали</th>
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={5} className="muted" style={{ padding: 20 }}>Загрузка…</td></tr>}
            {!loading && error && (
              <tr><td colSpan={5} style={{ padding: 20, color: 'var(--danger)' }}>{error}</td></tr>
            )}
            {!loading && !error && items.length === 0 && (
              <tr><td colSpan={5} className="muted" style={{ padding: 20 }}>Пока пусто</td></tr>
            )}
            {items.map((item) => (
              <tr key={item.id}>
                <td className="muted t-xs">{formatShortDateTime(item.createdAt)}</td>
                <td><span className="badge badge-outline">{ACTION_TITLES[item.action] ?? item.action}</span></td>
                <td className="t-xs">{item.actorEmail ?? '—'}</td>
                <td className="t-xs">{item.targetEmail ?? '—'}</td>
                <td className="muted t-xs">{item.details ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
