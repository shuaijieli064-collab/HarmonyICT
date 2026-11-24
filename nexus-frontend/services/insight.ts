import { request } from "@/lib/http";

/**
 * 洞察实验室 API（契约见 docs/proposal-insight-expansion.md）。
 * BIGINT 字段一律 string。
 */

export type EngineFamilyCode =
  | "FINANCE"
  | "SUBSCRIPTION"
  | "LEARNING"
  | "DEVICE"
  | "DATA_QUALITY"
  | "OTHER";

export interface EngineFamilySummary {
  family: EngineFamilyCode;
  label: string;
  total: number;
  high: number;
  medium: number;
  low: number;
}

export interface InsightOverview {
  totalOpen: number;
  families: EngineFamilySummary[];
}

export type TimelineKind = "event" | "expiry" | "deadline";
export type TimelineLevel = "high" | "medium" | "low";

export interface TimelineItem {
  date: string;
  kind: TimelineKind;
  title: string;
  level: TimelineLevel | null;
  entityId: string | null;
  refType: "fact" | "event";
  refId: string;
}

export interface TimelineResponse {
  days: number;
  count: number;
  items: TimelineItem[];
}

export type SimulationScenario = "cancel" | "keep";

export interface SimulationMonth {
  month: number;
  label: string;
  spend: number;
  cumulative: number;
}

export interface SimulationImpact {
  riskType: string;
  level: "high" | "medium" | "low";
  title: string;
  ruleCode: string;
}

export interface SimulationResponse {
  entityId: string;
  entityName: string;
  scenario: SimulationScenario;
  monthlyCost: number;
  months: SimulationMonth[];
  resolvedRisks: SimulationImpact[];
  introducedRisks: SimulationImpact[];
  recommendation: string;
  rationale: string[];
}

export function insightOverview(): Promise<InsightOverview> {
  return request<InsightOverview>("/api/v1/insight/overview");
}

export function insightTimeline(days = 30): Promise<TimelineResponse> {
  return request<TimelineResponse>("/api/v1/insight/timeline", { query: { days } });
}

export function simulateDecision(input: {
  entityId: string;
  scenario: SimulationScenario;
}): Promise<SimulationResponse> {
  return request<SimulationResponse>("/api/v1/decision/simulate", {
    method: "POST",
    body: input,
  });
}
