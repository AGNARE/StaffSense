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
