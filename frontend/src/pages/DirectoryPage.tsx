import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { contentApi } from '../content-api';
import type { DirectoryResponse } from '../content-api';
import { initials } from '../format';

/**
 * Справочник сотрудников (прототип: экран {@code pt-directory}).
 *
 * <p>🔴 Экран открыт любому сотруднику, поэтому показывает только
 * неконфиденциальные поля: ФИО, должность, отдел, рабочий e-mail и
 * руководителя (BR-031). Окладов, KPI, нагрузки и прогноза риска здесь нет —
 * они живут в разделе «Сотрудники» с другими правами.
 *
 * <p>Фильтрация — в памяти: справочник компании невелик, и поиск по буквам
 * не должен ходить на сервер. Те же фильтры сервер принимает параметрами
 * {@code department} и {@code q}, если понадобится большая выборка.
 *
 * <p>Маршрут: {@code #/directory}.
 */

const AVATAR_KEYS = ['ava-1', 'ava-2', 'ava-3', 'ava-5', 'ava-6', 'ava-7', 'ava-8'];

function avatarClass(id: number): string {
  return 'ava ava-sm ' + AVATAR_KEYS[Math.abs(id) % AVATAR_KEYS.length];
}

export function DirectoryPage() {
  const [data, setData] = useState<DirectoryResponse | null>(null);
  const [department, setDepartment] = useState('');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    contentApi
      .directory()
      .then(setData)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  const employees = useMemo(() => {
    const all = data?.employees ?? [];
    const needle = query.trim().toLowerCase();
    return all.filter((employee) => {
      if (department !== '' && employee.department !== department) {
        return false;
      }
      if (needle === '') {
        return true;
      }
      return [employee.fullName, employee.position, employee.department, employee.email]
        .some((value) => (value ?? '').toLowerCase().includes(needle));
    });
  }, [data, department, query]);

  return (
    <Shell
      active="onboarding"
      crumbs={['Портал', 'Справочник']}
      actions={
        <>
          <a className="btn btn-sm btn-ghost" href="#/onboarding">Портал</a>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Портал · справочник сотрудников (FR-043)</div>
          <h1 className="t-hero">Справочник</h1>
          <div className="t-sm muted mt-8">
            {data === null
              ? 'Загрузка…'
              : `${data.total} сотрудников · ${data.departments.length} подразделений · доступен всем`}
          </div>
        </div>
        <div className="spacer" />
        <div className="row gap-8">
          <input
            className="input input-sm"
            style={{ maxWidth: 260 }}
            placeholder="Имя, должность, отдел"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="callout mb-16">
        <div>
          <b>Что здесь есть и чего нет (BR-031).</b> В справочнике только неконфиденциальные
          поля: ФИО, должность, отдел, рабочий e-mail, руководитель. Окладов, KPI, индекса
          нагрузки, прогнозов риска и паспортных данных здесь нет — они живут в разделе
          «Сотрудники», доступном только HR, PM, EXEC и ADMIN.
        </div>
      </div>

      <div className="grid g-3-2 g-start">
        <div className="card card-flush">
          <div className="table-toolbar">
            <div className="row gap-6" style={{ flexWrap: 'wrap' }}>
              <button
                className={'chip' + (department === '' ? ' is-on' : '')}
                type="button"
                onClick={() => setDepartment('')}
              >
                Все <span className="n">{data?.total ?? 0}</span>
              </button>
              {(data?.departments ?? []).map((item) => (
                <button
                  key={item.name}
                  className={'chip' + (department === item.name ? ' is-on' : '')}
                  type="button"
                  onClick={() => setDepartment(item.name)}
                >
                  {item.name} <span className="n">{item.count}</span>
                </button>
              ))}
            </div>
            <div className="spacer" />
            <span className="t-xs muted">показано <b>{employees.length}</b></span>
          </div>

          <div className="table-wrap">
            <table className="table table-wide">
              <thead>
                <tr>
                  <th>Сотрудник</th>
                  <th>Должность</th>
                  <th>Отдел</th>
                  <th>E-mail</th>
                  <th>Руководитель</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td colSpan={5} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
                {!loading && employees.length === 0 && (
                  <tr><td colSpan={5} className="muted" style={{ padding: 24 }}>Никого не найдено</td></tr>
                )}
                {employees.map((employee) => (
                  <tr key={employee.id}>
                    <td>
                      <div className="row gap-8">
                        <div className={avatarClass(employee.id)}>{initials(employee.fullName)}</div>
                        <span className="t-main">{employee.fullName}</span>
                      </div>
                    </td>
                    <td className="t-xs">{employee.position ?? '—'}</td>
                    <td className="t-xs">{employee.department ?? '—'}</td>
                    <td className="t-xs">{employee.email ?? '—'}</td>
                    <td className="t-xs">{employee.managerName ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span>{employees.length} из {data?.total ?? 0}</span>
            <div className="spacer" />
            <span className="muted">Сортировка по отделу, затем по имени</span>
          </div>
        </div>

        <div className="col gap-16">
          <div className="card">
            <div className="card-hd"><h2 className="t-h3">Подразделения</h2></div>
            <div className="card-bd">
              <div className="col gap-10">
                {(data?.departments ?? []).map((item) => (
                  <div className="row t-sm" key={item.name}>
                    <span>{item.name}</span>
                    <span className="spacer" />
                    <b className="num">{item.count}</b>
                  </div>
                ))}
                {data !== null && data.departments.length === 0 && (
                  <div className="t-sm muted">Подразделений нет</div>
                )}
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-hd"><h2 className="t-h3">Руководители</h2></div>
            <div className="card-bd">
              <div className="col gap-12">
                {(data?.managers ?? []).map((manager) => (
                  <div className="row gap-10" key={manager.id}>
                    <div className={avatarClass(manager.id)}>{initials(manager.fullName)}</div>
                    <div style={{ flex: 1 }}>
                      <div className="t-sm strong">{manager.fullName}</div>
                      <div className="t-xs muted">
                        {manager.position ?? '—'} · {manager.directReports} в подчинении
                      </div>
                    </div>
                  </div>
                ))}
                {data !== null && data.managers.length === 0 && (
                  <div className="t-sm muted">Руководителей нет</div>
                )}
              </div>
            </div>
          </div>

          <div className="callout callout-warn">
            <div>
              Справочник и раздел «Сотрудники» — два разных экрана с разными правами. Их
              регулярно путают: в справочнике нет ни оклада, ни KPI, ни риска.
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}
