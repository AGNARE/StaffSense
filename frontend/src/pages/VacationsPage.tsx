import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { formatNumber } from '../format';

interface Balance {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  year: number;
  entitledDays: number | null;
  carriedOverDays: number | null;
  usedDays: number;
  remainingDays: number | null;
  entitledKnown: boolean;
}

/**
 * Отпуска: право, использование и остаток по сотрудникам.
 *
 * Право — кадровое решение, поэтому у части сотрудников его может не быть
 * («не начислено»). Остаток в этом случае не считается: показывать ноль
 * было бы утверждением, которого мы не проверяли.
 */
export function VacationsPage() {
  const [items, setItems] = useState<Balance[]>([]);
  const [year, setYear] = useState(new Date().getFullYear());
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setError(null);
    fetch('/api/v1/hr/vacations?year=' + year, { credentials: 'same-origin' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Не удалось загрузить отпуска: HTTP ' + response.status);
        return response.json() as Promise<Balance[]>;
      })
      .then(setItems)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [year]);

  const known = items.filter((item) => item.entitledKnown);
  const totalRemaining = known.reduce((sum, item) => sum + (item.remainingDays ?? 0), 0);

  return (
    <Shell active="timesheet" crumbs={['Люди', 'Отпуска']} actions={
      <div className="row gap-8">
        <button className="btn btn-sm btn-outline" type="button" onClick={() => setYear(year - 1)}>← {year - 1}</button>
        <span className="badge badge-neutral">{year}</span>
        <button className="btn btn-sm btn-outline" type="button" onClick={() => setYear(year + 1)}>{year + 1} →</button>
      </div>
    }>
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Кадры · отпуска</div>
          <h1 className="t-hero">Отпуска</h1>
          <div className="t-sm muted mt-8">
            Право на отпуск приходит из кадрового решения, использование считается по утверждённым запискам
          </div>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="grid g4">
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Сотрудников</span></div>
          <div className="kpi-val">{items.length}</div>
          <div className="kpi-foot"><span>активные</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Право начислено</span></div>
          <div className="kpi-val">{known.length} <span className="unit">из {items.length}</span></div>
          <div className="kpi-foot"><span>остальным остаток не считается</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Остаток всего</span></div>
          <div className="kpi-val">{formatNumber(totalRemaining, 1)} <span className="unit">дн.</span></div>
          <div className="kpi-foot"><span>по начисленным</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Год</span></div>
          <div className="kpi-val">{year}</div>
          <div className="kpi-foot"><span>календарный</span></div>
        </div>
      </div>

      <div className="card card-flush mt-16">
        <div className="card-hd">
          <h2 className="t-h3">Баланс по сотрудникам</h2>
          <div className="spacer" />
          <span className="muted t-xs">использовано — только утверждённые записки</span>
        </div>
        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Сотрудник</th>
                <th>Отдел</th>
                <th className="num">Положено</th>
                <th className="num">Перенос</th>
                <th className="num">Использовано</th>
                <th className="num">Остаток</th>
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={6} className="muted" style={{ padding: 20 }}>Загрузка…</td></tr>}
              {!loading && items.length === 0 && (
                <tr><td colSpan={6} className="muted" style={{ padding: 20 }}>Нет данных</td></tr>
              )}
              {items.map((item) => (
                <tr key={item.userId}>
                  <td>
                    <div className="t-main">{item.fullName}</div>
                    <div className="sub">{item.position ?? '—'}</div>
                  </td>
                  <td className="t-xs">{item.department ?? '—'}</td>
                  <td className="num">{item.entitledKnown ? formatNumber(item.entitledDays ?? 0, 1) : '—'}</td>
                  <td className="num">{item.entitledKnown ? formatNumber(item.carriedOverDays ?? 0, 1) : '—'}</td>
                  <td className="num">{formatNumber(item.usedDays, 1)}</td>
                  <td className="num">
                    {item.entitledKnown ? (
                      <strong style={{ color: (item.remainingDays ?? 0) < 0 ? 'var(--danger)' : undefined }}>
                        {formatNumber(item.remainingDays ?? 0, 1)}
                      </strong>
                    ) : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="table-foot">
          <span className="muted t-xs">
            Прочерк — право не начислено. Ноль дней и «не начислено» — разные утверждения,
            поэтому остаток не подставляется автоматически.
          </span>
        </div>
      </div>
    </Shell>
  );
}
