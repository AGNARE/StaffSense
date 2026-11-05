import { useEffect, useState } from 'react';
import { RiskPage } from './pages/RiskPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { AdminPage } from './pages/AdminPage';
import { RiskDetailPage } from './pages/RiskDetailPage';

type Route =
  | { name: 'risk' }
  | { name: 'employees' }
  | { name: 'admin' }
  | { name: 'riskDetail'; userId: number };

/**
 * Маршрутизация на хеше: #/risk, #/risk/12, #/employees, #/admin.
 * Без внешнего роутера — четыре экрана не стоят лишней зависимости,
 * а ссылки остаются рабочими при перезагрузке страницы.
 */
function parseRoute(): Route {
  const hash = window.location.hash.replace(/^#\/?/, '');
  if (hash === 'employees') return { name: 'employees' };
  if (hash === 'admin') return { name: 'admin' };

  const detail = hash.match(/^risk\/(\d+)$/);
  if (detail) return { name: 'riskDetail', userId: Number(detail[1]) };

  return { name: 'risk' };
}

export function App() {
  const [route, setRoute] = useState<Route>(parseRoute);

  useEffect(() => {
    const onHashChange = () => setRoute(parseRoute());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [route]);

  switch (route.name) {
    case 'employees':
      return <EmployeesPage />;
    case 'admin':
      return <AdminPage />;
    case 'riskDetail':
      return <RiskDetailPage userId={route.userId} />;
    default:
      return <RiskPage />;
  }
}
