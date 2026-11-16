import { useEffect, useState } from 'react';
import { NAV_ICONS } from '../icons';
import { api } from '../api';

export type Screen =
  | 'me'
  | 'overview' | 'risk' | 'employees' | 'admin' | 'projects' | 'memos'
  | 'surveys' | 'onboarding' | 'skills' | 'design' | 'documents' | 'analytics' | 'adminUsers'
  | 'journey'
  | 'hrDesk' | 'timesheet'
  | 'surveyBuilder' | 'portalManage' | 'directory'
  | 'vacations'
  | 'hiring'
  | 'skillsManage' | 'myDay'
  | 'documentsSignature'
  | 'exec';

interface NavChild {
  title: string;
  /** hash-маршрут подпункта */
  route: string;
  /**
   * Экран, который открывает доступ к подпункту. По умолчанию совпадает
   * с route; нужен там, где адрес вложенный, а права выданы на раздел
   * (`#/documents/inbox` живёт под экраном «Документы»).
   */
  requires?: string;
}

interface NavItem {
  title: string;
  /** ключ иконки — совпадает с title в прототипе (см. icons.ts) */
  icon: string;
  /** hash-маршрут; не задан — раздел ещё не перенесён в новый клиент */
  route?: string;
  /** вложенные пункты: секция раскрывается шевроном, как в прототипе */
  children?: NavChild[];
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

const GROUPS: NavGroup[] = [
  {
    label: 'Люди',
    items: [
      { title: 'Мой кабинет', icon: 'Кадровый процесс', route: '#/me' },
      { title: 'Мой день', icon: 'Обзор компании', route: '#/my-day' },
      { title: 'Мой путь', icon: 'Кадровый процесс', route: '#/journey' },
      { title: 'Обзор', icon: 'Обзор компании', route: '#/' },
      { title: 'Кадры', icon: 'Кадровый процесс', route: '#/employees' },
      { title: 'Пульт HR', icon: 'Кадровый процесс', route: '#/hr-desk' },
      { title: 'Табель', icon: 'Кадровый процесс', route: '#/timesheet' },
      { title: 'Отпуска', icon: 'Кадровый процесс', route: '#/vacations' },
      {
        title: 'Портал',
        icon: 'Портал онбординга',
        route: '#/onboarding',
        children: [
          { title: 'Портал онбординга', route: '#/onboarding' },
          { title: 'Справочник', route: '#/directory' },
          { title: 'Управление порталом', route: '#/portal/manage' }
        ]
      },
      { title: 'Опросы', icon: 'Опросы и благополучие', route: '#/surveys' },
      { title: 'Конструктор опросов', icon: 'Опросы и благополучие', route: '#/surveys/builder' },
      { title: 'Записки', icon: 'Служебные записки', route: '#/memos' }
    ]
  },
  {
    label: 'Работа',
    items: [
      { title: 'Найм', icon: 'Кадровый процесс', route: '#/hiring' },
      { title: 'Проекты', icon: 'Управление проектами', route: '#/projects' },
      { title: 'Аналитика', icon: 'Управление проектами', route: '#/analytics' },
      { title: 'Сводка для руководства', icon: 'Обзор компании', route: '#/exec' },
      {
        title: 'Риски увольнений',
        icon: 'Прогноз увольнений',
        route: '#/risk',
        children: [
          { title: 'Радар рисков', route: '#/risk', requires: '#/risk' },
          { title: 'ML-модель', route: '#/model', requires: '#/risk' },
          { title: 'Симулятор удержания', route: '#/simulate', requires: '#/risk' }
        ]
      },
      {
        title: 'Документы',
        icon: 'Документооборот',
        route: '#/documents',
        children: [
          { title: 'Реестр и архив', route: '#/documents' },
          { title: 'Входящие и маршруты', route: '#/documents/inbox', requires: '#/documents' },
          { title: 'На подпись', route: '#/documents/signature' }
        ]
      }
    ]
  },
  {
    label: 'Система',
    items: [
      { title: 'ИИ-скиллы', icon: 'AI Skill Hub', route: '#/skills' },
      { title: 'Управление каталогом', icon: 'AI Skill Hub', route: '#/skills/manage' },
      { title: 'Администрирование', icon: 'Администрирование', route: '#/admin' },
      { title: 'Пользователи', icon: 'Администрирование', route: '#/admin/users' },
      { title: 'Дизайн-система', icon: 'Дизайн-система', route: '#/design' }
    ]
  }
];

/**
 * Совпадает ли адрес с маршрутом пункта.
 *
 * <p>Точное совпадение или вложенный адрес: `#/documents/5` относится
 * к «Реестру и архиву», `#/risk/12` — к радару рисков.
 */
function matchesRoute(route: string, hash: string): boolean {
  if (route === '#/') {
    return hash === '#/' || hash === '' || hash === '#';
  }
  return hash === route || hash.startsWith(route + '/') || hash.startsWith(route + '?');
}

interface Me {
  id: number;
  fullName: string;
  role: string;
  screens?: Array<{ id: string; title: string; route: string; group: string }>;
}

/** Боковая навигация и верхняя панель — общий каркас всех экранов. */
export function Shell(props: {
  active: Screen;
  crumbs: [string, string];
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  const { active, crumbs, actions, children } = props;
  const hash = (typeof window !== 'undefined' ? window.location.hash : '') || '#/';
  const [me, setMe] = useState<Me | null>(null);
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});

