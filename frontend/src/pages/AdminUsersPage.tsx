import { useEffect, useState } from 'react';
import { Shell } from '../components/Shell';
import { api } from '../api';
import { adminApi, ADMIN_ROLES } from '../admin-api';
import type { AccessMatrix, AdminUser, CreatedUser } from '../admin-api';
import { initials } from '../format';

const PAGE_SIZE = 20;

/** Цвет аватара по роли: строка таблицы читается быстрее, чем селект роли. */
const ROLE_AVATAR: Record<string, string> = {
  ADMIN: 'ava-4',
  EXEC: 'ava-6',
  HR: 'ava-2',
  PM: 'ava-3',
  EMPLOYEE: 'ava-1'
};

const ROLE_TITLE: Record<string, string> = Object.fromEntries(
  ADMIN_ROLES.map((item) => [item.value, item.title])
);

/**
 * Администрирование пользователей и матрица доступа.
 *
 * <p>Два экрана прототипа в одном разделе: «Пользователи» (таблица, фильтры,
 * создание в боковой панели, смена роли, блокировка) и «Матрица доступа»
 * (роль × раздел). Маршрут {@code #/admin/users} подключается в App.tsx.
 *
 * <p>Правила, которые нельзя обойти из интерфейса, живут на сервере: себя
 * заблокировать нельзя, последнего администратора разжаловать нельзя. Кнопки
 * лишь предупреждают о самом очевидном (своя строка), а отказ сервера
 * показывается текстом правила — не «HTTP 409».
 */
