import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { ProjectCard } from '../types';
import { formatDate } from '../format';

/** Список проектов: прогресс по задачам и переход на доску. */
export function ProjectsPage() {
  const [projects, setProjects] = useState<ProjectCard[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    setError(null);
    api.projects()
      .then(setProjects)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  return (
    <Shell
      active="projects"
      crumbs={['Работа', 'Проекты']}
      actions={<button className="btn btn-sm btn-outline" type="button" onClick={load}>Обновить</button>}
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Работа · портфель проектов</div>
          <h1 className="t-hero">Проекты</h1>
          <div className="t-sm muted mt-8">Прогресс считается по задачам на доске</div>
        </div>
        <div className="spacer" />
        <span className="badge badge-neutral">{projects.length} проектов</span>
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {loading && !error && <div className="card card-bd muted">Загрузка…</div>}

      <div className="grid g3">
        {projects.map((project) => (
          <a
            key={project.id}
            className="card card-bd"
            href={'#/projects/' + project.id}
            style={{ textDecoration: 'none', color: 'inherit' }}
          >
            <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div style={{ minWidth: 0 }}>
                <div className="t-h3">{project.name}</div>
                <div className="t-xs muted mt-4">
                  {project.ownerName ? 'PM: ' + project.ownerName : 'PM не назначен'}
                  {' · создан '}{formatDate(project.createdAt)}
                </div>
              </div>
              <span className="badge badge-outline">{project.counts.progressPercent}%</span>
            </div>

            {project.description && (
              <div className="t-sm mt-8" style={{ color: 'var(--ink-3)' }}>{project.description}</div>
            )}

            <div
              style={{
                height: 6, borderRadius: 4, background: 'var(--surface-inset)',
                overflow: 'hidden', marginTop: 12
              }}
            >
              <div
                style={{
                  width: project.counts.progressPercent + '%', height: '100%',
                  background: 'var(--ok)'
                }}
              />
            </div>

            <div className="row t-xs muted mt-8" style={{ gap: 12, flexWrap: 'wrap' }}>
              <span>в работе {project.counts.inProgress}</span>
              <span>на проверке {project.counts.review}</span>
              <span>готово {project.counts.done}</span>
              <span>всего {project.counts.total}</span>
            </div>
          </a>
        ))}
      </div>

      {!loading && !error && projects.length === 0 && (
        <div className="card card-bd muted">Проектов пока нет</div>
      )}
    </Shell>
  );
}
