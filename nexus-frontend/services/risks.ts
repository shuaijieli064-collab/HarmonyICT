/* 风险服务（契约 docs/api-specification.md 6，已切真实接口）：
 * 列表过滤、详情（含相关事实快照）、确认与解决状态流转。
 */

import { request } from "@/lib/http";
import type { PageResult, Risk, RiskDetail, RiskTrendPoint } from "@/types";

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

/** 手动触发一次全量风险扫描（演示用：避免等待 60s 定时周期），返回本轮新增风险数 */
export function triggerRiskScan() {
  return request<{ created: number }>("/api/v1/risks/scan", { method: "POST" });
}

/** 近 N 天风险趋势（快照数据，缺失日期补 0；扩展 B 组） */
export function getRiskTrend(days = 7) {
  return request<RiskTrendPoint[]>("/api/v1/risks/trend", { query: { days } });
}