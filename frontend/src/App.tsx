import { useEffect, useState } from 'react';
import { RiskPage } from './pages/RiskPage';
import { EmployeesPage } from './pages/EmployeesPage';

type Route = 'risk' | 'employees';

/**
 * Маршрутизация на хеше: два экрана не требуют роутера с зависимостями,
 * а ссылка вида #/employees остаётся рабочей при перезагрузке.
 */
function currentRoute(): Route {
  const hash = window.location.hash.replace(/^#\/?/, '');
  return hash === 'employees' ? 'employees' : 'risk';
}

export function App() {
  const [route, setRoute] = useState<Route>(currentRoute);

  useEffect(() => {
    const onHashChange = () => setRoute(currentRoute());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  return route === 'employees' ? <EmployeesPage /> : <RiskPage />;
}
