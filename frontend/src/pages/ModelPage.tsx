import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { ModelInfo } from '../types';
import { formatNumber } from '../format';

/**
 * Экран «ML-модель» — параметры действующей модели.
 *
 * Коэффициенты приходят из ML-сервиса через /api/v1/risk/model; если сервис
 * недоступен, источник помечается как EXPERT, и экран говорит об этом прямо.
 * Метрики качества показаны по отчёту StaffSenseML/RESULTS.md и подписаны
 * как измерения на синтетических данных.
 */
export function ModelPage() {
  const [info, setInfo] = useState<ModelInfo | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.model().then(setInfo).catch((e: Error) => setError(e.message));
  }, []);

  const features = info?.features ?? [];
  const maxAbs = Math.max(0.001, ...features.map((f) => Math.abs(f.weight)));
  const source = features[0]?.source;

  return (
    <Shell
      active="risk"
      crumbs={['Прогноз увольнений', 'ML-модель']}
      actions={<a className="btn btn-sm btn-ghost" href="#/risk">К радару</a>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Модуль 3 · модель</div>
          <h1 className="t-hero">ML-модель</h1>
          <div className="t-sm muted mt-8">
            Логистическая регрессия на семи признаках · отдельный сервис
            {source ? ' · источник коэффициентов: ' + (source === 'TRAINED' ? 'обученная модель' : 'экспертная оценка') : ''}
          </div>
        </div>
        <div className="spacer" />
        {info && (
          <span className="badge badge-neutral">
            пороги {formatNumber(info.low, 0)} / {formatNumber(info.medium, 0)}
          </span>
        )}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="grid g-2-1 mt-16" style={{ alignItems: 'start' }}>
        <div className="card card-flush">
          <div className="card-hd"><h2 className="t-h3">Коэффициенты модели</h2></div>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Признак</th>
                  <th style={{ width: 180 }}>Влияние</th>
                  <th>Вес</th>
                  <th>Направление</th>
                </tr>
              </thead>
              <tbody>
                {features.length === 0 && !error && (
                  <tr><td colSpan={4} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>
                )}
                {features.map((feature) => {
                  const raises = feature.weight > 0;
                  return (
                    <tr key={feature.id}>
                      <td>
                        <div className="t-main">{feature.label}</div>
                        <div className="sub">{feature.description}</div>
                      </td>
                      <td>
                        <div style={{ height: 6, borderRadius: 4, background: 'var(--surface-inset)', overflow: 'hidden' }}>
                          <div style={{
                            width: Math.round(Math.abs(feature.weight) / maxAbs * 100) + '%',
                            height: '100%',
                            background: raises ? 'var(--danger)' : 'var(--ok)'
                          }} />
                        </div>
                      </td>
                      <td className="num">{formatNumber(feature.weight, 3)}</td>
                      <td>
                        <span className={'badge ' + (raises ? 'badge-danger' : 'badge-ok')}>
                          {raises ? 'повышает риск' : 'снижает риск'}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span className="muted t-xs">
              Вес — коэффициент логистической регрессии: положительный увеличивает вероятность ухода
            </span>
          </div>
        </div>

        <div className="grid" style={{ gap: 14 }}>
          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Как измеряли</h2></div>
            <div className="card-bd">
              <div className="kv"><span className="k">ROC-AUC</span><span className="v num">0,617</span></div>
              <div className="kv"><span className="k">Потолок данных</span><span className="v num">0,623</span></div>
              <div className="kv"><span className="k">PR-AUC</span><span className="v num">0,546</span></div>
              <div className="kv"><span className="k">Brier</span><span className="v num">0,241</span></div>
              <p className="t-xs muted mt-8" style={{ margin: 0 }}>
                Отложенная выборка 80/20 и независимая проверка на 5 000 записей.
                Значения — из отчёта ML-сервиса (RESULTS.md), на синтетических данных.
              </p>
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Пороги уровней</h2></div>
            <div className="card-bd">
              <div className="t-sm">
                Ниже <strong>{formatNumber(info?.low ?? 0, 0)}</strong> — низкий риск,
                от <strong>{formatNumber(info?.low ?? 0, 0)}</strong> до{' '}
                <strong>{formatNumber(info?.medium ?? 0, 0)}</strong> — средний,
                выше — высокий.
              </div>
              <p className="t-xs muted mt-8" style={{ margin: 0 }}>
                Настраиваются в разделе «Администрирование»
                {info?.thresholdsFromConfig ? ' · сейчас значения из конфигурации' : ' · сейчас значения из БД'}.
              </p>
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Чего модель не делает</h2></div>
            <div className="card-bd">
              <ul className="t-sm" style={{ margin: 0, paddingLeft: 16, lineHeight: 1.7 }}>
                <li>Не задаёт горизонт: метка — событие «в этот момент».</li>
                <li>Не обучена на реальных уходах — данные синтетические.</li>
                <li>Не принимает кадровых решений: только сигнал для человека.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}
