import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { RiskDetail, RiskLevel } from '../types';
import { formatNumber, formatShortDateTime } from '../format';

/** Почему объяснения нет — зависит от источника прогноза, а не от догадок. */
function noFactorsReason(source: string | null): string {
  if (source === 'HEURISTIC') {
    return 'ML-сервис был недоступен: прогноз посчитан эвристикой, а она не формирует объяснение факторов. '
      + 'Запустите ML-сервис и нажмите «Пересчитать прогноз».';
  }
  if (source === 'SEED') {
    return 'Это демонстрационные данные: объяснение появится после пересчёта через ML-сервис.';
  }
  return 'Объяснение не сохранилось вместе с прогнозом. Нажмите «Пересчитать прогноз».';
}

const LEVEL: Record<RiskLevel, { badge: string; title: string; color: string }> = {
  HIGH: { badge: 'badge-danger', title: 'Высокий риск', color: 'var(--danger)' },
  MEDIUM: { badge: 'badge-warn', title: 'Средний риск', color: 'var(--warn)' },
  LOW: { badge: 'badge-ok', title: 'Низкий риск', color: 'var(--ok)' },
  UNKNOWN: { badge: 'badge-neutral', title: 'Прогноза нет', color: 'var(--ink-3)' }
};

/**
 * Карточка риска сотрудника: балл, признаки, объяснение модели, тренд.
 *
 * <p>Заменяет серверную страницу /risk/{id} в новом клиенте; данные — один
 * запрос /api/v1/risk/{id}, пересчёт — POST на тот же ресурс.
 */
export function RiskDetailPage({ userId }: { userId: number }) {
  const [detail, setDetail] = useState<RiskDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () => {
    setError(null);
    api.risk(userId).then(setDetail).catch((e: Error) => setError(e.message));
  };

  useEffect(load, [userId]);

  const recompute = () => {
    setBusy(true);
    setNotice(null);
    setError(null);
    api.recompute(userId)
      .then((updated) => {
        setDetail(updated);
        setNotice('Прогноз пересчитан: ' + formatNumber(updated.riskScore) + '%');
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  if (error) {
    return (
      <Shell active="risk" crumbs={['Прогноз увольнений', 'Карточка риска']}>
        <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>
      </Shell>
    );
  }

  if (!detail) {
    return (
      <Shell active="risk" crumbs={['Прогноз увольнений', 'Карточка риска']}>
        <div className="card card-bd muted">Загрузка…</div>
      </Shell>
    );
  }

  const level = LEVEL[detail.riskLevel] ?? LEVEL.UNKNOWN;
  const known = detail.riskScore !== null && detail.riskScore >= 0;

  return (
    <Shell
      active="risk"
      crumbs={['Прогноз увольнений', 'Разбор прогноза']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>
          <button className="btn btn-sm btn-primary" type="button" onClick={recompute} disabled={busy}>
            {busy ? 'Пересчёт…' : 'Пересчитать прогноз'}
          </button>
          <a className="btn btn-sm btn-ghost" href="#/risk">К списку</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Разбор прогноза</div>
          <h1 className="t-hero">{detail.fullName}</h1>
          <div className="t-sm muted mt-8">
            {[detail.position, detail.department, detail.gradeLevel].filter(Boolean).join(' · ')}
            {' · '}расчёт {formatShortDateTime(detail.computedAt)}
          </div>
        </div>
        <div className="spacer" />
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 40, fontWeight: 700, color: level.color, lineHeight: 1 }}>
            {known ? formatNumber(detail.riskScore) + '%' : '—'}
          </div>
          <span className={'badge ' + level.badge}>{level.title}</span>
        </div>
      </div>

      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      <div className="grid g-2-1 mt-16">
        <div className="card card-flush">
          <div className="card-hd"><h2 className="t-h3">Признаки на момент расчёта</h2></div>
          <div className="card-bd">
            {detail.features.length === 0 && <span className="muted">Признаки не сохранены</span>}
            <div className="grid g4">
              {detail.features.map((feature) => (
                <div
                  key={feature.key}
                  className="card card-bd"
                  style={{ borderLeft: '3px solid ' + (feature.color || 'var(--line-strong)') }}
                >
                  <div className="t-xs muted">{feature.icon} {feature.label}</div>
                  <div style={{ fontSize: 20, fontWeight: 650 }}>{formatNumber(feature.value, 2)}</div>
                  <div className="t-xs muted">{feature.description}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="card card-flush">
          <div className="card-hd"><h2 className="t-h3">Динамика риска</h2></div>
          <div className="card-bd">
            {detail.trend.points.length === 0 ? (
              <span className="muted">История появится после второго пересчёта</span>
            ) : (
              <>
                <svg viewBox="0 0 240 56" preserveAspectRatio="none" style={{ width: '100%', height: 64 }}>
                  <line x1="0" y1="6" x2="240" y2="6" stroke="var(--line)" strokeDasharray="3 3" />
                  <line x1="0" y1="50" x2="240" y2="50" stroke="var(--line)" strokeDasharray="3 3" />
                  <polyline points={detail.trend.polyline} fill="none" stroke="var(--brand)" strokeWidth="2" />
                  {detail.trend.polyline.trim().split(/\s+/).filter(Boolean).map((pair, index) => {
                    const [x, y] = pair.split(',').map(Number);
                    return <circle key={index} cx={x} cy={y} r="2.4" fill="var(--brand)" />;
                  })}
                </svg>
                <div className="row t-xs muted" style={{ justifyContent: 'space-between' }}>
                  <span>{formatShortDateTime(detail.trend.points[0]?.at)}</span>
                  <span>
                    мин {formatNumber(detail.trend.minScore)} · макс {formatNumber(detail.trend.maxScore)}
                    {' · '}дельта {detail.trend.delta > 0 ? '+' : ''}{formatNumber(detail.trend.delta)} п.п.
                  </span>
                  <span>{formatShortDateTime(detail.trend.points[detail.trend.points.length - 1]?.at)}</span>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      <div className="card card-flush mt-16">
        <div className="card-hd"><h2 className="t-h3">Почему модель так считает</h2></div>
        <div className="card-bd">
          {detail.factors.length === 0 && (
            <span className="muted">{noFactorsReason(detail.source)}</span>
          )}
          {detail.factors.map((factor) => (
            <div key={factor.factor} style={{ marginBottom: 12 }}>
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <strong>{factor.factor}</strong>
                <span className="num">{formatNumber(factor.impact)}</span>
              </div>
              <div style={{ height: 6, borderRadius: 4, background: 'var(--surface-inset)', overflow: 'hidden' }}>
                <div style={{ width: Math.min(100, Math.max(0, factor.impact)) + '%', height: '100%', background: 'var(--danger)' }} />
              </div>
              <div className="t-xs muted mt-4">{factor.description}</div>
            </div>
          ))}
        </div>
      </div>
    </Shell>
  );
}
