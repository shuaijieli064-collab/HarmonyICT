/* 资产中心服务（GET /api/v1/assets，只读聚合接口）：
 * 资产 = 世界实体 + 其 active 事实，后端聚合，前端只做展示。
 */

import { request } from "@/lib/http";
import type { Asset, AssetSummary, PageResult } from "@/types";

export interface ListAssetsOptions {
  page?: number;
  pageSize?: number;
  entityType?: string;
  keyword?: string;
}

/** 资产列表（实体分页 + 关联事实聚合，按 createdAt 倒序） */
export function listAssets(options: ListAssetsOptions = {}) {
  return request<PageResult<Asset>>("/api/v1/assets", { query: options });
}

/** 资产汇总：按类型统计、价格类事实求和（unit 为众数单位）、未关闭风险数 */
export function getAssetSummary() {
  return request<AssetSummary>("/api/v1/assets/summary");
}
