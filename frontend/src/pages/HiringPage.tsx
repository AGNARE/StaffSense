import { useEffect, useMemo, useState } from 'react';
import { Shell } from '../components/Shell';

interface Vacancy {
  id: number;
  title: string;
  department: string;
  gradeLevel: string | null;
  status: string;
  statusTitle: string;
  openings: number;
  createdAt: string;
}

interface Candidate {
  id: number;
  vacancyId: number;
  fullName: string;
  email: string | null;
  stage: string;
  stageTitle: string;
  source: string | null;
  appliedAt: string;
  stageChangedAt: string | null;
}

interface Funnel {
  vacancyId: number;
  title: string;
  status: string;
  statusTitle: string;
  openings: number;
  hired: number;
  byStage: Record<string, number>;
  totalCandidates: number;
  averageDaysToHire: number | null;
  candidates: Candidate[];
}

/** Этапы воронки в порядке движения кандидата. */
const STAGES: Array<{ code: string; title: string }> = [
  { code: 'APPLIED', title: 'Отклик' },
  { code: 'SCREENING', title: 'Скрининг' },
  { code: 'INTERVIEW', title: 'Интервью' },
  { code: 'OFFER', title: 'Оффер' },
  { code: 'HIRED', title: 'Нанят' },
  { code: 'REJECTED', title: 'Отклонён' }
];

async function csrf(): Promise<{ headerName: string; token: string }> {
  const response = await fetch('/api/v1/csrf', { credentials: 'same-origin' });
  return response.json();
}

/**
 * Воронка найма: вакансии и кандидаты по этапам.
 *
 * Доступные переходы приходят с сервера вместе с кандидатом — правило
 * этапов живёт в домене, клиент его не повторяет.
 */
