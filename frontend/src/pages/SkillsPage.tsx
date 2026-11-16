import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { SkillItem } from '../types';
import { formatShortDateTime } from '../format';

const ROLES: Array<{ value: string; title: string }> = [
  { value: '', title: 'Все роли' },
  { value: 'DESIGNER', title: 'Дизайнер' },
  { value: 'FRONTEND', title: 'Фронтенд' },
  { value: 'BACKEND', title: 'Бэкенд' },
  { value: 'QA', title: 'Тестировщик' },
  { value: 'PM', title: 'Руководитель' },
  { value: 'ANALYST', title: 'Аналитик' }
];

/** Каталог ИИ-скиллов: описание, роль, скачивание файла или ссылка на репозиторий. */
export function SkillsPage() {
  const [items, setItems] = useState<SkillItem[]>([]);
  const [role, setRole] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    api.skills(role || undefined)
      .then(setItems)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [role]);

  return (
    <Shell
      active="skills"
      crumbs={['Система', 'ИИ-скиллы']}
      actions={
        <button className="btn btn-sm btn-outline" type="button" onClick={() => setRole(role)}>
          Обновить
        </button>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Система · каталог</div>
          <h1 className="t-hero">ИИ-скиллы</h1>
          <div className="t-sm muted mt-8">
            Готовые наборы инструкций для ролей: скачать файл или открыть репозиторий
          </div>
        </div>
        <div className="spacer" />
        <span className="badge badge-neutral">{items.length}</span>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="card card-flush">
        <div className="table-toolbar">
          <div className="seg">
            {ROLES.map((item) => (
              <button
                key={item.value || 'all'}
                type="button"
                className={role === item.value ? 'active' : ''}
                onClick={() => setRole(item.value)}
              >
                {item.title}
              </button>
            ))}
          </div>
        </div>

        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Скилл</th>
                <th>Роль</th>
                <th>Файл</th>
                <th>Обновлён</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={5} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
              {!loading && items.length === 0 && (
                <tr><td colSpan={5} className="muted" style={{ padding: 24 }}>Скиллов нет</td></tr>
              )}
              {items.map((item) => (
                <tr key={item.id}>
                  <td>
                    <div className="t-main">{item.name}</div>
                    <div className="sub">{item.description ?? '—'}</div>
                  </td>
                  <td>
                    <span className="badge badge-outline">{item.targetRoleTitle}</span>
                    {!item.published && <span className="badge badge-warn" style={{ marginLeft: 6 }}>черновик</span>}
                  </td>
                  <td className="muted t-xs">{item.fileName ?? '—'}</td>
                  <td className="muted t-xs">{formatShortDateTime(item.updatedAt)}</td>
                  <td className="t-actions">
                    <div className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
                      {item.fileName && (
                        <a className="btn btn-sm btn-outline" href={'/api/v1/skills/' + item.id + '/file'}>
                          Скачать
                        </a>
                      )}
                      {item.githubUrl && (
                        <a className="btn btn-sm btn-ghost" href={item.githubUrl} target="_blank" rel="noreferrer">
                          Репозиторий
                        </a>
                      )}
                      {!item.fileName && !item.githubUrl && <span className="muted t-xs">нет вложений</span>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Shell>
  );
}
