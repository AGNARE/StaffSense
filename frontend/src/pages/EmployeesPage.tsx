import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { EmployeeDetail, EmployeeItem } from '../types';
import { formatDate, formatNumber, initials } from '../format';

const PAGE_SIZE = 20;

const GRADE_BADGE: Record<string, string> = {
  JUNIOR: 'badge-info',
  MIDDLE: 'badge-outline',
  SENIOR: 'badge-ok',
  LEAD: 'badge-ai'
};

const RISK_BADGE = {
  HIGH: { className: 'badge-danger', title: 'высокий' },
  MEDIUM: { className: 'badge-warn', title: 'средний' },
  LOW: { className: 'badge-ok', title: 'низкий' },
  UNKNOWN: { className: 'badge-neutral', title: 'нет прогноза' }
} as const;

/** Каталог сотрудников: серверный поиск, фильтр по отделу, панель профиля. */
export function EmployeesPage() {
  const [items, setItems] = useState<EmployeeItem[]>([]);
  const [departments, setDepartments] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [department, setDepartment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [opened, setOpened] = useState<EmployeeDetail | null>(null);

  useEffect(() => {
    api.departments().then((data) => setDepartments(data.items)).catch(() => setDepartments([]));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    const timer = window.setTimeout(() => {
      api.employees({ page, size: PAGE_SIZE, search, department })
        .then((data) => {
          if (cancelled) return;
          setItems(data.content);
          setPages(Math.max(1, data.totalPages));
          setTotal(data.totalElements);
        })
        .catch((e: Error) => { if (!cancelled) setError(e.message); })
        .finally(() => { if (!cancelled) setLoading(false); });
    }, search ? 250 : 0);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [page, search, department]);

  const openProfile = (item: EmployeeItem) => {
    setOpened({ ...item, weeklyCapacityHours: null, risk: null });
    api.employee(item.userId).then(setOpened).catch(() => { /* панель уже открыта данными из строки */ });
  };

  return (
    <Shell
      active="employees"
      crumbs={['Кадровый процесс', 'Каталог сотрудников']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={() => setPage((p) => p)}>
            Обновить
          </button>
          <a className="btn btn-sm btn-ghost" href="/employees">Старый интерфейс</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Кадровый процесс · каталог</div>
          <h1 className="t-hero">Сотрудники</h1>
          <div className="t-sm muted mt-8">
            Каталог активных сотрудников · поиск и фильтр работают на сервере
          </div>
        </div>
        <div className="spacer" />
        <span className="badge badge-neutral">{total} сотрудник(ов)</span>
      </div>

      <div className="card card-flush">
        <div className="table-toolbar">
          <div className="searchbox">
            <input
              className="input input-sm"
              placeholder="Имя или отдел"
              value={search}
              onChange={(event) => { setSearch(event.target.value); setPage(0); }}
            />
          </div>
          <div className="spacer" />
          <select
            className="input input-sm"
            style={{ width: 200 }}
            value={department}
            onChange={(event) => { setDepartment(event.target.value); setPage(0); }}
          >
            <option value="">Все отделы</option>
            {departments.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </div>

        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Сотрудник</th>
                <th>Отдел</th>
                <th>Должность</th>
                <th>Грейд</th>
                <th>Руководитель</th>
                <th>Нанят</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {error && <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>{error}</td></tr>}
              {!error && loading && <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
              {!error && !loading && items.length === 0 && (
                <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Сотрудники не найдены</td></tr>
              )}
              {items.map((item) => (
                <tr key={item.userId} className="table-click" onClick={() => openProfile(item)}>
                  <td>
                    <div className="row gap-8">
                      <div className="ava ava-sm ava-1">{initials(item.fullName)}</div>
                      <div>
                        <span className="t-main">{item.fullName}</span>
                        <span className="sub">{item.email}</span>
                      </div>
                    </div>
                  </td>
                  <td>{item.department ?? '—'}</td>
                  <td>{item.position ?? '—'}</td>
                  <td>
                    <span className={'badge ' + (GRADE_BADGE[item.gradeLevel] ?? 'badge-outline')}>
                      {item.gradeLevel}
                    </span>
                  </td>
                  <td>{item.managerName ?? '—'}</td>
                  <td className="muted t-xs">{formatDate(item.hireDate)}</td>
                  <td className="t-actions">
                    <button
                      className="btn btn-sm btn-outline"
                      type="button"
                      onClick={(event) => { event.stopPropagation(); openProfile(item); }}
                    >
                      Профиль
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="table-foot">
          <span className="muted t-xs">страница {page + 1} из {pages} · всего {total}</span>
          <div className="spacer" />
          <button
            className={'btn btn-sm ' + (page === 0 ? 'btn-ghost' : 'btn-outline')}
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Назад
          </button>
          <button
            className={'btn btn-sm ' + (page >= pages - 1 ? 'btn-ghost' : 'btn-outline')}
            type="button"
            disabled={page >= pages - 1}
            onClick={() => setPage((p) => Math.min(pages - 1, p + 1))}
          >
            Вперёд
          </button>
        </div>
      </div>

      <div
        className={'overlay' + (opened ? ' is-open' : '')}
        onClick={() => setOpened(null)}
      />
      <aside className={'drawer' + (opened ? ' is-open' : '')} aria-hidden={!opened}>
        {opened && (
          <>
            <div className="drawer-hd">
              <div className="profile-hd" style={{ gap: 12, alignItems: 'center' }}>
                <div className="ava ava-lg ava-1">{initials(opened.fullName)}</div>
                <div style={{ minWidth: 0 }}>
                  <div className="row gap-8">
                    <h2 className="t-h2">{opened.fullName}</h2>
                    <span className="badge badge-outline">{opened.gradeLevel}</span>
                  </div>
                  <div className="t-sm muted mt-4">
                    {[opened.position, opened.department].filter(Boolean).join(' · ')}
                  </div>
                </div>
              </div>
              <div className="spacer" />
              <button className="btn btn-sm btn-ghost btn-icon" type="button" onClick={() => setOpened(null)}>
                ✕
              </button>
            </div>

            <div className="drawer-bd">
              <div className="profile-meta">
                <div className="kv"><span className="k">Отдел</span><span className="v">{opened.department ?? '—'}</span></div>
                <div className="kv"><span className="k">Должность</span><span className="v">{opened.position ?? '—'}</span></div>
                <div className="kv"><span className="k">Руководитель</span><span className="v">{opened.managerName ?? '—'}</span></div>
                <div className="kv"><span className="k">Нанят</span><span className="v num">{formatDate(opened.hireDate)}</span></div>
                <div className="kv">
                  <span className="k">Норма часов</span>
                  <span className="v num">
                    {opened.weeklyCapacityHours != null ? opened.weeklyCapacityHours + ' ч/нед' : '—'}
                  </span>
                </div>
                <div className="kv"><span className="k">Почта</span><span className="v">{opened.email}</span></div>
              </div>
            </div>

            <div className="drawer-ft">
              {opened.risk ? (
                <span className={'badge ' + (RISK_BADGE[opened.risk.riskLevel] ?? RISK_BADGE.UNKNOWN).className}>
                  риск {formatNumber(opened.risk.riskScore)}% · {(RISK_BADGE[opened.risk.riskLevel] ?? RISK_BADGE.UNKNOWN).title}
                </span>
              ) : (
                <span className="badge badge-neutral">прогноза нет</span>
              )}
              <div className="spacer" />
              <a className="btn btn-sm btn-outline" href={'/risk/' + opened.userId}>Карточка риска</a>
            </div>
          </>
        )}
      </aside>
    </Shell>
  );
}