export function HiringPage() {
  const [vacancies, setVacancies] = useState<Vacancy[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [funnel, setFunnel] = useState<Funnel | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ title: '', department: 'Engineering', gradeLevel: 'MIDDLE', openings: 1 });

  const loadVacancies = () => {
    fetch('/api/v1/hiring/vacancies', { credentials: 'same-origin' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Вакансии: HTTP ' + response.status);
        return response.json() as Promise<Vacancy[]>;
      })
      .then((list) => {
        setVacancies(list);
        setSelected((current) => current ?? (list.length ? list[0].id : null));
      })
      .catch((e: Error) => setError(e.message));
  };

  useEffect(loadVacancies, []);

  const loadFunnel = (id: number) => {
    fetch('/api/v1/hiring/vacancies/' + id + '/funnel', { credentials: 'same-origin' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Воронка: HTTP ' + response.status);
        return response.json() as Promise<Funnel>;
      })
      .then(setFunnel)
      .catch((e: Error) => setError(e.message));
  };

  useEffect(() => {
    if (selected != null) loadFunnel(selected);
  }, [selected]);

  const byStage = useMemo(() => {
    const map: Record<string, Candidate[]> = {};
    STAGES.forEach((stage) => { map[stage.code] = []; });
    (funnel?.candidates ?? []).forEach((candidate) => {
      (map[candidate.stage] ??= []).push(candidate);
    });
    return map;
  }, [funnel]);

  const move = async (candidate: Candidate, stage: string) => {
    setBusy(true);
    setError(null);
    try {
      const token = await csrf();
      const response = await fetch('/api/v1/hiring/candidates/' + candidate.id + '/stage', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [token.headerName]: token.token },
        body: JSON.stringify({ stage })
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message ?? 'Не удалось изменить этап');
      }
      if (selected != null) loadFunnel(selected);
      loadVacancies();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const createVacancy = async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await csrf();
      const response = await fetch('/api/v1/hiring/vacancies', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', [token.headerName]: token.token },
        body: JSON.stringify(form)
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message ?? 'Не удалось открыть вакансию');
      }
      const created = await response.json() as Vacancy;
      setCreating(false);
      setForm({ title: '', department: 'Engineering', gradeLevel: 'MIDDLE', openings: 1 });
      setSelected(created.id);
      loadVacancies();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Shell
      active="hiring"
      crumbs={['Работа', 'Найм']}
      actions={<button className="btn btn-sm btn-primary" type="button" onClick={() => setCreating(!creating)}>Открыть вакансию</button>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Работа · найм</div>
          <h1 className="t-hero">Найм</h1>
          <div className="t-sm muted mt-8">
            Воронка: от отклика до найма. Переходы этапов проверяет сервер
          </div>
        </div>
        <div className="spacer" />
        <span className="badge badge-neutral">{vacancies.length} вакансий</span>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}

      {creating && (
        <div className="card card-bd mt-16">
          <div className="t-h3 mb-8">Новая вакансия</div>
          <div className="row gap-8 wrap" style={{ alignItems: 'flex-end' }}>
            <label className="field" style={{ width: 280 }}>
              <span className="field-label">Название</span>
              <input className="input" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </label>
            <label className="field" style={{ width: 180 }}>
              <span className="field-label">Отдел</span>
              <input className="input" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} />
            </label>
            <label className="field" style={{ width: 140 }}>
              <span className="field-label">Грейд</span>
              <select className="input" value={form.gradeLevel} onChange={(e) => setForm({ ...form, gradeLevel: e.target.value })}>
                <option value="JUNIOR">Junior</option>
                <option value="MIDDLE">Middle</option>
                <option value="SENIOR">Senior</option>
                <option value="LEAD">Lead</option>
              </select>
            </label>
            <label className="field" style={{ width: 110 }}>
              <span className="field-label">Мест</span>
              <input className="input" type="number" min={1} value={form.openings}
                     onChange={(e) => setForm({ ...form, openings: Number(e.target.value) })} />
            </label>
            <button className="btn btn-sm btn-primary" type="button" disabled={busy || !form.title.trim()} onClick={createVacancy}>
              Открыть
            </button>
            <button className="btn btn-sm btn-ghost" type="button" onClick={() => setCreating(false)}>Отмена</button>
          </div>
        </div>
      )}

      <div className="card card-flush mt-16">
        <div className="table-toolbar">
          <select className="input input-sm" style={{ width: 320 }} value={selected ?? ''}
                  onChange={(e) => setSelected(Number(e.target.value))}>
            {vacancies.map((vacancy) => (
              <option key={vacancy.id} value={vacancy.id}>
                {vacancy.title} · {vacancy.department} · {vacancy.statusTitle}
              </option>
            ))}
          </select>
          <div className="spacer" />
          {funnel && (
            <span className="muted t-xs">
              кандидатов {funnel.totalCandidates} · нанято {funnel.hired} из {funnel.openings}
              {funnel.averageDaysToHire != null ? ' · средний срок ' + Math.round(funnel.averageDaysToHire) + ' дн.' : ''}
            </span>
          )}
        </div>

        <div className="card-bd">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, minmax(160px, 1fr))', gap: 10 }}>
            {STAGES.map((stage) => (
              <div key={stage.code}>
                <div className="row" style={{ justifyContent: 'space-between', marginBottom: 8 }}>
                  <span className="t-xs"><strong>{stage.title}</strong></span>
                  <span className="badge badge-neutral">{byStage[stage.code]?.length ?? 0}</span>
                </div>
                {(byStage[stage.code] ?? []).map((candidate) => (
                  <div key={candidate.id} className="card card-bd" style={{ marginBottom: 8, padding: '10px 12px' }}>
                    <div className="t-sm" style={{ fontWeight: 600 }}>{candidate.fullName}</div>
                    <div className="t-xs muted">{candidate.source ?? 'источник не указан'}</div>
                    <div className="row gap-8 mt-8 wrap">
                      {nextStages(candidate.stage).map((next) => (
                        <button
                          key={next.code}
                          className="btn btn-sm btn-outline"
                          type="button"
                          disabled={busy}
                          onClick={() => move(candidate, next.code)}
                        >
                          {next.title}
                        </button>
                      ))}
                    </div>
                  </div>
                ))}
                {(byStage[stage.code] ?? []).length === 0 && (
                  <div className="t-xs muted" style={{ padding: '8px 0' }}>пусто</div>
                )}
              </div>
            ))}
          </div>
        </div>
        <div className="table-foot">
          <span className="muted t-xs">
            Кнопки показывают только допустимые переходы; недопустимый сервер отклонит
          </span>
        </div>
      </div>
    </Shell>
  );
}

/**
 * Следующие шаги для этапа — зеркало серверного правила {@code CandidateStage}.
 * Список используется только для отрисовки кнопок; решение принимает сервер.
 */
function nextStages(stage: string): Array<{ code: string; title: string }> {
  switch (stage) {
    case 'APPLIED':
      return [{ code: 'SCREENING', title: 'В скрининг' }, { code: 'REJECTED', title: 'Отклонить' }];
    case 'SCREENING':
      return [{ code: 'INTERVIEW', title: 'На интервью' }, { code: 'REJECTED', title: 'Отклонить' }];
    case 'INTERVIEW':
      return [{ code: 'OFFER', title: 'Оффер' }, { code: 'REJECTED', title: 'Отклонить' }];
    case 'OFFER':
      return [{ code: 'HIRED', title: 'Нанять' }, { code: 'REJECTED', title: 'Отклонить' }];
    default:
      return [];
  }
}
