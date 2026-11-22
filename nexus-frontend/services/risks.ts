/* 风险服务（契约 docs/api-specification.md 6，已切真实接口）：
 * 列表过滤、详情（含相关事实快照）、确认与解决状态流转。
 */

import { request } from "@/lib/http";
import type { PageResult, Risk, RiskDetail } from "@/types";

export interface ListRisksOptions {
  page?: number;
  pageSize?: number;
  level?: string;
  /** 默认 open（契约 6） */
  status?: string;
  riskType?: string;
}

/** 风险列表（契约 6） */
export function listRisks(options: ListRisksOptions = {}) {
  return request<PageResult<Risk>>("/api/v1/risks", { query: options });
}

/** 风险详情（契约 6：含 relatedFacts 相关事实快照，展示风险依据） */
export function getRiskDetail(id: string) {
  return request<RiskDetail>(`/api/v1/risks/${id}`);
}

/** 确认风险：status → acknowledged（契约 6） */
export function acknowledgeRisk(id: string) {
  return request<Risk>(`/api/v1/risks/${id}/acknowledge`, { method: "PUT" });
}

/** 解决风险：status → resolved（契约 6） */
export function resolveRisk(id: string) {
  return request<Risk>(`/api/v1/risks/${id}/resolve`, { method: "PUT" });
}