  /** Меню строится по списку с сервера: клиент не дублирует правила доступа. */
  const allowedRoutes = me?.screens?.map((s) => s.route);
  const groups = allowedRoutes
    ? GROUPS.map((group) => ({
        ...group,
        items: group.items
          .map((item) => ({
            ...item,
            children: item.children?.filter((child) => allowedRoutes.includes(child.requires ?? child.route))
          }))
          .filter((item) => (!item.route || allowedRoutes.includes(item.route))
            || (item.children?.length ?? 0) > 0)
      })).filter((group) => group.items.length > 0)
    : GROUPS;

  /**
   * Активный подпункт — самый длинный совпавший маршрут: `#/documents/inbox`
   * точнее, чем `#/documents`, и подсвечиваться должен он, а не «Реестр».
   */
  let activeChildRoute: string | null = null;
  for (const group of groups) {
    for (const item of group.items) {
      for (const child of item.children ?? []) {
        if (matchesRoute(child.route, hash)
          && (!activeChildRoute || child.route.length > activeChildRoute.length)) {
          activeChildRoute = child.route;
        }
      }
    }
  }

  useEffect(() => {
    api.me().then(setMe).catch(() => setMe(null));
  }, []);

  // Группа с активным подпунктом раскрыта: свёрнутой её мог оставить пользователь.
  useEffect(() => {
    setOpenGroups((prev) => {
      let changed = false;
      const next = { ...prev };
      groups.forEach((group) => group.items.forEach((item) => {
        const childActive = (item.children ?? []).some((child) => matchesRoute(child.route, hash));
        if (childActive && next[item.title] === false) {
          next[item.title] = true;
          changed = true;
        }
      }));
      return changed ? next : prev;
    });
  }, [hash, me]);

  const ROUTES: Record<Screen, string> = {
    me: '#/me',
    analytics: '#/analytics',
    adminUsers: '#/admin/users',
    journey: '#/journey',
    hrDesk: '#/hr-desk',
    timesheet: '#/timesheet',
    surveyBuilder: '#/surveys/builder',
    portalManage: '#/portal/manage',
    directory: '#/directory',
    vacations: '#/vacations',
    hiring: '#/hiring',
    skillsManage: '#/skills/manage',
    myDay: '#/my-day',
    documentsSignature: '#/documents/signature',
    exec: '#/exec',
    overview: '#/',
    risk: '#/risk',
    employees: '#/employees',
    admin: '#/admin',
    projects: '#/projects',
    memos: '#/memos',
    surveys: '#/surveys',
    onboarding: '#/onboarding',
    skills: '#/skills',
    design: '#/design',
    documents: '#/documents'
  };
  const routeFor = (screen: Screen) => ROUTES[screen];

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="sb-head">
          <a className="sb-brand" href="#/risk" title="StaffSense">
            <span className="mark">S</span>
            <span className="nm">StaffSense</span>
          </a>
          <NotificationBell />
        </div>

        <button className="sb-search" type="button" title="Поиск появится позже">
          <span>Поиск</span>
          <span className="kbd">Ctrl K</span>
        </button>

