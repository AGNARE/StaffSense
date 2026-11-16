import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { RiskLevel, RiskListItem, Thresholds } from '../types';
import { formatNumber, formatShortDateTime } from '../format';

const LEVEL: Record<RiskLevel, { badge: string; title: string; score: string }> = {
  HIGH: { badge: 'badge-danger', title: 'Высокий', score: 'is-high' },
  MEDIUM: { badge: 'badge-warn', title: 'Средний', score: 'is-med' },
  LOW: { badge: 'badge-ok', title: 'Низкий', score: 'is-low' },
  UNKNOWN: { badge: 'badge-neutral', title: 'Без прогноза', score: 'is-low' }
};

type FilterValue = RiskLevel | '' | 'GROWING';

/** Компактная запись суммы: 1,26 млн / 780 тыс. */
function formatMoney(value: number): string {
  if (value >= 1_000_000) return formatNumber(value / 1_000_000, 2) + ' млн';
  if (value >= 1_000) return formatNumber(value / 1_000, 0) + ' тыс.';
  return formatNumber(value, 0);
}

const FILTERS: Array<{ value: FilterValue; label: string }> = [
  { value: '', label: 'Все' },
  { value: 'HIGH', label: 'Высокий' },
  { value: 'MEDIUM', label: 'Средний' },
  { value: 'LOW', label: 'Низкий' },
  { value: 'GROWING', label: 'Растёт' }
];

/** Радар рисков: KPI, фильтры и таблица поверх /api/v1/risk. */
export function RiskPage() {
  const [items, setItems] = useState<RiskListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [thresholds, setThresholds] = useState<Thresholds | null>(null);
  const [level, setLevel] = useState<FilterValue>('');
  const [view, setView] = useState<'list' | 'matrix'>('list');
  const [cost, setCost] = useState<{ enabled: boolean; currency: string; total: number | null } | null>(null);
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([api.risks(), api.thresholds().catch(() => null), api.riskCost().catch(() => null)])
      .then(([page, limits, costInfo]) => {
        setItems(page.content);
        setTotal(page.totalElements);
        setThresholds(limits);
        setCost(costInfo);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const withPrediction = useMemo(() => items.filter((i) => i.riskScore >= 0), [items]);
  const count = (value: RiskLevel) => items.filter((i) => i.riskLevel === value).length;
  const average = withPrediction.length
    ? withPrediction.reduce((sum, i) => sum + i.riskScore, 0) / withPrediction.length
    : null;
  const coverage = total ? Math.round((withPrediction.length / total) * 100) : 0;

  const chipCount = (value: FilterValue) => {
    if (value === '') return items.length;
    if (value === 'GROWING') return items.filter((i) => (i.trendDelta ?? 0) > 0).length;
    return items.filter((i) => i.riskLevel === value).length;
  };

  const rows = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return items.filter((item) => {
      if (level === 'GROWING') {
        if (!((item.trendDelta ?? 0) > 0)) return false;
      } else if (level && item.riskLevel !== level) {
        return false;
      }
      if (!needle) return true;
      return (item.fullName + ' ' + (item.department ?? '')).toLowerCase().includes(needle);
    });
  }, [items, level, search]);

  const lastComputed = items
    .map((i) => i.computedAt)
    .filter((x): x is string => Boolean(x))
    .sort()
    .pop();

  return (
    <Shell
      active="risk"
      crumbs={['Прогноз увольнений', 'Радар рисков']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <a className="btn btn-sm btn-ghost" href="#/admin">Администрирование</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Модуль 3 · прогнозирование увольнений</div>
          <h1 className="t-hero">Радар рисков</h1>
          <div className="t-sm muted mt-8">
            {total} сотрудник(ов) · последний расчёт {formatShortDateTime(lastComputed)} · источник: журнал прогнозов
          </div>
        </div>
        <div className="spacer" />
        <span className="badge badge-neutral">
          {thresholds
            ? `пороги ${formatNumber(thresholds.low, 0)} / ${formatNumber(thresholds.medium, 0)}`
            : 'пороги —'}
        </span>
      </div>

      <div className="grid g4">
        <Kpi label="Высокий риск" value={count('HIGH')} unit="чел." color="var(--danger)" foot="требует разбора" />
        <Kpi label="Средний риск" value={count('MEDIUM')} unit="чел." color="var(--warn)" foot="наблюдение" />
        <Kpi label="Средний балл" value={formatNumber(average)} unit="%" foot="по всем сотрудникам" />
        <Kpi label="Покрытие прогнозом" value={coverage} unit="%" foot={`${withPrediction.length} из ${total}`} />
        <Kpi
          label="Стоимость под риском"
          value={cost?.enabled && cost.total != null ? formatMoney(cost.total) : "—"}
          unit={cost?.enabled ? "сом" : ""}
          foot={cost?.enabled ? "риск × оклад × месяцы" : "коэффициенты не согласованы"}
        />
      </div>

      <div className="card card-flush mt-16">
        <div className="table-toolbar">
          <div className="seg">
            {FILTERS.map((filter) => (
              <button
                key={filter.value || 'all'}
                type="button"
                className={level === filter.value ? 'active' : ''}
                onClick={() => setLevel(filter.value)}
              >
                {filter.label} <span className="muted">{chipCount(filter.value)}</span>
              </button>
            ))}
          </div>
          <div className="spacer" />
          <div className="searchbox">
            <input
              className="input input-sm"
              placeholder="Имя, отдел"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <div className="seg">
            <button type="button" className={view === 'list' ? 'active' : ''} onClick={() => setView('list')}>Список</button>
            <button type="button" className={view === 'matrix' ? 'active' : ''} onClick={() => setView('matrix')}>Матрица</button>
          </div>
          <a className="btn btn-sm btn-outline" href="/risk/export/csv">CSV</a>
        </div>

        {view === 'matrix' && (
          <div className="card-bd">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 10 }}>
              {rows.map((item) => {
                const lv = LEVEL[item.riskLevel] ?? LEVEL.UNKNOWN;
                return (
                  <a
                    key={item.userId}
                    href={'#/risk/' + item.userId}
                    className="card card-bd"
                    style={{ display: 'block', textDecoration: 'none', borderLeft: '3px solid ' + (item.riskLevel === 'HIGH' ? 'var(--danger)' : item.riskLevel === 'MEDIUM' ? 'var(--warn)' : 'var(--ok)') }}
                  >
                    <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline' }}>
                      <span className="t-main truncate" style={{ maxWidth: 130 }}>{item.fullName}</span>
                      <span className={'risk-score ' + lv.score}>{formatNumber(item.riskScore)}%</span>
                    </div>
                    <div className="sub">{item.department ?? '—'}</div>
                    <div className="row mt-8" style={{ gap: 6 }}>
                      <span className={'badge ' + lv.badge}>{lv.title}</span>
                      {item.trendDelta != null && item.trendDelta !== 0 && (
                        <span className={'delta ' + (item.trendDelta > 0 ? 'delta-up' : 'delta-down')}>
                          {item.trendDelta > 0 ? '+' : ''}{formatNumber(item.trendDelta, 1)}
                        </span>
                      )}
                    </div>
                  </a>
                );
              })}
            </div>
          </div>
        )}

        <div className="table-wrap" style={view === 'matrix' ? { display: 'none' } : undefined}>
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Сотрудник</th>
                <th style={{ width: 210 }}>Риск увольнения</th>
                <th>Уровень</th>
                <th>Тренд</th>
                <th>Главный фактор</th>
                <th>Обновлён</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {error && <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>{error}</td></tr>}
              {!error && loading && <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>}
              {!error && !loading && rows.length === 0 && (
                <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Ничего не найдено</td></tr>
              )}
              {rows.map((item) => (
                <RiskRow key={item.userId} item={item} />
              ))}
            </tbody>
          </table>
        </div>

        <div className="table-foot">
          <span className="muted t-xs">показано {rows.length} из {items.length}</span>
          <div className="spacer" />
          <a className="btn btn-sm btn-ghost" href="#/employees">Каталог сотрудников →</a>
        </div>
      </div>
    </Shell>
  );
}

