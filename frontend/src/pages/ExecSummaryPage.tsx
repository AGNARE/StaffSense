import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { RiskListItem } from '../types';
import { formatNumber } from '../format';

interface CostInfo {
  enabled: boolean;
  currency: string;
  total: number | null;
}

/**
 * Сводка для руководства.
 *
 * Собирается из существующих API — своего эндпоинта не заводит: агрегация
 * в клиенте не создаёт второго источника правды. Показывает то, что решает
 * управленческий вопрос: сколько людей под риском, во что это оценивается
 * и куда движется.
 */
export function ExecSummaryPage() {
  const [risks, setRisks] = useState<RiskListItem[]>([]);
  const [cost, setCost] = useState<CostInfo | null>(null);
  const [workload, setWorkload] = useState<{ avg: number | null; overloaded: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      api.risks(200).catch(() => null),
      api.riskCost().catch(() => null),
      fetch('/api/v1/analytics/summary', { credentials: 'same-origin' })
        .then((r) => (r.ok ? r.json() : null))
        .catch(() => null)
    ])
      .then(([page, costInfo, summary]) => {
        setRisks(page ? page.content : []);
        setCost(costInfo);
        setWorkload(summary
          ? { avg: summary.avgWorkloadIndex ?? null, overloaded: summary.overloaded ?? 0 }
          : null);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const withPrediction = risks.filter((r) => r.riskScore >= 0);
  const high = risks.filter((r) => r.riskLevel === 'HIGH');
  const medium = risks.filter((r) => r.riskLevel === 'MEDIUM');
  const average = withPrediction.length
    ? withPrediction.reduce((sum, r) => sum + r.riskScore, 0) / withPrediction.length
    : null;
  const growing = risks.filter((r) => (r.trendDelta ?? 0) > 0).length;

  const formatMoney = (value: number) =>
    value >= 1_000_000
      ? formatNumber(value / 1_000_000, 2) + ' млн'
      : value >= 1_000
        ? formatNumber(value / 1_000, 0) + ' тыс.'
        : formatNumber(value, 0);

  return (
    <Shell active="exec" crumbs={['Обзор', 'Сводка для руководства']}>
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Обзор · руководство</div>
          <h1 className="t-hero">Сводка</h1>
          <div className="t-sm muted mt-8">
            {loading ? 'Загрузка…' : `${withPrediction.length} сотрудников под прогнозом · ${high.length} высокого риска`}
          </div>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="grid g4">
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Высокий риск</span></div>
          <div className="kpi-val" style={{ color: 'var(--danger)' }}>{high.length} <span className="unit">чел.</span></div>
          <div className="kpi-foot"><span>из {withPrediction.length} под прогнозом</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Средний балл риска</span></div>
          <div className="kpi-val">{average != null ? formatNumber(average) : '—'} <span className="unit">%</span></div>
          <div className="kpi-foot"><span>по всем с прогнозом</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Стоимость под риском</span></div>
          <div className="kpi-val">
            {cost?.enabled && cost.total != null
              ? formatMoney(cost.total) + ' сом'
              : '—'}
          </div>
          <div className="kpi-foot"><span>{cost?.enabled ? 'риск × оклад × месяцы' : 'коэффициенты не согласованы'}</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Растёт риск</span></div>
          <div className="kpi-val" style={{ color: 'var(--warn)' }}>{growing} <span className="unit">чел.</span></div>
          <div className="kpi-foot"><span>по динамике последних расчётов</span></div>
        </div>
      </div>

      <div className="grid g-2-1 mt-16" style={{ alignItems: 'start' }}>
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Кто требует внимания</h2>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/risk">Все риски →</a>
          </div>
          <div className="table-wrap">
            <table className="table table-wide">
              <thead>
                <tr><th>Сотрудник</th><th>Отдел</th><th>Риск</th><th>Уровень</th><th>Динамика</th></tr>
              </thead>
              <tbody>
                {!loading && high.length === 0 && (
                  <tr><td colSpan={5} className="muted" style={{ padding: 20 }}>Высокого риска нет</td></tr>
                )}
                {high.slice(0, 8).map((item) => (
                  <tr key={item.userId} className="table-click"
                      onClick={() => { window.location.hash = '#/risk/' + item.userId; }}>
                    <td><span className="t-main">{item.fullName}</span></td>
                    <td className="t-xs muted">{item.department ?? '—'}</td>
                    <td><span className="risk-score is-high">{formatNumber(item.riskScore)}%</span></td>
                    <td><span className="badge badge-danger">Высокий</span></td>
                    <td>
                      {item.trendDelta != null && item.trendDelta !== 0 ? (
                        <span className={'delta ' + (item.trendDelta > 0 ? 'delta-up' : 'delta-down')}>
                          {item.trendDelta > 0 ? '+' : ''}{formatNumber(item.trendDelta, 1)}
                        </span>
                      ) : <span className="muted t-xs">—</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="grid" style={{ gap: 14 }}>
          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Распределение</h2></div>
            <div className="card-bd">
              <div className="kv"><span className="k">Высокий</span><span className="v num">{high.length}</span></div>
              <div className="kv"><span className="k">Средний</span><span className="v num">{medium.length}</span></div>
              <div className="kv"><span className="k">Низкий</span><span className="v num">{risks.length - high.length - medium.length}</span></div>
              <div className="kv"><span className="k">Покрытие прогнозом</span><span className="v num">{withPrediction.length} из {risks.length}</span></div>
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Нагрузка</h2></div>
            <div className="card-bd">
              {workload ? (
                <>
                  <div className="kv"><span className="k">Средний индекс</span>
                    <span className="v num">{workload.avg != null ? formatNumber(workload.avg) : '—'}</span></div>
                  <div className="kv"><span className="k">Перегружено</span>
                    <span className="v num">{workload.overloaded} чел.</span></div>
                  <a className="btn btn-sm btn-outline mt-8" href="#/analytics/workload">График нагрузки →</a>
                </>
              ) : <span className="t-sm muted">данных нет</span>}
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}
