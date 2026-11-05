import { useEffect, useState } from 'react';
import { RiskPage } from './pages/RiskPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { AdminPage } from './pages/AdminPage';

type Route = 'risk' | 'employees' | 'admin';

/**
 * Маршрутизация на хеше: два экрана не требуют роутера с зависимостями,
 * а ссылка вида #/employees остаётся рабочей при перезагрузке.
 */
function currentRoute(): Route {
  const hash = window.location.hash.replace(/^#\/?/, '');
  if (hash === 'employees') return 'employees';
  if (hash === 'admin') return 'admin';
  return 'risk';
}

export function App() {
  const [route, setRoute] = useState<Route>(currentRoute);

  useEffect(() => {
    const onHashChange = () => setRoute(currentRoute());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  if (route === 'employees') return <EmployeesPage />;
  if (route === 'admin') return <AdminPage />;
  return <RiskPage />;
}