export function AdminUsersPage() {
  const [tab, setTab] = useState<'users' | 'access'>('users');

  const [items, setItems] = useState<AdminUser[]>([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [search, setSearch] = useState('');
  const [role, setRole] = useState('');
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busyUserId, setBusyUserId] = useState<number | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [created, setCreated] = useState<CreatedUser | null>(null);
  const [copied, setCopied] = useState(false);

  const [ownId, setOwnId] = useState<number | null>(null);

  const [matrix, setMatrix] = useState<AccessMatrix | null>(null);
  const [matrixLoading, setMatrixLoading] = useState(false);
  const [matrixError, setMatrixError] = useState<string | null>(null);

  useEffect(() => {
    api.me().then((me) => setOwnId(me.id)).catch(() => setOwnId(null));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setListError(null);

    const timer = window.setTimeout(() => {
      adminApi.users({ page, size: PAGE_SIZE, role, search })
        .then((data) => {
          if (cancelled) return;
          setItems(data.content);
          setPages(Math.max(1, data.totalPages));
          setTotal(data.totalElements);
        })
        .catch((e: Error) => { if (!cancelled) setListError(e.message); })
        .finally(() => { if (!cancelled) setLoading(false); });
    }, search ? 250 : 0);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [page, search, role, reload]);

  useEffect(() => {
    if (tab !== 'access' || matrix) return;
    setMatrixLoading(true);
    setMatrixError(null);
    adminApi.access()
      .then(setMatrix)
      .catch((e: Error) => setMatrixError(e.message))
      .finally(() => setMatrixLoading(false));
  }, [tab, matrix]);

  const refresh = () => setReload((value) => value + 1);

  const changeRole = (user: AdminUser, nextRole: string) => {
    if (nextRole === user.role) return;
    setBusyUserId(user.userId);
    setActionError(null);
    setNotice(null);
    adminApi.changeRole(user.userId, nextRole)
      .then((updated) => {
        setItems((prev) => prev.map((item) => (item.userId === updated.userId ? updated : item)));
        setNotice(updated.fullName + ' — роль «' + (ROLE_TITLE[updated.role] ?? updated.role) + '»');
        // Фильтр по роли мог перестать совпадать с новой ролью — перечитываем.
        if (role !== '') refresh();
      })
      .catch((e: Error) => setActionError(e.message))
      .finally(() => setBusyUserId(null));
  };

  const toggleActive = (user: AdminUser) => {
    setBusyUserId(user.userId);
    setActionError(null);
    setNotice(null);
    adminApi.setActive(user.userId, !user.active)
      .then((updated) => {
        setItems((prev) => prev.map((item) => (item.userId === updated.userId ? updated : item)));
        setNotice(updated.fullName + (updated.active ? ' разблокирован' : ' заблокирован'));
      })
      .catch((e: Error) => setActionError(e.message))
      .finally(() => setBusyUserId(null));
  };

  const copyPassword = () => {
    const password = created?.temporaryPassword;
    if (!password) return;
    navigator.clipboard.writeText(password)
      .then(() => setCopied(true))
      .catch(() => setCopied(false));
  };

  return (
    <Shell
      active="admin"
      crumbs={['Система', 'Пользователи и доступы']}
      actions={
        <>
          <button className="btn btn-sm btn-outline" type="button" onClick={refresh}>Обновить</button>
          <a className="btn btn-sm btn-ghost" href="/admin">Старый интерфейс</a>
        </>
      }
    >
      <div className="row" style={{ alignItems: 'flex-start', marginBottom: 20 }}>
        <div>
          <div className="micro mb-8">Система · доступ</div>
          <h1 className="t-hero">Пользователи и доступы</h1>
          <div className="t-sm muted mt-8">
            Учётные записи, роли и матрица разделов · права проверяет сервер
          </div>
        </div>
        <div className="spacer" />
        <span className="badge badge-neutral">{total} учётных записей</span>
      </div>

      <div className="tabs mb-16">
        <button
          type="button"
          className={tab === 'users' ? 'is-on' : ''}
          onClick={() => setTab('users')}
        >
          Пользователи
        </button>
        <button
          type="button"
          className={tab === 'access' ? 'is-on' : ''}
          onClick={() => setTab('access')}
        >
          Матрица доступа
        </button>
      </div>

      {actionError && <div className="card card-bd" style={{ color: 'var(--danger)' }}>{actionError}</div>}
      {notice && <div className="card card-bd" style={{ color: 'var(--ok)' }}>{notice}</div>}

      {created && (
        <div className="card card-bd mt-16" style={{ borderColor: 'var(--brand-3)' }}>
          <div className="row gap-8 wrap">
            <span className="t-h3">Учётная запись создана</span>
            <span className="badge badge-ok">{created.user.fullName}</span>
            <div className="spacer" />
            <button
              className="btn btn-sm btn-ghost"
              type="button"
              onClick={() => { setCreated(null); setCopied(false); }}
            >
              Закрыть
            </button>
          </div>
          <div className="t-sm muted mt-8">
            {created.temporaryPassword
              ? 'Передайте временный пароль сотруднику: он показывается один раз и в базе хранится только хешем.'
              : 'Пароль задан вами при создании — передайте его сотруднику.'}
          </div>
          {created.temporaryPassword && (
            <div className="row gap-8 mt-12 wrap">
              <code className="mono" style={{ fontSize: 16, letterSpacing: '.02em' }}>
                {created.temporaryPassword}
              </code>
              <button className="btn btn-sm btn-outline" type="button" onClick={copyPassword}>
                {copied ? 'Скопировано' : 'Скопировать'}
              </button>
            </div>
          )}
        </div>
      )}

      {tab === 'users' && (
        <div className="card card-flush mt-16">
          <div className="table-toolbar">
            <div className="searchbox">
              <input
                className="input input-sm"
                placeholder="ФИО, email или отдел"
                value={search}
                onChange={(event) => { setSearch(event.target.value); setPage(0); }}
              />
            </div>
            <div className="spacer" />
            <select
              className="input input-sm"
              style={{ width: 220 }}
              value={role}
              onChange={(event) => { setRole(event.target.value); setPage(0); }}
              title="Фильтр по роли"
            >
              <option value="">Все роли</option>
              {ADMIN_ROLES.map((item) => (
                <option key={item.value} value={item.value}>{item.title}</option>
              ))}
            </select>
            <button
              className="btn btn-sm btn-primary"
              type="button"
              onClick={() => { setCreateOpen(true); setCreated(null); setCopied(false); }}
            >
              Создать пользователя
            </button>
          </div>

          <div className="table-wrap">
            <table className="table table-wide">
              <thead>
                <tr>
                  <th>Пользователь</th>
                  <th>Роль</th>
                  <th>Отдел</th>
                  <th>Должность</th>
                  <th>Статус</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {listError && (
                  <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>{listError}</td></tr>
                )}
                {!listError && loading && (
                  <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Загрузка…</td></tr>
                )}
                {!listError && !loading && items.length === 0 && (
                  <tr><td colSpan={6} className="muted" style={{ padding: 24 }}>Никого не найдено</td></tr>
                )}
                {!listError && items.map((item) => {
                  const isSelf = ownId !== null && ownId === item.userId;
                  const busy = busyUserId === item.userId;
                  return (
                    <tr key={item.userId}>
                      <td>
                        <div className="row gap-8">
                          <div className={'ava ava-sm ' + (ROLE_AVATAR[item.role] ?? 'ava-1')}>
                            {initials(item.fullName)}
                          </div>
                          <div style={{ minWidth: 0 }}>
                            <div className="row gap-6">
                              <span className="t-main">{item.fullName}</span>
                              {isSelf && <span className="badge badge-outline">это вы</span>}
                            </div>
                            <span className="sub">{item.email}</span>
                          </div>
                        </div>
                      </td>
                      <td>
                        <select
                          className="input input-sm"
                          style={{ width: 190 }}
                          value={item.role}
                          disabled={busy}
                          onChange={(event) => changeRole(item, event.target.value)}
                          title="Сменить роль"
                        >
                          {ADMIN_ROLES.map((option) => (
                            <option key={option.value} value={option.value}>{option.title}</option>
                          ))}
                        </select>
                      </td>
                      <td>{item.department ?? '—'}</td>
                      <td>{item.position ?? '—'}</td>
                      <td>
                        <span className={'badge ' + (item.active ? 'badge-ok' : 'badge-danger')}>
                          {item.active ? 'Активен' : 'Заблокирован'}
                        </span>
                      </td>
                      <td className="t-actions">
                        <button
                          className={'btn btn-sm ' + (item.active ? 'btn-outline' : 'btn-primary')}
                          type="button"
                          disabled={busy || (isSelf && item.active)}
                          title={isSelf && item.active
                            ? 'Нельзя заблокировать самого себя'
                            : (item.active ? 'Заблокировать вход' : 'Вернуть доступ')}
                          onClick={() => toggleActive(item)}
                        >
                          {item.active ? 'Заблокировать' : 'Разблокировать'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="table-foot">
            <span className="muted t-xs">страница {page + 1} из {pages} · всего {total}</span>
            <div className="spacer" />
            <button
              className={'btn btn-sm ' + (page === 0 ? 'btn-ghost' : 'btn-outline')}
              type="button"
              disabled={page === 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              Назад
            </button>
            <button
              className={'btn btn-sm ' + (page >= pages - 1 ? 'btn-ghost' : 'btn-outline')}
              type="button"
              disabled={page >= pages - 1}
              onClick={() => setPage((value) => Math.min(pages - 1, value + 1))}
            >
              Вперёд
            </button>
          </div>
        </div>
      )}

      {tab === 'access' && (
        <div className="card card-flush mt-16">
          <div className="card-hd">
            <h2 className="t-h3">Матрица доступа</h2>
            <div className="spacer" />
            {matrix && (
              <span className="badge badge-neutral">
                {matrix.roles.length} ролей · {matrix.screens.length} разделов
              </span>
            )}
          </div>

          {matrixError && <div className="card-bd" style={{ color: 'var(--danger)' }}>{matrixError}</div>}
          {matrixLoading && <div className="card-bd muted">Загрузка…</div>}

          {matrix && (
            <div className="table-wrap">
              <table className="matrix">
                <thead>
                  <tr>
                    <th>Раздел</th>
                    {matrix.roles.map((roleItem) => (
                      <th key={roleItem.role}>
                        {roleItem.role}
                        <br />
                        <span style={{ fontWeight: 400, letterSpacing: 0, textTransform: 'none' }}>
                          {roleItem.title}
                        </span>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {matrix.screens.map((screen) => (
                    <tr key={screen.id}>
                      <td>
                        {screen.title}
                        <span className="sub">{screen.group} · {screen.route}</span>
                      </td>
                      {matrix.roles.map((roleItem) => {
                        const allowed = roleItem.screens.includes(screen.id);
                        return (
                          <td key={roleItem.role}>
                            <span
                              className={'lvl ' + (allowed ? 'lvl-3' : 'lvl-0')}
                              title={allowed ? 'Раздел доступен' : 'Раздел недоступен'}
                            >
                              {allowed ? '✓' : '·'}
                            </span>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {matrix && (
            <div className="card-bd t-xs muted">
              Разделы и их доступность приходят с сервера — из того же списка, по которому
              строится навигация. Поэтому меню и эта таблица не могут разойтись.
            </div>
          )}
        </div>
      )}

      {createOpen && (
        <CreateUserDrawer
          onClose={() => setCreateOpen(false)}
          onCreated={(result) => {
            setCreateOpen(false);
            setCreated(result);
            setCopied(false);
            setActionError(null);
            setNotice(null);
            refresh();
          }}
        />
      )}
    </Shell>
  );
}

/** Боковая панель создания учётной записи. */
function CreateUserDrawer(props: {
  onClose: () => void;
  onCreated: (result: CreatedUser) => void;
}) {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('EMPLOYEE');
  const [department, setDepartment] = useState('');
  const [position, setPosition] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  const canSubmit = fullName.trim() !== ''
    && email.includes('@')
    && department.trim() !== ''
    && position.trim() !== '';

  const submit = () => {
    if (!canSubmit || busy) return;
    setBusy(true);
    setLocalError(null);
    adminApi.createUser({
      fullName: fullName.trim(),
      email: email.trim(),
      role,
      department: department.trim(),
      position: position.trim(),
      password: password.trim() ? password : undefined
    })
      .then(props.onCreated)
      .catch((e: Error) => setLocalError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <>
      <div className="overlay is-open" onClick={props.onClose} />
      <aside className="drawer is-open" aria-hidden={false}>
        <div className="drawer-hd">
          <div>
            <h2 className="t-h2">Новая учётная запись</h2>
            <div className="t-sm muted mt-4">Доступ выдаётся сразу; пароль — временный</div>
          </div>
          <div className="spacer" />
          <button className="btn btn-sm btn-ghost btn-icon" type="button" onClick={props.onClose}>✕</button>
        </div>

        <div className="drawer-bd">
          {localError && (
            <div className="callout callout-danger mb-12" style={{ color: 'var(--danger)' }}>
              {localError}
            </div>
          )}

          <div className="col gap-12">
            <label className="field">
              <span className="field-label">ФИО <span className="field-req">*</span></span>
              <input
                className="input"
                placeholder="Иванова Мария Петровна"
                value={fullName}
                onChange={(event) => setFullName(event.target.value)}
              />
            </label>

            <label className="field">
              <span className="field-label">Служебный email <span className="field-req">*</span></span>
              <input
                className="input"
                type="email"
                placeholder="m.ivanova@staffsense.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              <span className="field-hint">Используется для входа; регистр приводится к нижнему</span>
            </label>

            <label className="field">
              <span className="field-label">Роль <span className="field-req">*</span></span>
              <select className="input" value={role} onChange={(event) => setRole(event.target.value)}>
                {ADMIN_ROLES.map((item) => (
                  <option key={item.value} value={item.value}>{item.title}</option>
                ))}
              </select>
            </label>

            <label className="field">
              <span className="field-label">Отдел <span className="field-req">*</span></span>
              <input
                className="input"
                placeholder="HR"
                value={department}
                onChange={(event) => setDepartment(event.target.value)}
              />
            </label>

            <label className="field">
              <span className="field-label">Должность <span className="field-req">*</span></span>
              <input
                className="input"
                placeholder="Рекрутер"
                value={position}
                onChange={(event) => setPosition(event.target.value)}
              />
            </label>

            <label className="field">
              <span className="field-label">Пароль</span>
              <input
                className="input"
                type="password"
                placeholder="Оставьте пустым — сервер сгенерирует"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <span className="field-hint">
                Не короче 8 символов. Пустое поле — временный пароль покажем один раз
              </span>
            </label>
          </div>
        </div>

        <div className="drawer-ft">
          <button
            className="btn btn-primary"
            type="button"
            disabled={!canSubmit || busy}
            onClick={submit}
          >
            {busy ? 'Создание…' : 'Создать учётную запись'}
          </button>
          <button className="btn btn-ghost" type="button" onClick={props.onClose}>Отмена</button>
        </div>
      </aside>
    </>
  );
}
