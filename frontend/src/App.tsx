import { useEffect, useState } from 'react';
import { RiskPage } from './pages/RiskPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { AdminPage } from './pages/AdminPage';
import { RiskDetailPage } from './pages/RiskDetailPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { ProjectBoardPage } from './pages/ProjectBoardPage';
import { MemosPage } from './pages/MemosPage';
import { SurveysPage } from './pages/SurveysPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { OverviewPage } from './pages/OverviewPage';

type Route =
  | { name: 'overview' }
  | { name: 'risk' }
  | { name: 'employees' }
  | { name: 'admin' }
  | { name: 'riskDetail'; userId: number }
  | { name: 'projects' }
  | { name: 'projectBoard'; projectId: number }
  | { name: 'memos' }
  | { name: 'surveys' }
  | { name: 'onboarding' };

/**
 * Маршрутизация на хеше: #/risk, #/risk/12, #/employees, #/admin,
 * #/projects, #/projects/3. Без внешнего роутера — шесть экранов не стоят
 * лишней зависимости, а ссылки остаются рабочими при перезагрузке.
 */
function parseRoute(): Route {
  const hash = window.location.hash.replace(/^#\/?/, '');
  if (hash === 'employees') return { name: 'employees' };
  if (hash === 'admin') return { name: 'admin' };
  if (hash === 'projects') return { name: 'projects' };
  if (hash === 'memos') return { name: 'memos' };
  if (hash === 'surveys') return { name: 'surveys' };
  if (hash === 'onboarding') return { name: 'onboarding' };

  const riskDetail = hash.match(/^risk\/(\d+)$/);
  if (riskDetail) return { name: 'riskDetail', userId: Number(riskDetail[1]) };

  const board = hash.match(/^projects\/(\d+)$/);
  if (board) return { name: 'projectBoard', projectId: Number(board[1]) };

  return { name: 'overview' };
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
    case 'projects':
      return <ProjectsPage />;
    case 'projectBoard':
      return <ProjectBoardPage projectId={route.projectId} />;
    case 'memos':
      return <MemosPage />;
    case 'surveys':
      return <SurveysPage />;
    case 'onboarding':
      return <OnboardingPage />;
    case 'risk':
      return <RiskPage />;
    default:
      return <OverviewPage />;
  }
}
