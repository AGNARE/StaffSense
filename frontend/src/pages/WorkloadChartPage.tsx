import { useCallback, useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { analyticsApi } from '../analytics-api';
import type {
  AnalyticsSummary,
  WorkloadEmployee,
  WorkloadResponse
} from '../analytics-api';
import { formatDate, formatNumber, formatShortDateTime } from '../format';

/**
 * График нагрузки отдела — визуализация уже существующей аналитики.
 *
 * <p><b>Данные только из API.</b> Высота столбика — `workloadIndex` из
 * `GET /api/v1/analytics/workload`; порог перегруза приходит в том же ответе
 * (`overloadThreshold`, правило BR-002) и рисуется линией, а не зашит в клиент.
 * Средний индекс берётся из `/summary`, потому что сервер считает его только
 * по сотрудникам с расчётом — «нет данных» не должно тянуть среднее к нулю.
 *
 * <p><b>Чего не рисуем.</b> Сотрудники без расчёта (`hasData: false`) в графике
 * не участвуют: столбик нулевой высоты означал бы «индекс 0», а на самом деле
 * индекса нет. Они перечислены отдельным списком ниже.
 *
 * <p><b>Стили.</b> Карточки, таблицы, бейджи и KPI — классы дизайн-системы;
 * положение столбиков задаётся инлайново, потому что высота зависит от данных.
 */
export function WorkloadChartPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [workload, setWorkload] = useState<WorkloadResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([analyticsApi.summary(), analyticsApi.workload(0, PAGE_SIZE)])
      .then(([summaryData, workloadData]) => {
        setSummary(summaryData);
        setWorkload(workloadData);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  const rows = workload?.employees.content ?? [];
  const threshold = workload?.overloadThreshold ?? null;

  /** Сотрудники с индексом — только они попадают на шкалу. */
  const measured = useMemo(
    () => rows.filter((row): row is WorkloadEmployee & { workloadIndex: number } =>
      row.workloadIndex !== null),
    [rows]
  );

  /** Без расчёта — отдельным списком: это не нули, а отсутствие данных. */
  const unmeasured = useMemo(
    () => rows.filter((row) => row.workloadIndex === null),
    [rows]
  );

  /** Топ перегруженных: индекс выше порога, по убыванию. */
  const overloaded = useMemo(
    () => measured
      .filter((row) => row.overloaded)
      .sort((a, b) => b.workloadIndex - a.workloadIndex),
    [measured]
  );

  /** Максимум шкалы: либо максимальный индекс, либо порог — что выше. */
  const scaleMax = useMemo(() => {
    const top = measured.reduce((max, row) => Math.max(max, row.workloadIndex), 0);
    return Math.max(top, threshold ?? 0, 0.1) * 1.08;
  }, [measured, threshold]);

  const average = summary?.avgWorkloadIndex ?? null;

  return (
    <Shell
      active="analytics"
      crumbs={['Аналитика', 'Нагрузка отдела']}
      actions={
        <button className="btn btn-sm btn-outline" type="button" onClick={load} disabled={loading}>
          Обновить
        </button>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Аналитика · нагрузка</div>
          <h1 className="t-hero">Нагрузка отдела</h1>
          <div className="t-sm muted mt-8">
            {loading
              ? 'Загрузка…'
              : workload
                ? `последняя запись по каждому сотруднику · данные на ${formatShortDateTime(workload.generatedAt)}`
                : 'данных нет'}
          </div>
        </div>
        <div className="spacer" />
        {workload && (
          <span className="badge badge-neutral">
            порог перегруза {formatNumber(workload.overloadThreshold, 2)}
          </span>
        )}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {loading && !workload && !error && <div className="card card-bd muted">Загрузка…</div>}

      {workload && !error && (
        <>
          <div className="grid g4">
            <Metric
              label="Сотрудников"
              value={summary ? summary.employees : workload.employees.totalElements}
              foot="в области видимости"
            />
            <Metric
              label="С расчётом"
              value={workload.withDataCount}
              foot={
                workload.employees.totalElements > 0
                  ? `из ${workload.employees.totalElements}`
                  : 'сотрудников нет'
              }
            />
            <Metric
              label="Средний индекс"
              value={average !== null ? formatNumber(average, 2) : '—'}
              foot={
                average !== null
                  ? 'только по сотрудникам с расчётом'
                  : 'расчётов нет — среднего нет'
              }
            />
            <Metric
              label="Перегруз"
              value={summary ? summary.overloaded : overloaded.length}
              color={overloaded.length > 0 ? 'var(--danger)' : undefined}
              foot={
                threshold !== null
                  ? 'индекс > ' + formatNumber(threshold, 2)
                  : 'порог не пришёл'
              }
            />
          </div>

          <div className="card card-flush mt-16">
            <div className="card-hd">
              <h2 className="t-h3">Индекс нагрузки по сотрудникам</h2>
              <div className="spacer" />
              <div className="legend">
                <span className="k"><span className="sw" style={{ background: 'var(--brand-3)' }} />в норме</span>
                <span className="k"><span className="sw" style={{ background: 'var(--danger)' }} />выше порога</span>
                <span className="k">
                  <span className="sw" style={{ width: 12, height: 2, borderRadius: 1, background: 'var(--danger)' }} />
                  порог {threshold !== null ? formatNumber(threshold, 2) : '—'}
                </span>
              </div>
            </div>

            {measured.length === 0 ? (
              <div className="card-bd t-sm muted">
                Расчётов нагрузки пока нет: ни у одного сотрудника нет записи
                (hasData = false). Столбики появятся после пересчёта показателей —
                нули вместо отсутствующих данных не рисуются.
              </div>
            ) : (
              <>
                <div className="card-bd" style={{ overflowX: 'auto' }}>
                  <div style={{ minWidth: Math.max(measured.length * 46, 320) }}>
                    <div
                      style={{
                        position: 'relative',
                        display: 'flex',
                        alignItems: 'flex-end',
                        gap: 6,
                        height: PLOT_HEIGHT,
                        paddingTop: LABEL_HEIGHT
                      }}
                    >
                      {threshold !== null && (
                        <div
                          title={'Порог перегруза BR-002: индекс > ' + formatNumber(threshold, 2)}
                          style={{
                            position: 'absolute',
                            left: 0,
                            right: 0,
                            bottom: barHeight(threshold, scaleMax),
                            borderTop: '1.5px dashed var(--danger)',
                            opacity: 0.75,
                            pointerEvents: 'none'
                          }}
                        />
                      )}
                      {threshold !== null && (
                        <span
                          className="t-xs"
                          style={{
                            position: 'absolute',
                            left: 2,
                            bottom: barHeight(threshold, scaleMax) + 2,
                            color: 'var(--danger)',
                            background: 'var(--surface)',
                            padding: '0 4px',
                            borderRadius: 3,
                            fontSize: 10,
                            zIndex: 2,
                            pointerEvents: 'none'
                          }}
                        >
                          порог {formatNumber(threshold, 2)}
                        </span>
                      )}

                      {measured.map((row) => (
                        <div
                          key={row.userId}
                          title={barHint(row)}
                          style={{
                            flex: '1 0 40px',
                            height: '100%',
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'flex-end',
                            alignItems: 'center',
                            gap: 3,
                            minWidth: 0
                          }}
                        >
                          <span
                            className="num"
                            style={{ fontSize: 9.6, color: 'var(--ink-3)', lineHeight: '10px' }}
                          >
                            {formatNumber(row.workloadIndex, 2)}
                          </span>
                          <div
                            style={{
                              width: '72%',
                              maxWidth: 26,
                              height: barHeight(row.workloadIndex, scaleMax) + 'px',
                              minHeight: 3,
                              borderRadius: '3px 3px 0 0',
                              background: row.overloaded ? 'var(--danger)' : 'var(--brand-3)'
                            }}
                          />
                        </div>
                      ))}
                    </div>

                    <div
                      style={{
                        display: 'flex',
                        gap: 6,
                        marginTop: 6,
                        paddingBottom: 2,
                        borderTop: '1px solid var(--line)',
                        paddingTop: 4
                      }}
                    >
                      {measured.map((row) => (
                        <div
                          key={row.userId}
                          title={row.fullName}
                          style={{
                            flex: '1 0 40px',
                            minWidth: 0,
                            fontSize: 9.6,
                            color: 'var(--ink-3)',
                            textAlign: 'center',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap'
                          }}
                        >
                          {shortName(row.fullName)}
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="table-foot">
                  <span>
                    {measured.length} с расчётом · периоды у сотрудников разные — показана
                    последняя запись каждого
                  </span>
                  <div className="spacer" />
                  {workload.employees.totalElements > rows.length && (
                    <span className="t-xs muted">
                      загружено {rows.length} из {workload.employees.totalElements}
                    </span>
                  )}
                  {overloaded.length > 0 && (
                    <span className="badge badge-danger">{overloaded.length} выше порога</span>
                  )}
                </div>
              </>
            )}
          </div>

          <div className="grid g2 mt-16">
            <div className="card card-flush">
              <div className="card-hd">
                <h2 className="t-h3">Топ перегруженных</h2>
                <div className="spacer" />
                <span className="badge badge-danger">{overloaded.length}</span>
              </div>
              {overloaded.length === 0 ? (
                <div className="card-bd t-sm muted">
                  {measured.length === 0
                    ? 'Сравнивать нечего: расчётов нагрузки нет.'
                    : `Перегруженных нет: максимальный индекс ${formatNumber(
                        Math.max(...measured.map((row) => row.workloadIndex)), 2
                      )} не выше порога ${formatNumber(threshold ?? 0, 2)}.`}
                </div>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Сотрудник</th>
                        <th>Период</th>
                        <th>Индекс</th>
                        <th>Часы</th>
                        <th>Сверх порога</th>
                      </tr>
                    </thead>
                    <tbody>
                      {overloaded.slice(0, TOP_LIMIT).map((row) => (
                        <tr key={row.userId}>
                          <td>
                            <span className="t-main">{row.fullName}</span>
                            <span className="sub">
                              {row.department ?? 'без отдела'}
                              {row.position ? ' · ' + row.position : ''}
                            </span>
                          </td>
                          <td className="muted t-xs">
                            {formatDate(row.periodStart)} — {formatDate(row.periodEnd)}
                          </td>
                          <td className="num">
                            <span className="badge badge-danger">
                              {formatNumber(row.workloadIndex, 2)}
                            </span>
                          </td>
                          <td className="num muted t-xs">
                            {row.totalEstimatedHours !== null && row.capacityHours !== null
                              ? `${formatNumber(row.totalEstimatedHours, 0)} / ${formatNumber(row.capacityHours, 0)} ч`
                              : '—'}
                          </td>
                          <td className="num muted t-xs">
                            {threshold !== null
                              ? '+' + formatNumber(row.workloadIndex - threshold, 2)
                              : '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {overloaded.length > TOP_LIMIT && (
                <div className="table-foot">
                  <span>
                    показаны {TOP_LIMIT} из {overloaded.length}: полный список — на вкладке
                    «Нагрузка» в разделе «Аналитика»
                  </span>
                </div>
              )}
            </div>

            <div className="card card-flush">
              <div className="card-hd">
                <h2 className="t-h3">По отделам</h2>
                <div className="spacer" />
                <span className="t-xs muted">средний индекс</span>
              </div>
              {workload.teams.length === 0 ? (
                <div className="card-bd t-sm muted">Отделов нет: у сотрудников не заполнено подразделение.</div>
              ) : (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>Отдел</th>
                        <th>С данными</th>
                        <th>Средний индекс</th>
                        <th>Перегруз</th>
                      </tr>
                    </thead>
                    <tbody>
                      {workload.teams.map((team) => (
                        <tr key={team.department}>
                          <td>
                            <span className="t-main">{team.department}</span>
                            <span className="sub">{team.employeeCount} чел.</span>
                          </td>
                          <td className="num muted t-xs">{team.withDataCount}</td>
                          <td>
                            {team.avgWorkloadIndex !== null ? (
                              <div className="row" style={{ gap: 8 }}>
                                <div className="bar" style={{ flex: 1, minWidth: 40 }}>
                                  <span
                                    style={{
                                      width: sharePercent(team.avgWorkloadIndex, scaleMax) + '%',
                                      background: threshold !== null && team.avgWorkloadIndex > threshold
                                        ? 'var(--danger)'
                                        : undefined
                                    }}
                                  />
                                </div>
                                <span className="num t-xs">
                                  {formatNumber(team.avgWorkloadIndex, 2)}
                                </span>
                              </div>
                            ) : (
                              <span className="muted t-xs">нет данных</span>
                            )}
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
              )}
              <div className="table-foot">
                <span>
                  средний индекс отдела считается только по сотрудникам с расчётом
                </span>
              </div>
            </div>
          </div>

          {unmeasured.length > 0 && (
            <div className="card mt-16">
              <div className="card-hd">
                <h2 className="t-h3">Без расчёта</h2>
                <div className="spacer" />
                <span className="badge badge-neutral">{unmeasured.length}</span>
              </div>
              <div className="card-bd">
                <div className="t-sm muted mb-8">
                  У этих сотрудников записи нагрузки нет: индекса не существует, поэтому
                  в графике они не участвуют и нулями не показаны.
                </div>
                <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                  {unmeasured.slice(0, UNMEASURED_LIMIT).map((row) => (
                    <span key={row.userId} className="badge badge-outline">
                      {row.fullName}
                    </span>
                  ))}
                  {unmeasured.length > UNMEASURED_LIMIT && (
                    <span className="badge badge-neutral">
                      + ещё {unmeasured.length - UNMEASURED_LIMIT}
                    </span>
                  )}
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </Shell>
  );
}

/** Размер страницы — предел контроллера (`MAX_PAGE_SIZE = 200`). */
const PAGE_SIZE = 200;

/** Сколько перегруженных показывать в топе: остальные доступны в таблице аналитики. */
const TOP_LIMIT = 5;

/** Сколько фамилий перечислять в списке «без расчёта». */
const UNMEASURED_LIMIT = 24;

/** Высота области столбиков и полоса под подписи значений. */
const PLOT_HEIGHT = 196;
const LABEL_HEIGHT = 16;
const BAR_AREA = PLOT_HEIGHT - LABEL_HEIGHT;

/** Высота столбика в пикселях: доля от максимума шкалы. */
function barHeight(value: number, scaleMax: number): number {
  if (scaleMax <= 0 || !Number.isFinite(value)) return 0;
  return Math.max(Math.round((value / scaleMax) * BAR_AREA), 3);
}

/** Ширина полосы отдела в процентах — та же шкала, что у столбиков. */
function sharePercent(value: number, scaleMax: number): number {
  if (scaleMax <= 0) return 0;
  return Math.min(Math.round((value / scaleMax) * 100), 100);
}

/** «Иван Петров» → «Иван П.»: в подписи под столбиком 40 px. */
function shortName(fullName: string): string {
  const parts = fullName.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '—';
  if (parts.length === 1) return parts[0];
  return parts[0] + ' ' + parts[1].charAt(0) + '.';
}

/** Подсказка столбика: всё, что известно о строке, без догадок. */
function barHint(row: WorkloadEmployee & { workloadIndex: number }): string {
  const hours =
    row.totalEstimatedHours !== null && row.capacityHours !== null
      ? ` · ${formatNumber(row.totalEstimatedHours, 0)} / ${formatNumber(row.capacityHours, 0)} ч`
      : '';
  const overtime =
    row.overtimeHours !== null ? ` · переработка ${formatNumber(row.overtimeHours, 0)} ч` : '';
  return (
    `${row.fullName}${row.department ? ' · ' + row.department : ''}` +
    ` · индекс ${formatNumber(row.workloadIndex, 2)}${hours}${overtime}` +
    (row.overloaded ? ' · выше порога' : '')
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
