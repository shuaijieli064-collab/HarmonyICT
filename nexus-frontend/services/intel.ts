/* 情报中心服务（GET /api/v1/intel，只读聚合接口）：
 * 风险 / 事件 / Agent 执行三类动态的统一情报流与汇总统计。
 */

import { request } from "@/lib/http";
import type { IntelFeedItem, IntelSummary, PageResult } from "@/types";

/** 汇总统计：风险分布、近 7 天事件、未完成任务、近 7 天 Agent 执行 */
export function getIntelSummary() {
  return request<IntelSummary>("/api/v1/intel/summary");
}

export interface ListIntelFeedOptions {
  page?: number;
  pageSize?: number;
  /** all/risk/event/agent，其他取值后端按 all 处理 */
  type?: string;
  /** 最近天数窗口（1-90，越界后端收敛为 30） */
  days?: number;
}

/** 情报流（按时间倒序合并分页） */
export function getIntelFeed(options: ListIntelFeedOptions = {}) {
  return request<PageResult<IntelFeedItem>>("/api/v1/intel/feed", { query: options });
}
