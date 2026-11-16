import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { Checklist, Memo, SurveyItem } from '../types';
import type { Me } from '../api';


/**
 * «Мой кабинет» — стартовая страница сотрудника.
 *
 * Собирается из уже существующих API, которые для роли EMPLOYEE отдают
 * только его собственные данные: опросы, записки, прогресс онбординга.
 * Своего эндпоинта не заводим — иначе сервер повторял бы выборки и правила,
 * которые уже есть в модулях.
 */
export function CabinetPage() {
  const [me, setMe] = useState<Me | null>(null);
  const [surveys, setSurveys] = useState<SurveyItem[]>([]);
  const [memos, setMemos] = useState<Memo[]>([]);
  const [checklist, setChecklist] = useState<Checklist | null>(null);

  useEffect(() => {
    api.me().then(setMe).catch(() => setMe(null));
    api.surveys().then(setSurveys).catch(() => setSurveys([]));
    api.memos().then(setMemos).catch(() => setMemos([]));
    api.onboardingChecklist().then(setChecklist).catch(() => setChecklist(null));
  }, []);

  const pendingSurveys = surveys.filter((s) => !s.alreadyTaken && s.status === 'ACTIVE');
  const myMemos = memos.slice(0, 5);

  return (
    <Shell active="me" crumbs={['Люди', 'Мой кабинет']}>
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Личный кабинет</div>
          <h1 className="t-hero">{me ? me.fullName : 'Мой кабинет'}</h1>
          <div className="t-sm muted mt-8">
            {me ? [me.position, me.department].filter(Boolean).join(' · ') : 'загрузка…'}
          </div>
        </div>
        <div className="spacer" />
        {me && <span className="badge badge-outline">{me.role}</span>}
      </div>

      <div className="grid g4">
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Опросы к прохождению</span></div>
          <div className="kpi-val">{pendingSurveys.length}</div>
          <div className="kpi-foot"><span>активные, ещё не пройдены</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Мои записки</span></div>
          <div className="kpi-val">{memos.length}</div>
          <div className="kpi-foot"><span>за всё время</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Онбординг</span></div>
          <div className="kpi-val">{checklist ? checklist.completed + '/' + checklist.total : '—'}</div>
          <div className="kpi-foot"><span>шагов пройдено</span></div>
        </div>
        <div className="kpi">
          <div className="kpi-top"><span className="kpi-label">Почта</span></div>
          <div className="kpi-val" style={{ fontSize: 15, wordBreak: 'break-all' }}>{me?.email ?? '—'}</div>
          <div className="kpi-foot"><span>учётная запись</span></div>
        </div>
      </div>

      <div className="grid g-2-1 mt-16" style={{ alignItems: 'start' }}>
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Опросы для меня</h2>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/surveys">Все опросы →</a>
          </div>
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>Опрос</th><th>Статус</th><th></th></tr></thead>
              <tbody>
                {pendingSurveys.length === 0 && (
                  <tr><td colSpan={3} className="muted" style={{ padding: 20 }}>Активных опросов нет</td></tr>
                )}
                {pendingSurveys.map((survey) => (
                  <tr key={survey.id}>
                    <td><span className="t-main">{survey.title}</span></td>
                    <td><span className="badge badge-warn">не пройден</span></td>
                    <td className="t-actions">
                      <a className="btn btn-sm btn-primary" href="#/surveys">Пройти</a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="grid" style={{ gap: 14 }}>
          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Мои записки</h2></div>
            <div className="card-bd">
              {myMemos.length === 0 && <div className="t-sm muted">Записок нет</div>}
              {myMemos.map((memo) => (
                <div key={memo.id} className="row" style={{ justifyContent: 'space-between', padding: '6px 0' }}>
                  <span className="truncate t-sm" style={{ maxWidth: 170 }}>{memo.typeTitle ?? 'Записка'}</span>
                  <span className="badge badge-neutral">{memo.statusTitle}</span>
                </div>
              ))}
              <a className="btn btn-sm btn-outline mt-8" href="#/memos">Открыть раздел</a>
            </div>
          </div>

          <div className="card card-flush">
            <div className="card-hd"><h2 className="t-h3">Онбординг</h2></div>
            <div className="card-bd">
              {checklist ? (
                <>
                  <div className="row" style={{ justifyContent: 'space-between' }}>
                    <span className="t-sm">пройдено</span>
                    <strong>{checklist.completed} из {checklist.total}</strong>
                  </div>
                  <div style={{ height: 6, borderRadius: 4, background: 'var(--surface-inset)', overflow: 'hidden', marginTop: 6 }}>
                    <div style={{ width: checklist.percent + '%', height: '100%', background: 'var(--ok)' }} />
                  </div>
                </>
              ) : <span className="t-sm muted">нет данных</span>}
              <a className="btn btn-sm btn-outline mt-8" href="#/onboarding">Портал →</a>
            </div>
          </div>
        </div>
      </div>
    </Shell>
  );
}
