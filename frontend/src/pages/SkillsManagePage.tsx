import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import type { Me } from '../api';
import { skillsApi } from '../skills-api';
import type { SkillFormInput, SkillRoleOption } from '../skills-api';
import type { SkillItem } from '../types';
import { formatShortDateTime } from '../format';

/**
 * Управление каталогом ИИ-скиллов (маршрут `#/skills/manage`, экран
 * `sk-manage` прототипа): создание, правка, публикация/снятие с публикации,
 * удаление.
 *
 * Права не дублируются на клиенте в виде «нарисуем кнопки и посмотрим»:
 * список приходит с сервера уже в нужном составе (ADMIN и HR видят черновики),
 * а мутации закрыты `@PreAuthorize`. Роль из `/api/v1/me` нужна только чтобы
 * не показывать сотруднику форму, которая всё равно получит 403.
 *
 * **Создание и правка — один `multipart/form-data` запрос.** Файл и поля едут
 * вместе, поэтому создание скилла с файлом не требует второго шага и не
 * зависит от того, сохранился ли черновик перед загрузкой. Ограничение размера
 * задаётся сервером (`spring.servlet.multipart.max-file-size`, по умолчанию
 * 1 МБ) — на 413 клиент покажет текст ошибки сервера.
 */
export function SkillsManagePage() {
  const [me, setMe] = useState<Me | null>(null);
  const [skills, setSkills] = useState<SkillItem[] | null>(null);
  const [roles, setRoles] = useState<SkillRoleOption[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<SkillItem | null>(null);
  const [confirmingId, setConfirmingId] = useState<number | null>(null);

  const canManage = me?.role === 'ADMIN' || me?.role === 'HR';

  const load = () => {
    setLoading(true);
    setError(null);
    skillsApi.list()
      .then(setSkills)
      .catch((e: Error) => { setError(e.message); setSkills(null); })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    api.me().then(setMe).catch(() => setMe(null));
    skillsApi.roles().then(setRoles).catch(() => setRoles([]));
    load();
  }, []);

  /**
   * Одно место для мутаций: иначе каждая кнопка повторяет «занято / ошибка /
   * сообщение / перечитать». `onDone` вызывается только при успехе — сбросить
   * форму при неудаче значит потерять то, что человек ввёл.
   */
  const act = (action: () => Promise<unknown>, message: string, onDone?: () => void) => {
    if (busy) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    action()
      .then(() => { setNotice(message); if (onDone) onDone(); load(); })
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  const drafts = skills?.filter((item) => !item.published).length ?? 0;
  const published = skills?.filter((item) => item.published).length ?? 0;

  return (
    <Shell
      active="skills"
      crumbs={['Система', 'ИИ-скиллы']}
      actions={
        <>
          <a className="btn btn-sm btn-ghost" href="#/skills">← К каталогу</a>
          <button className="btn btn-sm btn-outline" type="button" onClick={load} disabled={loading}>
            Обновить
          </button>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">AI Skill Hub · управление каталогом</div>
          <h1 style={{ fontSize: 28, fontWeight: 650, letterSpacing: '-.025em' }}>Управление каталогом</h1>
          <div className="t-sm muted mt-8">
            {skills === null
              ? 'данные не загружены'
              : `${skills.length} скиллов · ${published} опубликовано · ${drafts} черновиков`}
          </div>
        </div>
        <div className="spacer" />
        {me && (
          <span className={canManage ? 'badge badge-ok' : 'badge badge-warn'}>
            {canManage ? me.role + ': управление доступно' : me.role + ': только просмотр'}
          </span>
        )}
      </div>

      {error && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{error}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      {me && !canManage && (
        <div className="callout callout-warn mb-16">
          <div>
            Управление каталогом доступно только ADMIN и HR. Список ниже — то, что
            вы видите как сотрудник: опубликованные скиллы. Черновики и кнопки
            изменения сервер не отдаёт — запрос вернул бы 403.
          </div>
        </div>
      )}

      <div className={canManage ? 'grid g-3-2' : 'grid'} style={{ alignItems: 'start' }}>
        <div className="card card-flush">
          <div className="card-hd">
            <h2 className="t-h3">Скиллы</h2>
            <div className="spacer" />
            <span className="t-xs muted">по алфавиту</span>
          </div>
          <div className="table-wrap">
            <table className="table table-wide">
              <thead>
                <tr>
                  <th>Скилл</th>
                  <th>Роль</th>
                  <th>Файл</th>
                  <th>Статус</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {loading && skills === null && (
                  <tr><td colSpan={5} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>
                )}
                {!loading && skills?.length === 0 && (
                  <tr><td colSpan={5} className="muted" style={{ padding: 24 }}>Скиллов в каталоге нет</td></tr>
                )}
                {skills?.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <a className="t-main" href={'#/skills/' + item.id}>{item.name}</a>
                      <div className="sub">{formatShortDateTime(item.updatedAt)}</div>
                    </td>
                    <td><span className="badge badge-outline">{item.targetRoleTitle}</span></td>
                    <td className="muted t-xs">{item.fileName ?? 'без файла'}</td>
                    <td>
                      <span className={'badge ' + (item.published ? 'badge-ok' : 'badge-warn')}>
                        {item.published ? 'Опубликован' : 'Черновик'}
                      </span>
                    </td>
                    <td className="t-actions">
                      {!canManage && <a className="btn btn-sm btn-ghost" href={'#/skills/' + item.id}>Открыть</a>}
                      {canManage && (
                        <div className="row" style={{ gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                          <button
                            className="btn btn-sm btn-ghost"
                            type="button"
                            disabled={busy}
                            onClick={() => setEditing(item)}
                          >
                            Изменить
                          </button>
                          <button
                            className="btn btn-sm btn-outline"
                            type="button"
                            disabled={busy}
                            onClick={() => act(
                              () => skillsApi.setPublished(item.id, !item.published),
                              item.published
                                ? `«${item.name}» снят с публикации`
                                : `«${item.name}» опубликован`)}
                          >
                            {item.published ? 'Снять' : 'Опубликовать'}
                          </button>
                          {confirmingId === item.id ? (
                            <>
                              <button
                                className="btn btn-sm btn-danger"
                                type="button"
                                disabled={busy}
                                onClick={() => act(
                                  () => skillsApi.remove(item.id),
                                  `«${item.name}» удалён из каталога`)}
                              >
                                Удалить
                              </button>
                              <button
                                className="btn btn-sm btn-ghost"
                                type="button"
                                onClick={() => setConfirmingId(null)}
                              >
                                Отмена
                              </button>
                            </>
                          ) : (
                            <button
                              className="btn btn-sm btn-ghost"
                              type="button"
                              onClick={() => setConfirmingId(item.id)}
                            >
                              Удалить
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="table-foot">
            <span className="muted t-xs">
              {skills === null
                ? 'нет данных'
                : `${published} опубликовано · ${drafts} черновиков видят только ADMIN и HR`}
            </span>
          </div>
        </div>

        {canManage && (
          <SkillForm
            key={editing?.id ?? 'new'}
            roles={roles}
            editing={editing}
            busy={busy}
            onCancel={() => setEditing(null)}
            onSubmit={(input) => {
              const wasEditing = editing;
              act(
                () => (wasEditing
                  ? skillsApi.update(wasEditing.id, input)
                  : skillsApi.create(input)),
                wasEditing
                  ? `Скилл «${wasEditing.name}» сохранён`
                  : `Скилл «${input.name}» создан`,
                () => setEditing(null));
            }}
          />
        )}
      </div>
    </Shell>
  );
}

/**
 * Форма создания и правки.
 *
 * Один компонент на два режима: поля те же, отличается только вызов API.
 * `key` на форме сбрасывает состояние при переходе между скиллами — иначе
 * в поля подставлялись бы значения предыдущей карточки.
 */
function SkillForm(props: {
  roles: SkillRoleOption[];
  editing: SkillItem | null;
  busy: boolean;
  onCancel: () => void;
  onSubmit: (input: SkillFormInput) => void;
}) {
  const { roles, editing, busy } = props;
  const [name, setName] = useState(editing?.name ?? '');
  const [targetRole, setTargetRole] = useState(editing?.targetRole ?? '');
  const [description, setDescription] = useState(editing?.description ?? '');
  const [githubUrl, setGithubUrl] = useState(editing?.githubUrl ?? '');
  const [published, setPublished] = useState(editing ? editing.published : true);
  const [file, setFile] = useState<File | null>(null);

  const canSubmit = name.trim().length > 0 && targetRole !== '' && !busy;

  return (
    <div className="card">
      <div className="card-hd">
        <h2 className="t-h3">{editing ? 'Правка скилла' : 'Новый скилл'}</h2>
        <div className="spacer" />
        {editing && (
          <span className="badge badge-outline">{editing.published ? 'опубликован' : 'черновик'}</span>
        )}
      </div>
      <div className="card-bd">
        <div className="col gap-12">
          <label className="field">
            <span className="field-label">Название <span className="field-req">*</span></span>
            <input
              className="input"
              placeholder="Например, Migration Plan Writer"
              value={name}
              maxLength={150}
              onChange={(event) => setName(event.target.value)}
            />
          </label>

          <label className="field">
            <span className="field-label">Роль <span className="field-req">*</span></span>
            <select
              className="select"
              value={targetRole}
              onChange={(event) => setTargetRole(event.target.value)}
            >
              <option value="">— выберите роль —</option>
              {roles.map((role) => (
                <option key={role.value} value={role.value}>{role.title}</option>
              ))}
            </select>
            {roles.length === 0 && (
              <span className="field-hint">Справочник ролей не загрузился — доступен только просмотр</span>
            )}
          </label>

          <label className="field">
            <span className="field-label">Описание</span>
            <textarea
              className="textarea"
              placeholder="Что делает скилл и когда его применять"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>

          <label className="field">
            <span className="field-label">Файл скилла</span>
            <input
              className="input"
              type="file"
              accept=".md,.txt,text/markdown,text/plain"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
            <span className="field-hint">
              {editing?.fileName
                ? `Текущий файл: ${editing.fileName}. Новый файл заменит его; без файла прежний сохранится.`
                : '.md или .txt, до 1 МБ (лимит spring.servlet.multipart)'}
            </span>
          </label>

          <label className="field">
            <span className="field-label">Ссылка на репозиторий</span>
            <input
              className="input"
              placeholder="https://github.com/…"
              value={githubUrl}
              maxLength={500}
              onChange={(event) => setGithubUrl(event.target.value)}
            />
          </label>

          <label className="row gap-8" style={{ alignItems: 'center' }}>
            <input
              type="checkbox"
              checked={published}
              onChange={(event) => setPublished(event.target.checked)}
            />
            <span className="t-sm">Опубликовать сразу — скилл увидят все сотрудники</span>
          </label>

          <div className="row gap-8">
            <button
              className="btn btn-primary"
              type="button"
              disabled={!canSubmit}
              onClick={() => {
                if (!canSubmit) return;
                props.onSubmit({
                  name: name.trim(),
                  targetRole,
                  // Пустые строки отправляем как есть: сервер сам превратит их
                  // в null, а для правки «не менять» — это отсутствие поля.
                  description: description.trim(),
                  githubUrl: githubUrl.trim(),
                  published,
                  file
                });
              }}
            >
              {editing ? 'Сохранить' : 'Создать скилл'}
            </button>
            {editing && (
              <button className="btn btn-sm btn-ghost" type="button" onClick={props.onCancel}>
                Отмена
              </button>
            )}
          </div>

          <div className="t-xs muted" style={{ lineHeight: 1.6 }}>
            Файл и поля уходят одним multipart-запросом. Права проверяет сервер:
            создание, правка, публикация и удаление доступны ADMIN и HR, сотрудник
            получит 403.
          </div>
        </div>
      </div>
    </div>
  );
}