function Kpi(props: { label: string; value: number | string; unit: string; foot: string; color?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-top"><span className="kpi-label">{props.label}</span></div>
      <div className="kpi-val" style={props.color ? { color: props.color } : undefined}>
        {props.value} <span className="unit">{props.unit}</span>
      </div>
      <div className="kpi-foot"><span>{props.foot}</span></div>
    </div>
  );
}

function RiskRow({ item }: { item: RiskListItem }) {
  const level = LEVEL[item.riskLevel] ?? LEVEL.UNKNOWN;
  const known = item.riskScore >= 0;
  const delta = item.trendDelta;

  return (
    <tr
      className="table-click"
      onClick={() => { window.location.hash = '#/risk/' + item.userId; }}
    >
      <td>
        <div className="row gap-8">
          <div className="ava ava-sm ava-1">{initials(item.fullName)}</div>
          <div>
            <span className="t-main">{item.fullName}</span>
            <span className="sub">{[item.position, item.department].filter(Boolean).join(' · ')}</span>
          </div>
        </div>
      </td>
      <td>
        <div className="risk-meter">
          <span className="risk-track">
            <span className="marker" style={{ left: (known ? Math.min(100, item.riskScore) : 0) + '%' }} />
          </span>
          <span className={'risk-score ' + level.score}>{known ? formatNumber(item.riskScore) + '%' : '—'}</span>
        </div>
      </td>
      <td><span className={'badge ' + level.badge}>{level.title}</span></td>
      <td>
        {delta === null || delta === undefined ? (
          <span className="muted t-xs">—</span>
        ) : (
          <span className={'delta ' + (delta > 0 ? 'delta-down' : delta < 0 ? 'delta-up' : 'delta-flat')}>
            {(delta > 0 ? '+' : '') + formatNumber(delta)}
          </span>
        )}
      </td>
      <td>
        {item.topFactor
          ? <span className={'badge ' + level.badge}>{item.topFactor}</span>
          : <span className="muted t-xs">нет объяснения</span>}
      </td>
      <td className="muted t-xs">{formatShortDateTime(item.computedAt)}</td>
      <td className="t-actions">
        <button
          className="btn btn-sm btn-outline"
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            window.location.hash = '#/risk/' + item.userId;
          }}
        >
          Разбор
        </button>
      </td>
    </tr>
  );
}

function initials(fullName: string): string {
  return fullName.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p.charAt(0).toUpperCase()).join('');
}
