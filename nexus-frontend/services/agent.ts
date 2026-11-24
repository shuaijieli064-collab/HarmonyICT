/* Agent 编排服务（契约 docs/api-specification.md 8，已切真实接口）：
 * 发起执行、执行编排（异步）、L2 授权。
 * 注意：authorize(approve=true) 后后端立即真实执行动作（create_task / update_fact），
 * 调用方完成授权后须刷新任务列表与 run 详情。
 */

import { request } from "@/lib/http";
import type { AgentActionLog, AgentRun, PageResult, RunDetail } from "@/types";

export interface ListRunsOptions {
  page?: number;
  pageSize?: number;
  status?: string;
}

/** 执行记录列表（契约 8） */
export function listRuns(options: ListRunsOptions = {}) {
  return request<PageResult<AgentRun>>("/api/v1/agent/runs", { query: options });
}

/** 执行详情（契约 8：含 summary 与操作日志列表） */
export function getRunDetail(id: string) {
  return request<RunDetail>(`/api/v1/agent/runs/${id}`);
}

/** V3 执行日志分页查询（第②线 GET /{id}/logs，供 Agent Center 审计时间线）。
 * 字段与 run 详情内 logs 一致（含 audit 摘要）；level/result 过滤可选。
 * 只读接口：不触发执行、不改变 run 状态。 */
export function listRunLogs(
  id: string,
  options: { level?: string; result?: string; page?: number; pageSize?: number } = {}
) {
  return request<PageResult<AgentActionLog>>(`/api/v1/agent/runs/${id}/logs`, { query: options });
}

/** 发起执行（契约 8） */
export function createRun(input: { intent: string; triggerType?: string }) {
  return request<AgentRun>("/api/v1/agent/runs", { method: "POST", body: input });
}

/** 触发执行编排（异步，立即返回 run 当前快照） */
export function executeRun(id: string) {
  return request<AgentRun>(`/api/v1/agent/runs/${id}/execute`, { method: "POST" });
}

/** L2 操作授权（契约 8）：approve=true 后后端立即真实执行，调用方须刷新数据 */
export function authorizeRun(
  id: string,
  input: { logIds: string[]; approve: boolean }
) {
  return request<RunDetail>(`/api/v1/agent/runs/${id}/authorize`, { method: "POST", body: input });
}