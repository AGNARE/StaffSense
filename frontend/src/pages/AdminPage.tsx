import { useCallback, useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { Journal, Thresholds } from '../types';
import { formatNumber, formatShortDateTime } from '../format';

const PERIODS: Array<{ value: string; label: string }> = [
  { value: 'WEEK', label: 'Неделя' },
  { value: 'MONTH', label: 'Месяц' },
  { value: 'QUARTER', label: 'Квартал' },
  { value: 'ALL', label: 'Всё время' }
];

const LEVEL_CLASS: Record<string, string> = {
  HIGH: 'badge-danger', MEDIUM: 'badge-warn', LOW: 'badge-ok'
};

/**
 * Администрирование прогнозов: журнал за период, покрытие, пороги, пересчёт.
 *
 * <p>Раньше это была серверная страница; здесь те же данные приходят из
 * /api/v1/risk/admin и меняются без перезагрузки.
 */
export function AdminPage() {
  const [period, setPeriod] = useState('MONTH');
  const [journal, setJournal] = useState<Journal | null>(null);
  const [thresholds, setThresholds] = useState<Thresholds | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const [low, setLow] = useState('33');
  const [medium, setMedium] = useState('66');
  const [recomputeOnSave, setRecomputeOnSave] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([api.journal(period), api.thresholds()])
      .then(([data, limits]) => {
        setJournal(data);
        // Пороги в форму подставляем только при первой загрузке,
        // чтобы не затирать то, что человек уже начал править.
        if (limits && !thresholds) {
          setThresholds(limits);
          setLow(String(limits.low));
          setMedium(String(limits.medium));
        }
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period]);

  useEffect(load, [load]);

  const save = () => {
    setSaving(true);
    setError(null);
    setNotice(null);
    api.updateThresholds(Number(low), Number(medium), recomputeOnSave)
      .then((updated) => {
        setThresholds(updated);
        setLow(String(updated.low));
        setMedium(String(updated.medium));
        setNotice('Пороги обновлены' + (recomputeOnSave ? ', уровни прогнозов пересчитаны' : ''));
        load();
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setSaving(false));
  };

  const recompute = () => {
    setError(null);
    setNotice(null);
    api.recomputeAll()
      .then((result) => setNotice(result.message))
      .catch((e: Error) => setError(e.message));
  };

  const coverage = journal?.coverage;

  return (
    <Shell
      active="admin"
      crumbs={['Прогноз увольнений', 'Администрирование']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <a className="btn btn-sm btn-ghost" href="#/risk">К радару</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Модуль 3 · служебное</div>
          <h1 className="t-hero">Администрирование прогнозов</h1>
          <div className="t-sm muted mt-8">
            Журнал снимков риска: покрытие, сводка за период, пороги уровней
          </div>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="grid g4 mt-16">
        <Kpi label="Снимков в журнале" value={coverage?.totalSnapshots ?? '—'} foot="каждый пересчёт добавляет строку" />
        <Kpi label="Последний расчёт" value={formatShortDateTime(coverage?.lastComputedAt)} foot="время сервера" small />
        <Kpi label="Снимки эвристики" value={coverage?.heuristicSnapshots ?? '—'} foot="ML был недоступен" />
        <Kpi label="Без истории" value={coverage?.employeesWithoutHistory ?? '—'} foot="тренд не построить" />
      </div>

      <div className="card card-flush mt-16">
        <div className="table-toolbar">
          <div className="seg">
            {PERIODS.map((item) => (
              <button
                key={item.value}
                type="button"
                className={period === item.value ? 'active' : ''}
                onClick={() => setPeriod(item.value)}
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="spacer" />
          <button className="btn btn-sm btn-outline" type="button" onClick={recompute}>
            Пересчитать всех
          </button>
        </div>

        <div className="card-bd">
          {loading && <span className="muted">Загрузка…</span>}
          {journal && (
            <>
              <div className="t-sm muted">
                Период: {PERIODS.find((p) => p.value === journal.period)?.label ?? journal.period},
                {' '}с {formatShortDateTime(journal.from)} по {formatShortDateTime(journal.to)}
              </div>

              <div className="grid g4 mt-16">
                <Kpi label="Снимков за период" value={journal.total} foot="строк журнала" />
                <Kpi label="Высокий риск" value={journal.high} foot={`${journal.total ? Math.round(journal.high / journal.total * 100) : 0}%`} color="var(--danger)" />
                <Kpi label="Средний риск" value={journal.medium} foot="пороги из настроек" color="var(--warn)" />
                <Kpi label="Низкий риск" value={journal.low} foot="—" color="var(--ok)" />
              </div>
            </>
          )}
        </div>
      </div>

      <div className="card mt-16">
        <div className="card-hd">
          <h2 className="t-h3">Пороги уровней риска</h2>
          <div className="spacer" />
          <span className="badge badge-neutral">
            {thresholds?.usingDefaults ? 'из конфигурации' : 'из БД'}
          </span>
        </div>
        <div className="card-bd">
          <div className="t-sm muted mb-8">
            Правило BR-007: ниже первого порога — низкий риск, между порогами — средний,
            выше второго — высокий. Смена порогов не переписывает журнал: снимки остаются снимками.
          </div>

          <div className="row gap-8" style={{ alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <label className="field" style={{ width: 200 }}>
              <span className="k">Граница LOW / MEDIUM</span>
              <input
                className="input"
                type="number"
                step="0.1"
                min="0.1"
                max="99.8"
                value={low}
                onChange={(event) => setLow(event.target.value)}
              />
            </label>
            <label className="field" style={{ width: 200 }}>
              <span className="k">Граница MEDIUM / HIGH</span>
              <input
                className="input"
                type="number"
                step="0.1"
                min="0.2"
                max="99.9"
                value={medium}
                onChange={(event) => setMedium(event.target.value)}
              />
            </label>
            <label className="row gap-6 t-sm" style={{ alignItems: 'center' }}>
              <input
                type="checkbox"
                checked={recomputeOnSave}
                onChange={(event) => setRecomputeOnSave(event.target.checked)}
              />
              пересчитать уровни прогнозов
            </label>
            <div className="spacer" />
            <button className="btn btn-primary" type="button" disabled={saving} onClick={save}>
              {saving ? 'Сохранение…' : 'Сохранить пороги'}
            </button>
          </div>

          {thresholds?.updatedAt && (
            <div className="t-xs muted mt-8">Изменено: {formatShortDateTime(thresholds.updatedAt)}</div>
          )}
        </div>
      </div>

      <div className="card card-flush mt-16">
        <div className="card-hd"><h2 className="t-h3">Последние расчёты</h2></div>
        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Когда</th>
                <th>Сотрудник</th>
                <th>Отдел</th>
                <th>Риск</th>
                <th>Уровень</th>
                <th>Источник</th>
              </tr>
            </thead>
            <tbody>
              {!journal?.latest.length && (
                <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Журнал пуст</td></tr>
              )}
              {journal?.latest.map((row) => (
                <tr key={`${row.userId}-${row.at}`}>
                  <td className="muted t-xs">{formatShortDateTime(row.at)}</td>
                  <td><a href={'/risk/' + row.userId}>{row.fullName}</a></td>
                  <td>{row.department ?? '—'}</td>
                  <td className="num">{formatNumber(row.riskScore)}%</td>
                  <td><span className={'badge ' + (LEVEL_CLASS[row.riskLevel] ?? 'badge-neutral')}>{row.riskLevel}</span></td>
                  <td className="muted t-xs">{row.source}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Shell>
  );
}

function Kpi(props: { label: string; value: number | string; foot: string; color?: string; small?: boolean }) {
  return (
    <div className="kpi">
      <div className="kpi-top"><span className="kpi-label">{props.label}</span></div>
      <div
        className="kpi-val"
        style={{
          color: props.color,
          fontSize: props.small ? 20 : undefined
        }}
      >
        {props.value}
      </div>
      <div className="kpi-foot"><span>{props.foot}</span></div>
    </div>
  );
}
