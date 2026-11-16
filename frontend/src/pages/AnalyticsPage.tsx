import { useCallback, useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import type { Screen } from '../components/Shell';
import type { Page } from '../types';
import { analyticsApi } from '../analytics-api';
import type {
  AnalyticsSummary,
  KpiEmployee,
  KpiResponse,
  WorkloadEmployee,
  WorkloadResponse
} from '../analytics-api';
import { formatDate, formatNumber, formatShortDateTime } from '../format';

/**
 * Shell ещё не знает раздела «Аналитика»: ключ `analytics` в `Screen`
 * добавляет интегратор вместе с маршрутом `#/analytics` (App.tsx/Shell.tsx —
 * чужие файлы, их не правим). Приведение через `unknown` собирается и до,
 * и после этой правки; до неё в меню просто нет активного пункта.
 */
const ANALYTICS_SCREEN = 'analytics' as unknown as Screen;

const PAGE_SIZE = 50;

type Tab = 'workload' | 'kpi';

/**
 * Аналитика: две вкладки — нагрузка и KPI.
 *
 * Данные приходят из `/api/v1/analytics/*`, который читает те же
 * `WorkloadRecord`/`KpiRecord` через те же сервисы, что и прежние
 * Thymeleaf-страницы. Клиент ничего не досчитывает: если показателя нет,
 * сервер отдаёт `null` + `hasData: false`, и экран честно показывает «—»,
 * а не ноль. Порог перегруза тоже приходит с сервера — константа BR-002
 * не дублируется на клиенте.
 */
export function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>('workload');
  const [workloadPage, setWorkloadPage] = useState(0);
  const [kpiPage, setKpiPage] = useState(0);
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [workload, setWorkload] = useState<WorkloadResponse | null>(null);
  const [kpi, setKpi] = useState<KpiResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([
      analyticsApi.summary(),
      analyticsApi.workload(workloadPage, PAGE_SIZE),
      analyticsApi.kpi(kpiPage, PAGE_SIZE)
    ])
      .then(([summaryData, workloadData, kpiData]) => {
        setSummary(summaryData);
        setWorkload(workloadData);
        setKpi(kpiData);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, [workloadPage, kpiPage]);

  useEffect(load, [load]);

  const employees = summary?.employees ?? 0;
  const avgWorkload = summary?.avgWorkloadIndex ?? null;
  const avgKpi = summary?.avgKpiScore ?? null;
  const overloadThreshold = workload?.overloadThreshold ?? null;

  return (
    <Shell
      active={ANALYTICS_SCREEN}
      crumbs={['Аналитика', 'Нагрузка и KPI']}
      actions={
        <>
          <a className="btn btn-sm btn-outline" href="#/analytics/workload">График нагрузки</a>
          <button className="btn btn-sm btn-outline" type="button" onClick={load} disabled={loading}>
            Обновить
          </button>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">StaffSense · метрики</div>
          <h1 className="t-hero">Аналитика</h1>
          <div className="t-sm muted mt-8">
            {loading
              ? 'Загрузка…'
              : `данные на ${formatShortDateTime(summary?.generatedAt)}`}
          </div>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="grid g4">
        <Metric label="Сотрудников" value={summary ? employees : '—'} foot="в области видимости" />
        <Metric
          label="С индексом нагрузки"
          value={summary ? summary.withWorkload : '—'}
          foot={employees ? `из ${employees}` : 'нет сотрудников'}
        />
        <Metric
          label="Средний KPI"
          value={avgKpi != null ? formatNumber(avgKpi, 1) : '—'}
          foot={summary && summary.withKpi ? `по ${summary.withKpi} с расчётом` : 'расчётов нет'}
        />
        <Metric
          label="Перегруз"
          value={summary ? summary.overloaded : '—'}
          color={summary && summary.overloaded > 0 ? 'var(--danger)' : undefined}
          foot={overloadThreshold != null
            ? `индекс > ${formatNumber(overloadThreshold, 2)}`
            : 'порог BR-002'}
        />
      </div>

      <div className="card card-flush mt-16">
        <div className="table-toolbar">
          <div className="seg">
            <button
              type="button"
              className={tab === 'workload' ? 'is-on' : ''}
              aria-pressed={tab === 'workload'}
              onClick={() => setTab('workload')}
            >
              Нагрузка
            </button>
            <button
              type="button"
              className={tab === 'kpi' ? 'is-on' : ''}
              aria-pressed={tab === 'kpi'}
              onClick={() => setTab('kpi')}
            >
              KPI
            </button>
          </div>

          <div className="spacer" />

          {tab === 'workload' && workload && (
            <span className="t-xs muted">
              последняя запись по каждому сотруднику · средний индекс{' '}
              {avgWorkload != null ? formatNumber(avgWorkload, 2) : '—'}
            </span>
          )}
          {tab === 'kpi' && kpi && (
            <span className="t-xs muted">
              последняя оценка · с расчётом {kpi.withDataCount}
            </span>
          )}
        </div>

        {tab === 'workload' ? (
          <>
            {workload && workload.withDataCount === 0 && !loading && (
              <div className="card-bd t-sm muted">
                Расчётов нагрузки пока нет: записи появятся после пересчёта показателей.
              </div>
            )}
            <div className="table-wrap">
              <table className="table table-wide">
                <thead>
                  <tr>
                    <th>Сотрудник</th>
                    <th>Период</th>
                    <th>Индекс</th>
                    <th>Часы</th>
                    <th>Переработки</th>
                    <th>Обновлено</th>
                  </tr>
                </thead>
                <WorkloadRows rows={workload?.employees.content ?? []} loading={loading && !workload} />
              </table>
            </div>
            {workload && (
              <Pager page={workload.employees} onChange={(next) => setWorkloadPage(next)} />
            )}
          </>
        ) : (
          <>
            {kpi && kpi.withDataCount === 0 && !loading && (
              <div className="card-bd t-sm muted">
                Оценок KPI пока нет: записи появятся после расчёта по завершённым задачам.
              </div>
            )}
            <div className="table-wrap">
              <table className="table table-wide">
                <thead>
                  <tr>
                    <th>Сотрудник</th>
                    <th>Период</th>
                    <th>KPI</th>
                    <th>В срок</th>
                    <th>Переоткрытия</th>
                    <th>Оценка рук.</th>
                    <th>Обновлено</th>
                  </tr>
                </thead>
                <KpiRows rows={kpi?.employees.content ?? []} loading={loading && !kpi} />
              </table>
            </div>
            {kpi && <Pager page={kpi.employees} onChange={(next) => setKpiPage(next)} />}
          </>
        )}
      </div>

      {tab === 'workload' && workload && workload.teams.length > 0 && (
        <div className="card card-flush mt-16">
          <div className="card-hd">
            <h2 className="t-h3">По командам</h2>
            <div className="spacer" />
            <span className="t-xs muted">средний индекс по отделу, без сотрудников без данных</span>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Отдел</th>
                  <th>Сотрудников</th>
                  <th>С данными</th>
                  <th>Средний индекс</th>
                  <th>Перегруз</th>
                </tr>
              </thead>
              <tbody>
                {workload.teams.map((team) => (
                  <tr key={team.department}>
                    <td><span className="t-main">{team.department}</span></td>
                    <td className="num">{team.employeeCount}</td>
                    <td className="num">{team.withDataCount}</td>
                    <td className="num">
                      {team.avgWorkloadIndex != null ? formatNumber(team.avgWorkloadIndex, 2) : '—'}
                    </td>
                    <td>
                      {team.overloadedCount > 0
                        ? <span className="badge badge-danger">{team.overloadedCount}</span>
                        : <span className="muted t-xs">нет</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Shell>
  );
}

function WorkloadRows(props: { rows: WorkloadEmployee[]; loading: boolean }) {
  const { rows, loading } = props;

  if (loading) {
    return <tbody><tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr></tbody>;
  }
  if (rows.length === 0) {
    return (
      <tbody>
        <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Сотрудников нет</td></tr>
      </tbody>
    );
  }

  return (
    <tbody>
      {rows.map((row) => (
        <tr key={row.userId}>
          <td>
            <div className="t-main">{row.fullName}</div>
            <div className="sub">
              {row.department ?? '—'}{row.position ? ' · ' + row.position : ''}
            </div>
          </td>
          <td className="muted t-xs">
            {row.hasData ? `${formatDate(row.periodStart)} — ${formatDate(row.periodEnd)}` : '—'}
          </td>
          <td>
            {row.workloadIndex == null ? (
              <span className="muted t-xs">нет данных</span>
            ) : (
              <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                <strong className="num">{formatNumber(row.workloadIndex, 2)}</strong>
                {row.overloaded && <span className="badge badge-danger">перегруз</span>}
              </div>
            )}
          </td>
          <td className="muted t-xs">
            {row.hasData
              ? `${formatNumber(row.totalEstimatedHours, 1)} / ${formatNumber(row.capacityHours, 1)} ч`
              : '—'}
          </td>
          <td className="muted t-xs">
            {row.overtimeHours != null ? `${formatNumber(row.overtimeHours, 1)} ч` : '—'}
          </td>
          <td className="muted t-xs">{formatShortDateTime(row.computedAt)}</td>
        </tr>
      ))}
    </tbody>
  );
}

function KpiRows(props: { rows: KpiEmployee[]; loading: boolean }) {
  const { rows, loading } = props;

  if (loading) {
    return <tbody><tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr></tbody>;
  }
  if (rows.length === 0) {
    return (
      <tbody>
        <tr><td colSpan={7} className="muted" style={{ padding: 24 }}>Сотрудников нет</td></tr>
      </tbody>
    );
  }

  return (
    <tbody>
      {rows.map((row) => (
        <tr key={row.userId}>
          <td>
            <div className="t-main">{row.fullName}</div>
            <div className="sub">
              {row.department ?? '—'}{row.position ? ' · ' + row.position : ''}
            </div>
          </td>
          <td className="muted t-xs">
            {row.hasData ? `${formatDate(row.periodStart)} — ${formatDate(row.periodEnd)}` : '—'}
          </td>
          <td>
            {row.kpiScore == null
              ? <span className="muted t-xs">нет данных</span>
              : <strong className="num">{formatNumber(row.kpiScore, 1)}</strong>}
          </td>
          <td className="num">
            {row.onTimeCompletionRate != null
              ? `${formatNumber(row.onTimeCompletionRate * 100, 0)}%`
              : '—'}
          </td>
          <td className="num">
            {row.reopenRate != null ? `${formatNumber(row.reopenRate * 100, 0)}%` : '—'}
          </td>
          <td className="num">
            {row.managerRating != null ? formatNumber(row.managerRating, 1) : '—'}
          </td>
          <td className="muted t-xs">{formatShortDateTime(row.computedAt)}</td>
        </tr>
      ))}
    </tbody>
  );
}

/** Пагинация показывается только когда страниц больше одной — иначе это шум. */
function Pager(props: { page: Page<unknown>; onChange: (page: number) => void }) {
  const { page, onChange } = props;
  if (page.totalPages <= 1) {
    return null;
  }

  const from = page.page * page.size + 1;
  const to = Math.min((page.page + 1) * page.size, page.totalElements);

  return (
    <div className="row" style={{ padding: '10px 14px', alignItems: 'center' }}>
      <span className="t-xs muted">Показано {from}–{to} из {page.totalElements}</span>
      <div className="spacer" />
      <button
        className="btn btn-sm btn-ghost"
        type="button"
        disabled={page.page <= 0}
        onClick={() => onChange(page.page - 1)}
      >
        Назад
      </button>
      <button
        className="btn btn-sm btn-ghost"
        type="button"
        disabled={page.page + 1 >= page.totalPages}
        onClick={() => onChange(page.page + 1)}
      >
        Вперёд
      </button>
    </div>
  );
}

/** Карточка показателя — те же классы `.kpi`, что на обзоре и рисках. */
function Metric(props: { label: string; value: number | string; foot: string; color?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-top"><span className="kpi-label">{props.label}</span></div>
      <div className="kpi-val" style={props.color ? { color: props.color } : undefined}>
        {props.value}
      </div>
      <div className="kpi-foot"><span>{props.foot}</span></div>
    </div>
  );
}
