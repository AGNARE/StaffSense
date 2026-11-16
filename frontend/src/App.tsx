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
import { HrDeskPage } from './pages/HrDeskPage';
import { TimesheetPage } from './pages/TimesheetPage';
import { SurveyBuilderPage } from './pages/SurveyBuilderPage';
import { PortalManagePage } from './pages/PortalManagePage';
import { DirectoryPage } from './pages/DirectoryPage';
import { VacationsPage } from './pages/VacationsPage';
import { HiringPage } from './pages/HiringPage';
import { SkillCardPage } from './pages/SkillCardPage';
import { SkillsManagePage } from './pages/SkillsManagePage';
import { MyDayPage } from './pages/MyDayPage';
import { SignatureQueuePage } from './pages/SignatureQueuePage';
import { ProjectTimelinePage } from './pages/ProjectTimelinePage';
import { WorkloadChartPage } from './pages/WorkloadChartPage';

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
  | { name: 'documentCard'; documentId: number }
  | { name: 'hrDesk' }
  | { name: 'timesheet' }
  | { name: 'surveyBuilder' }
  | { name: 'portalManage' }
  | { name: 'directory' }
  | { name: 'vacations' }
  | { name: 'hiring' }
  | { name: 'skillCard'; skillId: number }
  | { name: 'skillsManage' }
  | { name: 'myDay' }
  | { name: 'documentsSignature' }
  | { name: 'projectPlan'; projectId: number }
  | { name: 'analyticsWorkload' };

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
  if (hash === 'documents/signature') return { name: 'documentsSignature' };
  if (hash === 'hr-desk') return { name: 'hrDesk' };
  if (hash === 'timesheet') return { name: 'timesheet' };
  if (hash === 'surveys/builder' || /^surveys\/builder\/\d+$/.test(hash)) return { name: 'surveyBuilder' };
  if (hash === 'portal/manage') return { name: 'portalManage' };
  if (hash === 'directory') return { name: 'directory' };
  if (hash === 'vacations') return { name: 'vacations' };
  if (hash === 'hiring') return { name: 'hiring' };
  if (hash === 'skills/manage') return { name: 'skillsManage' };
  if (hash === 'my-day') return { name: 'myDay' };
  const skillCard = hash.match(/^skills\/(\d+)$/);
  if (skillCard) return { name: 'skillCard', skillId: Number(skillCard[1]) };
  if (hash.startsWith('documents/')) {
    const id = Number(hash.slice('documents/'.length));
    if (Number.isFinite(id) && id > 0) return { name: 'documentCard', documentId: id };
  }
  if (hash === 'risk') return { name: 'risk' };
  if (hash === 'model') return { name: 'model' };
  if (hash === 'simulate') return { name: 'simulate' };
  if (hash === 'admin/users') return { name: 'adminUsers' };
  if (hash === 'journey') return { name: 'journey' };
  if (hash === 'analytics/workload') return { name: 'analyticsWorkload' };
  if (hash === 'analytics') return { name: 'analytics' };
  if (hash === 'me' || hash === '') return { name: 'me' };

  const riskDetail = hash.match(/^risk\/(\d+)$/);
  if (riskDetail) return { name: 'riskDetail', userId: Number(riskDetail[1]) };

  const plan = hash.match(/^projects\/(\d+)\/plan$/);
  if (plan) return { name: 'projectPlan', projectId: Number(plan[1]) };

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
    case 'hrDesk':
      return <HrDeskPage />;
    case 'surveyBuilder':
      return <SurveyBuilderPage />;
    case 'portalManage':
      return <PortalManagePage />;
    case 'skillCard':
      return <SkillCardPage skillId={route.skillId} />;
    case 'skillsManage':
      return <SkillsManagePage />;
    case 'projectPlan':
      return <ProjectTimelinePage projectId={route.projectId} />;
    case 'analyticsWorkload':
      return <WorkloadChartPage />;
    case 'documentsSignature':
      return <SignatureQueuePage />;
    case 'myDay':
      return <MyDayPage />;
    case 'hiring':
      return <HiringPage />;
    case 'vacations':
      return <VacationsPage />;
    case 'directory':
      return <DirectoryPage />;
    case 'timesheet':
      return <TimesheetPage />;
    case 'documentCard':
      return <DocumentCardPage documentId={route.documentId} />;
    case 'risk':
      return <RiskPage />;
    default:
      return <OverviewPage />;
  }
}
