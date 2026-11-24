/* 驾驶舱聚合统计服务（体量扩充新增）。
 * 对应后端 GET /api/v1/dashboard/summary：一次返回概览计数 + 风险分布 + 近期事件/风险摘要。
 */

import { request } from "@/lib/http";
import type { DashboardSummary, HealthScore } from "@/types";

/** 当前用户的世界模型 / 风险 / 任务 / 通知概览 */
export function getDashboardSummary() {
  return request<DashboardSummary>("/api/v1/dashboard/summary");
}

/** 世界模型健康度评分（数据新鲜度 40 + 风险状况 40 + 任务完成 20） */
export function getHealthScore() {
  return request<HealthScore>("/api/v1/dashboard/health-score");
}