        <nav className="sb-nav">
          {groups.map((group) => (
            <div className="sb-group" key={group.label}>
              <div className="sb-group-label">{group.label}</div>
              {group.items.map((item) => {
                const children = item.children ?? [];
                const hasChildren = children.length > 0;
                const childActive = children.some((child) => child.route === activeChildRoute);
                const isActive = item.route === routeFor(active) || childActive;
                const isOpen = openGroups[item.title] ?? childActive;
                const icon = NAV_ICONS[item.icon] ?? '';
                const className = 'sb-item' + (isActive ? ' is-active' : '') + (item.route ? '' : ' is-todo');

                if (!item.route) {
                  return (
                    <a
                      key={item.title}
                      className={className}
                      href="#"
                      title="Раздел ещё не перенесён в новый клиент"
                      onClick={(event) => event.preventDefault()}
                    >
                      <span className="ico" dangerouslySetInnerHTML={{ __html: icon }} />
                      <span>{item.title}</span>
                    </a>
                  );
                }

                return (
                  <div key={item.title}>
                    <a
                      className={className}
                      href={item.route}
                      title={item.title}
                      aria-expanded={hasChildren ? isOpen : undefined}
                      onClick={hasChildren
                        ? () => setOpenGroups((prev) => ({ ...prev, [item.title]: !isOpen }))
                        : undefined}
                    >
                      <span className="ico" dangerouslySetInnerHTML={{ __html: icon }} />
                      <span>{item.title}</span>
                      {hasChildren && (
                        <span
                          aria-hidden="true"
                          title={isOpen ? 'Свернуть' : 'Развернуть'}
                          style={{
                            marginLeft: 'auto',
                            fontSize: 9,
                            lineHeight: 1,
                            opacity: .6,
                            transition: 'transform .15s ease',
                            transform: isOpen ? 'rotate(90deg)' : 'none'
                          }}
                        >
                          ▶
                        </span>
                      )}
                    </a>

                    {hasChildren && isOpen && (
                      <div className="sb-sub">
                        {children.map((child) => (
                          <a
                            key={child.title}
                            className={'sb-subitem' + (child.route === activeChildRoute ? ' is-on' : '')}
                            href={child.route}
                          >
                            <span className="dot" />
                            <span className="truncate">{child.title}</span>
                          </a>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
        </nav>

        {me && (
          <div className="sb-foot">
            <div className="sb-user">
              <div className="ava ava-sm ava-1">{initials(me.fullName)}</div>
              <div style={{ minWidth: 0 }}>
                <div style={{ color: '#fff', fontWeight: 600, fontSize: 12.5, lineHeight: 1.3 }}>
                  {me.fullName}
                </div>
                <div style={{ color: 'var(--navy-ink-2)', fontSize: 11.4 }}>{me.role}</div>
              </div>
            </div>
          </div>
        )}
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="crumbs">
            <span>{crumbs[0]}</span>
            <span className="sep">/</span>
            <span className="here">{crumbs[1]}</span>
          </div>
          <div className="topbar-tools">{actions}</div>
        </header>
        <main className="page">{children}</main>
      </div>
    </div>
  );
}

function initials(fullName: string): string {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

/**
 * Колокол уведомлений.
 *
 * Внутрисистемные уведомления вместо отсутствующей рассылки: подпись,
 * визы, просрочки. Счётчик и список приходят с сервера, прочтение
 * отмечается там же — клиент не хранит состояние.
 */
function NotificationBell() {
  const [items, setItems] = useState<Array<{
    id: number; typeTitle: string; title: string; message: string | null;
    link: string | null; readAt: string | null; createdAt: string;
  }>>([]);
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);

  const load = () => {
    fetch('/api/v1/notifications?size=8', { credentials: 'same-origin' })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
      .then((page) => { setItems(page.content ?? []); setUnread(page.unread ?? 0); })
      .catch(() => { setItems([]); setUnread(0); });
  };

  useEffect(load, []);

  const markRead = async (id: number) => {
    const token = await fetch('/api/v1/csrf', { credentials: 'same-origin' }).then((r) => r.json());
    await fetch('/api/v1/notifications/' + id + '/read', {
      method: 'POST', credentials: 'same-origin',
      headers: { [token.headerName]: token.token }
    }).catch(() => null);
    load();
  };

  return (
    <div className="sb-bell">
      <button
        className="btn btn-sm btn-ghost"
        type="button"
        title="Уведомления"
        onClick={() => setOpen(!open)}
      >
        Уведомления{unread > 0 ? ' · ' + unread : ''}
      </button>

      {open && (
        <div className="card card-flush" style={{ position: 'absolute', zIndex: 60, width: 320, marginTop: 6 }}>
          <div className="card-hd"><h2 className="t-h3">Уведомления</h2></div>
          <div className="card-bd">
            {items.length === 0 && <div className="t-sm muted">Пока пусто</div>}
            {items.map((item) => (
              <div key={item.id} style={{ padding: '6px 0', borderBottom: '1px solid var(--line)' }}>
                <div className="t-xs muted">{item.typeTitle}</div>
                <div className="t-sm">
                  {item.link
                    ? <a href={item.link} onClick={() => markRead(item.id)}>{item.title}</a>
                    : item.title}
                </div>
                {item.message && <div className="t-xs muted">{item.message}</div>}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}