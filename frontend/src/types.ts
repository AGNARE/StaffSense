/** Типы контракта /api/v1 — то, за чем и берём TypeScript. */

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RiskListItem {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  gradeLevel: string;
  riskScore: number;
  riskLevel: RiskLevel;
  computedAt: string | null;
  trendDelta: number | null;
  topFactor: string | null;
}

export interface RiskFeature {
  key: string;
  label: string;
  icon: string;
  value: number;
  status: string;
  color: string;
  description: string;
}

export interface RiskFactor {
  factor: string;
  impact: number;
  description: string;
}

export interface TrendPoint {
  at: string;
  score: number;
}

export interface RiskTrend {
  polyline: string;
  minScore: number;
  maxScore: number;
  delta: number;
  points: TrendPoint[];
}

export interface RiskDetail {
  userId: number;
  fullName: string;
  department: string | null;
  position: string | null;
  gradeLevel: string;
  riskScore: number | null;
  riskLevel: RiskLevel;
  computedAt: string | null;
  features: RiskFeature[];
  factors: RiskFactor[];
  trend: RiskTrend;
  /** ML | HEURISTIC | SEED — чем посчитан текущий прогноз. */
  source: string | null;
}

export interface Thresholds {
  low: number;
  medium: number;
  updatedAt: string | null;
  usingDefaults: boolean;
}

export interface EmployeeItem {
  userId: number;
  fullName: string;
  email: string;
  department: string | null;
  position: string | null;
  gradeLevel: string;
  managerName: string | null;
  hireDate: string | null;
  active: boolean;
}

export interface EmployeeRisk {
  riskScore: number | null;
  riskLevel: RiskLevel;
  computedAt: string | null;
}

export interface EmployeeDetail extends EmployeeItem {
  weeklyCapacityHours: number | null;
  risk: EmployeeRisk | null;
}

export interface Departments {
  items: string[];
}

export interface JournalEntry {
  at: string;
  userId: number;
  fullName: string;
  department: string | null;
  riskScore: number;
  riskLevel: RiskLevel;
  source: string;
}

export interface JournalCoverage {
  totalSnapshots: number;
  heuristicSnapshots: number;
  seedSnapshots: number;
  employeesWithoutHistory: number;
  lastComputedAt: string | null;
}

export interface Journal {
  period: string;
  from: string;
  to: string;
  total: number;
  low: number;
  medium: number;
  high: number;
  distinctUsers: number;
  latest: JournalEntry[];
  coverage: JournalCoverage;
}
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';

export interface TaskCounts {
  todo: number;
  inProgress: number;
  review: number;
  done: number;
  total: number;
  progressPercent: number;
}

export interface ProjectCard {
  id: number;
  name: string;
  description: string | null;
  ownerName: string | null;
  createdAt: string | null;
  counts: TaskCounts;
}

export interface BoardTask {
  id: number;
  title: string;
  status: TaskStatus;
  priority: string | null;
  projectId: number | null;
  assigneeId: number | null;
  assigneeName: string | null;
  estimateHours: number | null;
  dueDate: string | null;
  createdAt: string | null;
  completedAt: string | null;
  allowedTransitions: TaskStatus[];
}

export interface ProjectBoard extends ProjectCard {
  tasks: BoardTask[];
}