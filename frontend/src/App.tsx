import { useEffect, useState } from 'react';
import { RiskPage } from './pages/RiskPage';
import { EmployeesPage } from './pages/EmployeesPage';
import { AdminPage } from './pages/AdminPage';
import { RiskDetailPage } from './pages/RiskDetailPage';
import { ModelPage } from './pages/ModelPage';
import { SimulatorPage } from './pages/SimulatorPage';
import { CabinetPage } from './pages/CabinetPage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { MyJourneyPage } from './pages/MyJourneyPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { ProjectBoardPage } from './pages/ProjectBoardPage';
import { MemosPage } from './pages/MemosPage';
import { SurveysPage } from './pages/SurveysPage';
import { OnboardingPage } from './pages/OnboardingPage';
import { OverviewPage } from './pages/OverviewPage';
import { SkillsPage } from './pages/SkillsPage';
import { DesignSystemPage } from './pages/DesignSystemPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { DocumentsInboxPage } from './pages/DocumentsInboxPage';
import { DocumentCardPage } from './pages/DocumentCardPage';

type Route =
  | { name: 'overview' }
  | { name: 'risk' }
  | { name: 'employees' }
  | { name: 'admin' }
  | { name: 'riskDetail'; userId: number }
  | { name: 'model' }
  | { name: 'simulate' }
  | { name: 'me' }
  | { name: 'analytics' }
  | { name: 'adminUsers' }
  | { name: 'journey' }
  | { name: 'projects' }
  | { name: 'projectBoard'; projectId: number }
  | { name: 'memos' }
  | { name: 'surveys' }
  | { name: 'onboarding' }
  | { name: 'skills' }
  | { name: 'design' }
  | { name: 'documents' }
  | { name: 'documentsInbox' }
  | { name: 'documentCard'; documentId: number };

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
  if (hash === 'skills') return { name: 'skills' };
  if (hash === 'design') return { name: 'design' };
  if (hash === 'documents') return { name: 'documents' };
  if (hash === 'documents/inbox') return { name: 'documentsInbox' };
  if (hash.startsWith('documents/')) {
    const id = Number(hash.slice('documents/'.length));
    if (Number.isFinite(id) && id > 0) return { name: 'documentCard', documentId: id };
  }
  if (hash === 'risk') return { name: 'risk' };
  if (hash === 'model') return { name: 'model' };
  if (hash === 'simulate') return { name: 'simulate' };
  if (hash === 'admin/users') return { name: 'adminUsers' };
  if (hash === 'journey') return { name: 'journey' };
  if (hash === 'analytics') return { name: 'analytics' };
  if (hash === 'me' || hash === '') return { name: 'me' };

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
    case 'model':
      return <ModelPage />;
    case 'simulate':
      return <SimulatorPage />;
    case 'me':
      return <CabinetPage />;
    case 'analytics':
      return <AnalyticsPage />;
    case 'adminUsers':
      return <AdminUsersPage />;
    case 'journey':
      return <MyJourneyPage />;
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
    case 'skills':
      return <SkillsPage />;
    case 'design':
      return <DesignSystemPage />;
    case 'documents':
      return <DocumentsPage />;
    case 'documentsInbox':
      return <DocumentsInboxPage />;
    case 'documentCard':
      return <DocumentCardPage documentId={route.documentId} />;
    case 'risk':
      return <RiskPage />;
    default:
      return <OverviewPage />;
  }
}
