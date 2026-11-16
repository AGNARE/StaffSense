import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { ModelInfo, RiskDetail, RiskListItem } from '../types';
import { formatNumber } from '../format';

/**
 * Симулятор удержания — что будет, если изменить условия.
 *
 * ВАЖНО: на этом этапе расчёт идёт на клиенте по весам модели, а не запросом
 * в ML-сервис. Это оценка направления и порядка величины: логит меняется на
 * сумму «вес × относительное изменение признака». Точный пересчёт появится,
 * когда в ML-сервис добавят what-if эндпоинт.
 */

/** Рычаги, которые можно двигать. Ключи совпадают с key из карточки. */
const LEVERS: Array<{ key: string; title: string; hint: string; min: number; max: number; invert?: boolean }> = [
  { key: 'workload_index', title: 'Нагрузка', hint: 'часы задач к ёмкости', min: -40, max: 40 },
  { key: 'salary_ratio', title: 'Оклад к грейду', hint: 'отношение к среднему по грейду', min: -20, max: 20 },
  { key: 'overtime_ratio', title: 'Переработки', hint: 'доля сверхурочных', min: -50, max: 30 },
  { key: 'satisfaction', title: 'Удовлетворённость', hint: 'по опросам', min: -30, max: 30 }
];

export function SimulatorPage() {
  const [items, setItems] = useState<RiskListItem[]>([]);
  const [userId, setUserId] = useState<number | null>(null);
  const [detail, setDetail] = useState<RiskDetail | null>(null);
  const [model, setModel] = useState<ModelInfo | null>(null);
  const [shifts, setShifts] = useState<Record<string, number>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.risks(200).then((page) => {
      setItems(page.content.filter((i) => i.riskScore >= 0));
      if (page.content.length) setUserId(page.content[0].userId);
    }).catch((e: Error) => setError(e.message));
    api.model().catch(() => null).then((m) => m && setModel(m));
  }, []);

  useEffect(() => {
    if (userId == null) return;
    setShifts({});
    api.risk(userId).then(setDetail).catch((e: Error) => setError(e.message));
  }, [userId]);

  const weightOf = (key: string) => model?.features.find((f) => f.id === key)?.weight ?? 0;

  const result = useMemo(() => {
    if (!detail || detail.riskScore == null) return null;
    const base = detail.riskScore;
    // логит текущего состояния восстанавливаем из вероятности
    const logit = Math.log(Math.min(0.995, Math.max(0.005, base / 100)) / (1 - Math.min(0.995, Math.max(0.005, base / 100))));
    let delta = 0;
    for (const lever of LEVERS) {
      const shift = (shifts[lever.key] ?? 0) / 100;
      if (!shift) continue;
      // относительное изменение признака умножаем на вес модели
      delta += weightOf(lever.key) * shift * 2.5;
    }
    const next = 100 / (1 + Math.exp(-(logit + delta)));
    return { base, next: Math.min(100, Math.max(0, next)), delta: next - base };
  }, [detail, shifts, model]);

  return (
    <Shell
      active="risk"
      crumbs={['Прогноз увольнений', 'Симулятор удержания']}
      actions={<a className="btn btn-sm btn-ghost" href="#/risk">К радару</a>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Модуль 3 · сценарии</div>
          <h1 className="t-hero">Симулятор удержания</h1>
          <div className="t-sm muted mt-8">
            Что будет с оценкой, если изменить условия: нагрузка, оклад, переработки, удовлетворённость
          </div>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="card card-bd mt-16" style={{ background: 'var(--surface-inset)', border: '1px dashed var(--line)' }}>
        <strong>Это оценка, а не прогноз модели.</strong>{' '}
        <span className="t-sm muted">
          Расчёт выполняется в браузере по коэффициентам действующей модели: каждое изменение признака
          умножается на его вес и пересчитывается вероятность. Направление и порядок величины верны,
          точное значение даст только пересчёт через ML-сервис.
        </span>
      </div>

      <div className="grid g-2-1 mt-16" style={{ alignItems: 'start' }}>
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Сценарий</h2>
            <div className="spacer" />
            <select
              className="input input-sm"
              style={{ width: 260 }}
              value={userId ?? ''}
              onChange={(event) => setUserId(Number(event.target.value))}
            >
              {items.map((item) => (
                <option key={item.userId} value={item.userId}>
                  {item.fullName} · {formatNumber(item.riskScore)}%
                </option>
              ))}
            </select>
          </div>

          <div className="card-bd">
            {LEVERS.map((lever) => (
              <div key={lever.key} style={{ marginBottom: 18 }}>
                <div className="row" style={{ justifyContent: 'space-between' }}>
                  <span className="t-sm"><strong>{lever.title}</strong></span>
                  <span className={'num ' + ((shifts[lever.key] ?? 0) > 0 ? 'delta-up' : (shifts[lever.key] ?? 0) < 0 ? 'delta-down' : 'muted')}>
                    {(shifts[lever.key] ?? 0) > 0 ? '+' : ''}{shifts[lever.key] ?? 0}%
                  </span>
                </div>
                <input
                  type="range"
                  min={lever.min}
                  max={lever.max}
                  step={5}
                  value={shifts[lever.key] ?? 0}
                  style={{ width: '100%' }}
                  onChange={(event) => setShifts({ ...shifts, [lever.key]: Number(event.target.value) })}
                />
                <div className="t-xs muted">{lever.hint}</div>
              </div>
            ))}
            <button className="btn btn-sm btn-ghost" type="button" onClick={() => setShifts({})}>
              Сбросить сценарий
            </button>
          </div>
        </div>

        <div className="grid" style={{ gap: 14 }}>
          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Оценка</h2></div>
            <div className="card-bd">
              {!detail && <div className="muted t-sm">Выберите сотрудника</div>}
              {detail && result && (
                <>
                  <div className="row" style={{ alignItems: 'baseline', gap: 12 }}>
                    <span className="stat num" style={{ fontSize: 34 }}>{formatNumber(result.next)}%</span>
                    <span className={'delta ' + (result.delta < 0 ? 'delta-down' : result.delta > 0 ? 'delta-up' : 'delta-flat')}>
                      {result.delta > 0 ? '+' : ''}{formatNumber(result.delta, 1)} п.п.
                    </span>
                  </div>
                  <div className="t-xs muted mt-4">
                    было {formatNumber(result.base)}% · {detail.fullName} · {detail.department ?? '—'}
                  </div>
                  <div style={{ height: 8, borderRadius: 4, background: 'var(--surface-inset)', marginTop: 12, overflow: 'hidden' }}>
                    <div style={{
                      width: Math.round(result.next) + '%',
                      height: '100%',
                      background: result.next >= 66 ? 'var(--danger)' : result.next >= 33 ? 'var(--warn)' : 'var(--ok)'
                    }} />
                  </div>
                </>
              )}
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Почему так</h2></div>
            <div className="card-bd">
              <ul className="t-sm" style={{ margin: 0, paddingLeft: 16, lineHeight: 1.7 }}>
                <li>Вес каждого признака взят из действующей модели (экран «ML-модель»).</li>
                <li>Вклад платформенных признаков измерен: 0,574 → 0,618 ROC-AUC.</li>
                <li>Оценка не сохраняется в журнал и не меняет прогноз сотрудника.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}
