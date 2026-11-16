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
  | 'hiring';

interface NavItem {
  title: string;
  /** ключ иконки — совпадает с title в прототипе (см. icons.ts) */
  icon: string;
  /** hash-маршрут; не задан — раздел ещё не перенесён в новый клиент */
  route?: string;
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
      { title: 'Мой путь', icon: 'Кадровый процесс', route: '#/journey' },
      { title: 'Обзор', icon: 'Обзор компании', route: '#/' },
      { title: 'Кадры', icon: 'Кадровый процесс', route: '#/employees' },
      { title: 'Пульт HR', icon: 'Кадровый процесс', route: '#/hr-desk' },
      { title: 'Табель', icon: 'Кадровый процесс', route: '#/timesheet' },
      { title: 'Отпуска', icon: 'Кадровый процесс', route: '#/vacations' },
      { title: 'Портал', icon: 'Портал онбординга', route: '#/onboarding' },
      { title: 'Управление порталом', icon: 'Портал онбординга', route: '#/portal/manage' },
      { title: 'Справочник', icon: 'Кадровый процесс', route: '#/directory' },
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
      { title: 'Риски увольнений', icon: 'Прогноз увольнений', route: '#/risk' },
      { title: 'Документы', icon: 'Документооборот', route: '#/documents' }
    ]
  },
  {
    label: 'Система',
    items: [
      { title: 'ИИ-скиллы', icon: 'AI Skill Hub', route: '#/skills' },
      { title: 'Администрирование', icon: 'Администрирование', route: '#/admin' },
      { title: 'Пользователи', icon: 'Администрирование', route: '#/admin/users' },
      { title: 'Дизайн-система', icon: 'Дизайн-система', route: '#/design' }
    ]
  }
];

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
  const hashNow = typeof window !== 'undefined' ? window.location.hash : '';
  const activeScreen = hashNow.indexOf('#/model') === 0 ? 'model'
    : hashNow.indexOf('#/simulate') === 0 ? 'simulate' : 'list';
  const [me, setMe] = useState<Me | null>(null);

  /** Меню строится по списку с сервера: клиент не дублирует правила доступа. */
  const allowedRoutes = me?.screens?.map((s) => s.route);
  const groups = allowedRoutes
    ? GROUPS.map((g) => ({ ...g, items: g.items.filter((i) => !i.route || allowedRoutes.includes(i.route)) }))
        .filter((g) => g.items.length > 0)
    : GROUPS;

  useEffect(() => {
    api.me().then(setMe).catch(() => setMe(null));
  }, []);

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
                const isActive = item.route === routeFor(active);
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

                const isRisk = item.route === ROUTES.risk;

                return (
                  <div key={item.title}>
                    <a className={className} href={item.route} title={item.title}>
                      <span className="ico" dangerouslySetInnerHTML={{ __html: icon }} />
                      <span>{item.title}</span>
                    </a>

                    {/* Подменю раздела рисков — как в прототипе: радар и модель */}
                    {isRisk && active === 'risk' && (
                      <div className="sb-sub" style={{ display: 'block' }}>
                        <a
                          className={'sb-subitem' + (activeScreen === 'list' ? ' is-on' : '')}
                          href="#/risk"
                        >
                          <span className="dot" />
                          <span className="truncate">Радар рисков</span>
                        </a>
                        <a
                          className={'sb-subitem' + (activeScreen === 'model' ? ' is-on' : '')}
                          href="#/model"
                        >
                          <span className="dot" />
                          <span className="truncate">ML-модель</span>
                        </a>
                        <a
                          className={'sb-subitem' + (activeScreen === 'simulate' ? ' is-on' : '')}
                          href="#/simulate"
                        >
                          <span className="dot" />
                          <span className="truncate">Симулятор удержания</span>
                        </a>
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
