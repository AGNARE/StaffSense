import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type {
  Checklist, Memo, ProjectCard, RiskListItem, SurveyItem
} from '../types';
import { formatNumber, formatShortDateTime } from '../format';

/**
 * Обзор — стартовый экран.
 *
 * Собирается из уже существующих эндпоинтов (риски, проекты, записки,
 * опросы, онбординг), а не из отдельного «дашбордного» API. Иначе сервер
 * начал бы дублировать выборки и правила, которые уже есть в модулях.
 */
export function OverviewPage() {
  const [risks, setRisks] = useState<RiskListItem[]>([]);
  const [projects, setProjects] = useState<ProjectCard[]>([]);
  const [pending, setPending] = useState<Memo[]>([]);
  const [surveys, setSurveys] = useState<SurveyItem[]>([]);
  const [checklist, setChecklist] = useState<Checklist | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    setError(null);
    Promise.all([
      api.risks(200).catch(() => null),
      api.projects().catch(() => []),
      api.pendingMemos().catch(() => []),
      api.surveys().catch(() => []),
      api.onboardingChecklist().catch(() => null)
    ])
      .then(([riskPage, projectList, pendingMemos, surveyList, progress]) => {
        setRisks(riskPage ? riskPage.content : []);
        setProjects(projectList);
        setPending(pendingMemos);
        setSurveys(surveyList);
        setChecklist(progress);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  const high = risks.filter((r) => r.riskLevel === 'HIGH');
  const totalRisks = risks.length;
  const coverage = risks.filter((r) => r.riskScore >= 0).length;
  const activeSurveys = surveys.filter((s) => s.status === 'ACTIVE');
  const openProjects = projects.filter((p) => p.counts.progressPercent < 100);
  const lastComputed = risks
    .map((r) => r.computedAt)
    .filter((x): x is string => Boolean(x))
    .sort()
    .pop();

  return (
    <Shell
      active="overview"
      crumbs={['Обзор', 'Сводка']}
      actions={<button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">StaffSense · рабочее утро</div>
          <h1 className="t-hero">Обзор</h1>
          <div className="t-sm muted mt-8">
            {loading ? 'Загрузка…' : `последний расчёт рисков ${formatShortDateTime(lastComputed)}`}
          </div>
        </div>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      <div className="grid g4">
        <Kpi label="Сотрудников под прогнозом" value={totalRisks}
             foot={`покрытие ${totalRisks ? Math.round(coverage / totalRisks * 100) : 0}%`} />
        <Kpi label="Высокий риск" value={high.length} color="var(--danger)"
             foot={high.length ? 'нужен разбор' : 'нет критичных'} />
        <Kpi label="Активных проектов" value={openProjects.length}
             foot={`из ${projects.length} всего`} />
        <Kpi label="Ждут моего решения" value={pending.length} color="var(--warn)"
             foot="служебные записки" />
      </div>

      <div className="grid g-2-1 mt-16">
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Требуют внимания</h2>
            <div className="spacer" />
            <a className="btn btn-sm btn-ghost" href="#/risk">Все риски →</a>
          </div>

          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Сотрудник</th>
                  <th>Риск</th>
                  <th>Главный фактор</th>
                  <th>Обновлён</th>
                </tr>
              </thead>
              <tbody>
                {!loading && high.length === 0 && (
                  <tr><td colSpan={4} className="muted" style={{ padding: 24 }}>Критичных рисков нет</td></tr>
                )}
                {high.slice(0, 8).map((item) => (
                  <tr key={item.userId} className="table-click"
                      onClick={() => { window.location.hash = '#/risk/' + item.userId; }}>
                    <td>
                      <div className="t-main">{item.fullName}</div>
                      <div className="sub">{item.department ?? '—'}</div>
                    </td>
                    <td><span className="risk-score is-high">{formatNumber(item.riskScore)}%</span></td>
                    <td>{item.topFactor ?? <span className="muted t-xs">—</span>}</td>
                    <td className="muted t-xs">{formatShortDateTime(item.computedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card card-flush">
          <div className="card-hd"><h2 className="t-h3">Мои задачи и прогресс</h2></div>
          <div className="card-bd">
            {checklist && (
              <>
                <div className="row" style={{ justifyContent: 'space-between' }}>
                  <span className="t-sm">Чек-лист онбординга</span>
                  <strong>{checklist.completed} / {checklist.total}</strong>
                </div>
                <div style={{ height: 6, borderRadius: 4, background: 'var(--surface-inset)', overflow: 'hidden', marginTop: 6 }}>
                  <div style={{ width: checklist.percent + '%', height: '100%', background: 'var(--ok)' }} />
                </div>
              </>
            )}

            <div className="mt-16">
              <div className="t-xs muted">Активные опросы</div>
              {activeSurveys.length === 0 && <div className="t-sm muted">нет</div>}
              {activeSurveys.slice(0, 3).map((survey) => (
                <div key={survey.id} className="row" style={{ justifyContent: 'space-between', padding: '4px 0' }}>
                  <span className="truncate" style={{ maxWidth: 200 }}>{survey.title}</span>
                  <a className="btn btn-sm btn-ghost" href="#/surveys">
                    {survey.alreadyTaken ? 'пройден' : 'пройти'}
                  </a>
                </div>
              ))}
            </div>

            <div className="mt-16">
              <div className="t-xs muted">Мои записки на согласовании</div>
              {pending.length === 0 && <div className="t-sm muted">нет</div>}
              {pending.slice(0, 4).map((memo) => (
                <div key={memo.id} className="row" style={{ justifyContent: 'space-between', padding: '4px 0' }}>
                  <span className="truncate" style={{ maxWidth: 190 }}>{memo.employeeName}</span>
                  <span className="badge badge-warn">{memo.statusTitle}</span>
                </div>
              ))}
              <a className="btn btn-sm btn-outline mt-8" href="#/memos">Все записки</a>
            </div>
          </div>
        </div>
      </div>

      <div className="card card-flush mt-16">
        <div className="card-hd">
          <h2 className="t-h3">Проекты</h2>
          <div className="spacer" />
          <a className="btn btn-sm btn-ghost" href="#/projects">Все проекты →</a>
        </div>
        <div className="table-wrap">
          <table className="table table-wide">
            <thead>
              <tr>
                <th>Проект</th>
                <th>PM</th>
                <th>Задач</th>
                <th style={{ width: 180 }}>Прогресс</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((project) => (
                <tr key={project.id} className="table-click"
                    onClick={() => { window.location.hash = '#/projects/' + project.id; }}>
                  <td><span className="t-main">{project.name}</span></td>
                  <td className="muted t-xs">{project.ownerName ?? '—'}</td>
                  <td className="num">{project.counts.total}</td>
                  <td>
                    <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                      <div style={{ flex: 1, height: 6, borderRadius: 4, background: 'var(--surface-inset)', overflow: 'hidden' }}>
                        <div style={{ width: project.counts.progressPercent + '%', height: '100%', background: 'var(--brand)' }} />
                      </div>
                      <span className="t-xs muted">{project.counts.progressPercent}%</span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </Shell>
  );
}

function Kpi(props: { label: string; value: number | string; foot: string; color?: string }) {
  return (
    <div className="kpi">
      <div className="kpi-top"><span className="kpi-label">{props.label}</span></div>
      <div className="kpi-val" style={props.color ? { color: props.color } : undefined}>{props.value}</div>
      <div className="kpi-foot"><span>{props.foot}</span></div>
    </div>
  );
}
