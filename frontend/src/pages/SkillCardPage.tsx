import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { skillsApi } from '../skills-api';
import type { SkillItem } from '../types';
import { formatShortDateTime } from '../format';

/**
 * Карточка ИИ-скилла (маршрут `#/skills/:id`, экран `sk-skill` прототипа).
 *
 * Показывает то, что действительно есть в модели `ai_skill`: описание, роль,
 * файл, ссылку на репозиторий, дату обновления и признак публикации.
 *
 * Чего здесь нет и почему:
 * • **содержимое файла** — по решению каталога содержимое в JSON не попадает
 *   (иначе список тянул бы мегабайты); файл отдаётся отдельной ручкой
 *   `GET /api/v1/skills/{id}/file`, поэтому в карточке он только скачивается;
 * • **автор** — в сущности хранится `created_by_id`, а имя автора в контракт
 *   скилла не входит, поэтому подставлять чужое имя было бы вымыслом;
 * • **версии и счётчик скачиваний** — таких полей и таблиц в модели нет:
 *   версионирование ведётся вручную в описании, история скачиваний не пишется.
 *
 * «Похожие скиллы» — не выдумка: это опубликованные скиллы той же роли из
 * того же каталога.
 */
export function SkillCardPage(props: { skillId: number }) {
  const [skill, setSkill] = useState<SkillItem | null>(null);
  const [related, setRelated] = useState<SkillItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    setError(null);
    setSkill(null);
    setRelated(null);

    skillsApi.detail(props.skillId)
      .then((data) => {
        if (!alive) return;
        setSkill(data);
        // Похожие — та же роль, только опубликованные, без самой карточки.
        skillsApi.list(data.targetRole)
          .then((items) => {
            if (alive) setRelated(items.filter((item) => item.published && item.id !== data.id));
          })
          // Каталог необязателен: без него карточка всё равно полезнa.
          .catch(() => { if (alive) setRelated([]); });
      })
      .catch((e: Error) => { if (alive) setError(e.message); })
      .finally(() => { if (alive) setLoading(false); });

    return () => { alive = false; };
  }, [props.skillId]);

  return (
    <Shell
      active="skills"
      crumbs={['Система', 'ИИ-скиллы']}
      actions={
        <a className="btn btn-sm btn-ghost" href="#/skills">← К каталогу</a>
      }
    >
      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {loading && !skill && !error && <div className="card card-bd muted">Загрузка…</div>}

      {skill && (
        <>
          <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
            <div>
              <div className="row gap-6 micro mb-8">
                <span>AI Skill Hub</span>
                <span className="sep">/</span>
                <span>{skill.targetRoleTitle}</span>
              </div>
              <div className="row gap-10">
                <h1 style={{ fontSize: 26, fontWeight: 650, letterSpacing: '-.025em' }}>{skill.name}</h1>
                <span className="badge badge-ai badge-lg">AI Skill</span>
                {!skill.published && <span className="badge badge-warn">Черновик</span>}
              </div>
              <div className="t-sm muted mt-8">
                {skill.targetRoleTitle} · обновлён {formatShortDateTime(skill.updatedAt)}
              </div>
            </div>
            <div className="spacer" />
            <div className="row gap-8">
              {skill.githubUrl && (
                <a className="btn" href={skill.githubUrl} target="_blank" rel="noreferrer">
                  Репозиторий
                </a>
              )}
              {skill.fileName && (
                <a className="btn btn-primary" href={'/api/v1/skills/' + skill.id + '/file'}>
                  Скачать {fileExtension(skill.fileName)}
                </a>
              )}
            </div>
          </div>

          <div className="grid g-2-1" style={{ alignItems: 'start' }}>
            <div className="grid" style={{ gap: 14 }}>
              <div className="card">
                <div className="card-hd"><h2 className="t-h3">Что делает</h2></div>
                <div className="card-bd">
                  {skill.description
                    ? <p className="t-sm" style={{ lineHeight: 1.65, margin: 0 }}>{skill.description}</p>
                    : <div className="t-sm muted">Описание не заполнено — автор скилла его не добавил</div>}
                </div>
              </div>

              <div className="card">
                <div className="card-hd">
                  <h2 className="t-h3">Файл и репозиторий</h2>
                  <div className="spacer" />
                  {skill.fileName && <span className="badge badge-outline">{skill.fileName}</span>}
                </div>
                <div className="card-bd">
                  <div className="kv">
                    <span className="k">Файл скилла</span>
                    <span className="v">{skill.fileName ?? 'файл не приложен'}</span>
                  </div>
                  <div className="kv mt-8">
                    <span className="k">Репозиторий</span>
                    <span className="v">
                      {skill.githubUrl
                        ? <a href={skill.githubUrl} target="_blank" rel="noreferrer">{skill.githubUrl}</a>
                        : 'ссылка не указана'}
                    </span>
                  </div>
                  <div className="hairline" />
                  <div className="t-xs muted" style={{ lineHeight: 1.6 }}>
                    Содержимое скилла на экране не показывается: файл лежит в базе
                    (<code>ai_skill.file_data</code>) и отдаётся отдельной ручкой
                    <code> GET /api/v1/skills/{'{id}'}/file</code>. Сотрудник скачивает
                    его и вставляет в свой ИИ-инструмент — вызова скилла из платформы нет.
                  </div>
                </div>
              </div>

              <div className="card">
                <div className="card-hd"><h2 className="t-h3">Публикация</h2></div>
                <div className="card-bd">
                  <div className="row">
                    <span className={'badge ' + (skill.published ? 'badge-ok' : 'badge-warn')}>
                      {skill.published ? 'Опубликован' : 'Черновик'}
                    </span>
                    <span className="t-sm muted">
                      {skill.published
                        ? 'Виден всем авторизованным сотрудникам'
                        : 'Виден только ADMIN и HR — сотрудник карточку не откроет'}
                    </span>
                  </div>
                  <div className="callout mt-12">
                    <div>
                      Публикацией управляет экран
                      <a href="#/skills/manage"> «Управление каталогом»</a>: доступен ADMIN и HR.
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="grid" style={{ gap: 14 }}>
              <div className="card">
                <div className="card-hd"><h2 className="t-h3">Реквизиты</h2></div>
                <div className="card-bd">
                  <div className="kv">
                    <span className="k">Роль</span>
                    <span className="v">{skill.targetRoleTitle}</span>
                  </div>
                  <div className="kv mt-8">
                    <span className="k">Код роли</span>
                    <span className="v num">{skill.targetRole}</span>
                  </div>
                  <div className="kv mt-8">
                    <span className="k">Обновлён</span>
                    <span className="v">{formatShortDateTime(skill.updatedAt)}</span>
                  </div>
                  <div className="kv mt-8">
                    <span className="k">Статус</span>
                    <span className="v">{skill.published ? 'опубликован' : 'черновик'}</span>
                  </div>
                  <div className="kv mt-8">
                    <span className="k">Автор</span>
                    <span className="v muted">не хранится в контракте</span>
                  </div>
                </div>
              </div>

              <div className="card">
                <div className="card-hd">
                  <h2 className="t-h3">Похожие скиллы</h2>
                  <div className="spacer" />
                  <span className="t-xs muted">{skill.targetRoleTitle}</span>
                </div>
                <div className="card-bd">
                  {related === null && <div className="t-sm muted">нет данных</div>}
                  {related?.length === 0 && (
                    <div className="t-sm muted">Других опубликованных скиллов этой роли нет</div>
                  )}
                  {related && related.length > 0 && (
                    <div className="col gap-10">
                      {related.map((item) => (
                        <a key={item.id} href={'#/skills/' + item.id} className="t-sm">
                          {item.name}
                        </a>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="card">
                <div className="card-hd"><h2 className="t-h3">Чего нет в карточке</h2></div>
                <div className="card-bd">
                  <div className="t-xs muted" style={{ lineHeight: 1.65 }}>
                    Версий и истории изменений, счётчика скачиваний и привязки скилла
                    к задачам в модели каталога нет — в прототипе эти блоки держались на
                    демо-данных. Показывать нули или выдуманные числа здесь было бы
                    враньём, поэтому блоки не перенесены.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </Shell>
  );
}

/** Расширение для подписи кнопки: «Скачать .md». */
function fileExtension(fileName: string): string {
  const dot = fileName.lastIndexOf('.');
  return dot >= 0 ? fileName.slice(dot) : 'файл';
}
