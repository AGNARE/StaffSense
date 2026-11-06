import { useEffect, useState } from 'react';
import { NAV_ICONS } from '../icons';
import { api } from '../api';

export type Screen = 'risk' | 'employees' | 'admin' | 'projects';

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
      { title: 'Обзор', icon: 'Обзор компании' },
      { title: 'Кадры', icon: 'Кадровый процесс', route: '#/employees' },
      { title: 'Портал', icon: 'Портал онбординга' },
      { title: 'Опросы', icon: 'Опросы и благополучие' },
      { title: 'Записки', icon: 'Служебные записки' }
    ]
  },
  {
    label: 'Работа',
    items: [
      { title: 'Проекты', icon: 'Управление проектами', route: '#/projects' },
      { title: 'Риски увольнений', icon: 'Прогноз увольнений', route: '#/risk' },
      { title: 'Документы', icon: 'Документооборот' }
    ]
  },
  {
    label: 'Система',
    items: [
      { title: 'ИИ-скиллы', icon: 'AI Skill Hub' },
      { title: 'Администрирование', icon: 'Администрирование', route: '#/admin' },
      { title: 'Дизайн-система', icon: 'Дизайн-система' }
    ]
  }
];

interface Me {
  id: number;
  fullName: string;
  role: string;
}

/** Боковая навигация и верхняя панель — общий каркас всех экранов. */
export function Shell(props: {
  active: Screen;
  crumbs: [string, string];
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  const { active, crumbs, actions, children } = props;
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    api.me().then(setMe).catch(() => setMe(null));
  }, []);

  const ROUTES: Record<Screen, string> = {
    risk: '#/risk',
    employees: '#/employees',
    admin: '#/admin',
    projects: '#/projects'
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
          {GROUPS.map((group) => (
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

                return (
                  <a key={item.title} className={className} href={item.route} title={item.title}>
                    <span className="ico" dangerouslySetInnerHTML={{ __html: icon }} />
                    <span>{item.title}</span>
                  </a>
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
